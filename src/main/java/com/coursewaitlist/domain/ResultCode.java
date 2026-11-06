package com.coursewaitlist.domain;

/**
 * Machine-readable outcome of an enrollment or drop request.
 *
 * <p>Every request returns exactly one code. Codes marked "no state change" are
 * guaranteed not to modify the enrolled roster, the capacity, or the waitlist.
 */
public enum ResultCode {

    // ---- enroll outcomes -------------------------------------------------------------
    /** A seat was available and the learner now holds it. */
    ENROLLED(true),
    /** The offering was full; the eligible learner was appended to the waitlist. */
    WAITLISTED(true),
    /** Duplicate request: learner already holds a seat. No state change. */
    ALREADY_ENROLLED(false),
    /** Duplicate request: learner is already on the waitlist. Position kept. No state change. */
    ALREADY_WAITLISTED(false),
    /** Learner has not completed all prerequisites. No state change. */
    PREREQUISITE_NOT_MET(false),

    // ---- drop outcomes ---------------------------------------------------------------
    /** An enrolled learner released their seat (and a promotion may have followed). */
    DROPPED(true),
    /** A waitlisted learner left the waitlist. No seat changed hands. */
    LEFT_WAITLIST(true),
    /** Learner is neither enrolled nor waitlisted (includes repeated drops). No state change. */
    NOT_REGISTERED(false),

    // ---- shared validation outcomes -------------------------------------------------
    /** Learner id is not known to the learner directory. No state change. */
    UNKNOWN_LEARNER(false),
    /** Request was malformed (null or blank learner id). No state change. */
    INVALID_REQUEST(false);

    private final boolean stateChanged;

    ResultCode(boolean stateChanged) {
        this.stateChanged = stateChanged;
    }

    /** @return {@code true} if this outcome modified enrollment or waitlist state */
    public boolean stateChanged() {
        return stateChanged;
    }
}
