# CourseWaitlist

Enrollment, dropping and **first-come-first-served (FIFO) waitlist promotion** for a single course
offering. Written in **Java 27**, tested with **JUnit 6**, and run from a small command-line app.

> **New to the project?** Read sections 1–5 in order, about 15 minutes, and you'll have run every
> important behaviour yourself. Sections 6 onward are reference material.

---

## Contents

1. [What this project is](#1-what-this-project-is)
2. [The rules in 60 seconds](#2-the-rules-in-60-seconds)
3. [Getting started](#3-getting-started)
4. [Using the app](#4-using-the-app)
5. [Example scenarios: what to type and what to expect](#5-example-scenarios-what-to-type-and-what-to-expect)
6. [Running scenarios from a script](#6-running-scenarios-from-a-script)
7. [Troubleshooting and common mistakes](#7-troubleshooting-and-common-mistakes)
8. [Automated tests](#8-automated-tests)
9. [Project structure](#9-project-structure)
10. [Reading the code](#10-reading-the-code)
11. [Further documentation](#11-further-documentation)

---

## 1. What this project is

This was built for a *Focused AI-Assisted Engineering Assessment*. The task was to implement one
**bounded slice** of a course-registration system: the part that decides who gets a seat in a course
section and who waits.

It is deliberately small:

| In scope | Out of scope (by design) |
|---|---|
| Enrolling in **one** course offering | Course catalog, multiple courses or terms |
| Dropping, and leaving the waitlist | Frontend / web UI |
| FIFO waitlist with automatic promotion | Database (everything is in memory) |
| Prerequisite checks | Payments, login, identity |
| Safe behaviour under concurrent requests | Deployment |

No starter project was supplied, so the assessment allowed a **minimal service with an in-memory store
and a command-line interface (CLI)**. That is what this is.

### Terms used throughout

| Term | Meaning |
|---|---|
| **Offering** | One concrete section of a course, e.g. `CS201-FALL-A`. Has a fixed **capacity** (number of seats). |
| **Learner** | A student, identified by an id such as `L1`. Has a list of **completed courses**. |
| **Prerequisite** | A course a learner must have completed before enrolling, e.g. `CS101`. |
| **Eligible** | The learner has completed **all** prerequisites of the offering. |
| **Enrolled** | The learner holds a seat. |
| **Waitlist** | Ordered queue of eligible learners waiting for a seat. The first in line is the **head**. |
| **Promotion** | Moving the head of the waitlist into a seat that just opened. |

---

## 2. The rules in 60 seconds

```
  enroll request
        │
        ▼
  Already enrolled or waiting? ──yes──► ALREADY_ENROLLED / ALREADY_WAITLISTED  (nothing changes)
        │ no
        ▼
  Known learner?  ──no──► UNKNOWN_LEARNER
        │ yes
        ▼
  Has all prerequisites? ──no──► PREREQUISITE_NOT_MET  (never put on the waitlist)
        │ yes
        ▼
  Seat free? ──yes──► ENROLLED
        │ no
        ▼
  WAITLISTED (added to the back of the queue)


  drop request
        │
        ├─ learner is enrolled   ──► DROPPED → seat opens → promote the head of the waitlist
        ├─ learner is waiting    ──► LEFT_WAITLIST (no seat opened, nobody promoted)
        └─ neither               ──► NOT_REGISTERED (nothing changes)
```

**The important policy decision.** A learner's record can change while they wait, for example if a
grade is revised. So eligibility is **checked again at promotion time**. If the learner at the head of
the queue is no longer eligible, they are **skipped and removed** from the waitlist, and the next
learner is promoted instead. If they become eligible again later, they must request again, and they
join at the **back** of the queue.

Full reasoning and all assumptions are in [docs/DESIGN.md Section 5](docs/DESIGN.md#5-prerequisite-and-waitlist-rules-assumptions).

---

## 3. Getting started

### Prerequisites

| Need | Check with | Notes |
|---|---|---|
| **JDK 27+** | `java -version` | Must say `27` or higher |
| Maven | *not required* | The included Maven Wrapper (`mvnw` / `mvnw.cmd`) downloads Maven 3.9.16 automatically on first run |

### Build

Run these from the project root, the folder that contains `pom.xml`.

**Windows (PowerShell or cmd):**
```bash
.\mvnw.cmd clean package
```

**macOS / Linux / Git Bash:**
```bash
./mvnw clean package
```

This compiles the code, runs all **137 automated tests**, and builds `target/course-waitlist-1.0.0.jar`.
The build should end with:

```
[INFO] Tests run: 137, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Start the app

```bash
java -jar target/course-waitlist-1.0.0.jar
```

You should see:

```
CourseWaitlist CLI - offering CS201-FALL-A (capacity 2, prerequisite CS101)
Demo learners: L1, L2, L3, L5, L6 (have CS101), L4 (missing CS101)
Type 'help' for commands.
>
```

The `>` is the prompt. Type one command, press **Enter**, and read the result. Type `quit` to exit.

---

## 4. Using the app

### Commands

Every command is **a command word followed by its arguments, separated by spaces**.

| Command | Example | What it does |
|---|---|---|
| `enroll <learnerId>` | `enroll L1` | Request a seat. The learner is waitlisted if the course is full. |
| `drop <learnerId>` | `drop L1` | Give up a seat, or leave the waitlist. |
| `status` | `status` | Show seats taken, who is enrolled and who is waiting. |
| `learners` | `learners` | Show every demo learner, their completed courses and whether they are eligible. |
| `grant <learnerId> <course>` | `grant L4 CS101` | *Test support:* mark a course as completed for a learner. |
| `revoke <learnerId> <course>` | `revoke L3 CS101` | *Test support:* remove a completed course (simulates a transcript change). |
| `reset` | `reset` | *Test support:* go back to the starting state (empty course, original transcripts). |
| `help` | `help` | List the commands. |
| `quit` / `exit` | `quit` | Leave the app. |

> The *test support* commands exist so you can try every rule by hand. In a real system, transcripts
> would come from a student-records service. These commands only change learner records, never the
> enrolled list or the waitlist.

### Demo data (loaded at start, and restored by `reset`)

Offering **CS201-FALL-A**: **2 seats**, prerequisite **CS101**.

| Learner | Name | Completed courses | Eligible for CS201? |
|---|---|---|---|
| L1 | Ada | CS101 | ✅ yes |
| L2 | Grace | CS101 | ✅ yes |
| L3 | Linus | CS101, MATH100 | ✅ yes |
| L4 | Alan | *(none)* | ❌ **no**. Use L4 to see a prerequisite failure. |
| L5 | Barbara | CS101 | ✅ yes |
| L6 | Edsger | CS101 | ✅ yes |

### Reading the output

Every result starts with a **result code** in square brackets, followed by a plain-English message:

```
[WAITLISTED] CS201-FALL-A is full; L3 waitlisted at position 1.
 └─ code ──┘ └──────────────── message ───────────────────────┘
```

| Result code | Meaning | Did anything change? |
|---|---|---|
| `ENROLLED` | Learner got a seat | ✅ yes |
| `WAITLISTED` | Course full; learner added to the back of the waitlist | ✅ yes |
| `DROPPED` | Learner gave up a seat (the message says who was promoted, if anyone) | ✅ yes |
| `LEFT_WAITLIST` | Waiting learner left the queue | ✅ yes |
| `ALREADY_ENROLLED` | Duplicate request: learner already has a seat | ❌ no |
| `ALREADY_WAITLISTED` | Duplicate request: learner is already waiting (position shown) | ❌ no |
| `PREREQUISITE_NOT_MET` | Learner lacks a prerequisite | ❌ no |
| `NOT_REGISTERED` | Drop for someone who is neither enrolled nor waiting (e.g. a repeated drop) | ❌ no |
| `UNKNOWN_LEARNER` | No learner with that id exists | ❌ no |
| `INVALID_REQUEST` | Blank learner id (reachable from code, not from the CLI) | ❌ no |

The `status` output looks like this:

```
Offering : CS201-FALL-A
Seats    : 2/2 taken (0 open)
Enrolled : [L1, L2]          ← in enrollment order
Waitlist : [L3, L5]          ← L3 is the head (next to be promoted)
```

---

## 5. Example scenarios: what to type and what to expect

Each scenario starts with `reset`, so you can run them in any order in one session.
**Type only the lines after `>`**, one at a time. The lines without `>` are what the app prints back.

All outputs below were captured from the real application.

### Scenario 1: Enroll while seats are available

*Goal: an eligible learner gets a seat while capacity remains.* (Acceptance criterion AC1)

```
> reset
[RESET] Demo state restored: CS201-FALL-A is empty.
> enroll L1
[ENROLLED] L1 enrolled in CS201-FALL-A.
> enroll L2
[ENROLLED] L2 enrolled in CS201-FALL-A.
> status
Offering : CS201-FALL-A
Seats    : 2/2 taken (0 open)
Enrolled : [L1, L2]
Waitlist : []
```

✅ **What this proves:** both learners are seated, and the course is now exactly full.

---

### Scenario 2: Course is full, so learners join the waitlist

*Goal: once the course is full, eligible learners queue in arrival order.* (AC2)

```
> reset
[RESET] Demo state restored: CS201-FALL-A is empty.
> enroll L1
[ENROLLED] L1 enrolled in CS201-FALL-A.
> enroll L2
[ENROLLED] L2 enrolled in CS201-FALL-A.
> enroll L3
[WAITLISTED] CS201-FALL-A is full; L3 waitlisted at position 1.
> enroll L5
[WAITLISTED] CS201-FALL-A is full; L5 waitlisted at position 2.
> status
Offering : CS201-FALL-A
Seats    : 2/2 taken (0 open)
Enrolled : [L1, L2]
Waitlist : [L3, L5]
```

✅ **What this proves:** L3 arrived first, so L3 is first in line.

---

### Scenario 3: Duplicate requests don't create duplicate records

*Goal: asking twice is harmless.* (AC2)

```
> reset
[RESET] Demo state restored: CS201-FALL-A is empty.
> enroll L1
[ENROLLED] L1 enrolled in CS201-FALL-A.
> enroll L2
[ENROLLED] L2 enrolled in CS201-FALL-A.
> enroll L3
[WAITLISTED] CS201-FALL-A is full; L3 waitlisted at position 1.
> enroll L1
[ALREADY_ENROLLED] L1 is already enrolled in CS201-FALL-A.
> enroll L3
[ALREADY_WAITLISTED] L3 is already on the waitlist at position 1.
> enroll L3
[ALREADY_WAITLISTED] L3 is already on the waitlist at position 1.
> status
Offering : CS201-FALL-A
Seats    : 2/2 taken (0 open)
Enrolled : [L1, L2]
Waitlist : [L3]
```

✅ **What this proves:** L1 and L3 each appear exactly once, and L3 keeps position 1.

---

### Scenario 4: A drop promotes the first learner in line

*Goal: when a seat opens, the earliest waiting learner gets it automatically.* (AC3)

```
> reset
[RESET] Demo state restored: CS201-FALL-A is empty.
> enroll L1
[ENROLLED] L1 enrolled in CS201-FALL-A.
> enroll L2
[ENROLLED] L2 enrolled in CS201-FALL-A.
> enroll L3
[WAITLISTED] CS201-FALL-A is full; L3 waitlisted at position 1.
> enroll L5
[WAITLISTED] CS201-FALL-A is full; L5 waitlisted at position 2.
> enroll L6
[WAITLISTED] CS201-FALL-A is full; L6 waitlisted at position 3.
> drop L1
[DROPPED] L1 dropped CS201-FALL-A. Promoted L3 from the waitlist.
> drop L2
[DROPPED] L2 dropped CS201-FALL-A. Promoted L5 from the waitlist.
> status
Offering : CS201-FALL-A
Seats    : 2/2 taken (0 open)
Enrolled : [L3, L5]
Waitlist : [L6]
```

✅ **What this proves:** promotions follow arrival order (L3, then L5), and L6 is still next in line.

---

### Scenario 5: Leaving the waitlist doesn't promote anyone

*Goal: a waiting learner who drops just leaves the queue, because no seat was freed.* (AC3)

Continue straight on from Scenario 4, without `reset`:

```
> drop L6
[LEFT_WAITLIST] L6 left the waitlist for CS201-FALL-A.
> drop L3
[DROPPED] L3 dropped CS201-FALL-A. Seat is now open.
> status
Offering : CS201-FALL-A
Seats    : 1/2 taken (1 open)
Enrolled : [L5]
Waitlist : []
```

✅ **What this proves:** L6 leaving changes no seats. When L3 drops with nobody waiting, the seat simply stays open.

---

### Scenario 6: A learner without the prerequisite is rejected

*Goal: an ineligible learner is never enrolled, and is never waitlisted either.* (AC4)

```
> reset
[RESET] Demo state restored: CS201-FALL-A is empty.
> learners
Learners (prerequisites: [CS101])
  L1  Ada      [CS101]            eligible
  L2  Grace    [CS101]            eligible
  L3  Linus    [CS101, MATH100]   eligible
  L4  Alan     []                 NOT eligible
  L5  Barbara  [CS101]            eligible
  L6  Edsger   [CS101]            eligible
> enroll L4
[PREREQUISITE_NOT_MET] L4 has not completed all prerequisites [CS101].
> enroll L1
[ENROLLED] L1 enrolled in CS201-FALL-A.
> enroll L2
[ENROLLED] L2 enrolled in CS201-FALL-A.
> enroll L4
[PREREQUISITE_NOT_MET] L4 has not completed all prerequisites [CS101].
> status
Offering : CS201-FALL-A
Seats    : 2/2 taken (0 open)
Enrolled : [L1, L2]
Waitlist : []
```

✅ **What this proves:** L4 is rejected both when seats are open and when the course is full, and L4 never appears on the waitlist.

**Try this next:** give L4 the prerequisite and free a seat. L4 can now enroll.

```
> grant L4 CS101
[TRANSCRIPT] CS101 granted for L4; now [CS101].
> drop L1
[DROPPED] L1 dropped CS201-FALL-A. Seat is now open.
> enroll L4
[ENROLLED] L4 enrolled in CS201-FALL-A.
```

---

### Scenario 7: A learner who loses eligibility while waiting is skipped (the key policy)

*Goal: show the skip-and-remove rule from [section 2](#2-the-rules-in-60-seconds).* (AC4)

```
> reset
[RESET] Demo state restored: CS201-FALL-A is empty.
> enroll L1
[ENROLLED] L1 enrolled in CS201-FALL-A.
> enroll L2
[ENROLLED] L2 enrolled in CS201-FALL-A.
> enroll L3
[WAITLISTED] CS201-FALL-A is full; L3 waitlisted at position 1.
> enroll L5
[WAITLISTED] CS201-FALL-A is full; L5 waitlisted at position 2.
> revoke L3 CS101
[TRANSCRIPT] CS101 revoked for L3; now [MATH100].
> drop L1
[DROPPED] L1 dropped CS201-FALL-A. Removed ineligible from waitlist: [L3]. Promoted L5 from the waitlist.
> status
Offering : CS201-FALL-A
Seats    : 2/2 taken (0 open)
Enrolled : [L2, L5]
Waitlist : []
```

Now L3 regains the course and asks again:

```
> grant L3 CS101
[TRANSCRIPT] CS101 granted for L3; now [CS101, MATH100].
> enroll L6
[WAITLISTED] CS201-FALL-A is full; L6 waitlisted at position 1.
> enroll L3
[WAITLISTED] CS201-FALL-A is full; L3 waitlisted at position 2.
```

✅ **What this proves:** L3 was at the front but no longer eligible, so L3 was **removed** and L5 got the seat. When L3 came back, L3 joined **behind** L6 and did not get the old place back.

---

### Scenario 8: Everyone waiting is ineligible, so the seat stays open

*Goal: if nobody in the queue qualifies, the waitlist empties and the seat is left open.* (AC4)

```
> reset
[RESET] Demo state restored: CS201-FALL-A is empty.
> enroll L1
[ENROLLED] L1 enrolled in CS201-FALL-A.
> enroll L2
[ENROLLED] L2 enrolled in CS201-FALL-A.
> enroll L3
[WAITLISTED] CS201-FALL-A is full; L3 waitlisted at position 1.
> enroll L5
[WAITLISTED] CS201-FALL-A is full; L5 waitlisted at position 2.
> revoke L3 CS101
[TRANSCRIPT] CS101 revoked for L3; now [MATH100].
> revoke L5 CS101
[TRANSCRIPT] CS101 revoked for L5; now [].
> drop L1
[DROPPED] L1 dropped CS201-FALL-A. Removed ineligible from waitlist: [L3, L5]. Seat is now open.
> status
Offering : CS201-FALL-A
Seats    : 1/2 taken (1 open)
Enrolled : [L2]
Waitlist : []
```

✅ **What this proves:** ineligible learners are never promoted, even when that leaves a seat empty.

---

### Scenario 9: Invalid requests and repeated drops don't break anything

*Goal: mistakes get a clear message and leave the state untouched.* (AC5)

```
> reset
[RESET] Demo state restored: CS201-FALL-A is empty.
> enroll L1
[ENROLLED] L1 enrolled in CS201-FALL-A.
> enroll L2
[ENROLLED] L2 enrolled in CS201-FALL-A.
> enroll L3
[WAITLISTED] CS201-FALL-A is full; L3 waitlisted at position 1.
> drop L1
[DROPPED] L1 dropped CS201-FALL-A. Promoted L3 from the waitlist.
> drop L1
[NOT_REGISTERED] L1 is not enrolled or waitlisted in CS201-FALL-A.
> drop L6
[NOT_REGISTERED] L6 is not enrolled or waitlisted in CS201-FALL-A.
> enroll GHOST
[UNKNOWN_LEARNER] No learner with id GHOST.
> enroll
Usage: enroll <learnerId>
> drop L1 L2
Usage: drop <learnerId>
> register L1
Unknown command 'register'. Type 'help' for usage.
> status
Offering : CS201-FALL-A
Seats    : 2/2 taken (0 open)
Enrolled : [L2, L3]
Waitlist : []
```

✅ **What this proves:** the second `drop L1` does **not** promote anyone again. Unknown ids and bad
commands are reported clearly, and the final status is exactly what it was after the first drop.

---

### Scenario coverage at a glance

| Scenario | Acceptance criterion | Rule demonstrated |
|---|---|---|
| 1 | AC1 | Seat available → enrolled |
| 2, 3 | AC2 | Full → waitlisted once; duplicates are harmless |
| 4, 5 | AC3 | Drop → FIFO promotion; leaving the waitlist promotes nobody |
| 6, 7, 8 | AC4 | Ineligible never enrolled or promoted; skip-and-remove policy |
| 9 | AC5 | Invalid and repeated requests don't corrupt state |
| — | AC6 | Concurrency can't be typed by hand. See [section 8](#8-automated-tests) |

A formal version with test IDs and a blank *Actual / Pass?* column for sign-off is in
[docs/MANUAL_TEST_PLAN.md](docs/MANUAL_TEST_PLAN.md).

---

## 6. Running scenarios from a script

Ready-made input files, one per acceptance criterion, are in [`manual-tests/`](manual-tests). Lines that
start with `#` are comments that label each step. When you pipe a file in, the app echoes each command
so the output reads like a typed session.

| Script | Covers |
|---|---|
| `ac1-available-capacity.txt` | Scenario 1 |
| `ac2-full-and-duplicates.txt` | Scenarios 2–3 |
| `ac3-drop-fifo-promotion.txt` | Scenarios 4–5 |
| `ac4-prerequisite-policy.txt` | Scenarios 6–8 |
| `ac5-invalid-and-repeated.txt` | Scenario 9 |

**Run all of them (Windows):**
```bash
manual-tests\run-all.cmd
```

**Run all of them (macOS / Linux / Git Bash):**
```bash
manual-tests/run-all.sh
```

**Run one script (Windows PowerShell):**
```bash
Get-Content manual-tests\ac4-prerequisite-policy.txt | java -jar target\course-waitlist-1.0.0.jar
```

**Run one script (cmd, macOS, Linux, Git Bash):**
```bash
java -jar target/course-waitlist-1.0.0.jar < manual-tests/ac4-prerequisite-policy.txt
```

**Write your own:** create a `.txt` file with one command per line and pipe it in the same way.

### Run everything and record the results (Windows)

One command runs the 137 automated tests **and** all 25 manual acceptance cases. It compares each
case's actual output with the expected output and writes a signed results report,
[docs/MANUAL_TEST_RESULTS.md](docs/MANUAL_TEST_RESULTS.md):

```bash
powershell -ExecutionPolicy Bypass -File manual-tests\run-and-record.ps1 -Tester "Your Name"
```

Expected console output at the end:

```
Automated: Tests run: 137, Failures: 0, Errors: 0, Skipped: 0 - BUILD SUCCESS
Manual:    25 / 25 cases passed
Overall:   PASS
Report written to docs\MANUAL_TEST_RESULTS.md
```

The report lists PASS/FAIL for every case (AC1-1 … AC5-6) with a link to its raw transcript in
`manual-tests/results/`. Re-run it any time; it overwrites the previous report.

---

## 7. Troubleshooting and common mistakes

| You see | Why | Fix |
|---|---|---|
| `Unknown command 'AC1'` | `AC1` is the name of a test case, not a command | Type the commands **inside** a scenario, e.g. `enroll L1` |
| `Unknown command 'L1'` | A learner id on its own isn't a command | Put a command first: `enroll L1` or `drop L1` |
| `Usage: enroll <learnerId>` | The command is missing its learner id (or has too many) | `enroll L1`, exactly one id |
| Output doesn't match a scenario | Earlier commands in the same session changed the state | Type `reset` and start the scenario again |
| `The '<' operator is reserved for future use` | PowerShell doesn't support `<` redirection | Use `Get-Content file.txt \| java -jar ...` ([section 6](#6-running-scenarios-from-a-script)) |
| `UnsupportedClassVersionError` | Java older than 27 | Install JDK 27+, and check with `java -version` |
| `Unable to access jarfile target/...` | The jar hasn't been built yet, or you're in the wrong folder | Run the build from the folder that contains `pom.xml` ([section 3](#3-getting-started)) |
| `./mvnw` is not recognized (Windows) | Windows uses the `.cmd` script | Use `.\mvnw.cmd ...` |

Ids are **case-sensitive**: `l1` is not `L1`. Command words are not case-sensitive: `ENROLL L1` works.

---

## 8. Automated tests

```bash
./mvnw test                                                      # everything (Windows: .\mvnw.cmd test)
./mvnw test -Dtest=CourseEnrollmentServiceTest                   # behaviour tests only
./mvnw test -Dtest=CourseEnrollmentConcurrencyTest               # concurrency tests only
./mvnw test -Dtest='CourseEnrollmentServiceTest$FifoPromotion'   # one nested group
```

Expected: `Tests run: 137, Failures: 0, Errors: 0, Skipped: 0`. That is 47 test methods. Parameterized
tests run once per input, and the concurrency tests repeat 10–20 times each because race conditions
are probabilistic. Reports are written to `target/surefire-reports/`.

| Test class | What it covers |
|---|---|
| `CourseEnrollmentServiceTest` | All business rules. `@Nested` groups map to AC1–AC5 |
| `CourseEnrollmentConcurrencyTest` | 200 learners racing for 10 seats; 100 duplicate requests at once; simultaneous drops promote in FIFO order (AC6) |
| `DomainModelTest` | Prerequisite rule, input validation, immutability |
| `CommandProcessorTest` | CLI parsing and the test-support commands |

To check that the tests actually catch bugs, the code was deliberately broken and the tests re-run.
Eight deliberate bugs (no locking, LIFO order, capacity off-by-one, skipped prerequisite checks, OR-instead-of-AND prerequisites, skip-and-retain, and more) were each caught by the tests. Details
are in [docs/AI_USAGE_RECORD.md Section 4.2](docs/AI_USAGE_RECORD.md).

---

## 9. Project structure

```
AI-Assessment/
├── README.md                          ← you are here
├── pom.xml                            Maven build (Java 27, JUnit 6.1.3, no runtime dependencies)
├── mvnw, mvnw.cmd, .mvn/              Maven Wrapper (no local Maven install needed)
├── docs/
│   ├── DESIGN.md                      Design spec: boundary, API, invariants, policy, concurrency
│   ├── MANUAL_TEST_PLAN.md            Formal manual test cases with a sign-off column
│   ├── MANUAL_TEST_RESULTS.md         Recorded PASS/FAIL results (generated by run-and-record.ps1)
│   ├── AI_USAGE_RECORD.md             Prompts, AI output, accept/edit/reject log, verification evidence
│   └── LIMITATIONS.md                 Known limitations and follow-up risks
├── manual-tests/
│   ├── ac1-…txt … ac5-…txt            CLI input scripts, one per acceptance criterion
│   ├── expected/                      Expected CLI output for each script
│   ├── results/                       Actual transcripts from your last recorded run
│   ├── run-and-record.ps1             ★ Runs ALL tests and writes docs/MANUAL_TEST_RESULTS.md
│   └── run-all.sh, run-all.cmd        Pipe every script through the app (no recording)
├── review-exercise/                   Planted-bug code-review exercise (PR-001), findings and answer key
└── src/
    ├── main/java/com/coursewaitlist/
    │   ├── cli/
    │   │   ├── WaitlistCli.java       ★ STARTING POINT: main(), reads commands from the keyboard or a script
    │   │   ├── CommandProcessor.java  Turns one text command into a service call and formats the result
    │   │   └── DemoData.java          The demo offering and learners L1–L6
    │   ├── service/
    │   │   └── CourseEnrollmentService.java   ★ CORE LOGIC: enroll / drop / promote / invariants / lock
    │   ├── domain/
    │   │   ├── CourseOffering.java    Offering, capacity and the prerequisite rule (isEligible)
    │   │   ├── Learner.java           Learner id, name, completed courses
    │   │   ├── ResultCode.java        Every possible outcome, and whether it changed state
    │   │   ├── EnrollmentResult.java  Result of enroll()
    │   │   ├── DropResult.java        Result of drop(): who was promoted and who was skipped
    │   │   └── EnrollmentSnapshot.java Read-only view of the roster and waitlist
    │   └── repository/
    │       ├── LearnerDirectory.java          Interface for looking up learners
    │       └── InMemoryLearnerDirectory.java  Thread-safe in-memory implementation
    └── test/java/com/coursewaitlist/        Automated tests (see section 8)
```

---

## 10. Reading the code

A suggested order for understanding the implementation:

1. [`CourseOffering.java`](src/main/java/com/coursewaitlist/domain/CourseOffering.java): the prerequisite rule (`isEligible`).
2. [`CourseEnrollmentService.java`](src/main/java/com/coursewaitlist/service/CourseEnrollmentService.java): the heart of the project.
   - `enroll()`: validate → duplicate check → look up the learner → prerequisite check → seat or waitlist
   - `drop()`: release the seat → `promoteNextEligible()`
   - `promoteNextEligible()`: walk the queue from the front, skipping and removing ineligible learners
   - `verifyInvariants()`: safety checks that run after every change
3. [`ResultCode.java`](src/main/java/com/coursewaitlist/domain/ResultCode.java): every outcome a caller can get.
4. [`CourseEnrollmentServiceTest.java`](src/test/java/com/coursewaitlist/service/CourseEnrollmentServiceTest.java): the rules written as executable examples.
5. [`CourseEnrollmentConcurrencyTest.java`](src/test/java/com/coursewaitlist/service/CourseEnrollmentConcurrencyTest.java): how thread safety is proven.
6. [`WaitlistCli.java`](src/main/java/com/coursewaitlist/cli/WaitlistCli.java) and [`CommandProcessor.java`](src/main/java/com/coursewaitlist/cli/CommandProcessor.java): the thin CLI layer, which contains no business rules.

Every class and public method has a Javadoc comment explaining its purpose.

---

## 11. Further documentation

| Document | Read it when you want to… |
|---|---|
| [docs/DESIGN.md](docs/DESIGN.md) | understand *why* it works this way: invariants, policy choice, concurrency, persistence plan |
| [docs/MANUAL_TEST_PLAN.md](docs/MANUAL_TEST_PLAN.md) | formally verify every acceptance criterion by hand and record pass or fail |
| [docs/AI_USAGE_RECORD.md](docs/AI_USAGE_RECORD.md) | see how AI was used, what was accepted, edited or rejected, and the verification evidence; Section 6 has a **reusable prompt playbook** for doing this kind of work with AI |
| [docs/LIMITATIONS.md](docs/LIMITATIONS.md) | know what this slice does **not** handle and what to do next |

### Assessment deliverables checklist

| Deliverable | Location |
|---|---|
| Runnable code for the scoped feature | `src/main/java`, run with `java -jar target/course-waitlist-1.0.0.jar` |
| Focused automated tests | `src/test/java`, run with `./mvnw test` |
| Manual acceptance verification | [docs/MANUAL_TEST_PLAN.md](docs/MANUAL_TEST_PLAN.md) + `manual-tests/` |
| Recorded test results | [docs/MANUAL_TEST_RESULTS.md](docs/MANUAL_TEST_RESULTS.md), generated with `manual-tests\run-and-record.ps1` |
| Design specification | [docs/DESIGN.md](docs/DESIGN.md) |
| AI prompt and use record + verification evidence | [docs/AI_USAGE_RECORD.md](docs/AI_USAGE_RECORD.md) |
| Setup and run instructions | this README, sections 3–8 |
| Limitations and follow-up risks | [docs/LIMITATIONS.md](docs/LIMITATIONS.md) |
