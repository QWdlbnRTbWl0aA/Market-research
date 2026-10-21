# ADR 0006: Java and Spring operational tooling

- Status: Accepted
- Date: 2026-10-21

## Decision

Replace the Python discovery, collection, replay and acceptance helpers with a Java 21 Maven module, `tools/research-tools`. Use a non-web Spring Boot CLI with Spring configuration, Jackson and a read-only RestClient Etsy adapter.

Separate credential loading, quota ledger, HTTP transport, research operations and local service lifecycle. Preserve the existing saved input/manifest/report and epoch-second quota-ledger formats; do not reset counters during migration. Ledger changes and output files use private permissions and atomic replacement. All discovery/collection attempts reserve their budget before network I/O, run under a process lock, enforce two-second spacing and reconcile server quota/cooldown headers. HTTP failures do not retry automatically.

Live commands require explicit enablement and obtain credentials from Spring environment configuration or the configured env-file path. Offline replay requires no credentials, launches only the local packaged analysis service, and does not touch the quota ledger or saved input. Public CLI errors and child-service logs must not expose credentials, provider error bodies or machine identifiers.

## Testing and build

JUnit tests under Maven Surefire replace the Python unit tests. Spring MockRestServiceServer verifies request formation, quotas, failure/cooldown behavior and pagination without external access. Maven Failsafe runs the CLI smoke/replay integration tests and the original cross-process workflow acceptance checks against packaged services and temporary databases.

The tooling module has test-scoped POM dependencies on the three participating services to establish reactor ordering without depending on their implementation classes. Running the root `mvn verify` packages those services before the acceptance tests. Ordinary tests never call Etsy or any AI provider.

## Consequences

The repository needs only Java/Maven for backend operations and tests. Collection remains an explicit offline-tool workflow rather than an automatically enabled orchestrator feature. The former Python files are removed; their history and historical usage documentation remain in Git. No live Etsy calls are needed to validate this refactor.
