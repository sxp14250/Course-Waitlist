package com.coursewaitlist.cli;

import com.coursewaitlist.domain.CourseOffering;
import com.coursewaitlist.domain.Learner;
import com.coursewaitlist.repository.InMemoryLearnerDirectory;

import java.util.Set;

/**
 * Seed data for the CLI and for manual acceptance testing.
 *
 * <p>Kept in one place so the CLI, the {@code reset} command, the CLI unit tests and
 * {@code docs/MANUAL_TEST_PLAN.md} all describe exactly the same starting state.
 *
 * <pre>
 *   Offering CS201-FALL-A, capacity 2, prerequisite CS101
 *
 *   L1 Ada      CS101            eligible
 *   L2 Grace    CS101            eligible
 *   L3 Linus    CS101, MATH100   eligible
 *   L4 Alan     (none)           NOT eligible
 *   L5 Barbara  CS101            eligible
 *   L6 Edsger   CS101            eligible
 * </pre>
 */
public final class DemoData {

    /** The single offering managed by the CLI. */
    public static final CourseOffering OFFERING =
            new CourseOffering("CS201-FALL-A", "CS201", 2, Set.of("CS101"));

    private DemoData() {
    }

    /** @return a fresh directory containing the demo learners listed above */
    public static InMemoryLearnerDirectory newDirectory() {
        return new InMemoryLearnerDirectory()
                .save(Learner.of("L1", "Ada", "CS101"))
                .save(Learner.of("L2", "Grace", "CS101"))
                .save(Learner.of("L3", "Linus", "CS101", "MATH100"))
                .save(Learner.of("L4", "Alan"))                    // missing CS101
                .save(Learner.of("L5", "Barbara", "CS101"))
                .save(Learner.of("L6", "Edsger", "CS101"));
    }
}
