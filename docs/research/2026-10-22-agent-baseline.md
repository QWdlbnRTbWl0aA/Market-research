# Frozen agent business analysis — before implementation/provider comparison

Author: the interactive AI development agent, not application code and not a Gemini/OpenAI API response. Frozen on 2026-10-21 UTC (2026-10-22 local date); the Git commit timestamp records the boundary. No project source changes or OpenAI API calls were made for this assignment before this analysis. Collection used runtime invocation of the unchanged Java adapters, quota ledger and cleanup helper. This document must not be rewritten to match subsequent model results; corrections belong in the comparison report.

## Evidence and coverage

Fresh collection: 5,000 unique digital listings from ten Halloween query families, 3,288 distinct shops represented, 50 fetched public shop profiles, 50 shop-review samples and 20 targeted listing-review samples. Collection finished 2026-10-21 21:09:08 UTC, after 173 Etsy requests (52 search pages, one quota probe, 50 profiles, 50 shop-review requests, 20 listing-review requests). Two-second spacing and preserved quota accounting were enforced. The assignment-specific invocation used a 400-request rolling-day local cap and at most 250 new requests, rather than the former small experiment's limits. Provider quota ended at 4,729/5,000 daily requests remaining.

Input SHA-256 values, to bind this analysis and both pipelines to the same frozen corpus:

- listings.json: `8f455d57a3741cce7249b609bd8bb90d045a5c4bed72c07f3f3e4116c79f900d`
- shops.json: `1f3e51e926f5f4758a0f0bd712f8303bb36d945a54f28cc87f156099ef591691`
- shop-reviews.json: `0971b3333e4b780e903b3cadac98502cd039e5c981bab70a37f229b68667edcd`
- listing-reviews.json: `2b612676ae5c564ff4521a8c9516db690e6638ab8c0a8d605f08c5b9bac226de`

Raw files live in ignored `data/halloween-run/comparison/`. Prior research outputs were erased, operational ledgers/docs/fixtures preserved. Preparation found 3,396 USD listings, 3,057 listings with zero favorites, and 700 with zero views. Eight of the 50 fetched shops had zero lifetime transactions; others ranged up to 168,699. These are context, not business scores or inferred monthly revenue.

My inspection used full-cohort factual aggregates, approximately 100 title/price/engagement examples spanning all query families and engagement levels, all 50 shop-profile summaries, six detailed comparable descriptions, matching-in-cohort shop reviews, all 20 listing-review count/date summaries and their recent reviews, plus low-rating shop-review text. I did **not** individually read all 5,000 complete descriptions or all 2,077 returned shop reviews. The API workflow can systematically scan more titles; this difference must be disclosed in the comparison. This is an agent judgment baseline, not ground truth or an exhaustive market audit.

## My reasoning and conclusions

1. Many score-ranked results are new/low-engagement supply, not proof of demand. Conversely high lifetime favorites/views can coexist with no recent returned reviews. Neither is a sales estimator.
2. The larger sample contains actual recent purchase proxies. Handprint listings 4575777630 and 4591206352 have recent reviews in two separate shops. Shop 63826629 is about 168 days old, with 466 lifetime transactions; shop 53893017 is about 795 days old, with 7,968. Success is not confined to decade-old giants, but these counters cover their whole portfolios.
3. Pass-the-candy listing 1772111508 has three returned reviews in the trailing 30-day window, including classroom use; bingo listing 1552053170 has two, including first-grade use. These support concrete buyer/use-case hypotheses, not a calibrated probability of our revenue.
4. Clipart listing 4515786397 has two recent reviews, including use on a parent-information board. SVG listing 4564020280 has two recent reviews in shop 33883319, which has 36,346 lifetime transactions. These are viable alternatives, not markets to reject simply because assets are cheap or competition exists.
5. Some low-rating shop reviews concern unrelated crochet patterns, ornaments or forms. They cannot be attributed to the sampled Halloween product. They do reveal possible failure modes worth checking: mismatched previews/files, incorrect instructions, printer readability and broken download links. Ratings and text sometimes disagree; neither should be interpreted mechanically.
6. Prefer direct-download, fixed-format products with reusable generation and QA. This is about reducing total research/support/manual work, not minimizing generation cost. A smaller complete product can be a better first experiment than an enormous bundle, but bundle size alone does not determine profitability.
7. There are only about ten days to Halloween. These are seasonal experiments, not recommendations promising USD 30 every month of the year. Recurring portfolio income requires a seasonal calendar and eventual learning from our own sales.

## Specific product experiments I would prioritize

### A. Original Halloween pass-the-pumpkin classroom game

