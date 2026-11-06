# Manual Acceptance Test Plan

The JUnit suite checks the code automatically. This plan lets a person **run the actual application,
type inputs, and see** that each acceptance criterion is met.

Every case below has a matching script in [`manual-tests/`](../manual-tests). You can run the cases
in either of two ways:

- **Option A, by hand:** start the CLI and type each *Input* line yourself.
- **Option B, scripted:** pipe a script into the CLI and compare its output with the *Expected* column.
- **Option C, run and record everything (recommended for the submission):** one command runs the
  automated suite and all 25 manual cases. It checks each case against its expected output and writes
  **[MANUAL_TEST_RESULTS.md](MANUAL_TEST_RESULTS.md)** with PASS/FAIL per case under your name:

  ```bash
  powershell -ExecutionPolicy Bypass -File manual-tests\run-and-record.ps1 -Tester "Your Name"
  ```

  Raw transcripts go to `manual-tests/results/`. Expected outputs are in `manual-tests/expected/`.

Use the **Actual / Pass?** column to record your own result. That is your evidence of independent verification.

> **Recorded run:** the candidate's Option C run on 2026-11-05 passed all 25 cases. See
> [MANUAL_TEST_RESULTS.md](MANUAL_TEST_RESULTS.md). The *Actual / Pass?* columns below are only
> needed if you run cases by hand.

---

## 0. Setup

```bash
./mvnw clean package          # builds the jar and runs the 137 automated tests
```

```bash
java -jar target/course-waitlist-1.0.0.jar          # Option A: interactive
```

```bash
java -jar target/course-waitlist-1.0.0.jar < manual-tests/ac1-available-capacity.txt   # Option B: one script (cmd / bash)
```

```bash
Get-Content manual-tests\ac1-available-capacity.txt | java -jar target\course-waitlist-1.0.0.jar   # Option B: one script (PowerShell)
```

> When typing by hand, enter the **commands** from the *Input* column (e.g. `enroll L1`), not the
> test IDs (`AC1-2`). A bare id like `L1` is not a command either.

```bash
manual-tests/run-all.sh        # Option B: all scripts (Windows: manual-tests\run-all.cmd)
```

When you run the scripts, each command is echoed after the `>` prompt and the `#` lines label the
test case IDs, so the output reads like an interactive session.

### Demo data (the same at every start, and after `reset`)

Offering **CS201-FALL-A**, capacity **2**, prerequisite **CS101**.

| Learner | Name | Completed | Eligible? |
|---|---|---|---|
| L1 | Ada | CS101 | yes |
| L2 | Grace | CS101 | yes |
| L3 | Linus | CS101, MATH100 | yes |
| L4 | Alan | — | **no** |
| L5 | Barbara | CS101 | yes |
| L6 | Edsger | CS101 | yes |

### Commands used for testing

| Command | Purpose |
|---|---|
| `enroll <id>` / `drop <id>` / `status` | The feature under test |
| `learners` | Show transcripts and eligibility |
| `revoke <id> <course>` / `grant <id> <course>` | **Test support:** simulate the student-records system changing a transcript while a learner waits (needed for AC4) |
| `reset` | **Test support:** return to the initial demo state |

> **Each script starts from a fresh process.** If you are typing cases by hand in one long session,
> type `reset` before each section.

---

## AC1: Enrollment succeeds when capacity remains and prerequisite is met

Script: `manual-tests/ac1-available-capacity.txt`

| ID | Input | Expected | Actual / Pass? |
|---|---|---|---|
| AC1-1 | `status` | `Seats : 0/2 taken (2 open)`, Enrolled `[]` | |
| AC1-2 | `enroll L1` | `[ENROLLED] L1 enrolled in CS201-FALL-A.` | |
| AC1-3 | `enroll L2` | `[ENROLLED] L2 enrolled in CS201-FALL-A.` (last seat) | |
| AC1-4 | `status` | `2/2 taken (0 open)`, Enrolled `[L1, L2]`, Waitlist `[]` | |

## AC2: When full, an eligible learner joins the waitlist once; duplicates create no records

Script: `manual-tests/ac2-full-and-duplicates.txt` (setup: `enroll L1`, `enroll L2`)

| ID | Input | Expected | Actual / Pass? |
|---|---|---|---|
| AC2-1 | `enroll L3` | `[WAITLISTED] ... L3 waitlisted at position 1.` | |
| AC2-2 | `enroll L5` | `[WAITLISTED] ... L5 waitlisted at position 2.` | |
| AC2-3 | `enroll L1` | `[ALREADY_ENROLLED] L1 is already enrolled ...` | |
| AC2-4 | `enroll L3` (twice) | `[ALREADY_WAITLISTED] L3 is already on the waitlist at position 1.` both times | |
| AC2-5 | `status` | Enrolled `[L1, L2]`, Waitlist `[L3, L5]`, with **no duplicates** | |

