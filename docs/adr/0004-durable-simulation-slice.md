# ADR 0004: Durable simulation slice

- Status: Accepted
- Date: 2026-10-21

## Decision

Implement the first working vertical slice using one embedded H2 file database per participating service. The orchestrator persists run state, downstream job identity, next-poll timestamp, retries, cancellation intent, and a four-hour workflow deadline. The analysis worker persists requests, execution deadlines, terminal reports and errors. The archive owns immutable business reports keyed by research run.

All HTTP routes use the `/v1` prefix. Job submission requires an idempotency key; reusing a key with different input returns 409. Worker replay uses the stable research-run ID as its idempotency key. Archiving the same report again is safe; different content for the same run returns 409.

Restart resumes the persisted phase. Submission and archive requests can be replayed after a crash between remote success and local persistence. Temporary dependency failures receive persisted exponential backoff, capped at six consecutive failed attempts. Successful progress resets the retry counter. Deadline exhaustion is explicit failure. Cancellation is best effort: a request can cancel the workflow after a worker finishes, and an already committed archive write may remain.

The synthetic worker supports FAST, SLOW (30 seconds), and FAIL scenarios. This tests long-running semantics without hours of waiting or billable calls. Its reports always remain insufficient evidence for a commercial recommendation.

## Trade-offs and limits

H2 file URLs set `WRITE_DELAY=0` so acknowledged commits are flushed rather than waiting for the default delayed write. Crash-recovery acceptance tests kill and restart the actual processes using the same databases.

The current service implementations support one process per service/database, with synchronized execution. They are intentionally small and do not yet support distributed worker leases, multiple replicas, database migrations, pagination of job queues, automatic orphan-job reconciliation, or externally configurable workflow policies. The four-hour workflow deadline is a baseline policy, not an external-provider timeout.

H2 removes local setup overhead while keeping ownership explicit. PostgreSQL and managed schema migrations remain a future implementation choice. Report payloads are bounded by database column size; binary assets will need separate filesystem storage. The archive report action is synchronous and small; analysis remains asynchronous.

No runtime Etsy adapter is enabled. Discovery is a separate read-only script. The content service remains a shell.
