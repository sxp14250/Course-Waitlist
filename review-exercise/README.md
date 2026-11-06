# Review Exercise: PR-001 (planted-bug review)

> **What this is:** a code-review exercise run at the candidate's request. The AI deliberately wrote
> the proposed change below with **several planted defects**: real bugs, false claims in comments,
> and unjustified business assumptions. All of them are disguised as reasonable improvements. The
> candidate's job is to review it **without AI help**, and decide what to accept or reject.
>
> The real code in `src/` is **not** changed. The answer key was held back until the candidate had
> submitted their findings, and is now at the end of this file. The outcome is recorded in
> [docs/AI_USAGE_RECORD.md](../docs/AI_USAGE_RECORD.md) as a planted-bug exercise.

---

## The pull request (as the AI assistant submitted it)

> ### PR-001: Add `increaseCapacity`, normalise ids, and small robustness/performance improvements
>
> **Summary**
>
> This PR adds a frequently requested capability and tidies up a few rough edges:
>
> 1. **New `increaseCapacity(int)`.** Registrars can add seats when a bigger room is booked. New seats
>    are filled from the waitlist straight away in FIFO order. Capacity is now a `volatile` field, so
>    updates are thread-safe.
> 2. **Id normalisation.** Learner ids are trimmed, so `" L1"` and `"L1"` are the same learner. This
>    prevents accidental duplicate records caused by stray whitespace.
> 3. **Transfer-student friendliness.** A completed course from the same department is accepted as
>    equivalent to the prerequisite, so transfer students aren't rejected unfairly.
> 4. **Idempotent drop.** Dropping a learner who is already gone now returns success (`DROPPED`), so
>    clients that retry after a timeout don't get confusing errors.
> 5. **Fairer waitlist.** Ineligible learners are moved to the back of the queue instead of being
>    discarded, so they get another chance once their record is fixed.
> 6. **Performance.** Redundant invariant checks are removed from the `enroll` hot path.
> 7. **Tests.** Added `IncreaseCapacityTest`.
>
> **Risk:** low. The changes are small and well-commented.

---

## Files to review

| File | What it is |
|---|---|
| [`PR-001.diff`](PR-001.diff) | **Start here.** A unified diff of every change against the current code |
| [`proposed/CourseEnrollmentService.java`](proposed/CourseEnrollmentService.java) | The full proposed service, after the change |
| [`proposed/IncreaseCapacityTest.java`](proposed/IncreaseCapacityTest.java) | The proposed new test |

## How to review it

Use any mix of these. Real reviewers use all three.

### 1. Read the diff critically

For each change, ask yourself:
- Is the comment **true**? Check claims like "thread-safe", "cannot be broken", "consistent with DESIGN.md".
- Does it **contradict** a documented assumption in [DESIGN.md §5](../docs/DESIGN.md) or an acceptance criterion?
- Is it applied **consistently**, in enroll *and* drop *and* promotion?
- Does it **invent a business rule** that the brief never asked for?
- Does the new test actually **prove** what its name claims?

### 2. Run the existing tests against the proposed change (in a copy, never in the real project)

**PowerShell**, from the project root:
```bash
Copy-Item -Recurse . ..\AI-Assessment-PR001 -Exclude target
```
```bash
Copy-Item review-exercise\proposed\CourseEnrollmentService.java ..\AI-Assessment-PR001\src\main\java\com\coursewaitlist\service\ -Force
```
```bash
Copy-Item review-exercise\proposed\IncreaseCapacityTest.java ..\AI-Assessment-PR001\src\test\java\com\coursewaitlist\service\
```
```bash
cd ..\AI-Assessment-PR001; .\mvnw.cmd test
```

Look at which tests fail and work out **which change** broke each one. Remember that some defects
will **not** be caught by any test, so passing tests don't mean a change is safe.

### 3. Try it by hand in the copy

Still inside the copy (`..\AI-Assessment-PR001`), run these two commands **one at a time**:

```bash
.\mvnw.cmd package -DskipTests
```
```bash
java -jar target\course-waitlist-1.0.0.jar
```

