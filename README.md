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

The viewer profile disables discovery workers, live/paid providers and SMTP sending. Brief controls only read saved results; the separate AI connection panel can authorize ChatGPT and list/select account models but does not launch research. The workflow API remains separate. The library shows the current saved iteration's completed handoffs, not an archive-history browser. Downloads are specifications and references, not generated product assets. Stop with Ctrl-C, and do not run it alongside another orchestrator using the same database/port.

### ChatGPT analysis connection

Refinement, business analysis and their reviews default to `research.runtime.analysis-model-provider=chatgpt`.
`research.runtime.chatgpt-api-fallback=true` permits the existing OpenAI API route only when ChatGPT is
definitively unavailable (not connected, plan permission/usage rejection or local daily limit). API fallback
also requires `paid-enabled`, a selected named allowance and its unchanged conservative reservation guard.
Interrupted streams, invalid model output and unknown failures do not trigger an automatic paid retry.
Product definition retains its existing provider selection.

Open **AI connection** in the brief library. When the browser is on another device, run this on that device
using your existing SSH destination, and keep it running during sign-in:

```bash
ssh -N -L 1456:127.0.0.1:1456 <your-SSH-destination>
```

Choose **Continue with ChatGPT**, open the resulting sign-in link, and authorize ChatGPT plan usage.
Then return to the app, choose **Load available models**, select an account-supported model and choose
**Use selected model**. Sign-in and model discovery do not invoke inference. Review app usage/credit limits
in ChatGPT settings. Plan limits/credits are separate from the API allowance; no dollar balance is inferred.

The application owns this OAuth registration and never reads Codex or browser credentials. Its protected
session defaults to `~/env_files/market-research-chatgpt.json` (override `research.models.chatgpt-session-file`).
The callback binds only `127.0.0.1`, with configurable `chatgpt-callback-port` defaulting to 1456. Tokens
are exchanged and refreshed server-side with cross-process locking; state, PKCE, nonce, issuer, signature,
audience, expiry and granted plan-use scope are checked. Tokens never enter the page or research databases.
Reconnect the same registration after authorization expires; manage revocation in ChatGPT settings.

ChatGPT uses streaming Responses with `store=false`, strict response schemas and existing semantic checks.
Finalized message/text events are retained when a terminal envelope does not repeat the answer; the full
response must still reach confirmed completion before any result is accepted. Private completion records
retain final messages, event types and usage for debugging, excluding reasoning content and credentials.
Its separate append-only call history and preflight records live under `data/provider-ledgers/chatgpt/`;
stage records include primary/fallback attempts. `chatgpt-daily-limit` defaults to 100 attempts per UTC day;
`chatgpt-read-timeout-ms` defaults to 300000. This local cap does not represent the account's remaining plan
allowance, which this adapter cannot read. An incomplete call remains uncertain and requires explicit
operator recovery. If both routes are unavailable, the owning worker pauses with its draft intact.

See OpenAI's [registration guide](https://developers.openai.com/siwc/token-sharing-open-source/sign-in),
[inference contract](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference)
and [preview limitations](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations).

Viewer HTTP/export regression tests run under Maven Failsafe. The buildless frontend also has dependency-free interaction tests: `node --test services/research-orchestrator/src/test/js/briefs.test.cjs`. These exercise rendering, filters, failures and stale-response handling with a small DOM fixture; they are not visual browser tests.

```bash
java -jar tools/research-tools/target/research-tools-0.1.0-SNAPSHOT.jar --research.tools.command=search-start --research.discovery.query="Jewish printables" --research.discovery.key=investigation-1
java -jar tools/research-tools/target/research-tools-0.1.0-SNAPSHOT.jar --research.tools.command=search-status --research.discovery.id=RUN_UUID
java -jar tools/research-tools/target/research-tools-0.1.0-SNAPSHOT.jar --research.tools.command=search-results --research.discovery.id=RUN_UUID
java -jar tools/research-tools/target/research-tools-0.1.0-SNAPSHOT.jar --research.tools.command=usage-report
```

SMTP delivery remains disabled until configured. Notices wait in the durable outbox; queued does not mean sent.

