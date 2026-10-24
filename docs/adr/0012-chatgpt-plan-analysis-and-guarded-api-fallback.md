# ChatGPT plan analysis with guarded API fallback

Status: accepted, 2026-10-24, following the operator's explicit provider preference.

Refinement, business analysis, reassessment and review prefer the application's own Sign in with ChatGPT
connection. Eligible ChatGPT plan usage is authorized through the documented OAuth flow, rather than by
reading browser/Codex credentials or automating the ChatGPT website. The Java provider adapter calls the
public streaming Responses endpoint. Product definition retains its existing provider route.

The alternative was wrapping Codex app-server or a separate SDK process. Direct Java integration preserves
the existing Spring/Maven service stack and keeps credential ownership and model-call auditing explicit.
It requires maintaining OAuth lifecycle, signed-ID-token validation and stream-completion handling.

Account consent, model discovery and provider calls are distinct operations. A loopback callback plus an
operator-managed SSH forward supports sign-in from a remote browser. Only connection state and model
choices reach the UI. Protected credentials live outside Git and service databases; refresh is serialized
across processes. Account model choices are discovered rather than assumed.

Only definitive pre-request unavailability, explicit authorization rejection or known usage-limit rejection
permits API fallback. Fallback still requires paid-enabled, an existing named allowance and its conservative
reservation guard. Incomplete streams, malformed answers and unknown errors pause the job without buying
another inference. ChatGPT plan usage has separate call history and reported token counts, not fabricated
API dollar costs; API historical ledgers are unchanged. Account-side remaining usage is not assumed known.

Existing source evidence, completed assessments, opportunities, briefs and saved job phases remain valid.
Switching providers does not erase caches or reset money. A resumed review may intentionally review a
previous provider's saved draft; stage inputs, outputs and provider usage retain that provenance.

Official contracts: [registration](https://developers.openai.com/siwc/token-sharing-open-source/sign-in),
[models and inference](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference),
[preview limitations](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations).
