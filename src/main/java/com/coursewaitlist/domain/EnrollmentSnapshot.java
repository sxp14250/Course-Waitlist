package com.coursewaitlist.domain;

import java.util.List;

/**
 * Immutable, point-in-time view of an offering's roster and waitlist.
 *
 * <p>Taken under the service lock, so the enrolled list, waitlist and seat count are
 * mutually consistent. Callers can never mutate service state through a snapshot.
 *
 * @param offeringId     the offering this snapshot describes
 * @param capacity       total seats
 * @param enrolled       enrolled learner ids, in enrollment order
 * @param waitlist       waitlisted learner ids, head (next to be promoted) first
 */
public record EnrollmentSnapshot(String offeringId, int capacity, List<String> enrolled, List<String> waitlist) {

    public EnrollmentSnapshot {
        enrolled = List.copyOf(enrolled);
        waitlist = List.copyOf(waitlist);
    }

    /** @return number of unoccupied seats */
    public int availableSeats() {
        return capacity - enrolled.size();
    }
}
