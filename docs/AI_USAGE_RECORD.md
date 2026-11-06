# AI Prompt and Use Record

**Tool:** Claude Code (Anthropic, model Claude Opus 5.5), run inside the Claude desktop app.
**Date:** 2026-11-05
**Data hygiene:** the prompts contained no credentials, secrets or personal data. All learner data in
the code and tests is made up (`L1`…`L6` in the CLI demo; `A`…`F`, `NOPRE`, `L0`…`L199` in tests).

> **Note to the reviewer / candidate:** Sections 1–4 are a factual record of what the AI was asked,
> what it produced, and how that output was checked by machine. Section 5 is for the **candidate's own
> independent review** and must be completed by the candidate. The AI has not filled it in, so that
> the record stays candid. Section 6 is a **recommended prompt playbook**: 21 reusable prompts covering
> every stage of the job, each with a way to verify the answer. Those prompts are suggestions, not part
> of the executed record until they are run. The 12 ticked ☑ were run as P4–P15.

---

## 1. Prompts used

| # | Prompt (summary) | Engineering activity |
|---|---|---|
| P1 | *Initial implementation brief:* supplied the full *CourseWaitlist* assessment brief (problem statement, acceptance criteria AC1–AC7, scope boundaries, deliverables) and asked the AI to implement the scoped slice with these engineering constraints: the latest Java release and JUnit version; meaningful, self-documenting code with Javadoc/comment blocks; a conventional project and folder structure; and a step-by-step README that names the application's entry point and how to navigate the project. | Task decomposition, design, implementation, tests, documentation |
| P2 | *Manual acceptance verification:* challenged whether passing automated tests was enough evidence on its own, and asked for a repeatable manual procedure: concrete CLI inputs and expected outputs for each acceptance criterion, so the behaviour could be checked independently by running the application itself. | Manual acceptance-test design: added `reset`/`grant`/`revoke`/`learners` CLI commands, `manual-tests/` scripts and `docs/MANUAL_TEST_PLAN.md` |
| P3 | "Update the README so a new team member understands the context and usage; add example test scenarios showing how to execute and what to expect." (Prompted after the candidate typed `AC1` and `L1` at the CLI prompt and got `Unknown command`.) | Documentation for onboarding: rewrote README with context, glossary, rules flowchart, command and result-code reference, 9 worked scenarios with exact output, and a troubleshooting table that includes that mistake. **Verified** by replaying every `>` line from README §5 through the jar and diffing against the documented output: all 177 lines identical. |
| P4 | **A1** "Act as a senior engineer. Without writing code, list every ambiguous requirement in the CourseWaitlist brief, give 2–3 interpretations of each, and recommend one." | Requirements analysis |
| P5 | **A2** "Convert acceptance criteria AC1–AC6 into Given/When/Then statements, one positive and one negative case each, and flag any that need an extra assumption." | Requirements → testable statements |
| P6 | **B1** "Compare skip-and-remove with skip-and-retain for an ineligible learner at the head of the waitlist: fairness, complexity, edge cases, testability. Recommend one and name the stakeholder question to confirm." | Policy decision |
| P7 | **B2** "List the invariants that must hold after every enroll and drop for one offering, which operation could break each, and a cheap runtime check for each." | Design: invariants |
| P8 | **B4** "Compare synchronized, ReentrantLock, ReadWriteLock, lock-free collections and a single-threaded actor for the check-then-act operations in this service. Which are correct and why?" | Design: concurrency |
| P9 | **C3** "Explain `promoteNextEligible()` line by line for a new team member, and list anything that looks like a bug along with an input that would trigger it." | Code comprehension / review |
| P10 | **D1** "Produce a test matrix (initial state, action, expected code, roster, waitlist) for this service, then tell me which rows have no existing test." | Test design / gap analysis |
| P11 | **D3** "Review CourseEnrollmentConcurrencyTest: would each test fail on broken locking? Explain why the start latch matters and suggest any missing race." | Test review: concurrency |
| P12 | **D4** "Propose 8 realistic mutations to CourseEnrollmentService, apply each in a copy, run the tests, and report which mutants survive." | Test-effectiveness verification |
| P13 | **E1** "Act as a strict reviewer. Find any way to exceed capacity, duplicate a learner, change state on a 'no change' result, misuse the lock, or apply the skip policy inconsistently. Give the triggering inputs and don't invent issues." | Adversarial code review |
| P14 | **E3** "Check the repository and the AI usage record for secrets, real personal data, or inputs that could crash the app or grow memory without bound." | Security / data hygiene |
| P15 | **F4** "Act as the grader. For every deliverable and acceptance criterion, mark it present, partial or missing with evidence, and give the three weakest areas with fixes under 30 minutes." | Submission readiness review |
| P16 | **F5** "The candidate found a defect by hand (a false `DROPPED` result for a learner who never enrolled, violating AC5) and rejected PR-001, requesting that `drop` return `NOT_REGISTERED` in that case and that the PR be re-submitted only once the full test suite passes.** | Code-review exercise (results in §4.6) |

The AI broke P1 into these steps: inspect the workspace and toolchain → create the Maven project →
domain model and service → JUnit tests (including concurrency) → CLI → run and verify → documentation.

