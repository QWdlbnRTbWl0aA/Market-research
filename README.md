# Market Research

A Java 21 / Maven / Spring system for discovering Etsy digital-product opportunities and defining original products to make. Image generation and publishing are disabled.

## Current workflow

A CLI controls durable asynchronous services. Broad searches refine into branch-local queries; deterministic preparation feeds cohort-level business reasoning and critique. A semantic opportunity catalog distinguishes new ideas, meaningful variants and extra evidence, avoiding repeated product-brief calls. Idea-to-Product independently chooses exact specifications while preserving the buyer and buying purpose.

Completed investigations archive source evidence, model inputs, drafts, reviewed decisions and usage context. Weekly deterministic monitoring can alert on changes, but only the operator can authorize deep reanalysis.

See the [operator guide](docs/api/continuous-discovery.md), [implementation review](docs/research/implementation-review.md) and [configuration example](config/discovery.example.yml).

## Services

| Component | Ownership |
| --- | --- |
| Research orchestrator | Branch frontier, work scheduling, evidence collection, handoffs, archive outbox and trend decisions |
| Analysis service | Cached cohort preparation, semantic opportunity identity, model reasoning and critique |
| Content service | Independently specified and reviewed idea-to-product instructions; no assets |
| Research archive | Immutable iteration archives with supporting evidence |
| Provider runtime | Shared transport, credentials, quota guards and append-only usage accounting |
| Research tools | CLI and local service supervisor |

No broker or application authentication. Services bind to loopback and own separate H2 databases. Run one instance of each service.

## Build and operate

```bash
mvn verify
java -jar tools/research-tools/target/research-tools-0.1.0-SNAPSHOT.jar --research.tools.command=serve --research.runtime.config-file=/path/to/private-discovery.yml
```

Copy the configuration example outside the repository and supply credential-file paths there. Default model routing is Gemini; paid access additionally requires an explicit active allowance and paid-enabled flag. Never put keys, SMTP passwords or actual machine details in repository files or command arguments.

### View and download briefs

Open [the brief library](http://127.0.0.1:8081/) when the orchestrator is running. Browse saved investigations, search/filter completed briefs, inspect complete specifications and warnings, and download individual JSON/Markdown documents or all briefs as JSON. It serves local assets only and viewing/downloading invokes no providers.

To run just the viewer against the existing project data, without the other services or credentials, run this from the repository root:

```bash
java -jar services/research-orchestrator/target/research-orchestrator-0.1.0-SNAPSHOT.jar --spring.profiles.active=viewer
```

The viewer profile disables discovery workers, live/paid providers and SMTP sending. The page has no mutation controls; the existing workflow API remains separate. It shows the current saved iteration's completed handoffs, not an archive-history browser. Downloads are specifications and references, not generated product assets. Stop with Ctrl-C, and do not run it alongside another orchestrator using the same database/port.

Viewer HTTP/export regression tests run under Maven Failsafe. The buildless frontend also has dependency-free interaction tests: `node --test services/research-orchestrator/src/test/js/briefs.test.cjs`. These exercise rendering, filters, failures and stale-response handling with a small DOM fixture; they are not visual browser tests.

```bash
java -jar tools/research-tools/target/research-tools-0.1.0-SNAPSHOT.jar --research.tools.command=search-start --research.discovery.query="Jewish printables" --research.discovery.key=investigation-1
java -jar tools/research-tools/target/research-tools-0.1.0-SNAPSHOT.jar --research.tools.command=search-status --research.discovery.id=RUN_UUID
java -jar tools/research-tools/target/research-tools-0.1.0-SNAPSHOT.jar --research.tools.command=search-results --research.discovery.id=RUN_UUID
java -jar tools/research-tools/target/research-tools-0.1.0-SNAPSHOT.jar --research.tools.command=usage-report
```

SMTP delivery remains disabled until configured. Notices wait in the durable outbox; queued does not mean sent.

## Fresh-investigation boundary

The requested reset cleared fetched evidence, research results, business conclusions and semantic caches without resetting historical spending/rate ledgers or conversation context. Tracked historical business-report documents were removed from the current tree; Git history was not rewritten. Previous results are not priors for the next investigation.

Normal incremental runs preserve their own evidence and caches. To deliberately clear research again, stop the services and explicitly run `--research.tools.command=fresh-investigation`. Each service clears its owned research tables while retaining operational projections and reset receipts. This is not ordinary run startup.

The [Jewish printables live acceptance report](docs/research/2026-10-22-live-continuous-acceptance.md) records real Etsy/OpenAI usage, the bounded archived run, corrected defects, recovery checks and business-quality limitations. Technical completion does not certify every generated brief for unattended production.

Historical experimental collection/replay/comparison code remains, along with test fixtures and methodology. Frozen-comparison commands now require freshly collected data and a new independently frozen baseline; they cannot replay the cleared investigation.

## Development

`main` is protected; feature changes merge into `dev` with meaningful commits preserved. Surefire runs unit tests; Failsafe runs service HTTP and packaged-process tests. Normal `mvn verify` uses no live provider calls. Provider usage history remains under [docs/provider-usage](docs/provider-usage/) and ignored runtime ledgers.

