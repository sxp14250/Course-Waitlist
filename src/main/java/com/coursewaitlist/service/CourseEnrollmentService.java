package com.coursewaitlist.service;

import com.coursewaitlist.domain.CourseOffering;
import com.coursewaitlist.domain.DropResult;
import com.coursewaitlist.domain.EnrollmentResult;
import com.coursewaitlist.domain.EnrollmentSnapshot;
import com.coursewaitlist.domain.Learner;
import com.coursewaitlist.domain.ResultCode;
import com.coursewaitlist.repository.LearnerDirectory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedSet;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Enrollment, drop and FIFO waitlist promotion for <strong>one</strong> course offering.
 *
 * <h2>Service boundary</h2>
 * One instance owns the roster and waitlist of exactly one {@link CourseOffering}.
 * Learner data is read through {@link LearnerDirectory}; this class never writes it.
 *
 * <h2>Invariants (held after every public method returns)</h2>
 * <ol>
 *   <li>{@code enrolled.size() <= capacity}</li>
 *   <li>A learner id appears at most once across {@code enrolled} and {@code waitlist}
 *       combined (no duplicates, never both enrolled and waiting).</li>
 *   <li>If a seat is free, the waitlist is empty (nobody waits while a seat sits idle).</li>
 *   <li>Every enrolled learner was eligible at the moment they were seated.</li>
 * </ol>
 *
 * <h2>Waitlist policy (documented assumption)</h2>
 * <ul>
 *   <li>Ineligible learners are rejected up front and <em>never</em> join the waitlist.</li>
 *   <li>Eligibility is re-checked at promotion time. A learner who reaches the head of the
 *       queue but is no longer eligible (or no longer exists in the directory) is
 *       <strong>skipped and removed</strong> from the waitlist; promotion continues with
 *       the next learner. The skipped ids are reported in {@link DropResult}. A removed
 *       learner may re-request and joins the back of the queue.</li>
 *   <li>A drop by a <em>waitlisted</em> learner just removes them; no seat changes hands.</li>
 * </ul>
 *
 * <h2>Concurrency</h2>
 * All reads and writes of roster/waitlist state happen while holding a single
 * {@link ReentrantLock}. Each request is therefore atomic and linearizable: the
 * check-then-act sequences (capacity check → seat, drop → promote) can never interleave.
 * The lock is per offering, so contention is bounded to one course section.
 */
public final class CourseEnrollmentService {

    private final CourseOffering offering;
    private final LearnerDirectory learnerDirectory;

    /** Guards {@link #enrolled} and {@link #waitlist}. */
    private final ReentrantLock lock = new ReentrantLock();

    /** Seated learner ids in enrollment order. Insertion-ordered set gives O(1) membership. */
    private final SequencedSet<String> enrolled = new LinkedHashSet<>();

    /** Waiting learner ids; first element is the head of the FIFO queue. */
    private final SequencedSet<String> waitlist = new LinkedHashSet<>();

    /**
     * @param offering         the single offering this service manages
     * @param learnerDirectory source of learner records used for eligibility checks
     */
    public CourseEnrollmentService(CourseOffering offering, LearnerDirectory learnerDirectory) {
        this.offering = Objects.requireNonNull(offering, "offering must not be null");
        this.learnerDirectory = Objects.requireNonNull(learnerDirectory, "learnerDirectory must not be null");
    }

    /** @return the offering managed by this service */
    public CourseOffering offering() {
        return offering;
    }

    // =====================================================================================
    // Enroll
    // =====================================================================================

    /**
     * Requests a seat for a learner.
     *
     * <p>Evaluation order: validate id → duplicate check → learner lookup → prerequisite
     * rule → seat if capacity remains, otherwise append to waitlist. The duplicate check
     * runs before eligibility so a repeated request always reports the learner's current
     * standing rather than a misleading error.
     *
     * @param learnerId the requesting learner
     * @return the outcome; never {@code null} and never throws for bad input
     */
    public EnrollmentResult enroll(String learnerId) {
        if (isBlank(learnerId)) {
            return EnrollmentResult.of(ResultCode.INVALID_REQUEST, learnerId, "Learner id must not be blank.");
        }
        lock.lock();
        try {
            // 1. Idempotency: duplicates never create a second record.
            if (enrolled.contains(learnerId)) {
                return EnrollmentResult.of(ResultCode.ALREADY_ENROLLED, learnerId,
                        learnerId + " is already enrolled in " + offering.offeringId() + ".");
            }
            if (waitlist.contains(learnerId)) {
                int position = positionOf(learnerId);
                return new EnrollmentResult(ResultCode.ALREADY_WAITLISTED, learnerId, position,
                        learnerId + " is already on the waitlist at position " + position + ".");
            }

            // 2. Learner must exist and satisfy the prerequisite rule.
            Optional<Learner> learner = learnerDirectory.findById(learnerId);
            if (learner.isEmpty()) {
                return EnrollmentResult.of(ResultCode.UNKNOWN_LEARNER, learnerId,
                        "No learner with id " + learnerId + ".");
            }
            if (!offering.isEligible(learner.get())) {
                return EnrollmentResult.of(ResultCode.PREREQUISITE_NOT_MET, learnerId,
                        learnerId + " has not completed all prerequisites " + offering.prerequisites() + ".");
            }

            // 3. Seat or queue.
            if (enrolled.size() < offering.capacity()) {
                enrolled.addLast(learnerId);
                verifyInvariants();
                return EnrollmentResult.of(ResultCode.ENROLLED, learnerId,
                        learnerId + " enrolled in " + offering.offeringId() + ".");
            }
            waitlist.addLast(learnerId);
            verifyInvariants();
            int position = waitlist.size();
            return new EnrollmentResult(ResultCode.WAITLISTED, learnerId, position,
                    offering.offeringId() + " is full; " + learnerId + " waitlisted at position " + position + ".");
        } finally {
            lock.unlock();
        }
    }

