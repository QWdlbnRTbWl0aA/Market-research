# ADR 0008: Separate agent judgment from an API product-reasoning pipeline

Status: accepted for the evaluation increment, 2026-10-22.

## Decision and sequence

Collect a fresh large corpus using existing guarded Java adapters before modifying source. Freeze the interactive agent's specific product recommendations, reasoning summary, evidence coverage and limitations in Git before source changes or OpenAI API calls. Treat that baseline as a fallible reference, not truth. Keep it out of provider inputs.

Implement a provider-neutral textual reasoning protocol:

1. Three scans cover all 5,000 compact listing records in shards of at most 1,667, with the same 50 public shop profiles and bounded review context. Listing prices retain amount/divisor/currency; no FX is invented.
2. The model nominates concrete candidate concepts with evidence IDs. Code retrieves original descriptions and fields for the cited listings, without business scoring or reranking.
3. The model compares candidates and defines up to three specific products: audience/use, files/quantities/dimensions, proposed price, automation/QA, manual-work assumptions, counterevidence, uncertainty and bounded publication experiments.
4. A fresh request to the same configured model critiques the draft against retrieved sources and returns a revised report. This is self-review, not an independent judge or verified demand assessment.

Code validates JSON shape, source identities, simple target arithmetic, bounded payloads and budgets. It makes no business recommendations, determines no probability/confidence thresholds and computes no opportunity scores. The old deterministic business endpoint remains available for historical work; it is not called by this comparison pipeline.

Allow one bounded correction per stage for malformed answers, invalid citations or schema/target-arithmetic failures. Preserve both returned and corrected outputs; stop if correction still fails. Do not retry HTTP/provider/quota failures automatically. Known shop IDs in listing records prove relationships, not availability of shop profiles or financial counters.

Use explicit provider selection: Gemini runs cannot fall through to OpenAI. Both providers receive the same canonical source projections and generic objective, never agent conclusions or each other's answers. Scan input hashes must agree for the same protocol/corpus. Later retrieval differs because each model chooses its own sources; disclose this, and distinguish a pipeline comparison from an identical-prompt single-response benchmark.

## Guardrails and ownership

This is an explicit Java/Spring evaluation CLI, not a new synchronous service endpoint or integration with the durable production orchestrator. Long-running production orchestration remains the research-orchestrator's responsibility. No broker, publishing or asset/image generation is introduced.

Keep the existing USD 2 total OpenAI stage ledger. Persist a comparison ceiling once at starting committed amount + USD 1.63, capped at USD 2; retries/restarts cannot allocate another allowance. Reserve conservative input-byte/output-token costs before dispatch, retain uncertain reservations, settle known successful usage. Comparison requests allow at most 750,000 input bytes and 8,000 output tokens, without weakening the old research command's smaller request limit.

The user confirmed Gemini has no payment method. Enable its free text route explicitly. For this bounded development comparison, enforce at most twelve generation attempts/Pacific day, at least 61 seconds spacing, persisted attempts and provider cooldown handling. Twelve covers five normal calls, at most five corrections and initial availability diagnostics. This is our local ceiling, not a claim about the account's provider quota; 429 stops rather than relying on enforcement as pacing. No billing setup is performed.

Model metadata alone does not prove generation access. Record actual attempts and non-secret error status/quota fields, never provider error messages containing project/account identifiers. Default Gemini text model is now the documented free-capable `gemini-3.5-flash-lite`; the former 2.5 model returned generation 404 despite metadata 200. Capture private model envelopes to diagnose format failures, omit thought parts from answer parsing, and preserve usage before parsing answer JSON.

## Consequences and limits

The user receives separately labeled agent conclusions and API-pipeline conclusions. The application-accepted report means structurally grounded output, not verified factual interpretation or revenue. A manual comparison must still check claims against source text, price/seasonality handling, useful specificity and human-work assumptions. Agreement among AI systems is not evidence of actual demand.

The agent inspected selected cases and aggregate context; API scans systematically cover more titles, but descriptions are retrieved selectively. Neither is an exhaustive audit of all descriptions/reviews. Single runs, different model configurations, correction feedback and source retrieval prevent broad claims that one provider is intrinsically better. Retain failures and latency/cost, not just successful final answers.