Analysis recovery preserves the job ID, original request, saved phase, rejected responses and usage history.
An explicit resume after assessment validation fails supplies corrective feedback, including invalid references
and the current cohort's allowed listing/opportunity IDs. Validation remains mandatory; unsupported products
are never silently accepted or marked analyzed. Recovery does not automatically authorize repeated paid calls.
OpenAI assessment schemas also constrain citation and existing-opportunity IDs to those supplied for that call;
earlier opportunity evidence does not become a valid citation for the current cohort.
An asynchronous child-job failure pauses its owning worker, exposed through `workerErrors`; other workers
continue. Paused workers consume no polling or persistence work. Explicit orchestrator resume retries the
saved jobs and clears worker pauses, including when the investigation itself remains ACTIVE. Pending or
paused analysis never counts as completed coverage and prevents final archival.

The query-count limit bounds each search pass. With `research.discovery.continuous-expansion-enabled=true`
(the default), a drained pass admits saved suggestions and revisits refinements deferred by that pass's
capacity. Branch-local novelty and business-value stops still apply. Accepted phrases retain page pointers,
source evidence and job identities; normalized phrases are admitted once. The status `coverage` object
reports passes and outstanding expansion. `POST /v1/discoveries/{id}/continue-search` advances a legacy
ACTIVE, unarchived investigation whose accepted frontier has drained; it does not clear worker errors.
Refinement depth and page limits remain explicit safety boundaries, never evidence of market exhaustion.
Disable continuous expansion to retain deliberately bounded investigations. A 2,000-pending-listing
backlog pauses fetching while refinement, analysis and product definition can continue; fetching resumes
as analysis drains it. None of these changes increases provider budgets.

Confirmed transient ChatGPT failures receive at most two persisted retries per stage, respecting
Retry-After. Accounted validation failures receive one corrective retry; uncertain interrupted requests
require explicit recovery. `WAITING_RETRY` retains phase, draft and attempt history across restarts.
Safe diagnostics retain HTTP status, code, field, response shape and request ID, excluding provider text.
Known quota-reset waits resume automatically; no reset time is invented for an unknown account limit.
Business cohorts include shop/review records connected to their listings, rather than all prior search
evidence. Prior operating notes and summaries are bounded previews; opportunity identities, buyer needs,
contents, differentiation and source IDs stay intact. Original requests and canonical records remain
available, and replaced provider inputs are retained in the job's `calls.attemptHistory`.
The deployed operational SMTP supervisor sends normal progress reports hourly; attention and lifecycle
events may notify immediately. Its minute health timer and delivery history are independent of that interval.

An Etsy search-quota wait does not delay business cohorts that already have sufficient saved evidence.
All Etsy workflows default to a shared **4,500-request rolling 24-hour ceiling**, including quota probes,
listing searches, shop profiles and reviews. The ledger preserves prior attempts across runs and processes;
the existing request throttle and provider-header/Retry-After checks remain authoritative too. Smaller
per-run sample/request bounds remain separate. Configurations above 4,500 are rejected. After changing
policy, `POST /v1/discoveries/{id}/recheck-etsy-quota` re-evaluates the saved wait without sending provider
requests, clearing usage history or retrying model jobs; an actual provider cooldown is still respected.
Only a cohort that actually needs a new shop/review request inherits the Etsy retry time. That wait is
persisted on the cohort; idle workers do not poll or write it. Scheduler projections migrate once on
upgrade without changing source data, processed IDs, business conclusions or provider ledgers.

## Fresh-investigation boundary

The requested reset cleared fetched evidence, research results, business conclusions and semantic caches without resetting historical spending/rate ledgers or conversation context. Tracked historical business-report documents were removed from the current tree; Git history was not rewritten. Previous results are not priors for the next investigation.

Normal incremental runs preserve their own evidence and caches. To deliberately clear research again, stop the services and explicitly run `--research.tools.command=fresh-investigation`. Each service clears its owned research tables while retaining operational projections and reset receipts. This is not ordinary run startup.

The [Jewish printables live acceptance report](docs/research/2026-10-22-live-continuous-acceptance.md) records real Etsy/OpenAI usage, the bounded archived run, corrected defects, recovery checks and business-quality limitations. Technical completion does not certify every generated brief for unattended production.

Historical experimental collection/replay/comparison code remains, along with test fixtures and methodology. Frozen-comparison commands now require freshly collected data and a new independently frozen baseline; they cannot replay the cleared investigation.

## Development

`main` is protected; feature changes merge into `dev` with meaningful commits preserved. Surefire runs unit tests; Failsafe runs service HTTP and packaged-process tests. Normal `mvn verify` uses no live provider calls. Provider usage history remains under [docs/provider-usage](docs/provider-usage/) and ignored runtime ledgers.

