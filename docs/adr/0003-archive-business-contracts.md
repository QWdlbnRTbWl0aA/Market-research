# ADR 0003: Archive through business contracts

- Status: Accepted
- Date: 2026-10-21

## Context

A central repository service could become a bottleneck or degenerate into SQL over HTTP. Nevertheless, evidence, reports, generated assets, and listing packages require consistent long-term storage and provenance.

## Decision

Name the component the **research archive** and expose business actions such as:

- archive marketplace evidence;
- archive an analysis report;
- register a source or generated asset;
- publish a listing package; and
- retrieve a research package.

Do not expose arbitrary table access, generic entity CRUD, or SQL-shaped filtering. The orchestrator owns active workflow state. The archive owns retained artifacts and archive metadata. Each service owns its database schema, even when schemas share one local database server.

## Consequences

- Archive contracts enforce provenance and business invariants.
- Services remain responsible for their own operational data.
- New archive operations require intentional API design rather than generic persistence shortcuts.