    // =====================================================================================
    // Drop
    // =====================================================================================

    /**
     * Drops a learner from the offering.
     *
     * <ul>
     *   <li>Enrolled → seat released, then {@link #promoteNextEligible()} fills it.</li>
     *   <li>Waitlisted → removed from the queue; no promotion (no seat was freed).</li>
     *   <li>Neither (unknown or repeated drop) → {@link ResultCode#NOT_REGISTERED}, no state change.</li>
     * </ul>
     *
     * <p>Unknown-to-directory learners are still allowed to drop if they hold a seat or a
     * queue position, so a deleted learner record can never leave a seat stranded.
     *
     * @param learnerId the learner leaving
     * @return the outcome; never {@code null} and never throws for bad input
     */
    public DropResult drop(String learnerId) {
        if (isBlank(learnerId)) {
            return DropResult.of(ResultCode.INVALID_REQUEST, learnerId, "Learner id must not be blank.");
        }
        lock.lock();
        try {
            if (enrolled.remove(learnerId)) {
                Promotion promotion = promoteNextEligible();
                verifyInvariants();
                return new DropResult(ResultCode.DROPPED, learnerId, promotion.promoted(),
                        promotion.skipped(), describeDrop(learnerId, promotion));
            }
            if (waitlist.remove(learnerId)) {
                verifyInvariants();
                return DropResult.of(ResultCode.LEFT_WAITLIST, learnerId,
                        learnerId + " left the waitlist for " + offering.offeringId() + ".");
            }
            return DropResult.of(ResultCode.NOT_REGISTERED, learnerId,
                    learnerId + " is not enrolled or waitlisted in " + offering.offeringId() + ".");
        } finally {
            lock.unlock();
        }
    }

    /**
     * Fills one freed seat from the head of the waitlist.
     *
     * <p>Walks the queue from the head. Each learner is re-checked against the
     * directory: an eligible learner is seated and the walk stops; an ineligible or
     * missing learner is removed and recorded as skipped. If the queue empties, the
     * seat stays open. Must be called while holding {@link #lock}.
     *
     * @return who was promoted (if anyone) and who was skipped
     */
    private Promotion promoteNextEligible() {
        List<String> skipped = new ArrayList<>();
        while (!waitlist.isEmpty() && enrolled.size() < offering.capacity()) {
            String candidateId = waitlist.removeFirst();
            boolean eligible = learnerDirectory.findById(candidateId)
                    .map(offering::isEligible)
                    .orElse(false);
            if (eligible) {
                enrolled.addLast(candidateId);
                return new Promotion(Optional.of(candidateId), skipped);
            }
            skipped.add(candidateId); // policy: skip AND remove
        }
        return new Promotion(Optional.empty(), skipped);
    }

    /** Internal pairing of a promotion outcome. */
    private record Promotion(Optional<String> promoted, List<String> skipped) { }

    private String describeDrop(String learnerId, Promotion promotion) {
        StringBuilder message = new StringBuilder(learnerId).append(" dropped ").append(offering.offeringId()).append('.');
        if (!promotion.skipped().isEmpty()) {
            message.append(" Removed ineligible from waitlist: ").append(promotion.skipped()).append('.');
        }
        promotion.promoted().ifPresentOrElse(
                id -> message.append(" Promoted ").append(id).append(" from the waitlist."),
                () -> message.append(" Seat is now open."));
        return message.toString();
    }

    // =====================================================================================
    // Queries
    // =====================================================================================

    /** @return a consistent, immutable view of the current roster and waitlist */
    public EnrollmentSnapshot snapshot() {
        lock.lock();
        try {
            return new EnrollmentSnapshot(offering.offeringId(), offering.capacity(),
                    List.copyOf(enrolled), List.copyOf(waitlist));
        } finally {
            lock.unlock();
        }
    }

    // =====================================================================================
    // Helpers
    // =====================================================================================

    /** @return 1-based position of a learner on the waitlist. Caller must hold the lock. */
    private int positionOf(String learnerId) {
        int position = 1;
        for (String id : waitlist) {
            if (id.equals(learnerId)) {
                return position;
            }
            position++;
        }
        return 0;
    }

    /**
     * Defensive guard for the class invariants. Cheap (O(n) on small collections) and
     * throws immediately if a future change ever breaks them, rather than letting
     * corrupted state leak out. Caller must hold the lock.
     */
    private void verifyInvariants() {
        if (enrolled.size() > offering.capacity()) {
            throw new IllegalStateException("Over capacity: " + enrolled.size() + " > " + offering.capacity());
        }
        for (String id : waitlist) {
            if (enrolled.contains(id)) {
                throw new IllegalStateException("Learner both enrolled and waitlisted: " + id);
            }
        }
        if (enrolled.size() < offering.capacity() && !waitlist.isEmpty()) {
            throw new IllegalStateException("Seat open while learners wait: " + waitlist);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