Then try the scenarios from the main README (section 5) and see what behaves differently from the
expected output.

---

## Candidate's findings (round 1)

The candidate could not edit this file, so the AI transcribed the findings below from the terminal
output the candidate pasted. Nothing has been added that the candidate didn't report.

| # | What the candidate observed | How found |
|---|---|---|
| F1 | Running the existing suite against PR-001 gave **138 tests, 7 failures, 1 error, BUILD FAILURE**. Failing: `revokeDrivesSkipPolicy`, `scriptedScenario`, `ineligibleHeadIsSkippedAndRemoved`, `allPrerequisitesRequiredByService`, `allIneligibleLeavesSeatOpen` (IllegalStateException "Seat open while learners wait"), `skippedLearnerRejoinsAtBack`, `dropNeverRegistered`, `repeatedDropIsHarmless`. The new `increaseCapacity` test passed. | Test run in a copy (`..\AI-Assessment-PR001`) |
| F2 | `drop L2` returned `[DROPPED] L2 dropped CS201-FALL-A.` although L2 had **never enrolled**, so a false success was reported. | Manual CLI run in the copy |

**Overall verdict (candidate, stated after the answer key was revealed):** **Reject.** Reasons: the build fails, and drop reports false success for a learner who was never registered.

**Requested change (candidate):** `drop` must return `NOT_REGISTERED`, not `DROPPED`, when the learner
is neither enrolled nor waitlisted. This keeps results accurate and matches AC5. Resubmit once the
existing test suite passes in full.
**Root-cause mapping (failure → PR item) and Accept/Edit/Reject decisions:** not provided in round 1.

---

## Answer key (revealed after round 1)

10 defects were planted. 3 can be detected by running the existing tests. 7 can only be found by reading the code.

| # | Planted defect | PR item | Caught by existing tests? | Candidate round 1 |
|---|---|---|---|---|
| 1 | `increaseCapacity` changes state **without the lock**, so it races with enroll/drop | 1 | No | ❌ Missed |
| 2 | Comment says `volatile` makes capacity "thread-safe". False: `+=` isn't atomic, and volatile doesn't protect the collections | 1 | No | ❌ Missed |
| 3 | `increaseCapacity` promotes **only one** learner. Adding 2 seats leaves one idle while learners wait | 1 | No | ❌ Missed |
| 4 | `increaseCapacity` accepts 0 or negative seats, and never calls `verifyInvariants()` | 1 | No | ❌ Missed |
| 5 | `IncreaseCapacityTest` only checks capacity and that C was enrolled. It passes even with defect #3 | 7 | No (it passes) | ⚠️ Observed passing, not flagged |
| 6 | Id trimming in `enroll` but **not** in `drop`, and silently contradicts assumption A10 | 2 | No | ❌ Missed |
| 7 | Invented rule: any same-department course counts as the prerequisite. Breaks the AND rule (A1), and is only applied at enroll | 3 | **Yes** (`allPrerequisitesRequiredByService`) | ⚠️ Test failure observed, root cause not identified |
| 8 | Drop of an unregistered learner returns `DROPPED`, a false result that violates AC5 | 4 | **Yes** (`repeatedDropIsHarmless`, `dropNeverRegistered`, `scriptedScenario`) | ✅ **Caught**: test failures **and** reproduced by hand (F2) |
| 9 | Silent switch to skip-and-**retain**, with a false "consistent with DESIGN.md §5" comment | 5 | **Yes** (4 tests, incl. the invariant error) | ⚠️ Test failures observed, root cause not identified |
| 10 | Invariant checks removed from `enroll`, with a false "cannot be broken here" justification | 6 | No | ❌ Missed |

**Round 1 score:** 1 fully caught (#8). 2 surfaced by test failures, with the cause not yet identified (#7, #9). 7 missed (#1–#6, #10).
Correct verdict: **Reject / request changes.**

**Key lesson:** every defect the candidate surfaced came from **running** the code (tests or CLI).
Every defect that needed **reading** the diff was missed, including both of the concurrency bugs and
the test that proves nothing. Passing tests (90/90 concurrency, `increaseCapacity` green) did not
mean those parts were safe.
