# Continuous discovery operator guide

## Run

Build with `mvn verify`. Copy `config/discovery.example.yml` outside the repository, configure credential-file paths, deliberately enable live access and confirm the known free tier. Start from the repository root:

```bash
java -jar tools/research-tools/target/research-tools-0.1.0-SNAPSHOT.jar --research.tools.command=serve --research.runtime.config-file=/path/to/private-discovery.yml
```

This foreground Java supervisor starts four loopback services (8081–8084). Keep it running for background work and weekly checks. Ctrl-C stops only its children; durable jobs/schedules survive. Restart picks up a missed weekly check without replaying a burst of historical checks. No OS service or system scheduler is installed. All processes share one runtime root and historical usage ledgers. Services remain independently runnable.

From another terminal:

```bash
java -jar tools/research-tools/target/research-tools-0.1.0-SNAPSHOT.jar --research.tools.command=search-start --research.discovery.query="Jewish printables" --research.discovery.key=jewish-printables-1
java -jar tools/research-tools/target/research-tools-0.1.0-SNAPSHOT.jar --research.tools.command=search-list
java -jar tools/research-tools/target/research-tools-0.1.0-SNAPSHOT.jar --research.tools.command=search-status --research.discovery.id=RUN_UUID
java -jar tools/research-tools/target/research-tools-0.1.0-SNAPSHOT.jar --research.tools.command=search-results --research.discovery.id=RUN_UUID
```

Also: `search-cancel`, `search-resume`, `search-approve-trend`, `search-dismiss-trend` (require ID), and `search-notifications`. Reusing a start key/query returns the same run; different input under that key returns 409. The CLI controls services, not providers directly.

HTTP equivalent: POST `/v1/discoveries`, `Idempotency-Key`, body `{"query":"Jewish printables"}` returns 202/Location. GET `/{id}` returns frontier, counters and pending jobs; GET `/{id}/results` returns assessments/briefs. POST `/{id}/resume`, `/{id}/approve-trend`, `/{id}/dismiss-trend`; DELETE `/{id}` cancels best effort.

## Stop and idle boundaries

| Stage | Stop/idle condition | Next action |
| --- | --- | --- |
| Preparation | Page normalized, structurally filtered, deduplicated | Next page |
| Refinement | At most three new focused queries per sampled intent; duplicate queries ignored; 80% clearly irrelevant sample ends that intent | Immediately execute accepted children |
| Paging | Available results end, or two pages have under 10% globally new usable IDs | Next frontier query |
| Individual branch | Available/novel coverage ends or two reviewed branch cohorts report no materially new insight | Stop only this branch, retain siblings |
| Broad search | Remaining frontier branches have each stopped | Stop fetching, drain analysis |
| Explicit bounded investigation | Optional `research.discovery.sampling-page-budget` search pages reached | `SAMPLING_COMPLETE`, defer remaining branches, drain and archive; never claim market exhaustion |
| Business analysis | No eligible unprocessed cohort; batches of 500, smaller tail flushed at exhaustion/page boundary | Idle until fresh/meaningfully changed evidence |
| Review | Critique plus schema/citation/required-field checks pass | Hand off concrete recommendations |
| Product definition | Brief and critique pass file/reference/QA contract checks | Idle until next recommendation |
| Completion | All eligible analysis and briefs settled, immutable iteration archived | Queue notice, enter WATCHING |
| Weekly scan | One fresh top-25 sample per useful non-superseded query | Seven days later, or explicit approval decision |

Defaults: 24 queries, depth 5, 20 pages/query, at most 50 cached shop profiles. A cap with continuing insight is BLOCKED, not EXHAUSTED. Pending analysis drains before a page-cap decision. Yield and parent-yield improvement are exposed; these measure digital/sample-intent coverage, not business quality. Splitting a large pool does not claim its entire tail was inspected.

Known quota waits preserve pending work with durable retry timestamps and resume automatically. Spending limits, ambiguous transport/interruption and invalid model output block explicitly. Resume acknowledges a possible repeat charge after uncertain remote execution; reservations are never reset. Idempotent inter-service failures retry up to six attempts with bounded persisted backoff. Cancelling cannot undo a model call already dispatched.

## Business evidence and reuse

Deterministic code handles structure/money/deduplication and does not recommend business opportunities. Models identify semantic junk and focused queries; business models interpret cohorts and earlier summaries. Descriptions/tags are compacted for aggregate reasoning, then cited originals are supplied to critique. Fingerprints include title/content/tags/price but not volatile views/favorites; cache scope includes niche, protocol and provider/model configuration.