- Buyer/use: teachers or parents hosting a group of children aged approximately 5–8.
- Deliverable: original host-read passing game, standard and non-food-prize instructions, a short and long version, large-print host sheet; fixed PDF in A4/US Letter, color/ink-saving layouts. No personalization, paid editor or custom order.
- Proposed test price: USD 2.99; 11 sales yields USD 32.89 gross before fees. This is target arithmetic, not expected sales.
- Why first: a text-led reusable template with little per-order work; recent buyer evidence supports the passing-game use case. Comparable 1772111508 is NZD 3.50, **not USD 3.50**; its three recent reviews establish neither our price nor a sales rate. Comparable 1791352958 supports the pumpkin variant as existing supply, not a verified unmet need.
- Automation/QA: original copy, layout template, spell/legibility checks and a one-time rule/play-through check reusable across holiday variants. Do not copy a competitor's verse. Subsequent orders should require no manual customization.
- Reservation: evidence is concentrated in one comparator's recent reviews; our acquisition/conversion and support cost are unknown. No forecast that 11 sales will occur.

### B. Three-design Halloween handprint keepsake mini-pack

- Buyer/use: daycare teachers and parents of ages 2–5.
- Deliverable: three original ghost/pumpkin/spider handprint templates, blank name/date lines, A4/US Letter and ink-saving versions, concise printing/placement instructions; direct-download PDF without external link dependence or personalization.
- Proposed test price: USD 3.49; nine sales yields USD 31.41 gross before fees.
- Support: recent reviews for listings 4575777630 and 4591206352 in two differently aged shops; USD comparables 1793426875 (4.25) and 1548423084 (2.50) support price context, not a proven optimal price. Recent-review comparables themselves use INR/IDR, so no unverified FX conversion is assumed.
- Differentiation: bounded, printer-friendly complete activity and predictable delivery, not an unsupported claim that competitors lack these features. A4/US Letter support already exists in comparables.
- Automation/QA: reusable layouts, original simple artwork, page-size/margin/font checks and a one-time physical handprint-space/printing check. Optional future season swaps amortize setup.
- Reservation: physical fit and actual buyer preference need testing. Cheap generation does not remove QA/support cost. Current-season observations do not establish year-round income.

### C. Low-ink picture-bingo pack for early-elementary classrooms

- Buyer/use: first-grade/early-elementary teachers running a roughly 30-child Halloween activity.
- Deliverable: 30 unique numbered picture cards, caller cards/master sheet and host instructions; one- and two-per-page PDFs, A4/US Letter, printer-friendly alternative. No named students/customization.
- Proposed test price: USD 3.49; nine sales yields USD 31.41 gross before fees.
- Support: recent first-grade/family reviews on 1552053170; 1597145513 is an existing USD 2.65/50-card alternative with calling material. This is not a new feature invention or an established market gap.
- Automation/QA: seeded unique card layouts, verified caller/card symbol agreement, usable print sizes and legibility; original consistent artwork and a reusable template. Do not assume 30 cards alone differentiates it from established 20–200-card packs.
- Reservation: weak differentiation and competitor price pressure. Test a single complete variant rather than building a huge catalog or researching indefinitely.

These priorities are my qualitative trade-offs, not computed profitability rankings. Confidence in feasibility/use-case fit is stronger than confidence in any revenue target; actual USD 30/month attainment remains uncalibrated for all three.

## Alternatives and what would change my mind

Original small clipart/SVG packs remain credible alternatives because there is recent purchase evidence. They could outrank the above if a reusable asset-generation/format-QA workflow makes human checking minimal. Large branded-character bundles and complex functional patterns need specific asset-rights/functionality checks; reviews alleging problems are not proof of wrongdoing. Scavenger hunts and non-food-treat signs are inexpensive exploration candidates, but the targeted responses supplied less recent purchase evidence; that is not a veto or proof of zero sales. Editable invitations can work if editing/access support is automated; it must not be assumed costless or categorically expensive.

The next meaningful business learning is exposure, orders, gross/net receipts, refunds/support and intervention minutes from bounded published experiments, not agreement among models. This assignment does not publish anything.

## Predeclared comparison criteria

Compare factual/source accuracy, specific usable product definitions, counterevidence/uncertainty, treatment of currencies and seasonality, plausible low-human-work execution, and actual API cost/latency/failures. Different product choices can be better than mine. Agreement is not correctness; my judgments may also be wrong. No deterministic business scoring/selection engine or calibrated probability model will be added. Validate structure/citations and record arithmetic as supporting checks only.

Do not send this document, its product choices or prior agent recommendations to the provider pipelines. Both must work independently from the same frozen source corpus and generic business objective, with separately retained raw outputs and application-accepted outputs.

OpenAI starting committed cost remains USD 0.3695175. This assignment may add at most USD 1.63, while preserving the global USD 2 stage ceiling. Gemini has no payment method per the user; attempt its text API with conservative local quotas, without the previous unresolved-entitlement gate or silent OpenAI fallback.
