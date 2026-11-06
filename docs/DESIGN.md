# CourseWaitlist — Design Specification

Scope: enrollment, drop and FIFO waitlist promotion for **one** course offering.
No starter project was supplied, so this is the permitted fallback: a small Java service
with an in-memory store and a CLI. There is no frontend, database, catalog, payments or identity integration.

---

## 1. Service boundary

```
            +-------------------+       +---------------------------+
 CLI  --->  |  CommandProcessor | --->  |  CourseEnrollmentService  |  (one instance = one offering)
 tests ---> |  (text <-> calls) |       |  enroll / drop / snapshot |
            +-------------------+       +-------------+-------------+
                                                      | findById (read-only)
                                                      v
                                        +---------------------------+
                                        |  LearnerDirectory (port)  |
                                        |  InMemoryLearnerDirectory |
                                        +---------------------------+
```

| Component | Responsibility | Package |
|---|---|---|
| `CourseEnrollmentService` | Owns the roster and waitlist of a single `CourseOffering`. All business rules live here. | `service` |
| `LearnerDirectory` | Read-only port for learner records (completed courses). The service never writes learners. | `repository` |
| `InMemoryLearnerDirectory` | `ConcurrentHashMap`-backed implementation of the port. | `repository` |
| `CourseOffering`, `Learner` | Immutable value objects with validation; `CourseOffering.isEligible` holds the prerequisite rule. | `domain` |
| `EnrollmentResult`, `DropResult`, `ResultCode`, `EnrollmentSnapshot` | Result/query types returned to callers. | `domain` |
| `CommandProcessor`, `WaitlistCli` | Text adapter and `main` entry point. No business rules. | `cli` |

## 2. API

```java
EnrollmentResult   enroll(String learnerId);   // ENROLLED | WAITLISTED | ALREADY_ENROLLED | ALREADY_WAITLISTED
                                               // | PREREQUISITE_NOT_MET | UNKNOWN_LEARNER | INVALID_REQUEST
DropResult         drop(String learnerId);     // DROPPED (+ promoted, skipped) | LEFT_WAITLIST
                                               // | NOT_REGISTERED | INVALID_REQUEST
EnrollmentSnapshot snapshot();                 // consistent, immutable view
```

Design choices:

- **Expected failures are return values, not exceptions.** Duplicate requests, missing prerequisites,
  unknown learners and blank ids all come back as a `ResultCode` with a message. Exceptions are only
  used for programming errors (bad constructor arguments, broken invariants).
- **`ResultCode.stateChanged()`** tells the caller whether a request changed state. Only `ENROLLED`,
  `WAITLISTED`, `DROPPED` and `LEFT_WAITLIST` do.
- **`DropResult`** reports `promotedLearnerId` and `skippedLearnerIds`, so a caller (for example a
  notification step) can see exactly what the drop caused.

## 3. Data model

| State | Type | Why |
|---|---|---|
| `enrolled` | `LinkedHashSet<String>` (`SequencedSet`) | O(1) membership check for duplicates and keeps enrollment order. |
| `waitlist` | `LinkedHashSet<String>` (`SequencedSet`) | FIFO via `addLast`/`removeFirst`, O(1) duplicate check and O(1) removal by id when a waiting learner leaves. |

Only learner ids are stored. Eligibility is always looked up fresh from the directory.

## 4. Invariants

These hold after every public method returns. `verifyInvariants()` checks them inside the lock
after every mutation, and the tests assert them from outside.

1. **Capacity:** `enrolled.size() <= capacity`.
2. **Uniqueness:** each learner id appears at most once across `enrolled ∪ waitlist`.
3. **No idle seat:** if `enrolled.size() < capacity` then `waitlist` is empty.
4. **Eligibility:** every enrolled learner satisfied the prerequisite rule when they were seated.
5. **No corruption on rejection:** any result with `stateChanged() == false` leaves both collections unchanged.

## 5. Prerequisite and waitlist rules (assumptions)

