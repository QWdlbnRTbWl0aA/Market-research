# Market Research

A local system for researching Etsy digital-product opportunities. It will gather marketplace evidence, refine searches, analyze demand and competition, define potential products, and eventually assemble listing packages.

## Current status

The Halloween playground includes durable simulated orchestration and a real-cohort deterministic analysis endpoint. A fresh 1000-listing Etsy collection has been processed through that endpoint. The orchestrator's demonstration workflow still uses synthetic evidence. Gemini, OpenAI and image generation remain disabled.

## Planned system

| Component | Ownership |
| --- | --- |
| Research orchestrator | Research workflow, canonical run state, retries, and dashboard API |
| Analysis service | Initial metrics, business analysis, ranking, and product-definition jobs |
| Content service | Listing text, visuals, mock-ups, prompts, and provider selection |
| Research archive | Business-oriented archival of evidence, reports, assets, and listing packages |
| Dashboard | React user interface through the orchestrator |

All backend services are independently deployable but live in one repository. Long-running work uses asynchronous HTTP job resources; no message broker is currently planned.

## Development model

- `main`: protected release branch; agents must not push directly.
- `dev`: integration, testing, and staging branch.
- Feature branches: normal development, merged into `dev` with significant commits preserved.

Architecture decisions live under `docs/adr/`. Provider quota checks will be recorded under `docs/provider-usage/` before the first potentially billable calls.

## Build

Requirements: JDK 21 and Maven 3.6.3 or newer.

```bash
mvn verify
```

Each service produces its own executable Spring Boot JAR and uses a distinct default local port. Provider integrations are disabled in the baseline configuration.

## Exercise the simulated workflow

After building, run the three JARs in separate terminals from the repository root:

```bash
java -jar services/analysis-service/target/analysis-service-0.1.0-SNAPSHOT.jar
java -jar services/research-archive-service/target/research-archive-service-0.1.0-SNAPSHOT.jar
java -jar services/research-orchestrator/target/research-orchestrator-0.1.0-SNAPSHOT.jar
```

Default ports are 8081 (orchestrator), 8082 (analysis), 8083 (content shell), and 8084 (archive). Each participating service owns a separate H2 file under ignored `data/`. Database URLs and service base URLs are configurable through the environment. Run one instance per service.

```bash
curl -i -X POST http://localhost:8081/v1/research-runs \\
  -H 'Content-Type: application/json' -H 'Idempotency-Key: halloween-demo-1' \\
  --data '{"query":"halloween scavenger hunt printable","scenario":"FAST"}'
```

Follow the returned Location with GET. Fetch `/v1/research-runs/{id}/report` after SUCCEEDED; DELETE the run resource to request cancellation. FAST, SLOW (30 seconds), and FAIL scenarios are available. Query text labels the simulation; it does not query Etsy or alter the fixed synthetic cohort. Reports identify their source and never declare a verified opportunity.

Run cross-process acceptance checks after `mvn verify`:

```bash
python3 scripts/verify_workflow.py
python3 -m unittest discover -s scripts -p 'test_*.py'
```

This uses temporary databases and ports, tests crash recovery and retries, and makes no external API calls.

## Etsy research findings

See the [analysis methodology](docs/research/halloween-data-and-analysis-plan.md), [experiment operating policy](docs/research/experiment-operating-policy.md) and [1000-listing run](docs/research/2026-10-21-cohort-1000-run.md). The objective is expected recurring receipts relative to total operating cost, including AI/API, compute and human involvement. Cheap automated publication experiments do not require pre-production demand proof.

`scripts/etsy_probe.py` performs at most five requests per invocation within a six-attempt local rolling-day cap, using a credential-file argument. It is an explicit live discovery tool, not part of the default tests. Raw responses and its persisted rate ledger stay in ignored `data/`.

## Deterministic cohort analysis

`POST /v1/etsy-cohort-analyses` on the analysis service accepts a query, UTC observation timestamp and 1..1000 raw listing objects. It returns normalized digital metrics, currency-specific distributions, missingness and automation hints. It makes no provider calls and estimates no sales from views.

`python3 scripts/analyze_etsy_cohort.py` replays the saved live cohort through a temporary packaged analysis service without consuming more Etsy quota. Results remain in ignored `data/etsy-cohort-1000/`.

`python3 scripts/collect_etsy_cohort.py /path/to/etsy.env` explicitly collects a new cohort only if none exists. It uses a quota probe, up to fourteen 100-result pages, a 30-attempt local rolling-day cap shared with the discovery ledger, two-second spacing and a daily reserve. Do not run it as part of ordinary testing.

JUnit unit tests run through Maven Surefire; Spring Boot HTTP integration tests named `*IT` run through Maven Failsafe during `mvn verify`. The HTTP integration test processes 1000 synthetic listings without any API credentials. The separately recorded live test processes 1000 actual Etsy listings.

