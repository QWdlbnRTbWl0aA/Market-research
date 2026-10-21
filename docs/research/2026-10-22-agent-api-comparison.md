# Agent versus API product-reasoning comparison

## Sequence and scope

1. Fresh data was collected through unchanged Java adapters: 5,000 digital listings, 50 public shop profiles/review samples and 20 additional listing-review samples. Old fetched results were erased; operational counters/docs/fixtures survived.
2. The agent inspected the evidence and froze [specific product conclusions](2026-10-22-agent-baseline.md) in commit `86fc0bf` before project source changes or any OpenAI API calls for this assignment.
3. The [implemented protocol](product-api-protocol.md), `product-reasoning-v2`, scans all 5,000 compact listing records, retrieves original descriptions for model-selected candidates, creates product definitions and requests critical revision. Code prepares/retrieves evidence and validates structure, IDs and arithmetic. It does **not** score, rank or recommend business opportunities.
4. Gemini and OpenAI ran independently against the same frozen corpus. Neither received agent recommendations, the baseline document or the other model's responses. The three corresponding scan input hashes match exactly. Each model selected different sources for subsequent retrieval, so later prompts naturally differ.

The agent is also an AI, not a ground-truth human judge. Its inspection was selective (coverage is documented in the frozen baseline); the API scans read more titles, while neither exhaustively read all full descriptions/reviews. This is a small pipeline experiment, not a statistically controlled claim about provider superiority.

## Separately attributed product recommendations

All prices below are **proposed test prices**, not forecasts or silently converted competitor prices. Units are only the sales needed to exceed USD 30 gross before fees, not expected demand.

| Origin | Specific proposal | Test USD price | Units for USD 30 |
| --- | --- | ---: | ---: |
| Frozen agent | Original pass-the-pumpkin classroom game, ages approximately 5–8; short/long host-read variants, non-food option, A4/Letter | 2.99 | 11 |
| Frozen agent | Three-design ghost/pumpkin/spider handprint mini-pack; name/date blanks, A4/Letter, ink-saving versions | 3.49 | 9 |
| Frozen agent | Thirty unique picture-bingo cards for early-elementary classrooms, caller materials, A4/Letter, printer-friendly version | 3.49 | 9 |
| OpenAI API pipeline | Pass-the-pumpkin story game; full/half-size host sheets and instructions | 4.99 | 7 |
| OpenAI API pipeline | Four-motif preschool/daycare handprint bundle: pumpkin, ghost, skeleton, monster; PDF/JPEG | 3.49 | 9 |
| OpenAI API pipeline | Non-food-treat/allergy-aware porch sign kit with matching mini-flyer | 2.99 | 11 |
| Gemini API pipeline | Six-page printable picture scavenger hunt with classroom/indoor bonus cards | 3.00 | 10 |
| Gemini API pipeline | Spider handprint craft; fixed PDF in 8x10, Letter and A4 | 4.25 | 8 |

These summaries preserve the differences; they do not replace either model's answers with the agent's preferred choices. Files with complete structured answers, counterevidence, QA and experiment plans remain private in ignored run directories.

## My assessment of the API results (agent judgment after the frozen baseline)

### OpenAI: more usable briefs in this run, not verified business predictions

The pipeline independently converged on two of my three ideas: a passing game and handprint pack. It prioritized a practical sign kit instead of bingo and explicitly declined SVG, generic workbooks and bingo. The passing-game price is higher than mine; neither proposal establishes an optimal price or likely unit sales.

Strengths: concrete motif/file quantities, useful QA plans, explicit support uncertainty, clear seasonal limitations and publication-outcome measurements. It distinguishes proposed products from adjacent evidence and avoids review-to-sales multipliers. The critical pass softened several overconfident claims.

Remaining issues: “crowded”/incumbent arguments are qualitative judgments, not measured entry barriers; they must not become automatic rejection gates. Some sign citations are generic candy-bowl/adjacent signage rather than allergy-specific evidence. The review-based use case supports a related passing game, not the exact new product. Some QA suggestions (such as print-shop workflows) could create needless per-product human involvement; validate reusable templates once and reserve extra checks for exceptions. Naming audiences broadly is less actionable than choosing one concrete buyer first. MEDIUM confidence is an explanation of weak evidence, not a calibrated chance of attaining USD 30.

### Gemini: valid candidate ideas, but important reasoning weaknesses remained

The final handprint idea is specifically a spider craft, with concrete file sizes. The scavenger-hunt concept is plausible and has cited historical review evidence; it is not disproved by absence of recent reviews. Both have runnable digital-delivery ideas and acknowledge broad seasonality.

However, I would **not adopt the final report unchanged**:

- Its critical pass changed the proposed handprint price from USD 2.99 to USD 4.25 because that matched a competitor's price. This confuses a test-price decision with an observed comparable price. Our proposed price need not equal a competitor's.
- Both confidence fields literally contain `MEDIUM with explanation`, without actually giving that explanation. Structural validation accepted a non-empty string; semantic quality was still inadequate.
- Claims of “minimal human minutes” or “no manual intervention” are unsupported assumptions. Automated fulfillment does not establish negligible support/maintenance cost.
- It suggests 30- and 45-day observation windows even though Halloween is about ten days away. It states seasonality but does not operationalize it. A November-heavy window cannot be read as ordinary in-season performance.
- The scavenger brief specifies page formats but leaves the number/content of hunt prompts and precise age/use context insufficiently defined for a build-ready product. Broad “original layout” is not a demonstrated differentiator.

