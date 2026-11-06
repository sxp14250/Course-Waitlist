package com.coursewaitlist.service;

import com.coursewaitlist.domain.CourseOffering;
import com.coursewaitlist.domain.DropResult;
import com.coursewaitlist.domain.EnrollmentResult;
import com.coursewaitlist.domain.EnrollmentSnapshot;
import com.coursewaitlist.domain.Learner;
import com.coursewaitlist.domain.ResultCode;
import com.coursewaitlist.repository.InMemoryLearnerDirectory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioural tests for {@link CourseEnrollmentService}, grouped by acceptance criterion.
 *
 * <p>Fixture: offering CS201 with capacity 2 and prerequisite CS101.
 * Learners {@code A..F} have CS101; {@code NOPRE} does not.
 */
@DisplayName("CourseEnrollmentService")
class CourseEnrollmentServiceTest {

    private static final int CAPACITY = 2;

    private InMemoryLearnerDirectory directory;
    private CourseEnrollmentService service;

    @BeforeEach
    void setUp() {
        directory = new InMemoryLearnerDirectory();
        for (String id : List.of("A", "B", "C", "D", "E", "F")) {
            directory.save(Learner.of(id, "Learner " + id, "CS101"));
        }
        directory.save(Learner.of("NOPRE", "No Prerequisite"));
        service = new CourseEnrollmentService(
                new CourseOffering("CS201-01", "CS201", CAPACITY, Set.of("CS101")), directory);
    }

    /** Fills every seat with A and B. */
    private void fillCourse() {
        service.enroll("A");
        service.enroll("B");
    }

    /** Asserts the snapshot exactly matches the expected roster and waitlist. */
    private void assertState(List<String> enrolled, List<String> waitlist) {
        EnrollmentSnapshot s = service.snapshot();
        assertAll(
                () -> assertEquals(enrolled, s.enrolled(), "enrolled"),
                () -> assertEquals(waitlist, s.waitlist(), "waitlist"),
                () -> assertEquals(CAPACITY - enrolled.size(), s.availableSeats(), "available seats"));
    }

    // -------------------------------------------------------------------------------------
    @Nested
    @DisplayName("AC1 - enrollment with available capacity")
    class AvailableCapacity {

        @Test
        @DisplayName("eligible learner is enrolled while seats remain")
        void enrollsWhenSeatAvailable() {
            EnrollmentResult result = service.enroll("A");

            assertEquals(ResultCode.ENROLLED, result.code());
            assertEquals(0, result.waitlistPosition());
            assertState(List.of("A"), List.of());
        }

        @Test
        @DisplayName("last seat can be taken")
        void fillsToExactCapacity() {
            assertEquals(ResultCode.ENROLLED, service.enroll("A").code());
            assertEquals(ResultCode.ENROLLED, service.enroll("B").code());
            assertState(List.of("A", "B"), List.of());
        }

        @Test
        @DisplayName("offering with no prerequisites admits any known learner")
        void noPrerequisitesAdmitsEveryone() {
            var open = new CourseEnrollmentService(
                    new CourseOffering("ART100-01", "ART100", 1, Set.of()), directory);
            assertEquals(ResultCode.ENROLLED, open.enroll("NOPRE").code());
        }
    }

    // -------------------------------------------------------------------------------------
    @Nested
    @DisplayName("AC2 - full capacity and duplicate requests")
    class FullCapacityAndDuplicates {

        @Test
        @DisplayName("eligible learner joins waitlist when full, with FIFO positions")
        void waitlistsWhenFull() {
            fillCourse();

            EnrollmentResult c = service.enroll("C");
            EnrollmentResult d = service.enroll("D");

            assertEquals(ResultCode.WAITLISTED, c.code());
            assertEquals(1, c.waitlistPosition());
            assertEquals(ResultCode.WAITLISTED, d.code());
            assertEquals(2, d.waitlistPosition());
            assertState(List.of("A", "B"), List.of("C", "D"));
        }

