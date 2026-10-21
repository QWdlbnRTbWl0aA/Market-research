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
| Deterministic evaluator + exception review | Validated model output, explicit operating budgets and published-product outcomes | Reject invented numeric claims, estimate total cost including human time, choose cheap experiments, learn which templates warrant further spending | An auditable experiment queue and portfolio feedback loop |

Model output is untrusted structured input: validate schemas and evidence IDs, forbid following instructions embedded in listing descriptions, limit token/context budgets, cache by content and prompt/model version, and log token/cost usage without credentials or machine details. Models do not calculate canonical counts, invent sales, or upgrade weak evidence to verified demand. Cheap/free quota selects execution order, not confidence.

No model calls are needed to validate the current workflow. Before any later Gemini/OpenAI call, inspect account quota/current usage, record the result, and set a local call/token budget. Use available Gemini free allowance first. A reasoning stage may use Gemini where suitable; it does not automatically imply OpenAI.

## Opportunity methodology v2: operating economics

Optimize expected recurring portfolio receipts relative to total operating cost over the same stated horizon (default planning horizon: 12 months, with monthly seasonal breakdowns). Recurring means continuing portfolio sales, not assumed subscriptions or repeat purchases by the same buyer.

Total cost includes research, AI/API tokens, compute, publication/platform/payment fees, generation, maintenance, support, refunds and especially manual involvement valued at an explicitly configured hourly rate. Allocate shared automation development/setup across the portfolio and record allocation assumptions. Marginal generation can fall to cents; it must not hide human time or overhead.

For explicit scenarios calculate expected gross receipts / total operating cost and expected surplus = expected gross receipts - total operating cost. Record currency and horizon consistently; do not subtract fees twice. Unknown inputs stay unknown. A zero-cost scenario has an undefined efficiency ratio, not infinite priority. Portfolio decisions consider both efficiency and expected surplus under spending and human-time budgets, so an attractive ratio alone does not justify negligible recurring value.

Products are cheap experiments. Remove the former mandatory sample/shop-count/two-window gates and the arbitrary 35/25/25/15 weighting. Weak market evidence reduces confidence; it does not automatically block an inexpensive, automated publication experiment. Low hit rate is acceptable when portfolio winners cover experiments and ongoing operating cost.

Prefer reusable standard-download templates and automated distribution/measurement over personalized products requiring repeated manual work. Customization flags are workload hints, not proof that the work cannot be automated. The deterministic queue sorts these hints transparently; it does not invent revenue predictions.

Before spending on additional research, ask whether its expected improvement to the publish/stop decision exceeds the added research and delay cost. Stop researching when a bounded publication experiment is cheaper or more informative. When value-of-information inputs are unknown, default to the small automated experiment within configured budgets rather than mandatory evidence gathering.

Human review should focus on reusable templates, exceptions, aggregate budget changes and decisions with material downside. Do not require per-product review by default. Published performance, rather than extensive pre-production validation, supplies the main learning loop.

The [experiment operating policy](experiment-operating-policy.md) defines accounting, automation and the feedback plan. No publishing or asset generation is performed by the current analysis run.

## Sampling and validation plan

- Start broad with `limit=1` counts and one structural example, as in this session.
- Expand only selected query families under an explicit request budget. Return more items per call when collecting a cohort; `limit=1` saves payload, not request quota.
- Separate search phrase variants and record ranking mode, locale/currency where available, request time, and overlap. Query counts cannot be added because listings may overlap.
- Use several pages/orderings where supported; report selection bias. A score-sorted first page is not a random sample.
- Compare original creation age and repeated view/favorite deltas within comparable products. A lifetime count divided by age is at most a rough lifetime average, not current sales velocity.
- Halloween is strongly seasonal. Observations close to October 31 cannot establish year-round or next-year demand; label the time window and plan pre-season comparisons.
- Use cheap publication experiments to learn conversion and acquisition economics. Do not wait for marketplace observations to prove demand; report uncertainty and keep each experiment inside its budget.

## Implemented versus planned

Implemented now: synthetic workflow tests plus real-cohort normalization, digital filtering, deduplication, shop concentration, currency-specific price distributions, engagement/missingness, age buckets, normalized tags/title overlap and automation flags. Explicit cost scenarios include manual time; expected receipts remain unknown unless supplied. Durable orchestration and guarded live collection/replay remain available.

Planned: published-product performance ingestion, automated experiment budgets and stop rules, snapshots/deltas, semantic clustering, model extraction/reasoning only where worth the cost, and calibrated portfolio economics. No production digital assets or images are generated in this increment.
