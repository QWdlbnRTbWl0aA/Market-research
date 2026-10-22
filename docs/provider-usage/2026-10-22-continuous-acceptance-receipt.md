# Live acceptance completion receipt

- Main run `145174d1-aa96-4957-b671-63be450f1235`, iteration 1, reached WATCHING after explicit bounded sampling and complete archival.
- Final additional OpenAI allowance: ACTIVE, USD 2 cap, USD 0.81639075 settled across 40 calls, zero uncertain reservations. USD 1.18360925 remains.
- Etsy: 46 main-run requests including probes, 58 with startup pilots. All 267 directly captured baseline timestamps remained in the final 313-entry rolling ledger.
- Historical OpenAI and comparison spending ledgers remained byte-identical. Gemini retained prior events and appended one free pilot event; no historical ledger was reset.
- Final Maven verification: 121 tests passed, no failures/errors/skips.
- Terminal restart and duplicate initial submission preserved run identity, counts, weekly due time and archive content without provider calls. A 44-second live idle check left the orchestrator database and allowance file byte-identical.
- The full [acceptance report](../research/2026-10-22-live-continuous-acceptance.md) was sent once through configured SMTP to the user-requested recipient. The sender returned SMTP_ACCEPTED; inbox delivery is not independently observable.
- Test services were stopped after verification; durable state and the weekly due time remain. Automatic notification-outbox SMTP was not enabled or claimed as verified.

This receipt records technical observations and local token-cost calculations, not an account invoice or a certification of product readiness. See the report's separate API_PIPELINE and AGENT_REVIEW sections for business-quality limitations.
