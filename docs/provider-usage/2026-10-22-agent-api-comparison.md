# Provider usage — frozen agent/API comparison

Assignment date: 2026-10-22 local; network activity occurred 2026-10-21 UTC. No credentials, account/project identifiers or machine/network details are recorded.

## Etsy

The fresh corpus contains 5,000 unique digital listings and 50 fetched shop profiles. Requests: one quota probe, 52 search pages, 50 shop profiles, 50 shop-review samples and 20 targeted listing-review samples: **173 total**. The same preserved rolling ledger now contains 271 calls, including 98 earlier calls. Request spacing was at least two seconds. The larger assignment invocation used a 400-call local rolling-day cap and 250-new-call bound, while reconciling every response header and preserving a reserve. Final headers reported 5 requests/second, 5,000/day and 4,729 remaining. No HTTP retries, writes or purchases occurred.

## Gemini

The user confirmed the account has no payment method. Preflight recorded that confirmation, rather than treating inaccessible account-wide counters as a reason not to try text generation.

- Model metadata: 200 for both the old `gemini-2.5-flash-lite` and replacement `gemini-3.5-flash-lite`.
- Old model generation: 404. Metadata/listing a model did not establish generation access. Google's current [model guidance](https://ai.google.dev/gemini-api/docs/models) documents restricted legacy 2.5 access and recommends current models for new projects; this is consistent with, not proof of, the particular account's failure cause.
- The replacement text API generated answers successfully. Its [published free-tier pricing](https://ai.google.dev/gemini-api/docs/pricing) is free of token charges. No billing configuration was changed, no paid fallback or image endpoint used. Billing/account-wide usage/remaining provider quota remain unavailable, not inferred from metadata.
- Local guard: twelve generation attempts/Pacific day, at least 61 seconds between dispatches, attempts persisted before network, 429 cooldown and no automatic provider retry/fallback. This is a local development ceiling, not a declaration of the provider's quota. See [provider limit documentation](https://ai.google.dev/gemini-api/docs/rate-limits).
- Eleven attempts were counted: the old-model 404, an early answer with an invalid citation, an early response-handling failure, and eight requests in the completed comparison (five normal calls plus three grounding corrections). The early response-handling failure did not retain an envelope/usage; its token use is unknown, though the attempt is preserved. Later code retains private envelopes and records available usage before parsing the answer.
- Completed comparison: **822,099 input tokens, 9,373 output tokens, 831,472 total**, including 229,267 reported cached input tokens. Expected token charge is USD 0 on the user-confirmed free account, not an independently retrieved invoice. One additional pre-completion successful diagnostic answer reported 212,597 input / 1,532 output tokens; do not confuse this with the completed-run totals.

## OpenAI

All source inspection and the agent baseline commit `86fc0bf` preceded this assignment's first OpenAI API call. Non-generative preflight returned model metadata 200 and organization usage 403; account-wide usage remains unknown without an appropriate Admin credential.

The [gpt-5.4-mini standard rates](https://developers.openai.com/api/docs/models/gpt-5.4-mini) were checked: USD 0.75/M input and USD 4.50/M output, including reasoning. The run used text-only Chat Completions, low reasoning effort, JSON output, no model tools, `store=false` and an 8,000-output-token cap. No cache discount was assumed in cost arithmetic.

The persistent comparison budget started at USD 0.3695175 committed. The maximum additional authorization is USD 1.63, yielding an effective USD 1.9995175 ceiling within the original USD 2 total stage cap. It is not renewed per provider/run/retry. Conservative input-byte/output reservations precede network dispatch; uncertain failures remain reserved.

Completed run: **six calls, 666,606 input tokens, 11,214 output tokens, USD 0.5504175** at list prices. All settled, no uncertain OpenAI reservations. Total stage commitment is USD 0.919935, leaving **USD 1.0795825** of this assignment's additional allowance. These are local token-cost calculations, not an invoice. No additional OpenAI generation calls were made after that successful run.
