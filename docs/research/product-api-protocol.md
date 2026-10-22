# Product-level API reasoning protocol

A frozen agent baseline is a separate research artifact, never model input. The previous baseline was deliberately cleared; any new comparison must create a fresh one. The implemented protocol is `ProductReasoning.VERSION`; request files save the actual instructions, evidence and canonical input hash. See [ADR 0008](../adr/0008-agent-baseline-and-model-product-comparison.md) for historical protocol decisions and limitations.

## Run the comparison

From the repository root, build/test with `mvn verify`. Credential arguments are file paths, never key values:

```bash
java -jar tools/research-tools/target/research-tools-0.1.0-SNAPSHOT.jar --research.tools.command=comparison-prepare
java -jar tools/research-tools/target/research-tools-0.1.0-SNAPSHOT.jar --research.tools.command=provider-check --research.models.gemini-free-confirmed=true --research.models.gemini-credentials-file=/path/to/gemini.env --research.models.open-ai-credentials-file=/path/to/openai.env
java -jar tools/research-tools/target/research-tools-0.1.0-SNAPSHOT.jar --research.tools.command=compare --research.tools.live-enabled=true --research.comparison.provider=gemini --research.models.gemini-free-confirmed=true --research.models.gemini-credentials-file=/path/to/gemini.env
java -jar tools/research-tools/target/research-tools-0.1.0-SNAPSHOT.jar --research.tools.command=compare --research.tools.live-enabled=true --research.comparison.provider=openai --research.models.open-ai-credentials-file=/path/to/openai.env
```

`comparison-prepare` is offline. It checks all four source digests against the baseline, reports bounded projections, and initializes/reuses the persistent additional-spend ceiling. `compare` refuses altered evidence before dispatch, requires the live flag, uses no Etsy calls and never cleans the shared corpus between providers. Each attempt gets its own private directory, including failures. No application execution reads the agent's conclusions into a prompt; the baseline file is consulted only for digest verification.

The actual five normal requests are three full-title/compact-metadata scans, product definition using retrieved descriptions, and critical revision. Each stage may get one bounded grounding/format correction. There are no deterministic business scores or category recommendation rules. Files named `*-raw.json` contain parsed model answers before application acceptance, `*-request.json` preserves evidence/instructions, `retrieved-evidence.json` preserves original-source detail, and `accepted-report.json` records the separately labeled pipeline report. Gemini provider envelopes are retained privately under the disposable run output, never in public docs or operational counters.

## Repeat collection deliberately

```bash
java -jar tools/research-tools/target/research-tools-0.1.0-SNAPSHOT.jar --research.tools.command=collect-comparison --research.tools.live-enabled=true --research.tools.credentials-file=/path/to/etsy.env
```

This is a **new research run**, so it deletes previous fetched outputs, including comparison results, after checking local budget capacity. It preserves code, docs/fixtures and provider ledgers. It collects 5,000 unique downloads across ten query families, 50 shops and their review samples, plus 20 targeted listing-review samples, under a 250-new-request bound, 400-request rolling local day cap, two-second spacing, header reconciliation and a reserve. It stops without padding if the sample cannot be reached. The initial frozen run used the unchanged adapters through a Java runtime session; this command codifies that collection afterwards.

Recollection invalidates the existing baseline digests: do not edit the frozen historical baseline to pretend it described new data. A fresh agent analysis/freeze and an explicitly updated baseline reference are required before another comparison corpus is accepted. The current evaluator intentionally targets this named experiment, not an unrestricted production scheduling system.

## Interpretation

The report proposes specific products, not only niches. Price × target-unit arithmetic is not a revenue forecast. Public lifetime shop transactions do not establish per-product or monthly receipts; absent reviews do not establish absent sales. Review excerpts from other items are labeled as shop context, and only fifty shops have profile counters/age. Currency conversion, calibrated probabilities, unknown human-time estimates and year-round Halloween revenue are not invented.

Evaluate factual interpretation, citations, usable product specifications, uncertainty/counterevidence, automation/QA and actual cost/latency/failures. A valid JSON report may still make a poor inference. The same-model critical request is not a guarantee of truth. Published outcomes remain the intended business learning loop.
