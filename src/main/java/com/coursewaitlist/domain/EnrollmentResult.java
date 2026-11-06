package com.coursewaitlist.domain;

import java.util.Objects;

/**
 * Result of an {@code enroll} request.
 *
 * @param code              outcome of the request
 * @param learnerId         the learner the request was for (may be {@code null} for an invalid request)
 * @param waitlistPosition  1-based waitlist position when {@code code} is {@link ResultCode#WAITLISTED}
 *                          or {@link ResultCode#ALREADY_WAITLISTED}; otherwise 0
 * @param message           human-readable explanation, suitable for CLI output
 */
public record EnrollmentResult(ResultCode code, String learnerId, int waitlistPosition, String message) {

    public EnrollmentResult {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }

    /** Creates a result that carries no waitlist position. */
    public static EnrollmentResult of(ResultCode code, String learnerId, String message) {
        return new EnrollmentResult(code, learnerId, 0, message);
    }
}
