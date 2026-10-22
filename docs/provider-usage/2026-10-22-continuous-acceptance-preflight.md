# Continuous live acceptance preflight — 2026-10-22

- Initial intent: Jewish printables. Etsy and OpenAI explicitly authorized for this acceptance test.
- Named allowance `research-quality-review-20261022`: ACTIVE, USD 2.00 cap, USD 0 committed before this run. Existing historical spending records are not a source of new authorization and will not be reset.
- Metadata checked at 2026-10-22T19:23:05Z: configured `gpt-5.4-mini` HTTP 200. Organization usage HTTP 403; no Admin credential is available, so account-wide spending/quota remain unknown rather than zero.
- Gemini metadata HTTP 200; this acceptance explicitly exercises OpenAI rather than automatic/free-provider routing.
- Pricing checked against [the official model page](https://developers.openai.com/api/docs/models/gpt-5.4-mini): USD 0.75/M input, USD 4.50/M output; local accounting conservatively ignores cached-input discounts. [Reported output totals include reasoning tokens](https://developers.openai.com/api/docs/guides/token-counting).
- Shared, locked allowance reserves conservative input/output costs before requests and refuses reservations above the approved cap. Ambiguous attempts keep their reservations. No image generation.
- Etsy requests use the existing rolling ledger, a local 360-attempt rolling-day ceiling (including pre-existing attempts), two-second spacing, header reconciliation and a pre-operation quota probe. Unknown/low provider quota prevents the data request. The live probe reported 5 requests/second, 5,000/day and over 4,600 remaining. An initial 160 cap correctly deferred because historical rolling usage already exceeded it; raising the local cap to 360 does not erase that history.
- Initial bounded configuration: 200-listing cohorts, at most 12 shop profiles, three shop-review probes and two listing-review probes. Page/query safety stops are not evidence of natural exhaustion. No automatic enlargement just to consume the allowance.
- Acceptance sampling stops after six search pages (at most 600 returned rows); remaining frontier is explicitly deferred, not declared exhausted. This still drains pending analysis, briefs and archive. Search selection prioritizes unvisited branches over deeper pagination.

This file records authorization and access checks, not verified business results or an account invoice.
