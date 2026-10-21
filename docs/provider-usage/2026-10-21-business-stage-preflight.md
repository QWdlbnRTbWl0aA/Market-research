# Business stage provider preflight

Checked 2026-10-21 19:39 UTC before generation. No keys, account IDs or machine identifiers are recorded.

- Gemini model metadata returned 200 for gemini-2.5-flash-lite. It does not expose the account's billing tier, remaining free quota or current account usage. Free-tier entitlement is unverified with the supplied API key alone. Gemini generation remains disabled unless a free-tier account is explicitly confirmed.
- OpenAI model metadata returned 200 for gpt-5.4-mini. The organization usage endpoint returned 403, so account-wide usage remains unknown. No Admin API credential is configured.
- The user authorized USD 2 for OpenAI development/testing of this stage. A persistent stage ledger reserves a worst-case token charge before every request. Failed/uncertain requests retain their reservation. Cleanup cannot reset spending or rate counters.
- Published standard gpt-5.4-mini rates checked: USD 0.75/M input and USD 4.50/M output tokens, including reasoning output. Each call is capped at 8000 completion tokens and a conservative input byte/token bound.
- No image generation, web-search grounding or other paid model tools are enabled.
- Source: [OpenAI model pricing](https://developers.openai.com/api/docs/models/gpt-5.4-mini), [Admin APIs](https://developers.openai.com/api/docs/guides/admin-apis), [Gemini limits](https://ai.google.dev/gemini-api/docs/rate-limits), [Gemini pricing](https://ai.google.dev/gemini-api/docs/pricing).

Observed stage usage is recorded in [the run report](../research/2026-10-21-business-analysis-run.md): 31 OpenAI generation calls and USD 0.3695175 at published token rates, with no uncertain reservations; no Gemini/image generation. These are local token-cost calculations, not a provider invoice. Account-wide usage is not inferred from the local ledger.
