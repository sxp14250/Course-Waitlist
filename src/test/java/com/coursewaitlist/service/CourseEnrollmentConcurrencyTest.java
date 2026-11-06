package com.coursewaitlist.service;

import com.coursewaitlist.domain.CourseOffering;
import com.coursewaitlist.domain.EnrollmentSnapshot;
import com.coursewaitlist.domain.Learner;
import com.coursewaitlist.domain.ResultCode;
import com.coursewaitlist.repository.InMemoryLearnerDirectory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused concurrency tests (AC6).
 *
 * <p>Each test releases many threads at once via a start latch so requests genuinely
 * race, then checks the service invariants on the final snapshot. Tests are repeated
 * because races are probabilistic; a broken lock would fail some repetitions.
 */
@DisplayName("CourseEnrollmentService under concurrency")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class CourseEnrollmentConcurrencyTest {

    private static final int CAPACITY = 10;
    private static final int LEARNERS = 200;

    private static CourseEnrollmentService newService(InMemoryLearnerDirectory directory) {
        return new CourseEnrollmentService(
                new CourseOffering("CS201-01", "CS201", CAPACITY, Set.of("CS101")), directory);
    }

    private static InMemoryLearnerDirectory directoryWith(int count) {
        InMemoryLearnerDirectory directory = new InMemoryLearnerDirectory();
        IntStream.range(0, count).forEach(i -> directory.save(Learner.of("L" + i, "L" + i, "CS101")));
        return directory;
    }

    /** Runs all tasks simultaneously on platform threads and waits for every result. */
    private static <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(32)) {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> f : futures) {
                results.add(f.get());
            }
            return results;
        }
    }

    /** Asserts capacity, uniqueness, disjointness and "no idle seat while waiting". */
    private static void assertInvariants(EnrollmentSnapshot s) {
        assertTrue(s.enrolled().size() <= CAPACITY, "over capacity: " + s.enrolled().size());
        assertEquals(s.enrolled().size(), new HashSet<>(s.enrolled()).size(), "duplicate enrolled");
        assertEquals(s.waitlist().size(), new HashSet<>(s.waitlist()).size(), "duplicate waitlisted");
        Set<String> both = new HashSet<>(s.enrolled());
        both.retainAll(s.waitlist());
        assertTrue(both.isEmpty(), "learner both enrolled and waitlisted: " + both);
        assertTrue(s.availableSeats() == 0 || s.waitlist().isEmpty(), "seat idle while learners wait");
    }

    @RepeatedTest(20)
    @DisplayName("many distinct learners racing for seats never exceed capacity")
    void concurrentEnrollsRespectCapacity() throws Exception {
        CourseEnrollmentService service = newService(directoryWith(LEARNERS));

        List<Callable<ResultCode>> tasks = new ArrayList<>();
        IntStream.range(0, LEARNERS).forEach(i -> tasks.add(() -> service.enroll("L" + i).code()));
        List<ResultCode> codes = runConcurrently(tasks);

        EnrollmentSnapshot s = service.snapshot();
        assertInvariants(s);
        assertEquals(CAPACITY, codes.stream().filter(c -> c == ResultCode.ENROLLED).count());
        assertEquals(LEARNERS - CAPACITY, codes.stream().filter(c -> c == ResultCode.WAITLISTED).count());
        assertEquals(CAPACITY, s.enrolled().size());
        assertEquals(LEARNERS - CAPACITY, s.waitlist().size());
    }

    @RepeatedTest(20)
    @DisplayName("the same learner submitting many concurrent requests gets exactly one record")
    void concurrentDuplicateRequestsCreateOneRecord() throws Exception {
        CourseEnrollmentService service = newService(directoryWith(1));

        List<Callable<ResultCode>> tasks = new ArrayList<>();
        IntStream.range(0, 100).forEach(_ -> tasks.add(() -> service.enroll("L0").code()));
        List<ResultCode> codes = runConcurrently(tasks);

        assertEquals(1, codes.stream().filter(c -> c == ResultCode.ENROLLED).count());
        assertEquals(99, codes.stream().filter(c -> c == ResultCode.ALREADY_ENROLLED).count());
        assertEquals(List.of("L0"), service.snapshot().enrolled());
    }

    @RepeatedTest(20)
    @DisplayName("the same learner flooding a FULL course gets exactly one waitlist entry")
    void concurrentDuplicateWaitlistRequestsCreateOneEntry() throws Exception {
        CourseEnrollmentService service = newService(directoryWith(CAPACITY + 1));
        IntStream.range(0, CAPACITY).forEach(i -> service.enroll("L" + i));   // fill the course
        String latecomer = "L" + CAPACITY;

        List<Callable<ResultCode>> tasks = new ArrayList<>();
        IntStream.range(0, 100).forEach(_ -> tasks.add(() -> service.enroll(latecomer).code()));
        List<ResultCode> codes = runConcurrently(tasks);

        assertEquals(1, codes.stream().filter(c -> c == ResultCode.WAITLISTED).count());
        assertEquals(99, codes.stream().filter(c -> c == ResultCode.ALREADY_WAITLISTED).count());
        assertEquals(List.of(latecomer), service.snapshot().waitlist());
    }

    @RepeatedTest(20)
    @DisplayName("concurrent drops promote each waitlisted learner exactly once, in FIFO order")
    void concurrentDropsPromoteInOrder() throws Exception {
        InMemoryLearnerDirectory directory = directoryWith(30);
        CourseEnrollmentService service = newService(directory);
        // Sequential setup: L0..L9 enrolled, L10..L29 waiting in order.
        IntStream.range(0, 30).forEach(i -> service.enroll("L" + i));

        // All 10 enrolled learners drop at once, plus each drop is attempted twice.
        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < CAPACITY; i++) {
            String id = "L" + i;
            tasks.add(() -> service.drop(id).promotedLearnerId().orElse(null));
            tasks.add(() -> service.drop(id).promotedLearnerId().orElse(null));
        }
        List<String> promoted = runConcurrently(tasks).stream().filter(p -> p != null).toList();

        EnrollmentSnapshot s = service.snapshot();
        assertInvariants(s);
        // Exactly 10 promotions (repeat drops promote nobody), and they are L10..L19.
        assertEquals(CAPACITY, promoted.size());
        assertEquals(Set.copyOf(IntStream.range(10, 20).mapToObj(i -> "L" + i).toList()), Set.copyOf(promoted));
        assertEquals(IntStream.range(10, 20).mapToObj(i -> "L" + i).toList(), s.enrolled(),
                "promotion must follow FIFO order");
        assertEquals(IntStream.range(20, 30).mapToObj(i -> "L" + i).toList(), s.waitlist());
    }

    @RepeatedTest(10)
    @DisplayName("mixed concurrent enrolls and drops leave state consistent")
    void mixedWorkloadPreservesInvariants() throws Exception {
        CourseEnrollmentService service = newService(directoryWith(50));

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int round = 0; round < 4; round++) {
            for (int i = 0; i < 50; i++) {
                String id = "L" + i;
                tasks.add(() -> {
                    service.enroll(id);
                    return null;
                });
                if (i % 3 == 0) {
                    tasks.add(() -> {
                        service.drop(id);
                        return null;
                    });
                }
            }
        }
        runConcurrently(tasks);

        assertInvariants(service.snapshot());
    }
}
