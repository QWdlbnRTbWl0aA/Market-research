# Project agent instructions

## Product

Build a local Etsy marketplace research system that discovers promising digital-product niches, records demand and competition evidence, and eventually produces product recommendations and listing packages.

Optimize expected recurring portfolio receipts relative to total operating cost, including AI/API tokens, compute and especially manual involvement. Favor automated, inexpensive publication experiments and learning from actual performance. Research must justify its incremental cost; do not impose demand-proof or minimum-sample gates before cheap experiments. Low hit rate is acceptable when portfolio economics work. Keep unknown revenue and cost inputs explicit rather than treating them as zero.

Authentication and application security are out of scope while the system remains local-only.

## Repository workflow

- `main` is protected. Never push directly to it.
- Use `dev` as the integration, testing, and staging branch.
- Develop significant changes on feature branches and merge them into `dev` after validation.
- Preserve meaningful commits when merging; do not automatically squash branch history.
- Use the repository-local Git identity `smith <smith@my-home.local>`.
- Run the relevant Maven tests before commits and the full reactor build before merging to `dev`.
- Keep README files, API contracts, and ADRs aligned with behavior.
- Follow the workspace health-status protocol in `/home/smith/AGENTS.md` for every assignment.

## Architecture and ownership

- Use independently deployable Spring Boot services in a single Maven monorepo.
- The research orchestrator owns the system-wide workflow and canonical research-run state.
- Analysis and content services own execution of their respective jobs, not cross-service workflow decisions.
- The research archive exposes business actions. Never turn it into generic CRUD, SQL-over-HTTP, or a shared database abstraction.
- Each service owns its schema and accesses no other service's tables. A shared local database process is acceptable.
- Put cross-service schemas in `contracts`; do not share domain implementation code between services.
- Prefer provider-neutral ports and keep SDK-specific models inside adapters.
- Use package names under `local.myhome.marketresearch`.

## Long-running operations

- Use asynchronous HTTP job resources for operations with variable or long duration.
- A submission returns `202 Accepted` with a stable job URL.
- Require idempotency keys for job creation.
- Support status polling and explicit terminal errors. Cancellation is best effort and must be represented as state.
- The orchestrator persists polling and retry state. Do not depend on open HTTP connections or in-memory timers for correctness.
- Do not introduce a message broker without a new architectural decision.

## Providers, quota, and cost controls

- Before the first potentially billable call to Etsy, Gemini, or OpenAI, verify available quota and current usage, record the timestamp and result under `docs/provider-usage/`, and establish a local budget/rate guard.
- Never print, commit, or copy API keys. Use the appropriate files under `/home/smith/env_files/` at runtime.
- Treat machine hostnames, VPN addresses, network topology and infrastructure identifiers as secrets too. Exclude them from documentation, logs, commits, model inputs and external messages. Generic loopback examples are acceptable.
- Prefer Gemini's available free quota before using OpenAI for an equivalent operation.
- Enforce Etsy limits locally with conservative request budgeting, throttling, and response-header reconciliation. Never rely solely on Etsy rejecting excess traffic.
- Do not make image-generation calls until the user explicitly lifts the current restriction.
- The current refinement/business-analysis stage has a persistent USD 2 OpenAI development/testing ceiling. Never reset its ledger during research cleanup or silently extend this authorization to scheduled production.
- Fresh Halloween live runs must delete previous fetched research outputs using the explicit allowlist, preserving operational ledgers, code, test fixtures and documentation. Offline replay/forecast are not new searches.
- Keep refinement lightweight: bounded semantic grouping and query probes, not elaborate regex taxonomies or exhaustive pagination. Business analysis targets modest recurring receipts, price-neutrally, and distinguishes public purchase proxies from unavailable competitor revenue.

## Engineering conventions

- Target Java 21 and build with Maven from the repository root.
- Keep backend services and operational/research tooling in Java and Spring. Run unit tests with Maven Surefire and integration/acceptance tests with Maven Failsafe; do not add a Python tooling dependency.
- Prefer immutable records, explicit contracts, constructor injection, and small provider adapters.
- Keep configuration non-secret and environment-driven. Commit safe defaults only.
- Test domain behavior, API contract mapping, idempotency, state transitions, and adapter failure handling.
- Preserve unrelated changes, generated research data, secrets, and build output outside commits.