## AC3: Dropping opens a seat and promotes the earliest eligible waiter

Script: `manual-tests/ac3-drop-fifo-promotion.txt` (setup: L1, L2 enrolled; L3, L5, L6 waiting)

| ID | Input | Expected | Actual / Pass? |
|---|---|---|---|
| AC3-1 | `drop L1` | `[DROPPED] L1 dropped ... Promoted L3 from the waitlist.` → Enrolled `[L2, L3]`, Waitlist `[L5, L6]` | |
| AC3-2 | `drop L2` | `... Promoted L5 ...` (FIFO) → Enrolled `[L3, L5]`, Waitlist `[L6]` | |
| AC3-3 | `drop L6` | `[LEFT_WAITLIST] L6 left the waitlist ...`, nobody promoted, Enrolled unchanged | |
| AC3-4 | `drop L3` | `[DROPPED] ... Seat is now open.` → `1/2 taken (1 open)` | |

## AC4: An ineligible learner is never enrolled or promoted (skip-and-remove policy)

Script: `manual-tests/ac4-prerequisite-policy.txt`

| ID | Input | Expected | Actual / Pass? |
|---|---|---|---|
| AC4-1 | `enroll L4` (seats open) | `[PREREQUISITE_NOT_MET] L4 has not completed all prerequisites [CS101].` Status still `0/2` | |
| AC4-2 | `enroll L1`, `enroll L2`, then `enroll L4` (full) | `[PREREQUISITE_NOT_MET]`, and Waitlist stays `[]` (an ineligible learner is **not** waitlisted) | |
| AC4-3 | `enroll L3`, `enroll L5`, `revoke L3 CS101` | `[TRANSCRIPT] CS101 revoked for L3; now [MATH100].` | |
| AC4-4 | `drop L1` | `... Removed ineligible from waitlist: [L3]. Promoted L5 from the waitlist.` → Enrolled `[L2, L5]`, Waitlist `[]` | |
| AC4-5 | `grant L3 CS101`, `enroll L6`, `enroll L3` | L6 at position 1, **L3 at position 2** (they rejoin at the back, and their old place is not restored) | |
| AC4-6 | `reset`, fill with L1, L2, wait L3, L5, `revoke` both, `drop L1` | `Removed ineligible from waitlist: [L3, L5]. Seat is now open.` → `1/2 taken`, Waitlist `[]` | |

## AC5: Invalid requests and duplicate drops return clear results without corrupting state

Script: `manual-tests/ac5-invalid-and-repeated.txt` (setup: L1, L2 enrolled; L3 waiting)

| ID | Input | Expected | Actual / Pass? |
|---|---|---|---|
| AC5-1 | `drop L1` | `[DROPPED] ... Promoted L3 ...` | |
| AC5-2 | `drop L1` again | `[NOT_REGISTERED] L1 is not enrolled or waitlisted ...`, nobody promoted | |
| AC5-3 | `drop L6` (never registered) | `[NOT_REGISTERED]` | |
| AC5-4 | `enroll GHOST`, `drop GHOST` | `[UNKNOWN_LEARNER] No learner with id GHOST.` / `[NOT_REGISTERED]` | |
| AC5-5 | `enroll`, `drop`, `drop L1 L2`, `register L1` | `Usage: ...` messages, then `Unknown command 'register'` | |
| AC5-6 | `status` | Still Enrolled `[L2, L3]`, Waitlist `[]`. **Nothing was corrupted.** | |

---

## What can't be tested by hand (and where it is covered instead)

| Behaviour | Why not by hand | Covered by |
|---|---|---|
| **Concurrency** (AC6): many simultaneous requests never exceed capacity or duplicate records | A person can't type requests at the same instant | `CourseEnrollmentConcurrencyTest`. Run it alone with `./mvnw test -Dtest=CourseEnrollmentConcurrencyTest` |
| A null or whitespace-only learner id → `INVALID_REQUEST` | The CLI splits input on spaces, so a blank id can't reach the service | `CourseEnrollmentServiceTest.InvalidAndRepeated` (parameterized) |
| A learner deleted from the directory can still drop / is skipped at promotion | The CLI has no "delete learner" command | `deletedLearnerCanStillDrop`, `missingLearnerIsSkipped` |

---

## Reference run

The outputs in the *Expected* column were captured by running all five scripts against the built jar
(JDK 27.0.1). Every line matched. If your output differs, the most common cause is typing several
sections in one session without `reset` in between.
