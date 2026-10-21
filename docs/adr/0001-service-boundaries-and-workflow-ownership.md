# ADR 0001: Service boundaries and workflow ownership

- Status: Accepted
- Date: 2026-10-21

## Context

The system needs parallel development, isolated testing and debugging, independent deployment, maintainable boundaries, and abstraction from Etsy and AI providers. Marketplace and generation operations range from fast calls to jobs lasting minutes or hours.

## Decision

Use independently deployable services in a monorepo:

- The **research orchestrator** owns the end-to-end workflow, canonical research-run state, stage transitions, polling, retries, and cancellation policy.
- The **analysis service** owns initial analysis, business analysis, opportunity ranking, and product-definition job execution.
- The **content service** owns listing text, visual generation, mock-ups, prompts, and provider-specific generation adapters.
- The **research archive** owns long-lived evidence, reports, assets, and package metadata through business-oriented operations.
- The **dashboard** communicates with the orchestrator rather than coordinating services directly.

The orchestrator decides what happens next. Worker services decide how assigned work runs. The archive decides how completed business artifacts are retained.

## Consequences

- Services can be developed, run, tested, and deployed independently.
- Cross-service API compatibility and operational configuration become explicit concerns.
- Workflow state has one owner, avoiding distributed coordination by the dashboard or workers.
- The monorepo supports atomic contract changes and shared engineering conventions.