P4–P15 are taken from the §6 playbook. The candidate chose them and asked for them to be run against
the finished project in one session. Each was executed for real. Its findings were acted on or
rejected, and any code change was re-verified (see §1.1 and §4).

### 1.1 Results of P4–P17

| # | Key response (summary) | Decision | Evidence / files |
|---|---|---|---|
| P4 | Found 6 ambiguities: (1) whether "enroll only once" means once ever or once at a time; (2) whether eligibility is re-checked at promotion; (3) skip vs retain; (4) whether a waitlisted learner can "drop"; (5) what "completed" means; (6) whether the waitlist has a cap. Items 2–4 were already covered by A3, A4 and A8. **Items 1, 5 and 6 were undocumented.** | **Accepted.** Added assumptions A12 (once *at a time*; a learner who drops may request again and joins the back of the queue), A13 (completed = in the directory's completed list) and A14 (no cap). | `DESIGN.md §5`; new test `droppedLearnerCanReenrollAtBack` |
| P5 | Wrote positive and negative Given/When/Then cases for AC1–AC6 and mapped each one to an existing test. Two had no matching test: "re-enroll after drop" and "positions shift when a waiter leaves". | **Accepted.** Tests added (see P10). | `CourseEnrollmentServiceTest` |
| P6 | Retain: positions become misleading, every drop re-checks the same learners, and the rule for restoring a place is unclear. Remove: simple and consistent, but a learner who is ineligible only briefly loses their place. Recommended **remove**. Stakeholder question: "Should a learner who is temporarily ineligible keep their place for a grace period?" | **Accepted** (confirms the existing design). Stakeholder question recorded. | `DESIGN.md §5`, `LIMITATIONS.md #3` |
| P7 | Listed 5 invariants, matching DESIGN §4. Pointed out that invariant 4 ("enrolled learners were eligible when seated") is a property of a past moment, so it can't be checked cheaply at runtime and has to be guaranteed by tests. | **Accepted, no change.** Already covered by `PrerequisitePolicy` tests. | `verifyInvariants()` checks 1–3 |
| P8 | `synchronized` and `ReentrantLock` are both correct, and equivalent here. `ReadWriteLock` only helps `snapshot()` and adds complexity. Lock-free collections are **incorrect** because the compound operations span two collections. An actor is correct but too heavy. | **Accepted, no change.** It matches the existing D2 rejection. | §3 D2 |
| P9 | Explained the loop. No bug found. It noted that the loop condition `enrolled.size() < capacity` is always true right after a drop, but keeps it as a cheap safeguard. | **Rejected** the suggestion to remove the condition. It's a deliberate safeguard if a future feature (such as raising capacity) reuses the method. | `CourseEnrollmentService.promoteNextEligible` |
| P10 | Built the matrix from the scenarios listed in the prompt and checked each against the suite. **3 had no test**: re-enroll after drop, positions shifting, capacity = 1. | **Accepted.** Added `droppedLearnerCanReenrollAtBack`, `positionsShiftWhenWaiterLeaves`, `capacityOfOne`. | `CourseEnrollmentServiceTest.FifoPromotion` |
| P11 | All 4 concurrency tests use a start latch, so threads really race. Without the latch, early tasks finish before later ones start, and the test can pass even on broken code. **Missing race:** one learner flooding a course that is already *full* (the existing duplicate test only covered an open course). | **Accepted.** Added `concurrentDuplicateWaitlistRequestsCreateOneEntry` (20 repeats). | `CourseEnrollmentConcurrencyTest` |
| P12 | Ran 8 mutants (§4.2). **All 8 were caught**, but M6 (waitlist duplicate check removed) and M7 (prerequisites joined with OR instead of AND) were caught by **only one test each**. | **Accepted.** Added the P11 test, which strengthens M6, and `allPrerequisitesRequiredByService`, which strengthens M7. After re-running, M6 is caught by 21 failures and M7 by 2 tests. | §4.2 |
| P13 | Service: **none found** in any of the 5 categories, with each one reasoned through (lock released in `finally`; rejections return before any mutation; skip rule only in one place). CLI: `learners` was hard-coded to ids `L1`–`L9`, so it would silently hide other learners. | **Accepted the CLI finding.** Added `InMemoryLearnerDirectory.findAll()` and changed `learners` to use it. | `CommandProcessor`, test `learnersListsWholeDirectory` |
| P14 | Scanned for secrets, keys, tokens, emails and personal data: **none** (grep hits were only the policy text in this document). Risk found: the waitlist has no cap (A14). The CLI reads one line at a time, so oversized input affects only that one line. | **Accepted.** Recorded as LIMITATIONS #11. | `LIMITATIONS.md` |
| P15 | All deliverables and AC1–AC7 **present**. Weakest areas: (1) §5 candidate review is empty; (2) the scoring-guide file `Course-Registration-Rubric-Score.md` is not in the workspace, so the submission can't be self-scored; (3) no git history shows the work done step by step. | **Accepted as action items for the candidate.** These can't be fixed by the AI without misrepresenting who did the review. Suggested fixes: complete §5; obtain the rubric; `git init` and commit. | §5 |
| P16 | The AI wrote PR-001, a realistic proposed change with **10 planted defects**. It checked the change compiles and that only 3 of the defects are caught by the existing tests, and withheld the answer key. **The candidate reviewed it without AI help:** ran the existing suite on a copy (138 tests, 7 failures, 1 error, BUILD FAILURE) and tested the CLI by hand, finding that `drop L2` returned `DROPPED` for a learner who never enrolled. | **Candidate rejected PR-001.** Requested change: `drop` must return `NOT_REGISTERED`, not `DROPPED`, when the learner is neither enrolled nor waitlisted (AC5). Resubmit once the existing test suite passes in full. Score: 1 caught, 2 surfaced by tests, 7 missed (only findable by reading). | §4.6; `review-exercise/` |
| P17 | Wrote `manual-tests/run-and-record.ps1` and the expected-output files. The AI's own validation run first reported **FAIL**, which exposed two real bugs: the wrong working folder, and a PowerShell byte-order mark that corrupted piped input. Both were fixed, and the CLI now strips a leading BOM. **The candidate then ran it under their own name:** 137/137 automated, 25/25 manual, Overall PASS. | **Accepted.** | §3 D13; §4.5a; `docs/MANUAL_TEST_RESULTS.md` |

## 2. AI contributions (response summaries)

| Area | What the AI proposed or produced | Files |
|---|---|---|
| Toolchain discovery | Found an empty folder, JDK 27.0.1 and no Maven on the PATH. Looked up the latest stable versions on Maven Central (JUnit 6.1.3, compiler plugin 3.16.0, surefire 3.6.1). | `pom.xml` |
| Architecture | One service per offering with a read-only `LearnerDirectory` port, immutable records, and result codes instead of exceptions. | `service/`, `repository/`, `domain/` |
| Waitlist policy | Proposed **skip-and-remove** for learners who are ineligible when they reach the head of the queue, with eligibility re-checked at promotion. Gave the reasoning: no stale entries and positions only move forward. | `CourseEnrollmentService`, `DESIGN.md §5` |
| Concurrency | A single `ReentrantLock` per offering around every operation. | `CourseEnrollmentService` |
| Tests | Behaviour tests grouped by acceptance criterion, 5 repeated concurrency tests, domain-validation and CLI tests. 137 executions from 47 methods (109 after P1, 112 after P2, 137 after P10–P13). | `src/test/...` |
| Prompt playbook | At the candidate's request, wrote §6: 21 reusable prompts in six phases, with a check for each answer. 12 of them were then run as P4–P15. | `docs/AI_USAGE_RECORD.md §6` |
| Manual verification (P2) | Found that the AC4 "became ineligible while waiting" case couldn't be reached from the CLI. Added test-support commands that change transcripts in the learner directory only (never roster or waitlist state), plus one input script per AC with expected outputs. | `cli/`, `manual-tests/`, `docs/MANUAL_TEST_PLAN.md` |
| Docs | README, design spec, limitations, manual test plan, this record. | `README.md`, `docs/` |

## 3. Decisions log: accepted, edited, rejected (AI's own iterations)

| # | Suggestion / draft | Outcome | Reason |
|---|---|---|---|
| D1 | Policy option: *retain* an ineligible learner in the queue and skip over them | **Rejected** | They would be re-checked on every drop, hold misleading positions, and the rule for getting their place back would be unclear. Skip-and-remove was chosen (DESIGN §5). |
| D2 | Lock-free design using `ConcurrentLinkedQueue` + `ConcurrentHashMap.newKeySet()` | **Rejected** | Each collection is thread-safe on its own, but "check capacity → seat" and "drop → promote" span two collections, so they would not be atomic. |
| D3 | `ArrayDeque` for the waitlist | **Edited** to `LinkedHashSet` (`SequencedSet`) | It needs O(1) duplicate checks and O(1) removal by id when a waiting learner drops. Java 21+ `SequencedSet` gives FIFO `removeFirst()` as well. |
| D4 | Check eligibility before the duplicate check | **Edited** to duplicate check first | Otherwise a waitlisted learner whose record changed would get `PREREQUISITE_NOT_MET` instead of their real standing (`ALREADY_WAITLISTED`). |
| D5 | Require the learner to exist in the directory in order to drop | **Rejected** | A deleted learner record would strand a seat. Drops only check the roster (assumption A9, test `deletedLearnerCanStillDrop`). |
| D6 | First draft of the mixed-workload concurrency test had an unused counter map | **Edited** (removed) | Dead code that didn't strengthen any assertion. |
| D7 | `CommandProcessor` first draft used fully-qualified `java.util.Optional` | **Edited** | Replaced with a normal import for readability. |
| D8 | Download a standalone Maven distribution | **Not done** (blocked by the environment's permission policy) | Used the Maven 3.9.16 bundled with IntelliJ IDEA 2026.3 instead, then generated the Maven Wrapper (`mvnw`) so reviewers don't need Maven installed. |
| D9 | (P9) Remove the "always true" `enrolled.size() < capacity` loop guard | **Rejected** | Kept as a safeguard for future reuse of the method (e.g. raising capacity). |
| D10 | (P13) CLI `learners` hard-coded to `L1`–`L9` | **Edited** | Replaced with `InMemoryLearnerDirectory.findAll()`. |
| D11 | (P15) Have the AI fill in §5 "candidate's independent review" | **Rejected** | §5 must record the candidate's own judgement. |
| D12 | (candidate request) Replace the recorded prompts with a set of better-sounding prompts that were never run | **Rejected** | It would misrepresent the record. Instead, the request that produced the §6 playbook was taken out of §1 at the candidate's request (it is described in §2), and twelve playbook prompts were **actually run** as P4–P15. |
| D13 | (P17) First version of `run-and-record.ps1` | **Edited** after the AI's own validation run reported **FAIL** (2 of 25 manual cases, plus no automated summary) | Two real defects: (1) `cmd.exe` started outside the project folder, so `mvnw.cmd` wasn't found; (2) Windows PowerShell prefixed piped input with a UTF-8 byte-order mark, which corrupted the first command of each script. Fixed the script (project-root working directory, UTF-8 without BOM). Also hardened `WaitlistCli` to strip a leading BOM, so `Get-Content … \| java -jar …` works for every user. Re-run: 137/137 automated, 25/25 manual, PASS. |

## 4. Verification evidence

### 4.1 Full test run (`mvn clean verify`, JDK 27.0.1, Maven 3.9.16)

```
[INFO] Tests run: 137, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

(Final run via `./mvnw clean verify` after P4–P15: CommandProcessor 7, Domain model 6, concurrency
90, CourseEnrollmentService 34. Earlier runs: 109 after P1, 112 after P2.)

### 4.2 Mutation checks (do the tests catch real bugs?)

A test suite that passes proves little unless it fails on broken code. So the service was deliberately
broken in a throwaway copy of the project, and the tests were run against each broken version:

M1 and M2 were first run after P1 (109-test suite: 53 and 28 failures). All eight were run in P12
against the 112-test suite:

| Mutant | Change | Result (112 tests) |
|---|---|---|
| M1: no locking | Removed every `lock.lock()` / `lock.unlock()` | **Caught**: 11 failures + 45 errors (invariant violations), all in the concurrency suite |
| M2: LIFO promotion | `waitlist.removeFirst()` → `removeLast()` | **Caught**: 29 failures |
| M3: capacity off-by-one | `enrolled.size() < capacity` → `<=` in `enroll` | **Caught**: 64 errors (the over-capacity invariant guard fired) |
| M4: no re-check at promotion | `.map(offering::isEligible)` → `.map(l -> true)` | **Caught**: 4 failures (skip-policy tests) |
| M5: no prerequisite check at enroll | `if (!offering.isEligible(...))` → `if (false)` | **Caught**: 4 failures |
| M6: no waitlist duplicate check | `if (waitlist.contains(id))` → `if (false)` | **Caught, but only by 1 test** ⚠ |
| M7: prerequisites OR instead of AND | `containsAll` → `anyMatch` | **Caught, but only by 1 test** (domain level only) ⚠ |
| M8: skip-and-**retain** | skipped learner re-added to the queue | **Caught**: 5 errors (no-idle-seat invariant) |

**Follow-up (P12 → tests added → re-run against 137 tests):**

| Mutant | Before | After | Caught by |
|---|---|---|---|
| M6 | 1 failure | **21 failures** | `duplicateWaitlistIsIdempotent`, new `concurrentDuplicateWaitlistRequestsCreateOneEntry` (×20) |
| M7 | 1 failure | **2 failures** | `eligibilityRequiresAllPrerequisites`, new `allPrerequisitesRequiredByService` |

### 4.3 CLI smoke run (piped input)

```
> [ENROLLED] L1 enrolled in CS201-FALL-A.
> [ENROLLED] L2 enrolled in CS201-FALL-A.
> [WAITLISTED] CS201-FALL-A is full; L3 waitlisted at position 1.
> [WAITLISTED] CS201-FALL-A is full; L5 waitlisted at position 2.
> [PREREQUISITE_NOT_MET] L4 has not completed all prerequisites [CS101].
> [ALREADY_WAITLISTED] L3 is already on the waitlist at position 1.
> [DROPPED] L1 dropped CS201-FALL-A. Promoted L3 from the waitlist.
> [NOT_REGISTERED] L1 is not enrolled or waitlisted in CS201-FALL-A.
> Usage: drop <learnerId>
> Unknown command 'foo'. Type 'help' for usage.
> Offering : CS201-FALL-A
Seats    : 2/2 taken (0 open)
Enrolled : [L2, L3]
Waitlist : [L5]
```

### 4.4 Acceptance-criteria traceability

| AC | Covered by (test class → nested group / method) |
|---|---|
| AC1 capacity available + prerequisite | `CourseEnrollmentServiceTest.AvailableCapacity` |
| AC2 full → waitlist once; no duplicate records | `FullCapacityAndDuplicates`, `concurrentDuplicateRequestsCreateOneRecord` |
| AC3 drop → FIFO promotion | `FifoPromotion`, `concurrentDropsPromoteInOrder` |
| AC4 ineligible not enrolled/promoted; policy consistent | `PrerequisitePolicy` (6 tests), `ineligibleNotWaitlistedWhenFull` |
| AC5 invalid / repeated drops, no corruption | `InvalidAndRepeated` (parameterized blank ids, repeated drop, unknown ids) |
| AC6 tests incl. concurrency | all of the above + `CourseEnrollmentConcurrencyTest` |
| AC7 AI use recorded | this document |

### 4.5 Manual acceptance run (P2)

All five scripts in `manual-tests/` were piped through the built jar (`manual-tests/run-all.sh`, and
`manual-tests\run-all.cmd` on Windows). Every output line matched the *Expected* column in
[MANUAL_TEST_PLAN.md](MANUAL_TEST_PLAN.md). Key lines from the run:

```
> drop L1                       (AC3-1)
[DROPPED] L1 dropped CS201-FALL-A. Promoted L3 from the waitlist.
> enroll L4                     (AC4-2, course full)
[PREREQUISITE_NOT_MET] L4 has not completed all prerequisites [CS101].
> revoke L3 CS101               (AC4-3)
> drop L1                       (AC4-4)
[DROPPED] L1 dropped CS201-FALL-A. Removed ineligible from waitlist: [L3]. Promoted L5 from the waitlist.
> drop L1                       (AC5-2, repeated)
[NOT_REGISTERED] L1 is not enrolled or waitlisted in CS201-FALL-A.
> status                        (AC5-6, after all invalid requests)
Enrolled : [L2, L3]
Waitlist : []
```

### 4.5a Candidate's recorded test run (P17)

The candidate ran `manual-tests\run-and-record.ps1 -Tester "Sandeep Peruka"` on 2026-11-05 at 23:07
(JDK 27.0.1). Result: **Overall PASS**. Automated: `Tests run: 137, Failures: 0, Errors: 0, Skipped: 0
- BUILD SUCCESS`. Manual: **25 of 25** acceptance cases passed (AC1-1 … AC5-6). Full per-case report:
[MANUAL_TEST_RESULTS.md](MANUAL_TEST_RESULTS.md). Raw transcripts are in `manual-tests/results/`.

### 4.6 Planted-bug review exercise PR-001 (P16)

**Setup (by the AI):** `review-exercise/PR-001.diff` pitches itself as a helpful pull request:
`increaseCapacity`, id trimming, "transfer-student friendly" prerequisites, idempotent drop, a
"fairer" waitlist, a performance tweak, and a new test. It contains **10 planted defects**. Before
handing it over, the AI checked that it compiles and that only 3 of the defects are caught by the
existing tests. The answer key was withheld until the candidate reported. The real `src/` code was
never changed.

**Review (by the candidate, without AI help):**
- Copied the project to `..\AI-Assessment-PR001`, applied the proposed files, and ran `.\mvnw.cmd test`:
  **138 tests, 7 failures, 1 error, BUILD FAILURE**. The new `increaseCapacity` test passed.
- Built the copy and tested the CLI by hand. Found that `drop L2` returned `[DROPPED]` for a learner who
  **never enrolled**, a false success result.

**Result against the answer key:**

| Outcome | Defects |
|---|---|
| ✅ Caught (tests **and** reproduced by hand) | #8: false `DROPPED` for an unregistered learner (violates AC5) |
| ⚠️ Surfaced by failing tests, root cause not yet identified | #7: invented "same-department" prerequisite rule; #9: silent switch to skip-and-retain with a false "consistent with DESIGN.md" comment |
| ❌ Missed (only detectable by reading) | #1 no lock in `increaseCapacity`; #2 false "volatile = thread-safe" claim; #3 only one learner promoted; #4 no validation of seat count; #5 new test that proves nothing; #6 id trimming inconsistent with `drop` and A10; #10 invariant checks removed with a false justification |

**Decision (candidate): PR-001 rejected.** The candidate's reasons: the change breaks the existing
test suite (7 failures, 1 error), and their own manual run showed it reports `DROPPED` for a learner
who was never registered. AC5 requires a clear `NOT_REGISTERED` result there. The real code in
`src/` was not changed.

**Requested change (candidate):** `drop` must return `NOT_REGISTERED`, not `DROPPED`, when the learner
is neither enrolled nor waitlisted. This keeps results accurate and matches AC5. Resubmit once the
existing test suite passes in full.

**What this shows:** running tests and the app by hand caught every defect that changes the observed
behaviour of existing features. It caught none of the defects in **new** code that no test covers, or
in comments that make false claims. The remaining findings need line-by-line reading of the diff.
Full answer key: [review-exercise/README.md](../review-exercise/README.md#answer-key-revealed-after-round-1).

---

## 5. Candidate's independent review (to be completed by the candidate)

> Fill this in after reading the code yourself. Reviewers want to see *your* judgement: what you
> checked, what you changed, and what you would have done differently.

| AI output | Accepted / Edited / Rejected | What I checked or changed | How I verified it |
|---|---|---|---|
| Skip-and-remove waitlist policy | | | |
| Per-offering `ReentrantLock` | | | |
| Result-code API (no exceptions for expected failures) | | | |
| Test suite / AC coverage | | | |
| Design spec & assumptions | | | |

**Additional prompts I used (if any):**

| # | Prompt | Summary of response | Outcome |
|---|---|---|---|
| | | | |

---

## 6. Recommended prompt playbook (for reuse; not part of the executed record)

> **Status:** the prompts in this section are a **recommended workflow**. They show how an engineer
> could use AI for each stage of this job. They have **not** been run as part of sections 1–4, unless
> the "Used?" box is ticked and the outcome is recorded below.
>
> **How to use it:** run a prompt, review the response against its *Check the answer* list, then tick
> **Used?** and fill in the outcome line. If you act on the response, add a row to §5 as well.
> Never paste credentials, real student data or company-internal code into a prompt.

### 6.1 How to write a good prompt for this kind of work

Every prompt below follows the same five-part pattern. Reuse it for your own prompts.

| Part | Example |
|---|---|
| **Role**: who the AI should act as | "Act as a senior Java engineer reviewing a concurrency-sensitive service." |
| **Context**: the facts it can't guess | Paste the acceptance criteria, the relevant class, or the assumptions table. |
| **Task**: one concrete job | "List every way two concurrent `drop` calls could break invariant 3." |
| **Constraints**: what it must **not** do | "Java 27, no new dependencies, no frontend, no database, don't change the public API." |
| **Output format**: something you can check | "Return a table: scenario, invariant broken, line number, suggested fix." |

Ground rules that apply to every prompt:

- **One task per prompt.** "Design, implement and test everything" produces output too large to review properly.
- **Ask for reasoning and alternatives**, not only an answer. You can't judge a choice you weren't shown.
- **Ask it to list its assumptions.** Unstated assumptions are where AI output goes wrong.
- **Never trust, always verify.** Each prompt below says how to check the answer independently.

---

### Phase A: Understand the problem

#### A1. Find ambiguities in the brief
```text
Act as a senior engineer starting a new feature. Below is an assessment brief for a course
waitlist feature (enroll, drop, FIFO waitlist promotion for one course offering).

<paste the Problem Statement, Acceptance Criteria and Scope Boundaries>

Do not write code. List every requirement that is ambiguous or underspecified. For each one give:
(1) the exact phrase, (2) why it is ambiguous, (3) two or three possible interpretations,
(4) which interpretation you'd recommend and why. Pay special attention to prerequisites,
duplicate requests, what "eligible" means over time, and what happens when an ineligible
learner reaches the front of the queue.
```
**Check the answer:** it should at least flag (a) whether eligibility is re-checked at promotion,
(b) skip vs retain for ineligible learners, (c) whether a waitlisted learner can "drop", and (d) what
counts as an invalid request. Compare it with [DESIGN.md §5](DESIGN.md). Anything it found that §5 doesn't cover is a gap to close.
**Used?** ☑ **Outcome:** run as P4; see §1.1.

#### A2. Turn acceptance criteria into testable statements
```text
Convert each acceptance criterion below into Given/When/Then statements that a test could check.
Produce at least one positive and one negative case per criterion. Number them AC1.1, AC1.2, ...
Flag any criterion you could not make testable without an extra assumption, and state the assumption.

<paste Acceptance Criteria 1-6>
```
**Check the answer:** each statement must map to a test in `CourseEnrollmentServiceTest` or a manual
case in [MANUAL_TEST_PLAN.md](MANUAL_TEST_PLAN.md). Any statement that doesn't map is a missing test.
**Used?** ☑ **Outcome:** run as P5; see §1.1.

---

### Phase B: Decide the policy and design

#### B1. Compare waitlist policies before choosing
```text
For a FIFO course waitlist where a learner's prerequisite status can change while they wait,
compare these two policies when an ineligible learner reaches the head of the queue:
  (1) SKIP AND REMOVE - take them off the waitlist, promote the next eligible learner
  (2) SKIP AND RETAIN - leave them in place, promote the next eligible learner behind them
For each, describe: fairness to learners, implementation complexity, impact on queue positions
reported to learners, re-evaluation cost per drop, edge cases (all waiting learners ineligible,
learner regains eligibility), and how each would be tested. Then recommend one, and state what
product question I should confirm with a stakeholder before committing.
```
**Check the answer:** does it notice that with retain, "position 1" can be misleading indefinitely? Does
it raise the problem of a learner who is ineligible only briefly? Record the policy you choose and why in §5.
**Used?** ☑ **Outcome:** run as P6; see §1.1.

#### B2. Derive invariants
```text
Given this in-memory model for ONE course offering:
  - capacity: int >= 1
  - enrolled: ordered set of learner ids
  - waitlist: FIFO queue of learner ids
List the invariants that must hold after every enroll and drop operation. For each invariant:
state it formally, explain which operation could break it, and propose a cheap runtime check.
```
**Check the answer:** compare with DESIGN.md §4 (capacity bound, uniqueness across both collections,
no idle seat while learners wait, everyone enrolled was eligible when seated). Missing or extra invariants are worth discussing.
**Used?** ☑ **Outcome:** run as P7; see §1.1.

#### B3. Design the API and result model
```text
Design a Java 27 service API for enroll(learnerId) and drop(learnerId) on a single course offering.
Requirements: callers must be able to distinguish every outcome (enrolled, waitlisted, duplicate,
prerequisite failure, unknown learner, invalid input, not registered, left waitlist), and know
who was promoted or skipped after a drop. Compare (a) exceptions for business failures vs
(b) result objects with an enum code. Recommend one and show the record/enum signatures only - no
implementation.
```
**Check the answer:** compare with `ResultCode`, `EnrollmentResult` and `DropResult`. Does its version expose anything the current one lacks?
**Used?** ☐ **Outcome:** ______________________

#### B4. Choose a concurrency strategy
```text
The enroll operation does "check capacity, then add" and drop does "remove, then promote head of
waitlist". Both touch two collections. Compare for a single-JVM, in-memory service:
(1) synchronized methods, (2) one ReentrantLock, (3) ReadWriteLock, (4) lock-free concurrent
collections, (5) a single-threaded actor/queue. For each: is it correct for these compound
operations, and what does it cost? Recommend one and describe how it maps to a database
(row lock / optimistic version) later.
```
**Check the answer:** it must explain why option 4 is **not** correct on its own: each collection is
thread-safe, but the compound operations across them are not atomic. If it recommends lock-free collections without addressing that, reject the answer.
**Used?** ☑ **Outcome:** run as P8; see §1.1.

---

### Phase C: Plan and implement

#### C1. Break the work into tasks
```text
Break this feature into ordered implementation tasks of at most ~1 hour each, for a Maven project
on Java 27 with JUnit 6. For each task give: goal, files touched, definition of done, and which
acceptance criterion it advances. Put test-writing alongside each task, not at the end.
```
**Used?** ☐ **Outcome:** ______________________

#### C2. Implement one class at a time
```text
Act as a senior Java engineer. Implement CourseEnrollmentService.enroll(String learnerId) with
these rules, in this order: blank id -> INVALID_REQUEST; already enrolled -> ALREADY_ENROLLED;
already waitlisted -> ALREADY_WAITLISTED with 1-based position; unknown learner -> UNKNOWN_LEARNER;
prerequisites not met -> PREREQUISITE_NOT_MET; seat available -> ENROLLED; else -> WAITLISTED.
Constraints: Java 27, JDK only, guard all state with the existing ReentrantLock `lock`, never
throw for bad input, Javadoc on the public method explaining the evaluation order and why the
duplicate check runs before the eligibility check. Return only the method.
```
**Check the answer:** read it line by line. Is the lock released in `finally`? Is validation done
before taking the lock? Does a rejected request leave the state unchanged? Then run `./mvnw test`.
**Used?** ☐ **Outcome:** ______________________

#### C3. Explain code before accepting it
```text
Explain this method line by line as if teaching a new team member. Then list any behaviour that
surprised you or that you think might be a bug, with an input that would trigger it.

<paste promoteNextEligible() or any method you didn't write yourself>
```
**Check the answer:** if you can't follow the explanation, don't merge the code. Any "might be a bug" item becomes a test.
**Used?** ☑ **Outcome:** run as P9; see §1.1.

---

### Phase D: Test

#### D1. Generate a test matrix (before writing tests)
```text
Produce a test matrix for a single-offering enrollment service with capacity N and a prerequisite.
Rows = scenarios, columns = initial state, action, expected result code, expected roster,
expected waitlist. Cover: available capacity, exactly full, over-full, duplicate enroll (enrolled
and waitlisted), FIFO promotion over several drops, prerequisite failure with seats open and when
full, learner becoming ineligible while waiting, all waiting learners ineligible, waitlisted learner
dropping, repeated drop, unknown learner, null/blank id. Do not write code.
```
**Check the answer:** tick each row off against existing tests. Rows with no test are gaps to fill.
**Used?** ☑ **Outcome:** run as P10; see §1.1.

#### D2. Write focused JUnit 6 tests
```text
Write JUnit 6 (Jupiter) tests for the scenarios below. Use @Nested classes named after acceptance
criteria, @DisplayName on every test, Arrange/Act/Assert layout, and assert the FULL roster and
waitlist after each action (not just the result code) so state corruption is caught. No mocking
framework - use the InMemoryLearnerDirectory. Scenarios:
<paste rows from D1>
```
**Check the answer:** every test must fail if you break the code it covers. Check that with D4.
**Used?** ☐ **Outcome:** ______________________

#### D3. Design a concurrency test that would actually catch a race
```text
Write a JUnit 6 test proving that 200 concurrent enroll requests for distinct eligible learners
against capacity 10 produce exactly 10 ENROLLED and 190 WAITLISTED, with no duplicates and
no learner in both lists. Use a CountDownLatch start gate so threads really race, an
ExecutorService in try-with-resources, @RepeatedTest, and a @Timeout. Explain why a test without
a start gate might pass even on broken code.
```
**Check the answer:** remove the lock and confirm the test **fails** (see D4). A concurrency test that still passes on broken code proves nothing.
**Used?** ☑ **Outcome:** run as P11; see §1.1.

#### D4. Propose mutations to check the tests
```text
Here is my service and my test suite. Propose 8 small, realistic mutations to the service
(e.g. off-by-one in the capacity check, removeLast instead of removeFirst, missing re-check of
eligibility at promotion, lock not released). For each, predict which test should fail. If you
believe no test would fail, say so - that is a gap.

<paste CourseEnrollmentService.java and the test class names / display names>
```
**Check the answer:** apply each mutation **in a copy of the project** and run `./mvnw test`. Record
which mutants were caught, as in §4.2. A mutant that survives means a test is missing.
**Used?** ☑ **Outcome:** run as P12; see §1.1.

#### D5. Design manual acceptance scenarios
```text
Using this CLI (commands: enroll, drop, status, learners, grant, revoke, reset) and this demo
data (<paste DemoData table>), write a step-by-step manual test script for each acceptance
criterion. For each step give the exact command and the exact expected output line.
```
**Check the answer:** run each script for real ([MANUAL_TEST_PLAN.md](MANUAL_TEST_PLAN.md)). AI-predicted
output is often slightly wrong, so record the **actual** output.
**Used?** ☐ **Outcome:** ______________________

---

### Phase E: Review

#### E1. Adversarial code review
```text
Act as a strict reviewer whose goal is to find bugs, not to praise. Review this service for:
(1) any way to exceed capacity, (2) any way a learner ends up both enrolled and waitlisted or
listed twice, (3) any request that returns a "no change" code but actually changes state,
(4) lock misuse (not released, held during slow calls, missed paths), (5) inconsistent application
of the skip-and-remove policy. For each finding give the input sequence that triggers it.
If you find nothing in a category, say "none found" - do not invent issues.

<paste CourseEnrollmentService.java>
```
**Check the answer:** turn every claimed bug into a failing test **before** fixing it. If you can't
reproduce it, mark it rejected in §3/§5 and say why.
**Used?** ☑ **Outcome:** run as P13; see §1.1.

#### E2. Readability and comments review
```text
Review these files for readability by a new team member. Flag: comments that restate the code,
missing "why" comments, misleading names, methods over ~30 lines, and anything a newcomer would
need explained. Suggest concrete rewrites. Do not change behaviour.
```
**Used?** ☐ **Outcome:** ______________________

#### E3. Security and data-hygiene check
```text
Review this repository for: hard-coded secrets or credentials, real personal data, logging of
sensitive fields, and inputs that could cause unbounded memory use or crashes. Also check that
the prompts recorded in docs/AI_USAGE_RECORD.md contain no sensitive data.
```
**Used?** ☑ **Outcome:** run as P14; see §1.1.

---

### Phase F: Document and hand over

#### F1. Draft the design specification
```text
Write a design specification for this feature, proportionate to a small bounded service (2-3
pages max). Sections: service boundary, API, data model, invariants, prerequisite and waitlist
rules (as a numbered assumptions table), concurrency approach with rejected alternatives,
persistence plan, trade-offs table. Base it ONLY on the code and decisions below - do not
invent features.
<paste decisions from B1-B4 and the public API>
```
**Check the answer:** every claim in the spec must be true of the code. Look out for features the AI "remembered" that don't exist.
**Used?** ☐ **Outcome:** ______________________

#### F2. Find limitations and follow-up risks
```text
Given this design and code, list the limitations and follow-up risks WITHIN the current scope
(not missing out-of-scope features). For each: impact, likelihood, and a suggested follow-up.
Include risks introduced by the chosen waitlist policy and by in-memory, single-JVM locking.
```
**Check the answer:** compare with [LIMITATIONS.md](LIMITATIONS.md) and add anything new that you agree with.
**Used?** ☐ **Outcome:** ______________________

#### F3. Write the onboarding README
```text
Write a README for a new team member who has never seen this project. Include: context, a
glossary, the rules as a flowchart, setup for Windows and macOS/Linux, a command reference,
how to read the output, worked example scenarios with exact input and output, and a
troubleshooting table. Assume they will copy-paste commands, so every command must be exact.
```
**Check the answer:** replay every example through the app and diff the output, as was done for P3.
**Used?** ☐ **Outcome:** ______________________

#### F4. Final submission check
```text
Here is the assessment's deliverables list and acceptance criteria, and the file tree of my
submission. Act as the grader. For each deliverable and criterion, say: present / partial /
missing, and where the evidence is. Then list the three weakest areas of the submission and
what would most improve each one in under 30 minutes.
<paste deliverables list, ACs, and `tree` output>
```
**Check the answer:** fix anything marked *partial* or *missing*, then re-run the full build and the manual scripts one last time.
**Used?** ☑ **Outcome:** run as P15; see §1.1.

---

### 6.2 Prompting anti-patterns to avoid

| Don't | Why | Do instead |
|---|---|---|
| "Build the whole course registration feature." | Output too large to review properly. Scope creeps into catalog, UI, DB | One component per prompt (C1, C2) |
| Accept code because the tests the AI wrote pass | The same model wrote both, so they can share the same blind spot | Mutation-check the tests (D4); write some tests yourself |
| "Is this code correct?" | Invites a reassuring "yes" | Ask for specific failure modes and triggering inputs (E1) |
| Let the AI pick the waitlist policy silently | It's a product decision the brief says you must state | Ask for a comparison and choose yourself (B1) |
| Paste production code, real student data or secrets | Data-handling violation | Use the demo data and anonymised snippets |
| Copy AI-predicted CLI output into docs | Predicted output is often slightly wrong | Run it and paste the real output (D5, F3) |
| Leave AI involvement out of the record | The assessment explicitly scores candour | Record the prompt, the response summary, your decision and the evidence |

### 6.3 Recording template (copy into §1 and §3 after running a prompt)

```text
| P# | <prompt summary, no sensitive data> | <engineering activity> |
| D# | <what the AI suggested> | Accepted / Edited / Rejected | <why> |
Verification: <command run + result, e.g. "./mvnw test -> 112 passed"; or mutant caught; or manual script output>
Files affected: <paths>
```