Recommendations include a specific product, buyer/use case, counted deliverables, original differentiation, proposed price, recurring-receipts hypothesis, automation/manual-effort assumptions, seasonal experiment rule, counterevidence, confidence explanation and actual listing IDs. Empty recommendations are valid. Shop age/lifetime sales/review counts are context; competitor verified monthly/per-listing revenue is unknown. Own-shop performance ingestion and publication remain outside this slice.

Results are labeled `analysisOrigin=API_PIPELINE`, not the developer agent's independent conclusions. JSON/citation validation and self-critique are not proof of business accuracy.

Cohort reasoning and critique receive a persistent opportunity catalog and return NEW, MEANINGFUL_VARIANT or ADDITIONAL_EVIDENCE with a target ID and rationale. Only new opportunities and meaningful variants create briefs; more citations, prices or wording changes update support without another definition/review pair. Catalog context is bounded to 100 opportunities per prompt; semantic matching remains a model judgment.

Shop samples span cohort engagement quantiles, not simply the first ranked shops. By default each cohort can select up to six profiles, within 50 total; shop-review probes are capped at eight and listing-review probes at four per investigation. Each probe returns up to 25 sanitized reviews with observation dates and evidence IDs. Empty responses are cached too. These are convenience samples and purchase proxies, not representative demand estimates.

## Idea-to-product contract

POST `/v1/product-definition-jobs` takes an idempotency key and `ProductDefinition.Request`: run/query, reviewed recommendation and cited listing records. Returns 202/Location; GET polls; DELETE cancels; explicit POST `/{id}/resume` retries a blocked job.

The reviewed brief defines product/buyer/use, exact files/formats/dimensions/counts/contents, artistic style/palette/typography/layout/constraints, listing references and originality restrictions, commercial context, production acceptance checks, explicit assumptions and unresolved questions. A complete schema can still contain visible unresolved questions; it is not silently declared production-ready.

Idea-to-Product independently chooses exact contents/counts/specifications; recommendation quantities are not binding. Buyer/use-case anchors remain unchanged and critique explains how the specification preserves the buying purpose. Per-file criteria include expected counts and dimensions matching the actual file specs and explicit verification methods. READY is forbidden while unresolved questions remain. This validates the instructions, not an asset that has actually been generated.

References resolve to `https://www.etsy.com/listing/{listingId}`. IDs/links satisfy this contract; pictures are not downloaded or claimed as licensed assets. No asset generation, Etsy writes or publishing occurs.

## Weekly checks and notifications

Each useful non-superseded query receives one top-25 read giving both count and fresh sample. A conservative quota probe precedes it: two Etsy requests/query, zero model tokens.

Significance means: count change of at least 25% and 20 listings; at least 60% sample churn with ten records; USD median-price change of at least 25%; digital-share change of 30 percentage points; or loss of a nonempty digital sample. These are sample-change alerts, not demand conclusions. Compare to the completed research baseline to detect cumulative drift; ranking noise and seasonality can cause false positives.

Significance enters AWAITING_APPROVAL and queues a notice. No timer or generic resume launches deep analysis from that state. Approval starts another iteration and aggregate reassessment using prior context. Dismissal adopts the fresh baseline and resumes monitoring.

SMTP remains disabled until supplied outside the repo: `spring.mail.host/port/username/password`, `research.notifications.from/to`, `research.notifications.smtp-enabled=true`. TLS is required. Notices wait in WAITING_CONFIGURATION until configured. Ambiguous delivery is DELIVERY_UNCERTAIN, never silently retried; explicit POST `/v1/discoveries/notifications/{key}/retry` retries. List via `search-notifications`.

## Retention, costs and tests

The orchestrator also serves a read-only brief library at `/`. Its `/v1/brief-library/runs` endpoints list lightweight run/brief summaries, fetch complete saved brief documents, and download individual `json` or `markdown` documents and per-run JSON bundles. Only SUCCEEDED handoffs with a stored brief are downloadable. Documents include the full brief, original opportunity and cited listing metadata/URLs, excluding raw research aggregates and model prompts. Missing/pending/cross-run job IDs return 404; unsupported export formats return 400. No provider or worker is invoked by these read paths.

For library-only operation use `--spring.profiles.active=viewer` from the project root; it disables discovery scheduling/recovery and provider access. This is not an archive-history viewer or a new authenticated/public deployment. API READY labels remain model judgments, not independent production approval.

Service-owned databases retain discoveries, caches, handoffs, archives, notices and schedules across normal runs. Experimental Halloween cleanup remains separate; it does not reset databases or provider ledgers.

