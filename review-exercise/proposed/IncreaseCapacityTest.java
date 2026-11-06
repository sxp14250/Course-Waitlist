package com.coursewaitlist.service;

import com.coursewaitlist.domain.CourseOffering;
import com.coursewaitlist.domain.Learner;
import com.coursewaitlist.repository.InMemoryLearnerDirectory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for the new increaseCapacity feature. */
@DisplayName("increaseCapacity")
class IncreaseCapacityTest {

    @Test
    @DisplayName("adding seats promotes waitlisted learners into the new seats")
    void increaseCapacityPromotesWaitlist() {
        var directory = new InMemoryLearnerDirectory();
        for (String id : new String[] {"A", "B", "C", "D"}) {
            directory.save(Learner.of(id, id, "CS101"));
        }
        var service = new CourseEnrollmentService(
                new CourseOffering("CS201-01", "CS201", 2, Set.of("CS101")), directory);
        service.enroll("A");
        service.enroll("B");
        service.enroll("C");
        service.enroll("D");

        service.increaseCapacity(2);

        assertEquals(4, service.snapshot().capacity());
        assertTrue(service.snapshot().enrolled().contains("C"));
    }
}
