# ADR 0002: Use asynchronous HTTP job resources

- Status: Accepted
- Date: 2026-10-21

## Context

Some operations complete quickly while marketplace research and AI generation can take minutes or hours. Holding an HTTP connection for the full operation is unreliable. A message broker is not currently warranted.

## Decision

Model long-running operations as HTTP job resources:

1. A client submits a request with an idempotency key.
2. The service returns `202 Accepted` and a `Location` header for the job.
3. The client polls the job resource until it reaches a terminal state.
4. The client may request best-effort cancellation.

The shared lifecycle is `ACCEPTED`, `RUNNING`, `SUCCEEDED`, `FAILED`, `CANCELLATION_REQUESTED`, or `CANCELLED`. Services persist job state required for recovery. Fast, deterministic reads may remain synchronous.

## Consequences

- Long operations survive client disconnects and orchestrator restarts.
- Idempotency, polling intervals, errors, progress, and result links require explicit contracts.
- Polling adds controlled request traffic but avoids broker infrastructure and callback coupling.

