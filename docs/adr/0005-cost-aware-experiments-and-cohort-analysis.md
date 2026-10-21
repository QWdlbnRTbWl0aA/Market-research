# ADR 0005: Cost-aware experiments and real-cohort deterministic analysis

- Status: Accepted
- Date: 2026-10-21

## Decision

Optimize recurring portfolio receipts relative to total operating cost, especially manual time. Retire the previous minimum-evidence gates and weighted niche score. Research must be proportional to its expected incremental decision value. Favor inexpensive automated publication experiments and learning from actual published performance.

Implement a provider-specific mapping adapter and a pure deterministic cohort analyzer. A versioned HTTP business operation accepts an explicitly supplied Etsy cohort and returns aggregate facts and an automation-oriented queue. It performs no network or model calls and does not infer revenue from lifetime engagement.

The 1000-item analysis is fast synchronous local computation. Long-running marketplace/provider jobs continue to use the existing asynchronous workflow conventions. This operation is not automatically wired into the orchestrator yet; the saved evidence can be replayed without more Etsy calls.

Use Maven Surefire for JUnit unit tests and Maven Failsafe for Spring Boot HTTP integration tests. Keep the earlier cross-process restart/retry/cancellation acceptance harness as additional coverage.

Delete prior fetched research payloads while preserving code, synthetic fixtures, documentation and the operational request-counter ledger. Fetch a new cohort under an explicit maximum of 15 new requests and 30 local rolling-day attempts shared with earlier probes. Store raw evidence and reports only under ignored local data storage; commit aggregate documentation.

## Consequences

The analysis code supplies inexpensive facts without imposing demand-proof prerequisites. Financial forecasts and actual human workload remain unknown until explicitly estimated or measured. Publication, performance ingestion, portfolio allocation and learned forecasts require later implementation.