        @Test
        @DisplayName("duplicate enroll by an enrolled learner creates no record")
        void duplicateEnrollIsIdempotent() {
            service.enroll("A");

            EnrollmentResult again = service.enroll("A");

            assertEquals(ResultCode.ALREADY_ENROLLED, again.code());
            assertFalse(again.code().stateChanged());
            assertState(List.of("A"), List.of());
        }

        @Test
        @DisplayName("duplicate enroll by a waitlisted learner keeps original position")
        void duplicateWaitlistIsIdempotent() {
            fillCourse();
            service.enroll("C");
            service.enroll("D");

            EnrollmentResult again = service.enroll("C");

            assertEquals(ResultCode.ALREADY_WAITLISTED, again.code());
            assertEquals(1, again.waitlistPosition());
            assertState(List.of("A", "B"), List.of("C", "D"));
        }

        @Test
        @DisplayName("ineligible learner is rejected even when full (never waitlisted)")
        void ineligibleNotWaitlistedWhenFull() {
            fillCourse();

            assertEquals(ResultCode.PREREQUISITE_NOT_MET, service.enroll("NOPRE").code());
            assertState(List.of("A", "B"), List.of());
        }
    }

    // -------------------------------------------------------------------------------------
    @Nested
    @DisplayName("AC3 - drop opens a seat and promotes FIFO")
    class FifoPromotion {

        @Test
        @DisplayName("dropping an enrolled learner promotes the earliest waitlisted learner")
        void promotesHeadOfQueue() {
            fillCourse();
            service.enroll("C");
            service.enroll("D");

            DropResult result = service.drop("A");

            assertEquals(ResultCode.DROPPED, result.code());
            assertEquals(Optional.of("C"), result.promotedLearnerId());
            assertTrue(result.skippedLearnerIds().isEmpty());
            assertState(List.of("B", "C"), List.of("D"));
        }

        @Test
        @DisplayName("successive drops promote in arrival order")
        void successiveDropsKeepFifoOrder() {
            fillCourse();
            service.enroll("C");
            service.enroll("D");
            service.enroll("E");

            assertEquals(Optional.of("C"), service.drop("B").promotedLearnerId());
            assertEquals(Optional.of("D"), service.drop("A").promotedLearnerId());
            assertEquals(Optional.of("E"), service.drop("C").promotedLearnerId());
            assertState(List.of("D", "E"), List.of());
        }

        @Test
        @DisplayName("drop with empty waitlist simply frees the seat")
        void dropWithoutWaitlistOpensSeat() {
            fillCourse();

            DropResult result = service.drop("A");

            assertEquals(ResultCode.DROPPED, result.code());
            assertTrue(result.promotedLearnerId().isEmpty());
            assertState(List.of("B"), List.of());
        }

        @Test
        @DisplayName("waitlisted learner leaving does not trigger promotion")
        void leavingWaitlistDoesNotPromote() {
            fillCourse();
            service.enroll("C");
            service.enroll("D");

            DropResult result = service.drop("C");

            assertEquals(ResultCode.LEFT_WAITLIST, result.code());
            assertTrue(result.promotedLearnerId().isEmpty());
            assertState(List.of("A", "B"), List.of("D"));
        }

        @Test
        @DisplayName("waitlist positions move up when a learner in the middle leaves")
        void positionsShiftWhenWaiterLeaves() {
            fillCourse();
            service.enroll("C");
            service.enroll("D");
            service.enroll("E");

            service.drop("D");

            assertEquals(2, service.enroll("E").waitlistPosition());
            assertState(List.of("A", "B"), List.of("C", "E"));
        }

        @Test
        @DisplayName("a learner who dropped may request again and joins the back of the queue (A12)")
        void droppedLearnerCanReenrollAtBack() {
            fillCourse();
            service.enroll("C");
            service.drop("A");                       // C promoted

            EnrollmentResult again = service.enroll("A");

            assertEquals(ResultCode.WAITLISTED, again.code());
            assertEquals(1, again.waitlistPosition());
            assertState(List.of("B", "C"), List.of("A"));
        }

