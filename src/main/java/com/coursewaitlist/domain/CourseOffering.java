package com.coursewaitlist.domain;

import java.util.Objects;
import java.util.Set;

/**
 * A single, concrete offering of a course (one section in one term) with a fixed
 * seat capacity and a set of prerequisite courses.
 *
 * <h2>Prerequisite rule (documented assumption)</h2>
 * A learner is <em>eligible</em> for this offering when they have completed
 * <strong>every</strong> course listed in {@link #prerequisites()} (logical AND).
 * An offering with no prerequisites admits every known learner. Eligibility is
 * evaluated at request time <em>and again</em> at promotion time, because a learner's
 * record may change while they wait.
 *
 * @param offeringId    identifier of this offering, e.g. {@code "CS201-FALL-A"}
 * @param courseCode    course code, e.g. {@code "CS201"}
 * @param capacity      maximum number of enrolled learners; must be at least 1
 * @param prerequisites course codes a learner must have completed
 */
public record CourseOffering(String offeringId, String courseCode, int capacity, Set<String> prerequisites) {

    /** Validates the offering and defensively copies the prerequisite set. */
    public CourseOffering {
        Objects.requireNonNull(offeringId, "offeringId must not be null");
        Objects.requireNonNull(courseCode, "courseCode must not be null");
        if (offeringId.isBlank() || courseCode.isBlank()) {
            throw new IllegalArgumentException("offeringId and courseCode must not be blank");
        }
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1, was " + capacity);
        }
        prerequisites = prerequisites == null ? Set.of() : Set.copyOf(prerequisites);
        if (prerequisites.contains(courseCode)) {
            throw new IllegalArgumentException("a course cannot be its own prerequisite");
        }
    }

    /**
     * Applies the prerequisite rule described in the type documentation.
     *
     * @param learner the learner to check
     * @return {@code true} if the learner has completed all prerequisites
     */
    public boolean isEligible(Learner learner) {
        return learner != null && learner.completedCourses().containsAll(prerequisites);
    }
}
