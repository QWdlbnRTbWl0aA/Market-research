# Market Research

A local system for researching Etsy digital-product opportunities. It will gather marketplace evidence, refine searches, analyze demand and competition, define potential products, and eventually assemble listing packages.

## Current status

The Halloween playground includes durable simulated orchestration, deterministic cohort analysis, bounded live search refinement and a business-evidence analyzer. The research CLI coordinates real Etsy and budgeted text-model calls; the orchestrator's demonstration workflow still uses synthetic evidence. Image generation and publishing remain disabled.

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

All unit, HTTP integration and cross-process acceptance tests run with `mvn verify`. Maven Surefire runs JUnit unit tests; Failsafe runs HTTP and packaged-service tests, including forced restarts, cancellation and dependency recovery. Tests use temporary databases and mocked Etsy responses; no external API credentials or calls are required.

## Etsy research findings

See the [analysis methodology](docs/research/halloween-data-and-analysis-plan.md), [experiment operating policy](docs/research/experiment-operating-policy.md) and [1000-listing run](docs/research/2026-10-21-cohort-1000-run.md). The objective is expected recurring receipts relative to total operating cost, including AI/API, compute and human involvement. Cheap automated publication experiments do not require pre-production demand proof.


## Deterministic cohort analysis

`POST /v1/etsy-cohort-analyses` on the analysis service accepts a query, UTC observation timestamp and 1..1000 raw listing objects. It returns normalized digital metrics, currency-specific distributions, missingness and automation hints. It makes no provider calls and estimates no sales from views.


The `research-tools` Maven module supplies a Spring Boot command-line application. From the repository root, after `mvn verify`, replay the saved live cohort without any external calls:

```bash
java -jar tools/research-tools/target/research-tools-0.1.0-SNAPSHOT.jar --research.tools.command=replay
```

Results remain in ignored `data/etsy-cohort-1000/`. Saved input and quota-ledger formats are compatible with the earlier collection; replay does not modify the input or request ledger.

Live commands are explicit and disabled by default. Supply credentials through Spring environment variables `ETSY_API_KEY`/`ETSY_API_SECRET`, or a credential-file path (never key values on the command line):

```bash
java -jar tools/research-tools/target/research-tools-0.1.0-SNAPSHOT.jar --research.tools.command=collect --research.tools.live-enabled=true --research.tools.credentials-file=/path/to/etsy.env
java -jar tools/research-tools/target/research-tools-0.1.0-SNAPSHOT.jar --research.tools.command=discover --research.tools.live-enabled=true --research.tools.credentials-file=/path/to/etsy.env
```

Every fresh live command first deletes previous research outputs from the allowlisted Halloween, cohort and discovery directories. Operational quota/spending ledgers, documentation and fixtures survive. Consequently replay requires a currently saved cohort; historical run documentation is not replay input. `collect` uses one quota probe plus at most fourteen 100-result pages, a 30-attempt rolling-day cap, two-second spacing and a daily reserve. `discover` uses one probe and four `limit=1` searches under its six-attempt rolling-day cap. Both share the existing ledger and lock under `data/etsy-discovery/`; neither retries HTTP failures.

The CLI defaults to help; `--research.tools.root=.` selects the project root. The Java tooling has replaced the former scripts and their Python tests. All ordinary checks run through Maven; live collection is never part of the test lifecycle.

## Focused searches and business analysis

After building, check model access without generation, then explicitly run the bounded live investigation:

```bash
java -jar tools/research-tools/target/research-tools-0.1.0-SNAPSHOT.jar --research.tools.command=provider-check --research.models.gemini-credentials-file=/path/to/gemini.env --research.models.open-ai-credentials-file=/path/to/openai.env
java -jar tools/research-tools/target/research-tools-0.1.0-SNAPSHOT.jar --research.tools.command=research --research.tools.live-enabled=true --research.tools.credentials-file=/path/to/etsy.env --research.models.gemini-credentials-file=/path/to/gemini.env --research.models.open-ai-credentials-file=/path/to/openai.env
java -jar tools/research-tools/target/research-tools-0.1.0-SNAPSHOT.jar --research.tools.command=forecast
```

`research` clears old results, samples 60 seed listings, proposes at most three focused searches, and makes at most one further narrowing pass for pools above 2,000 listings. It samples up to 100 listings per child instead of enumerating the pool. Semantic classification rejects irrelevant downloads; code handles type filtering, duplicates, query yield and money. A single missing-ID repair is allowed per classification batch; invalid or still-incomplete output stops the run.

The business engine targets USD 30/month gross receipts without favoring high-priced products. It calculates required units, samples up to two independent shops per group, and collects recent listing-review purchase proxies. Public Etsy data does **not** reveal competitors' verified monthly revenue. Explicit review-rate scenarios are uncalibrated illustrations, not forecasts or confidence intervals. Model reasoning adds differentiation, automation plans and TEST/DEFER recommendations, subject to deterministic evidence validation. Low confidence need not block a cheap automated experiment.

Results are private, ignored files under `data/halloween-run/`. `forecast` derives daily/monthly token costs from that run without cleanup or provider calls. Persistent `data/provider-ledgers/` survives cleanup and enforces the user's **USD 2 development-stage ceiling**, including uncertain reservations. Model preflight must be less than 24 hours old. Gemini is preferred only with verified operator confirmation of free-tier access; account metadata alone cannot establish this. OpenAI account-wide usage may be unavailable without an Admin key and is never inferred from our ledger.

The research command enforces a 100-request rolling-day local Etsy cap, two-second spacing, at most 20 new requests/run, response-header reconciliation and a daily reserve. It performs only read operations. The slow provider coordination currently lives in the explicit CLI, not a new synchronous HTTP workflow; quick deterministic endpoints are described in [the API contract](docs/api/market-business-analysis.md). [ADR 0007](docs/adr/0007-bounded-search-and-business-evidence.md) records scope and trade-offs.

See the [live business-stage results and daily cost forecast](docs/research/2026-10-21-business-analysis-run.md). The final run found 633 usable digital listings; all three proposed experiments remain LOW confidence. Measured text analysis costs about USD 0.112/day, or USD 3.36 per thirty daily runs, excluding compute and human involvement.

