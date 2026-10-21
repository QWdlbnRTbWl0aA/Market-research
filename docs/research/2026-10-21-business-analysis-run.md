# Halloween refinement and business-analysis run

Final fresh run: 2026-10-21 20:00:01–20:02:00 UTC. Status: SUCCEEDED.

## Outcome

The live Java pipeline returned 654 unique digital listings, 633 semantically usable, six public shop observations and six listing-review samples. It used 20 Etsy requests and nine text-model calls. Previous fetched results were deleted before every fresh attempt; code, fixtures, documentation, Etsy quota counters and model spending ledgers were preserved. The final attempt removed 32 old research files; erased raw runs are not recoverable through Git. Historical documentation remains available.

No images, product generation, publication or scheduled production execution occurred. Slow provider work was coordinated by the explicit CLI; the demonstration orchestrator still uses synthetic evidence.

## Search refinement

The seed `halloween printable` reported 432,758 results, approximately 4,328 pages at 100/page. The pipeline sampled it, rather than enumerating those pages. Model grouping supplied literal child queries, code measured actual pool sizes and usable yield, and one bounded model pass narrowed oversized pools.

| Query | API result count | Estimated pages | Usable / returned | New usable after overlap |
| --- | ---: | ---: | ---: | ---: |
| halloween kids activity printable | 60,677 | 607 | 99 / 100 | 96 |
| halloween party games printable | 43,150 | 432 | 100 / 100 | 100 |
| halloween sign printable | 25,895 | 259 | 99 / 100 | 99 |
| halloween preschool activity sheet printable | 1,856 | 19 | 100 / 100 | 99 |
| halloween classroom bingo cards printable | 2,476 | 25 | 100 / 100 | 99 |
| halloween teal pumpkin allergy sign printable | 137 | 2 | 99 / 100 | 98 |

Two narrowed pools fell below the 2,000-result threshold. Bingo remains oversized and marked for further refinement, but the bounded run stops instead of repeatedly researching or downloading it. Counts overlap and cannot be summed as market size or demand. These are score-ranked samples, not random/representative coverage. Relevance is model-classified, not externally verified precision.

An early attempt produced descriptive phrases with no search hits; literal-query prompting and validation corrected that. Another attempt omitted one classification; the pipeline stopped safely. There is now one missing-ID-only repair per batch, followed by exact coverage validation. Unknown/duplicate IDs or continued omissions still fail. No large regex relevance filter was added.

## Business evidence and interpretation

The target is USD 30 monthly gross receipts, not profit or a promised forecast. Prices are normalized deterministically, USD only. Cheap items are not penalized simply for needing more units.

| Group | Usable listings / shops | Median USD price | Units for USD 30 | Recently reviewed sampled shops | Decision |
| --- | ---: | ---: | ---: | ---: | --- |
| Kids activities | 197 / 175 | 3.00 | 10 | 0 / 2 | TEST, LOW confidence |
| Party games | 244 / 225 | 3.99 | 8 | 0 / 2 | TEST, LOW confidence |
| Signs | 192 / 151 | 3.49 | 9 | 0 / 2 | TEST, LOW confidence |

The six sampled listing review responses had no reviews within the requested trailing 30-day window. This means insufficient purchase-proxy evidence, **not zero sales or no demand**. Shop lifetime transactions observed in the two-shop samples were 6,722 / 0 / 391 respectively; they cover all products and all years, and do not establish monthly revenue in these groups. Public Etsy data cannot supply competitors' verified monthly revenue. No calibrated probability of reaching USD 30 is claimed.

The implementation supports explicit 10% and 2% purchase-to-review scenarios when recent reviews exist. Both rates are uncalibrated illustrations, neither credible bounds nor sales forecasts. In this final run the scenarios correctly remain unknown rather than displaying zero revenue. Unit tests exercise positive-review calculations and demonstrate that a USD 2 product needs 15 units for the same target a USD 30 product reaches with one.