These are business-reasoning defects, not deterministic findings or reasons to relabel its raw response as correct. The code's accepted report means JSON/evidence identity/target arithmetic passed; it is not a claim of verified business quality. The same-model critical pass did not reliably catch—and in the pricing case introduced—mistakes.

### Updated agent judgment

I retain the frozen passing-game and small handprint experiments as sensible first low-intervention tests, not proven winners. OpenAI's sign kit is a reasonable cheap exploration alternative to bingo, not a demonstrably superior opportunity. Gemini's scavenger hunt is also a legitimate alternative once its buyer, clue count and file specification are narrowed. Actual published exposure, receipts, support and intervention time—not majority vote among models—should decide further portfolio spending.

The current dataset has recent listing-level purchase proxies in handprints, passing games, bingo, clipart and SVG, including evidence in a relatively young handprint shop. That increases confidence in concrete use cases compared with the earlier six-shop screen, but still does not establish competitor monthly revenue or our acquisition/conversion. Halloween remains a seasonal playground, not a uniform year-round revenue stream.

## Reliability, cost and latency

| Completed run | Model | Requests | Grounding corrections | Input / output tokens | Token-cost estimate | Wall time |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| OpenAI | gpt-5.4-mini, low reasoning | 6 | 1 | 666,606 / 11,214 | USD 0.5504175 | about 75 seconds |
| Gemini | gemini-3.5-flash-lite | 8 | 3 | 822,099 / 9,373 | USD 0 on confirmed free account | about 432 seconds |

All three initial Gemini scans needed source-ID corrections; OpenAI needed one correction on its third scan. Original and corrected answers are retained separately. Correct citations still do not prove correct interpretation. The matching first-stage input digests are:

- scan-0: `40358b891e2f81a33192f20c47a2031a87f8790b0e5967d79eb67856110c475c`
- scan-1667: `bbf2c9c9c385ca1e66610aaee6c97ff1a6c37deea2a2fe461fa483ca5936c7f9`
- scan-3334: `9ec9552529f3bf81b20bbd0908e40075d7d5e9e7790da0b028d59970b20c75cd`

Gemini's wall time includes our enforced 61-second dispatch spacing, so it is not a pure inference-speed comparison. Its complete run reported 229,267 cached input tokens. OpenAI costs use standard token rates without assuming discounts. These are local calculations/free-tier expectations, not provider invoices.

There were three earlier Gemini attempts: an old-model generation 404 despite metadata 200, an answer rejected for an invalid citation, and a response-handling failure. The latter's usage/envelope was not retained by the earlier code; the attempt is counted, but its tokens remain unknown. Eleven total Gemini attempts were counted, within the twelve-attempt local day ceiling. The successful run above is reported separately, not substituted for the development history. No additional OpenAI generation calls were used beyond the six-call successful run.

New OpenAI testing spent **USD 0.5504175 of USD 1.63**. Remaining assignment allowance: **USD 1.0795825**. Including the earlier stage, committed cost is USD 0.919935 against the original USD 2 ceiling; no uncertain OpenAI reservations remain. See [the quota/usage record](../provider-usage/2026-10-22-agent-api-comparison.md).

At one equivalent 5,000-listing investigation/day, measured OpenAI analysis would be about **USD 16.51 per thirty days**, plus Etsy request usage, compute, human involvement and subsequent product/publication costs. The equivalent Gemini run would consume eight requests/day under the observed correction pattern and currently has no expected token charge on the confirmed free account; provider capacity is not guaranteed. One fresh collection uses 173 Etsy calls/day (5,190 over thirty days), not 5,190 in one day. There is no production schedule and no authorization to reset/extend the development ledger. No full operating-cost total is claimed while compute and intervention costs remain unmeasured.

## Outcome and next improvements

Implemented: repeatable bounded Java collection, frozen-corpus checks, provider-neutral scan/retrieve/define/review, one correction per stage, separate raw/application-accepted artifacts, strict provider selection, quotas and persistent spending controls. No new deterministic business-analysis engine was added. No images, products or listings were generated/published. The workflow remains an evaluation CLI, not a production durable-job integration.

Final verification: `mvn verify` passed **72 tests, zero failures/errors/skips**, using JUnit/Surefire unit tests, Spring/mocked-adapter checks and Failsafe packaged CLI/HTTP/workflow integration tests. New coverage includes frozen-source tampering, absence of agent conclusions from prompts, bounded grounding/format correction, provider isolation, safe quota diagnostics, shared budget persistence, large-sample collection/privacy and packaged offline preparation/live opt-in. Automated tests use synthetic fixtures and mocked providers, not billable APIs; the live comparisons are separately recorded above.

Before automating adoption of these reports, make confidence labels/explanations separate fields, clarify proposed-price versus observed-price semantics, require a season-aligned experiment window and use shorter source aliases to reduce ID copying errors. These are observed quality issues, not claims already fixed/retested here. More repeated trials or stronger Gemini configurations may change the comparison; a single run cannot establish provider-wide quality rankings.
