package com.coursewaitlist.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of a {@code drop} request.
 *
 * <p>When an enrolled learner drops, the freed seat is offered to the waitlist in FIFO
 * order. The result reports who (if anyone) was promoted and which learners were
 * skipped and removed from the waitlist because they were no longer eligible.
 *
 * @param code             outcome of the request
 * @param learnerId        the learner who asked to drop (may be {@code null} for an invalid request)
 * @param promotedLearnerId learner promoted into the freed seat, if any
 * @param skippedLearnerIds learners removed from the head of the waitlist as ineligible, in queue order
 * @param message          human-readable explanation, suitable for CLI output
 */
public record DropResult(ResultCode code,
                         String learnerId,
                         Optional<String> promotedLearnerId,
                         List<String> skippedLearnerIds,
                         String message) {

    public DropResult {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(message, "message must not be null");
        promotedLearnerId = promotedLearnerId == null ? Optional.empty() : promotedLearnerId;
        skippedLearnerIds = skippedLearnerIds == null ? List.of() : List.copyOf(skippedLearnerIds);
    }

    /** Creates a result with no promotion and no skipped learners. */
    public static DropResult of(ResultCode code, String learnerId, String message) {
        return new DropResult(code, learnerId, Optional.empty(), List.of(), message);
    }
}
