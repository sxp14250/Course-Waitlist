package com.coursewaitlist.repository;

import com.coursewaitlist.domain.Learner;

import java.util.Optional;

/**
 * Read-side port for learner records.
 *
 * <p>The enrollment service depends only on this interface, so an in-memory store can be
 * swapped for a real student-records adapter without touching enrollment logic.
 * Implementations must be safe for concurrent reads.
 */
public interface LearnerDirectory {

    /**
     * @param learnerId learner identifier
     * @return the learner's current record, or empty if the id is unknown
     */
    Optional<Learner> findById(String learnerId);
}
