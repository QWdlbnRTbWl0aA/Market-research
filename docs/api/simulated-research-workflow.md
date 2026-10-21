# Simulated research workflow HTTP API — v1

All operations below are implemented. This increment runs synthetic evidence only. Real discovery is deliberately a separate, explicitly invoked script.

## Orchestrator

| Method and resource | Meaning |
| --- | --- |
| POST `/v1/research-runs` | Accept a run with required `Idempotency-Key`; return 202, Location and run state |
| GET `/v1/research-runs/{id}` | Read persisted workflow state |
| DELETE `/v1/research-runs/{id}` | Persist best-effort cancellation intent; return 202 |
| GET `/v1/research-runs/{id}/report` | Retrieve the archived report after success |

Submission example:

```json
{"query":"halloween scavenger hunt printable","scenario":"FAST"}
```

Scenario defaults to FAST; supported values are FAST, SLOW (30 seconds), and FAIL. Query text describes the run; it does not select a different fixture or call Etsy. The response contains `id`, `query`, `scenario`, `status`, `analysisJobId`, `errorCode`, `retryCount`, and `nextPollAt` (Unix milliseconds).

The normal lifecycle is ACCEPTED → ANALYZING → ARCHIVING → SUCCEEDED. FAILED and CANCELLED are terminal. CANCELLATION_REQUESTED is persisted before downstream cancellation. A DELETE on a terminal run is a safe no-op. A cancellation racing with completed work does not erase worker results or a previously committed archive action.

Missing/invalid input returns 400; an unknown UUID returns 404; reusing an idempotency key with different normalized input returns 409. A report requested before SUCCEEDED returns 409. A temporarily unavailable archive during report retrieval returns 503 without exposing the downstream address or error body.

The orchestrator uses two-second HTTP connection/read timeouts for quick job-resource calls. This is unrelated to the long worker execution duration. It persists polling timestamps and exponential dependency retry delays, capped at six consecutive failed attempts. Normal poll interval is 500 ms in the simulation. The four-hour workflow deadline is persisted. Longer provider workflows will require explicit policy configuration and job reconciliation.

## Analysis worker

| Method and resource | Meaning |
| --- | --- |
| POST `/v1/analysis-jobs` | Submit `SimulationRequest` with Idempotency-Key; return 202 and job Location |
| GET `/v1/analysis-jobs/{id}` | Read job state and report link |
| DELETE `/v1/analysis-jobs/{id}` | Request cancellation; return 202 |
| GET `/v1/analysis-jobs/{id}/report` | Read the completed typed report; 409 until ready |

The orchestrator supplies a stable fixture and research-run UUID. The worker persists inputs, execution deadline, state, report, and failure code. It accepts no provider credentials and performs no external calls. Each report contains source provenance, digital/sample/shop counts, prices by currency, findings, and an explicit INSUFFICIENT_EVIDENCE decision.

## Research archive

| Method and resource | Meaning |
| --- | --- |
| POST `/v1/research-reports` | Archive a typed analysis report; return 201 and report Location |
| GET `/v1/research-reports/{researchRunId}` | Retrieve the immutable archived report |

Research-run identity is the business idempotency key for this action. Replaying the same canonical report returns the same resource; different content for that run returns 409. Decimal values are normalized so equivalent monetary values survive JSON replay. No generic database access API is exposed.

## Verification

`mvn verify` runs the Java tests. `scripts/verify_workflow.py` launches the actual packaged services with isolated databases and performs HTTP acceptance tests for success, duplicate/conflicting submissions, archive replay, cancellation, failure, forced process restarts, report persistence, and transient dependency recovery. `scripts/test_etsy_probe.py` tests discovery quota controls entirely offline.
