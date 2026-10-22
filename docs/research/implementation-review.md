# Research-engine review — 2026-10-22

All eight reported weaknesses were verified against the original implementation.

| Finding | Correction |
| --- | --- |
| Idle aggregate persistence | Indexed per-worker due projections; no loading/serialization/writes for idle discoveries; unchanged polls do not save |
| Content-hash opportunity identity | Cohort reasoning/review resolves NEW, MEANINGFUL_VARIANT and ADDITIONAL_EVIDENCE against a persistent catalog; evidence-only updates never create briefs |
| Global two-cohort stopping | Per-branch novelty history; untouched siblings remain eligible; global exhaustion requires the remaining frontier to finish |
| Weak/bias-prone evidence | Engagement-quantile shop sampling and bounded dated shop/listing review probes, including cached empty observations |
| Structurally valid but unusable briefs | Business-intent anchors, independent specification choices, per-file count/dimension/method criteria and explicit readiness |
| Incomplete archives | Sanitized source pages, full cohort evidence, reviews, catalog support, prompts/model context, drafts, decisions and usage snapshots; frozen retry payloads |
| Redundant model usage | Stable opportunity handoffs, validated refinement cache, unchanged-listing cache and per-stage tokens/time/cost records |
| Concentrated/dense orchestration | Cohesive search, cohort/evidence, delivery and monitoring collaborators with a lifecycle/retry coordinator |

## Decisions and limits

Keep business critique and product-definition critique: the prior critiques were present but their contracts were too weak. Removing them merely to reduce call counts would remove useful validation. Duplicate/evidence-only opportunities now avoid an entire definition/review pair instead.

Semantic identity and marginal insight remain model judgments, not mathematical proof. An exact normalized buyer/use/contents/differentiation match is a cheap fallback against obvious repeated NEW outputs; there is no regex business taxonomy. The current model catalog is bounded to 100 entries per cohort prompt. Ranked/quantile sampling improves coverage but is not statistically representative, and public reviews do not establish listing revenue.

Brief READY means an actionable specification with no stated unresolved questions, not a produced or empirically QA-tested product. Intent anchors are copied verbatim; critique checks the specification against the underlying buying purpose. Exact contents/counts may change from the recommendation.

Historical invoices/account-wide usage are not inferred from local estimates. Unknown attempts retain reservations and are not reported as zero spend. A new allowance has a separate file and cannot reset or reuse historical budget records. No live paid calls were made during this review.

## Fresh investigation

Explicit clearing removed the previous fetched files and research payloads. Owned databases retain sanitized operational status/usage projections and reset receipts. Historical rate/spending ledgers were byte-compared before and after clearing and were unchanged. Code, fixtures, configuration, Git history and conversation context remain.

The current tree no longer contains previous dated business-result/baseline documents. New API inputs are built from newly fetched evidence and the new investigation's catalog, never old Git reports or agent conclusions. Historical conclusions remain recoverable only by deliberately inspecting version history, not as automatic research inputs.

The explicit cleanup removed 108 fetched/result artifacts and cleared one existing service database's research tables. Four historical quota/spending ledgers were byte-identical afterward. Removed ignored payloads are not recoverable through Git; deleted tracked reports remain in version history.

## Verification and allowance

`mvn clean verify` passed: 112 tests, zero failures/errors/skips, using Surefire and Failsafe with synthetic/local HTTP fixtures. Regression coverage includes idle/no-change writes, provider contention recovery, untouched search siblings, semantic evidence/variant handoffs, bounded review sampling, brief intent/criteria, immutable complete archives, separate allowances and history-preserving clearing. No live provider acceptance run was performed.

At review completion, the separate allowance `research-quality-review-20261022` was created with zero commitments and status AWAITING_CAP. The user subsequently authorized USD 2 without resetting historical ledgers. The later [live acceptance report](2026-10-22-live-continuous-acceptance.md) records actual new usage; the original offline review itself made no paid calls.
