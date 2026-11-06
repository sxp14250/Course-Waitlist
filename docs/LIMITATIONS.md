# Limitations and Follow-up Risks

These are known gaps **within** the scoped slice. Features that are out of scope by design (frontend,
catalog, payments and so on) are not listed.

| # | Limitation / risk | Impact | Suggested follow-up |
|---|---|---|---|
| 1 | **In-memory only.** State is lost when the process exits. | Not suitable for production use as-is. | Add a persistence adapter with a unique `(offering, learner)` constraint and a waitlist sequence column (see DESIGN Section 7). |
| 2 | **Single-JVM locking.** `ReentrantLock` only coordinates threads inside one process. | Two service instances for the same offering would race. | Use database row locks or optimistic versioning once state is persisted. |
| 3 | **Skip-and-remove is irreversible.** A learner who is ineligible for a short time (for example a grade update still in progress) loses their place in the queue. | Possible fairness complaint. | Product decision: confirm the policy, or add a short grace window. Skipped ids are already returned in `DropResult` so the learner can be notified. |
| 4 | **No notifications.** Promotions and skips are returned to the caller but not sent anywhere. | The promoted or skipped learner is not informed. | Publish an event built from `DropResult`. |
| 5 | **No capacity change.** Capacity is fixed when the offering is created. | Adding seats won't trigger promotions. | Add `increaseCapacity(n)` that reuses `promoteNextEligible()` under the lock. |
| 6 | **No audit trail.** There is no history of who enrolled, dropped or was skipped, or when. | Disputes can't be investigated. | Add an append-only event log. |
| 7 | **Exact id matching.** `"L1"` and `"l1"` are different learners, and ids are not trimmed. | Callers must normalize ids. | Normalize ids at the boundary if the real id format requires it. |
| 8 | **Concurrency tests are probabilistic.** They show the lock works (and fail when it is removed) but cannot prove there are no races. | Low. | Model-checking or stress testing for a production system. |
| 9 | **Directory reads happen inside the lock.** That is fine for an in-memory map. A slow remote directory would hold the lock while waiting. | Latency or contention if the directory is remote. | Cache eligibility, or fetch before locking and re-validate after. |
| 10 | **No authorization.** Any caller can drop any learner. | Out of scope (no identity integration), but a real risk. | Enforce "self or admin" at the API layer. |
| 11 | **Unbounded waitlist.** Nothing limits how many learners can wait (A14). *(Found by security review, prompt P14.)* | In-memory growth is limited only by the number of learner ids in the directory, so the risk is low here but real behind a public API. | Product decision on a waitlist cap; if one is added, return a `WAITLIST_FULL` code. |