        @Test
        @DisplayName("single-seat offering: second learner waits, drop promotes them")
        void capacityOfOne() {
            var single = new CourseEnrollmentService(
                    new CourseOffering("CS999-01", "CS999", 1, Set.of("CS101")), directory);

            assertEquals(ResultCode.ENROLLED, single.enroll("A").code());
            assertEquals(ResultCode.WAITLISTED, single.enroll("B").code());
            assertEquals(Optional.of("B"), single.drop("A").promotedLearnerId());
            assertEquals(List.of("B"), single.snapshot().enrolled());
        }

        @Test
        @DisplayName("a freed seat can be re-taken by a new request")
        void freedSeatIsReusable() {
            fillCourse();
            service.drop("A");

            assertEquals(ResultCode.ENROLLED, service.enroll("F").code());
            assertState(List.of("B", "F"), List.of());
        }
    }

    // -------------------------------------------------------------------------------------
    @Nested
    @DisplayName("AC4 - prerequisite failure and skip-and-remove policy")
    class PrerequisitePolicy {

        @Test
        @DisplayName("learner missing the prerequisite is not enrolled while seats remain")
        void prerequisiteFailureWithOpenSeats() {
            EnrollmentResult result = service.enroll("NOPRE");

            assertEquals(ResultCode.PREREQUISITE_NOT_MET, result.code());
            assertFalse(result.code().stateChanged());
            assertState(List.of(), List.of());
        }

        @Test
        @DisplayName("with several prerequisites, having only some of them is not enough")
        void allPrerequisitesRequiredByService() {
            directory.save(Learner.of("PARTIAL", "Partial", "CS101"));
            directory.save(Learner.of("FULL", "Full", "CS101", "MATH200"));
            var advanced = new CourseEnrollmentService(
                    new CourseOffering("CS301-01", "CS301", 5, Set.of("CS101", "MATH200")), directory);

            assertEquals(ResultCode.PREREQUISITE_NOT_MET, advanced.enroll("PARTIAL").code());
            assertEquals(ResultCode.ENROLLED, advanced.enroll("FULL").code());
            assertEquals(List.of("FULL"), advanced.snapshot().enrolled());
        }

        @Test
        @DisplayName("learner who became ineligible while waiting is skipped and removed")
        void ineligibleHeadIsSkippedAndRemoved() {
            fillCourse();
            service.enroll("C");
            service.enroll("D");
            // C's prerequisite record is revoked while waiting.
            directory.save(Learner.of("C", "Learner C"));

            DropResult result = service.drop("A");

            assertEquals(Optional.of("D"), result.promotedLearnerId());
            assertEquals(List.of("C"), result.skippedLearnerIds());
            assertState(List.of("B", "D"), List.of());
        }

        @Test
        @DisplayName("learner deleted from directory while waiting is skipped and removed")
        void missingLearnerIsSkipped() {
            fillCourse();
            service.enroll("C");
            service.enroll("D");
            directory.remove("C");

            DropResult result = service.drop("B");

            assertEquals(Optional.of("D"), result.promotedLearnerId());
            assertEquals(List.of("C"), result.skippedLearnerIds());
        }

        @Test
        @DisplayName("when every waiting learner is ineligible the seat stays open and queue empties")
        void allIneligibleLeavesSeatOpen() {
            fillCourse();
            service.enroll("C");
            service.enroll("D");
            directory.save(Learner.of("C", "Learner C"));
            directory.save(Learner.of("D", "Learner D"));

            DropResult result = service.drop("A");

            assertTrue(result.promotedLearnerId().isEmpty());
            assertEquals(List.of("C", "D"), result.skippedLearnerIds());
            assertState(List.of("B"), List.of());
        }