Idle work uses indexed due projections and performs no aggregate loading/serialization/writes. Unchanged job polls do not save; known quota waits are not busy-polled. Archives contain sanitized source pages with dates/digests, complete cohort requests, shop/review context, catalog support, model instructions/context, drafts, results and usage records. A frozen archive outbox makes retries independent of later source changes.

The shared Etsy ledger enforces 400 rolling-day attempts by default, two-second spacing and daily reserve. Gemini retains twelve attempts/Pacific day and minimum 61-second spacing; known free-quota exhaustion waits for reset. Explicit Gemini never silently falls through to OpenAI. OpenAI requires a named ACTIVE allowance and `research.runtime.paid-enabled=true`; historical limits/ledgers are not reset or reused as a new authorization.

Create a separate allowance with `--research.tools.command=new-allowance --research.allowance.id=NEW_ID --research.allowance.usd=CONFIRMED_CAP`. Omit the amount to create AWAITING_CAP, which permits no spending. Confirm that pending amount once with `--research.tools.command=confirm-allowance --research.allowance.id=NEW_ID --research.allowance.usd=CONFIRMED_CAP`. These commands never send model requests or reset an existing allowance. Select its ID through `research.runtime.openai-allowance-id` only when paid use is authorized.

Incremental model calls are approximately one refinement per visited query, two per fresh cohort, two per accepted new product, plus two for an operator-approved group reassessment. Weekly watches use none. The historical USD 0.55 comparison is not a measurement of this new batching strategy; forecast from actual new-run token ledgers, not a full 5,000-listing rescan every day.

Use `--research.tools.command=usage-report` for per-stage invocations/request attempts, reported input/output/cached tokens, elapsed time and conservative cost estimates. Unknown attempts and uncertain reservations are not zero spend; totals with unknown usage remain explicitly incomplete. Business/content critique stays enabled because removing it would compromise validation. Validated identical refinements and unchanged-listing analysis are cached.

To explicitly start fresh, stop services and run `--research.tools.command=fresh-investigation`. Each service clears only its owned research tables and preserves operational status/usage projections and reset receipts. Fetched files, raw model responses, semantic caches, recommendations and archives are discarded. Historical usage/rate ledgers, fixtures, code and configuration are preserved. This action is not routine startup and must not be run concurrently with research.

Surefire covers domain/cache/citation/schema/idempotency/cancellation/interruption behavior. Failsafe covers actual HTTP job APIs and a 1,050-listing orchestrator lifecycle using local downstream protocol fixtures; existing packaged-service restart tests remain. Automated tests use no external provider calls or credentials. New full live-provider acceptance and SMTP delivery require separate verification.

An explicitly bounded investigation prioritizes unvisited branches before deeper pages. The page budget is persisted per discovery and iteration (default zero/unbounded). Already-submitted refinements are drained; no new model refinement is bought at the boundary. Deferred queries and the `SAMPLING_COMPLETE` reason remain in the archive. This is a scope stop, not evidence that remaining branches lack value. Safety limits still block and do not silently become successful sampling completion.

Job evidence includes append-only returned structured outputs, including rejected outputs, and safe local validation failure details. Explicitly retrying a rejected review preserves its accepted generation draft and reruns the review, rather than regenerating the whole cohort. Historical failures from before this output-retention change cannot be reconstructed retroactively.

Before archival, an operator may explicitly request POST `/{id}/review-briefs` with an `Idempotency-Key` to re-review completed briefs. This can spend model tokens; it is never automatic idle work. Child POST `/v1/product-definition-jobs/{job}/review` is idempotent by the same review-round key and retains prior outputs. Immutable archives cannot be silently revised by this action. Provider observations include the current UTC observation time so seasonal choices do not depend on a model guessing the date.

OpenAI continuous stages use strict Structured Outputs schemas generated from the shared response records. All fields are required, nullable opportunity links are explicit, extra keys/catalog wrappers are rejected, and refinement/product counts are bounded. Existing semantic validators still check citations, intent, file consistency and readiness; a schema-valid response does not prove business quality. Experimental protocols retain their existing JSON mode. See [official Structured Outputs guidance](https://developers.openai.com/api/docs/guides/structured-outputs).

For a deliberate one-shot full report email, use `--research.tools.command=mail-report` with `research.report.smtp-file`, `research.report.file`, `research.report.to` and `research.report.subject`. It requires STARTTLS, loads credentials from the private env file and uses a generic SMTP client hostname. SMTP acceptance is not proof of inbox delivery; uncertain failures are not automatically resent.
