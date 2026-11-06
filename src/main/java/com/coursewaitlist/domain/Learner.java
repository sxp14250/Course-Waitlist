package com.coursewaitlist.domain;

import java.util.Objects;
import java.util.Set;

/**
 * A learner who may request a seat in a course offering.
 *
 * <p>Immutable value object. The set of completed courses is the only data the
 * enrollment slice needs to evaluate the prerequisite rule; nothing else about the
 * learner (contact details, identity, payments) is modelled, by design.
 *
 * @param id               stable, non-blank learner identifier (e.g. {@code "L-1001"})
 * @param name             display name used only for CLI output
 * @param completedCourses course codes the learner has already passed (e.g. {@code "CS101"})
 */
public record Learner(String id, String name, Set<String> completedCourses) {

    /**
     * Compact constructor: validates input and takes a defensive, unmodifiable copy of
     * {@code completedCourses} so a caller cannot mutate a learner after creation.
     */
    public Learner {
        Objects.requireNonNull(id, "id must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        name = (name == null || name.isBlank()) ? id : name;
        completedCourses = completedCourses == null ? Set.of() : Set.copyOf(completedCourses);
    }

    /**
     * Convenience factory for a learner with the given completed courses.
     *
     * @param id               learner identifier
     * @param name             display name
     * @param completedCourses zero or more completed course codes
     * @return a new immutable learner
     */
    public static Learner of(String id, String name, String... completedCourses) {
        return new Learner(id, name, Set.of(completedCourses));
    }

    /**
     * @param courseCode a course code
     * @return {@code true} if the learner has completed that course
     */
    public boolean hasCompleted(String courseCode) {
        return completedCourses.contains(courseCode);
    }
}
