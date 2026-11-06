package com.coursewaitlist.repository;

import com.coursewaitlist.domain.Learner;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe, in-memory {@link LearnerDirectory}.
 *
 * <p>This is the "small in-memory store" fallback allowed by the assessment because no
 * starter project was supplied. {@link #save(Learner)} replaces a record atomically,
 * which tests use to simulate a learner's transcript changing while they wait.
 */
public final class InMemoryLearnerDirectory implements LearnerDirectory {

    private final ConcurrentMap<String, Learner> learners = new ConcurrentHashMap<>();

    /**
     * Inserts or replaces a learner record.
     *
     * @param learner the learner to store
     * @return this directory, for fluent seeding
     */
    public InMemoryLearnerDirectory save(Learner learner) {
        Objects.requireNonNull(learner, "learner must not be null");
        learners.put(learner.id(), learner);
        return this;
    }

    /**
     * Removes a learner record (e.g. the learner withdrew from the institution).
     *
     * @param learnerId learner identifier
     */
    public void remove(String learnerId) {
        learners.remove(learnerId);
    }

    /**
     * @return a point-in-time copy of all learners, sorted by id (used by the CLI {@code learners} command)
     */
    public List<Learner> findAll() {
        return learners.values().stream()
                .sorted(Comparator.comparing(Learner::id))
                .toList();
    }

    @Override
    public Optional<Learner> findById(String learnerId) {
        return learnerId == null ? Optional.empty() : Optional.ofNullable(learners.get(learnerId));
    }
}
