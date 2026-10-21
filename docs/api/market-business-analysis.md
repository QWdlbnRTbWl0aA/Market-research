# Market refinement and business analysis

Both endpoints belong to analysis-service. They perform bounded deterministic computation only: no Etsy/model calls or long HTTP waits. Slow live coordination currently uses the explicit research CLI, not the demonstration orchestrator.

## POST /v1/refinement-plans

Contract: `MarketResearch.RefinementRequest` → `RefinementPlan` in `analysis-contract`.

Fields: `seed`, nonnegative `resultCount`, positive `maxPages`, raw `listings`, semantic `groups` (`id`, `label`, literal `query`) and `assignments` (`listingId`, `groupId`, boolean `usable`).

Returns estimated pages at 100 results/page, sampled/digital/usable counts, usable listings per sampled request and at most three safe child queries. It signals whether the parent needs splitting; child counts require real probes. Queries are bounded literal keyword phrases containing the seed anchor, not boolean expressions or descriptive lists. Counts do not measure demand, and child counts must not be summed without deduplication. Invalid input returns 400.

## POST /v1/business-analyses

Contract: `MarketResearch.BusinessRequest` → `BusinessReport`.

Input: UTC `observedAt`, positive decimal `targetMonthlyUsd`, up to 1,000 raw `listings`, up to ten `groups`, validated `assignments`, public `shops`, sanitized listing `reviews` keyed by listing ID, and optional `modelInterpretation` (empty object for deterministic evidence only).

Output per group: usable listing/distinct shop counts, USD median price, integer units needed to reach target, independently reviewed shop count, observed lifetime shop transactions, nullable verified monthly revenue (currently always unknown), evidence band and sampled comparables. Comparables carry listing/shop IDs, USD price, price-specific target units, recent review count, review-sample completeness, and explicitly illustrative 10%/2% review-rate receipt scenarios. Other currencies remain unknown, not silently converted.

An optional interpretation must contain only `recommendations`, one per selected group at most, with exact fields: `groupId`, `decision` (TEST/DEFER), `confidence` (LOW/MEDIUM), `rationale`, `differentiator`, `automationPlan`, `risks`, `citedListingIds`, `citedShopIds`. Unknown fields/IDs, duplicate assignments/groups and cross-group listing citations return 400. A MEDIUM claim without two independently reviewed shops is deterministically capped at LOW. This does not veto an inexpensive experiment.

Shop lifetime sales are not monthly group revenue. Review-rate scenarios are neither forecasts nor statistical bounds, and current competitor observations are not a new seller's expected sales. No calibrated probability is asserted. Halloween cannot be uniformly annualized.
