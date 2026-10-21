# Experiment operating policy — v2

## Objective and accounting

Maximize expected recurring receipts relative to total operating cost at the portfolio level. Track expected surplus too, within both money and human-time budgets. Neither revenue, product count, nor cost per product is a stand-alone objective.

Use a stated planning horizon and currency. Initially report 12-month scenarios with monthly seasonal timing rather than extrapolating October sales across a whole year. Receipt expectations are scenarios or estimates learned from published outcomes, not a transformation of Etsy favorite/view counts.

Record actual cost separately from allocated shared cost:

- AI/provider usage: model, prompt version, tokens, calls, published pricing/check date and billable amount or unknown. Free quota still consumes a finite resource.
- Compute: measured runtime/resources and a configured cost basis. Wall time alone is not a monetary cost.
- Publication, platform and payment fees, refunds, promotion and support.
- Human involvement: batch setup, exception review, maintenance and support minutes multiplied by a configured hourly value. Unknown time/value cannot be entered as zero.
- Shared research and automation development allocated across the portfolio; report allocation sensitivity.

The Java `ExperimentEconomics` calculator evaluates explicit same-currency, same-horizon inputs including valued human minutes. It leaves receipts/surplus/ratio unknown when receipts are missing and avoids an infinite ratio at zero cost. It is a scenario calculator, not a learned forecast.

## Proportional research and automation

Set batch spending, API/token, compute and manual-minute budgets before execution. Publication and asset generation are subsequent capabilities; the current increment includes bounded text-model interpretation but stops before producing or publishing products.

A small experiment can be worth running at low confidence and low success probability. Choose further research only when its expected effect on the decision exceeds its cost and delay; otherwise run the bounded experiment. Do not use fixed listing/shop counts or repeat observation windows as mandatory publication prerequisites.

Automate standard downloadable formats first, reuse vetted templates, and batch exceptions. Signals such as personalization/customization indicate possible work, not guaranteed support cost. Route uncertain template automation for reusable batch-level review instead of repeatedly asking for individual product approvals. Unsupported product claims or asset-rights concerns remain exceptions to resolve; a model cannot remove those by asserting confidence.

## Learning from published performance

For each future experiment record:

- Product/template and hypothesis, version, publication timestamp, niche/season and exploration allocation.
- Actual exposure/search impressions when available, views/visits, orders, receipts, refunds and ongoing fees. Record absent metrics as unknown.
- Actual AI/API usage, compute and manual intervention minutes, including failures/retries.
- A predefined observation window and budget cap; pause or stop at the cap. Do not assume every product requires long research or continued maintenance.
- Learning value: which reusable template/automation policy improves future expected receipts per total cost.

Compare rates per exposure and elapsed seasonal window, not raw sales alone. Distinguish no exposure from exposed products that do not convert. Review aggregates at planned intervals (for example weekly), automatically applying configured budget/expiry rules. Product-specific human attention should be exceptional.

Allocate most spending to templates with promising observed cost-adjusted outcomes while reserving a bounded exploration budget. A low hit rate is acceptable if the portfolio yields attractive recurring receipts after all operating costs. Adjust using uncertainty-aware estimates; do not claim statistical certainty from a few sales.

## Current deterministic output

The 1000-listing run is a pipeline/initial-screening exercise. It reports digital share, money distributions by currency, shop concentration, tag/title overlap, missingness, lifetime engagement, age context and rough automation queues. It makes no model calls and estimates no recurring revenue from these fields.

Missing market proof is disclosed, not used as an automatic publication veto. The legacy simulation report's `INSUFFICIENT_EVIDENCE` label describes synthetic input quality only; it is not a publication gate. The initial queue prioritizes automation hints and uses stable IDs for ties, not claimed financial return.