        @Test
        @DisplayName("skipped learner who regains eligibility rejoins at the back")
        void skippedLearnerRejoinsAtBack() {
            fillCourse();
            service.enroll("C");
            service.enroll("D");
            service.enroll("E");
            directory.save(Learner.of("C", "Learner C"));
            service.drop("A");                               // C skipped, D promoted
            directory.save(Learner.of("C", "Learner C", "CS101"));

            EnrollmentResult rejoin = service.enroll("C");

            assertEquals(ResultCode.WAITLISTED, rejoin.code());
            assertState(List.of("B", "D"), List.of("E", "C"));
        }

        @Test
        @DisplayName("ineligible learner is skipped only when they reach the head; others behind are untouched")
        void onlyHeadIsEvaluated() {
            fillCourse();
            service.enroll("C");
            service.enroll("D");
            service.enroll("E");
            directory.save(Learner.of("E", "Learner E")); // E is ineligible but not at head

            DropResult result = service.drop("A");

            assertEquals(Optional.of("C"), result.promotedLearnerId());
            assertTrue(result.skippedLearnerIds().isEmpty());
            assertState(List.of("B", "C"), List.of("D", "E"));
        }
    }

    // -------------------------------------------------------------------------------------
    @Nested
    @DisplayName("AC5 - invalid requests and repeated drops")
    class InvalidAndRepeated {

        @ParameterizedTest(name = "enroll(\"{0}\") is invalid")
        @NullSource
        @ValueSource(strings = {"", "   ", "\t"})
        void blankEnrollIsInvalid(String learnerId) {
            fillCourse();

            assertEquals(ResultCode.INVALID_REQUEST, service.enroll(learnerId).code());
            assertState(List.of("A", "B"), List.of());
        }

        @ParameterizedTest(name = "drop(\"{0}\") is invalid")
        @NullSource
        @ValueSource(strings = {"", "   "})
        void blankDropIsInvalid(String learnerId) {
            fillCourse();

            assertEquals(ResultCode.INVALID_REQUEST, service.drop(learnerId).code());
            assertState(List.of("A", "B"), List.of());
        }

        @Test
        @DisplayName("unknown learner cannot enroll")
        void unknownLearnerRejected() {
            assertEquals(ResultCode.UNKNOWN_LEARNER, service.enroll("GHOST").code());
            assertState(List.of(), List.of());
        }

        @Test
        @DisplayName("repeated drop returns NOT_REGISTERED and promotes nobody twice")
        void repeatedDropIsHarmless() {
            fillCourse();
            service.enroll("C");
            service.enroll("D");
            assertEquals(Optional.of("C"), service.drop("A").promotedLearnerId());

            DropResult second = service.drop("A");

            assertEquals(ResultCode.NOT_REGISTERED, second.code());
            assertTrue(second.promotedLearnerId().isEmpty());
            assertState(List.of("B", "C"), List.of("D"));
        }

        @Test
        @DisplayName("dropping a learner who never registered changes nothing")
        void dropNeverRegistered() {
            fillCourse();
            service.enroll("C");

            assertEquals(ResultCode.NOT_REGISTERED, service.drop("E").code());
            assertEquals(ResultCode.NOT_REGISTERED, service.drop("GHOST").code());
            assertState(List.of("A", "B"), List.of("C"));
        }

        @Test
        @DisplayName("enrolled learner deleted from the directory can still drop (no stranded seat)")
        void deletedLearnerCanStillDrop() {
            fillCourse();
            directory.remove("A");

            assertEquals(ResultCode.DROPPED, service.drop("A").code());
            assertState(List.of("B"), List.of());
        }
    }

    // -------------------------------------------------------------------------------------
    @Nested
    @DisplayName("snapshot isolation")
    class SnapshotIsolation {

        @Test
        @DisplayName("snapshot is immutable and does not change after later requests")
        void snapshotIsImmutable() {
            service.enroll("A");
            EnrollmentSnapshot before = service.snapshot();

            service.enroll("B");

            assertEquals(List.of("A"), before.enrolled());
            assertThrows(UnsupportedOperationException.class,
                    () -> before.enrolled().add("X"));
        }
    }
}