| # | Assumption |
|---|---|
| A1 | **Prerequisite rule:** a learner is eligible if they have completed **all** courses in `CourseOffering.prerequisites` (AND). An empty set admits every known learner. |
| A2 | **Ineligible at request time:** rejected with `PREREQUISITE_NOT_MET` and never added to the waitlist, even when the course is full. |
| A3 | **Eligibility is re-checked at promotion time**, because a learner's record can change while they wait (for example a grade is revised). |
| A4 | **Ineligible at the head of the queue: SKIP and REMOVE.** The learner is taken off the waitlist, added to `DropResult.skippedLearnerIds`, and promotion moves on to the next learner. If they become eligible again they must re-request and join the **back** of the queue. |
| A5 | A learner who no longer exists in the directory is treated as ineligible at promotion (skipped and removed). |
| A6 | Only the learner at the head is evaluated. Ineligible learners further back are left alone until they reach the head. |
| A7 | One drop frees one seat and causes at most one promotion. If everyone remaining is ineligible, the waitlist empties and the seat stays open. |
| A8 | A drop by a **waitlisted** learner only removes them from the queue (`LEFT_WAITLIST`). No promotion happens because no seat was freed. |
| A9 | Drop does not require the learner to exist in the directory. Someone who holds a seat can always release it, so a deleted record can never strand a seat. |
| A10 | Ids are compared exactly (case-sensitive, no trimming). A null or whitespace-only id is `INVALID_REQUEST`. |
| A11 | Duplicate checks run **before** eligibility checks, so a repeated request reports the learner's current standing (`ALREADY_ENROLLED` / `ALREADY_WAITLISTED` with position). |
| A12 | "A learner may enroll only once" means **one active record at a time** (one seat *or* one waitlist entry), not once ever. A learner who drops may request again. That counts as a new request and goes through the normal rules, so if the course is full they join the **back** of the waitlist. *(Found by ambiguity review, prompt P4; test `droppedLearnerCanReenrollAtBack`.)* |
| A13 | "Completed" means the course appears in the learner's completed-course list supplied by the directory. In-progress or planned courses don't count. Deciding what counts as "passed" is the student-records system's job, not this service's. |
| A14 | The waitlist has **no maximum length**. The brief sets no limit, so a cap would be an invented rule. Noted as a risk in LIMITATIONS #11. |

**Why skip-and-remove instead of retain-and-skip:** if ineligible learners stayed in the queue, they
would be re-evaluated on every future drop and could hold a misleading "position 1" indefinitely.
The rule for when, or whether, they get back their old position would also be unclear. Removing them
keeps the rule simple: the queue only contains learners who were eligible the last time they were
checked, and positions only ever move forward. The trade-off is that a learner who is ineligible for
a short time loses their place. This is reported in `skippedLearnerIds` so the learner can be notified.

## 6. Concurrency approach

- A single `ReentrantLock` per service instance (and therefore per offering) guards both collections.
  `enroll`, `drop` and `snapshot` each run entirely inside the lock, so each request is
  **atomic and linearizable**.
- This specifically protects the two check-then-act sequences that would otherwise race:
  *"is there a seat? → take it"* and *"release seat → promote head"*.
- Directory reads inside the lock are safe because `InMemoryLearnerDirectory` uses a `ConcurrentHashMap`.
- Input validation (blank id) runs before the lock is taken, so malformed requests don't wait on it.
- **Rejected alternative:** lock-free concurrent collections (`ConcurrentLinkedQueue`,
  `ConcurrentHashMap.newKeySet()`). Each collection would be thread-safe on its own, but the capacity
  check plus insert, and the drop plus promote, would still not be atomic *across* the two
  collections. A single coarse lock is correct, simple, and contention is limited to one course section.
- **Evidence:** `CourseEnrollmentConcurrencyTest` (70 repeated runs). When the lock was removed as a
  deliberate mutation, 53 of the 109 tests failed (see the AI usage record).

## 7. Persistence

All state is in memory and lost on exit, which the assessment allows. To persist it, replace
`InMemoryLearnerDirectory` with an adapter and store roster/waitlist rows with:

- a unique constraint on `(offering_id, learner_id)` (enforces invariant 2),
- a monotonic `waitlist_seq` column for FIFO order,
- `SELECT … FOR UPDATE` on the offering row (or optimistic versioning) in place of the in-process lock.

## 8. Trade-offs summary

| Decision | Benefit | Cost |
|---|---|---|
| Coarse per-offering lock | Obviously correct; easy to reason about | Serializes requests for one section (fine at this scale) |
| Result codes instead of exceptions | Callers handle every outcome explicitly; nothing is swallowed | Callers must check the code |
| Skip-and-remove policy | Simple, consistent, no stale queue entries | A learner who is ineligible for a short time loses their position |
| Ids only in state, eligibility looked up live | Always uses current data | One directory lookup per promotion candidate |
