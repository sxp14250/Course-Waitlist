package com.coursewaitlist.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validation and prerequisite-rule tests for the domain value objects. */
@DisplayName("Domain model")
class DomainModelTest {

    @Test
    @DisplayName("prerequisite rule requires ALL listed courses")
    void eligibilityRequiresAllPrerequisites() {
        var offering = new CourseOffering("CS301-01", "CS301", 5, Set.of("CS101", "CS201"));

        assertTrue(offering.isEligible(Learner.of("x", "x", "CS101", "CS201", "ART100")));
        assertFalse(offering.isEligible(Learner.of("y", "y", "CS101")));
        assertFalse(offering.isEligible(Learner.of("z", "z")));
        assertFalse(offering.isEligible(null));
    }

    @Test
    @DisplayName("capacity below 1 is rejected")
    void capacityMustBePositive() {
        assertThrows(IllegalArgumentException.class,
                () -> new CourseOffering("CS201-01", "CS201", 0, Set.of()));
    }

    @Test
    @DisplayName("course cannot list itself as prerequisite")
    void selfPrerequisiteRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new CourseOffering("CS201-01", "CS201", 1, Set.of("CS201")));
    }

    @Test
    @DisplayName("learner id must be non-blank")
    void learnerIdValidated() {
        assertThrows(NullPointerException.class, () -> Learner.of(null, "n"));
        assertThrows(IllegalArgumentException.class, () -> Learner.of(" ", "n"));
    }

    @Test
    @DisplayName("learner keeps a defensive copy of completed courses")
    void learnerIsImmutable() {
        Set<String> courses = new HashSet<>(Set.of("CS101"));
        Learner learner = new Learner("L1", "Ada", courses);

        courses.clear();

        assertTrue(learner.hasCompleted("CS101"));
        assertThrows(UnsupportedOperationException.class, () -> learner.completedCourses().add("X"));
    }

    @Test
    @DisplayName("blank display name falls back to id")
    void nameDefaultsToId() {
        assertEquals("L1", new Learner("L1", "", Set.of()).name());
    }
}
