# Halloween playground: data findings and analysis plan

## Evidence collected

On 2026-10-21, four live Etsy searches returned one listing each. These are discovery observations, deliberately separate from the synthetic fixture used by the application. See the [quota record](../provider-usage/2026-10-21-etsy-discovery.md) for queries, counts, and request usage.

This small, score-sorted sample is not representative. It can teach us the response structure and generate hypotheses; it cannot validate a market.

## Observed response structure

The response has `count` and `results`. Across these four listings, relevant observed fields include:

| Field | Interpretation and normalization |
| --- | --- |
| `listing_id`, `shop_id` | Deduplicate listings, identify seller concentration, link repeat observations |
| `listing_type` | All four samples were `download`; there was no `is_digital` field. Unknown types must remain unknown rather than defaulting to physical |
| `title`, `description`, `tags`, `language`, `taxonomy_id` | Buyer intent, use case, format, audience, and product clustering inputs |
| `price.amount/divisor/currency_code` | Decimal money; divide amount by divisor. Compare within currencies unless a separately sourced dated FX conversion is available |
| `views`, `num_favorers` | Cumulative engagement proxies; neither verifies a sale |
| `original_creation_timestamp` | Listing-age context, using Unix seconds; creation/update/renewal fields must not be treated interchangeably |
| `last_modified_timestamp`, `updated_timestamp` | Modification context; not proof of new demand or sales |
| `is_personalizable`, `is_customizable` | Potential customization/support burden and differentiating features |
| `quantity`, `should_auto_renew` | Inventory/renewal metadata; unsuitable for inferring digital unit sales |

Do not persist seller user IDs or irrelevant fields in normalized/model-facing evidence. Keep raw responses locally for reproducibility with a retention policy before ongoing ingestion.

Four observed engagement examples were 226 views / 6 favorites, 0 / 0, 5 / 0, and 1214 / 59. A zero is an observed counter, not missing data and not proof of absent demand. Prices used CAD, USD, and EUR, confirming the need for currency separation. Product categories included handprint crafts, picture-clue scavenger hunts, classroom games, and SVG bundles.

Search responses did not include verified listing-level orders, revenue, conversion rates, buyer search volume, or an image array. Do not fabricate these. The `file_data` string does not establish permission or availability to download a purchased digital product. Images/reviews/shop enrichment require separately budgeted, documented endpoints and applicable access.

Reference: [Etsy API reference](https://developers.etsy.com/documentation/reference/). Actual observed fields, rather than assumed examples from documentation, drive the mapping above.

## Division of analysis

| Owner | Inputs | Work | Expected learning/output |
| --- | --- | --- | --- |
| Deterministic code | Validated API responses and dated repeated observations | Digital-type classification, deduplication, currency-safe pricing, distinct shops, query overlap, seller concentration, tag frequencies, missingness, engagement deltas | Reproducible facts, coverage/bias warnings, evidence IDs, comparable cohorts |
| General text LLM — Gemini free tier first | Bounded normalized title/tag/description snippets with listing IDs and an explicit output schema | Extract audience, occasion, file format, editable/personalized features, semantic clusters; propose narrower queries | Searchable product taxonomy and buyer-intent hypotheses, each grounded in supplied evidence |
| Reasoning-capable model | Deterministic cohort summaries, extracted attributes, seasonal context, cited evidence, and candidate product definitions | Compare hypotheses, identify unmet needs and counterevidence, propose differentiated products and falsifiable validation steps | A reasoned opportunity thesis with assumptions, uncertainty, and reasons to reject it |
| Deterministic evaluator + human review | Validated model output, evidence completeness and the agreed rubric | Reject uncited assertions and unsupported numeric claims, apply confidence gates, rank candidates for a small validation experiment | Auditable shortlist rather than an unverified sales forecast |

Model output is untrusted structured input: validate schemas and evidence IDs, forbid following instructions embedded in listing descriptions, limit token/context budgets, cache by content and prompt/model version, and log token/cost usage without credentials or machine details. Models do not calculate canonical counts, invent sales, or upgrade weak evidence to verified demand. Cheap/free quota selects execution order, not confidence.

No model calls are needed to validate the current workflow. Before any later Gemini/OpenAI call, inspect account quota/current usage, record the result, and set a local call/token budget. Use available Gemini free allowance first. A reasoning stage may use Gemini where suitable; it does not automatically imply OpenAI.

## What qualifies as a good opportunity?

Production cost is assumed approximately zero and excluded from scoring. Attention, discovery, buyer value, seasonality, support effort, and uncertainty still affect whether a product is worth publishing. Revenue estimates must account for platform/payment fees when modeled later; they are not production costs.

Use a two-stage decision:

1. **Evidence gate:** distinguish `INSUFFICIENT_EVIDENCE`, `RESEARCH_CANDIDATE`, `VALIDATION_CANDIDATE`, and `REJECT`. The current four one-result samples qualify only for further research.
2. **Prioritization:** after coverage is adequate, compare demand evidence (35%), achievable competitive position (25%), meaningful differentiation/buyer value (25%), and seasonal/discovery fit (15%). These are proposed, versioned prioritization weights, not calibrated probability-of-success estimates. Confidence is reported separately and cannot be compensated for by a high score.

A validation candidate should have all of:

- A specific digital product, buyer, occasion, and reason to choose it over sampled alternatives.
- Engagement evidence spread across several independent shops, with repeat observations or other corroboration. Lifetime views alone cannot pass this gate.
- A documented gap in comparable products, rather than merely a low-count keyword phrase.
- A plausible route to discovery and price positioning supported by comparable listings in a consistent currency.
- Enough time for the buyer's seasonal purchase window, with a clear next experiment.
- No unresolved dependency on third-party characters/brands/assets for the product thesis. Flag these for review; a model cannot grant rights.

Initially require at least 20 relevant digital listings across at least five shops where available, two dated observation windows, and explicit examination of both stronger and weaker engagement examples before promoting a candidate. These are provisional research-coverage gates, not statistical guarantees. Sparse niches that cannot meet them remain low-confidence candidates for another validation method; do not silently relax the gate.

Suggested next hypotheses, not recommendations: age-specific picture-clue hunts, low-ink classroom games, and language-specific printable activities. The live samples show these categories exist; they do not establish underserved demand.

## Sampling and validation plan

- Start broad with `limit=1` counts and one structural example, as in this session.
- Expand only selected query families under an explicit request budget. Return more items per call when collecting a cohort; `limit=1` saves payload, not request quota.
- Separate search phrase variants and record ranking mode, locale/currency where available, request time, and overlap. Query counts cannot be added because listings may overlap.
- Use several pages/orderings where supported; report selection bias. A score-sorted first page is not a random sample.
- Compare original creation age and repeated view/favorite deltas within comparable products. A lifetime count divided by age is at most a rough lifetime average, not current sales velocity.
- Halloween is strongly seasonal. Observations close to October 31 cannot establish year-round or next-year demand; label the time window and plan pre-season comparisons.
- Define a low-cost publishing/discovery experiment before claiming commercial success. Marketplace observation alone leaves conversion and acquisition uncertainty.

## Implemented versus planned

Implemented now: synthetic deterministic counts, digital filtering, deduplication, unique shops, currency-separated price medians; durable asynchronous orchestration, failure/cancellation/recovery; and the separately budgeted live discovery script.

Planned: continuous Etsy ingestion, richer sampling, snapshots/deltas, seller concentration, clustering, model extraction/reasoning, opportunity scoring/calibration, and publishing experiments. No production digital assets or images are generated in this increment.