Model interpretation recommends small automated learning experiments in all three groups: reusable age/skill-band activity templates, modular party games and non-personalized sign variants. These are hypotheses, not a production authorization or validated revenue ranking. Confidence remains LOW. A previous model review presumed signs required manual customization; the final prompt explicitly rejects category-based workload assumptions and receives actual product examples and customization flags. Missing reviews alone cannot justify demanding more pre-production research.

The shop sample is deliberately small and centered near median engagement rather than hit listings. It may favor young/low-engagement products; it does not estimate the population's sales distribution. The next useful business evidence is our own published-product exposure, orders, receipts, costs and intervention time, rather than an ever-larger pre-publication dataset. Published-performance ingestion and calibrated portfolio allocation are not implemented yet.

## Cost and quota accounting

Across five development attempts (four completed, one safely failed):

- OpenAI: 31 generation calls, 202,794 input tokens and 48,316 output tokens including reasoning. Standard list-price arithmetic totals **USD 0.3695175**, leaving USD 1.6304825 of the authorized USD 2 stage ceiling. All calls settled; no uncertain reservations remain. This is not a provider invoice or account-wide usage statement.
- Gemini: zero generation calls. Metadata access succeeded, but account billing tier/free entitlement and remaining quota could not be established with the supplied key alone. The adapter prefers operator-confirmed free access, conservatively limits it to five requests/Pacific day with at least 61 seconds spacing, then uses the already-authorized paid route when that local allowance is exhausted. Provider errors are not automatically retried or silently upgraded to paid calls.
- Etsy: 82 requests in this stage. The preserved rolling-day ledger includes 16 earlier requests, totaling 98 against our local cap of 100. The final provider response reported 5 requests/second, 5,000/day and 4,902 remaining. We enforced two-second request spacing, a 20-request/run bound and a daily reserve ourselves. No further live research was attempted after this run.

Preflight and pricing sources are recorded in [the provider check](../provider-usage/2026-10-21-business-stage-preflight.md). No credentials, local network details or machine identifiers were sent to a model or committed.

## Daily-production forecast

One equivalent fresh investigation per day, no caching discount, thirty days/month:

| Item | Daily | Thirty days |
| --- | ---: | ---: |
| OpenAI, measured workload | USD 0.1119405 | USD 3.358215 |
| Etsy requests | 20 | 600 |
| Human-time illustration only: 1 minute/day at USD 30/hour | USD 0.50 | USD 15.00 |

Measured model workload: 62,110 input and 14,524 output tokens across nine calls. At the illustrative human rate the subtotal is **USD 18.36/month plus compute**, before production, publication, selling fees, support and other portfolio costs. Human time and hourly value are not measured or configured user values; this is a sensitivity example, not a total-cost claim. Compute remains `active process-hours × configured rate + allocated idle/shared infrastructure`; observed wall time alone cannot establish its cost. The fresh run took about 119 seconds, mostly provider waits.

The deliberately loose configured token/request maximum, including seven possible semantic repairs, is USD 2.425152/day or USD 72.75456/30 days. This is a theoretical no-caching reservation envelope, not expected spending; **the current persistent USD 2 stage guard would stop execution before exceeding its stage authorization**. Production needs its own explicit budget rather than resetting this ledger. Confirmed Gemini free capacity could lower token charges, but zero-cost coverage of the entire nine-call workload is not verified or assumed.

`--research.tools.command=forecast` regenerates ignored `data/halloween-run/production-forecast.json` without provider calls or cleanup. The full business report, query samples and manifest remain private ignored files in the same run directory.

## Verification and limits

Final `mvn verify`: **61 tests, zero failures/errors/skips**. Java/JUnit unit tests run through Maven Surefire; Spring HTTP integration and packaged cross-process/recovery tests run through Failsafe. Coverage includes safe cleanup, preserved ledgers, semantic ID validation, money and target-unit calculations, review windows/caps, unknown revenue, confidence caps, model adapter/provider preference, budget reservation/failure accounting and forecast windows. Live research is an explicit opt-in smoke test, not part of the automated test lifecycle.

This increment supplies evidence-grounded experiment hypotheses, not verified competitor revenue, calibrated sales probabilities, automated publishing or a live durable orchestrator integration. Those boundaries must remain explicit when interpreting the results.
