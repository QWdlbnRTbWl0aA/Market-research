# ADR 0007: Bounded refinement and evidence-based modest-revenue experiments

Status: accepted for the Halloween research increment, 2026-10-21.

## Decision

Use Java/Spring deterministic analysis endpoints plus an explicit Java CLI for budgeted provider coordination. Preserve service boundaries and the orchestrator's ownership of production workflow; do not misrepresent the existing synthetic orchestration as a live research workflow. Integrating this research command into durable asynchronous jobs remains subsequent work.

Refinement gets a small fixed effort budget: one 60-listing seed, three focused 100-listing samples and, for pools above 20 pages, at most three narrower samples. Do not download thousands of pages. Record actual child counts, semantic usable yield and incremental deduplicated yield. Oversized children remaining at the bound are disclosed rather than recursively enumerated. `limit=1` remains useful for count-only discovery; a sample-bearing probe uses 100 to maximize evidence per request.

Models handle semantic relevance/grouping, literal query suggestions and business interpretation. Deterministic code handles digital filtering, duplicate IDs, evidence validation, money, units needed for USD 30/month, review windows and cost arithmetic. One bounded missing-classification repair is permitted; unknown IDs, duplicate IDs or a second omission stop the run. There is no growing keyword/regex taxonomy.

Enrich two distinct shops per group near the sample's engagement median, avoiding a hit-only sample. Prefer USD comparables to avoid inventing FX rates. Public shop lifetime transaction counters are contextual, not category-specific or monthly revenue. Recent listing reviews are purchase proxies with reporting lag and unknown review propensity. Show 2% and 10% review-rate scenarios explicitly as uncalibrated assumptions. Zero reviews means unknown sales, not zero. Do not claim calibrated success probabilities or verified competitor revenue.

Reasoning produces cited TEST/DEFER hypotheses, differentiation, automation and risks. Confidence cannot exceed LOW without purchase proxies in at least two independent shops; this is a reporting cap, never a publication gate. Cheap experiments can proceed at LOW confidence. Actual published-product outcomes must eventually calibrate revenue expectations and cost-adjusted portfolio allocation.

## Operational constraints

Fresh live runs clean only known research output directories. Preserve all operational ledgers, fixtures and documentation. Count uncertain requests before network dispatch. Bound Etsy traffic locally and reconcile response headers. Persist worst-case OpenAI token reservations under a stage-wide USD 2 ceiling; settle against reported usage, retaining uncertain reservations on failure. Verify metadata and disclose unavailable account-wide usage. Prefer confirmed free Gemini capacity, never assume the supplied key implies a free account. No images, production scheduling or publishing are authorized here.

## Alternatives and consequences

Exhaustive search pagination and extensive pre-production validation cost more than these cheap experiments justify. Pure deterministic semantics would require a brittle filter taxonomy; pure model financial analysis risks invented arithmetic/revenue. The split keeps facts reproducible while using models for interpretation. Samples remain score-ranked and seasonal, not representative; two shops cannot establish a reliable probability. Daily token forecasts are scenarios for the measured workload, and exclude unknown compute/human cost rather than pretending they are zero.
