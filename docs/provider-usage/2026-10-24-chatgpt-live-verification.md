# ChatGPT live analysis verification

Preflight: the operator completed the app-owned sign-in and selected `gpt-6.1-sol`.
The live connection reports CONNECTED, granted ChatGPT plan-use permission, primary `chatgpt` and
guarded API fallback enabled. Account model discovery was completed through the connection UI.
The adapter's local cap is 100 ChatGPT attempts per UTC day. Account-side remaining plan usage and
credits are not exposed by this adapter; no unlimited or zero-cost entitlement is inferred.

Before the bounded test, the existing API allowance remains USD 4, committed USD 3.756648 across
151 historical calls. No increase or reset is authorized. API fallback retains the same reservation
guard. Etsy is waiting on its existing daily quota and is not fetched by this test.

The test explicitly resumes only the saved blocked analysis child job, retaining its request, draft,
output history and REVIEW phase. The orchestrator's ANALYSIS worker remains paused until the child
result has been inspected, preventing an unverified provider from consuming the rest of the queue.
Private snapshots are kept outside Git. Agent review of the output remains distinct from the
application/model-generated business assessment.

The first review call completed with 66,889 input tokens and 2,591 output tokens reported under ChatGPT
plan usage. The initial adapter failed to extract an answer after completion; no API fallback was made,
the paid allowance was unchanged and the draft remained pending. The streaming adapter is being corrected
to retain finalized message/text events while still requiring confirmed whole-response completion. Final
messages and stream event types are now privately retained for audit; incomplete output is never accepted.

After the streaming correction and full Maven verification, the same saved review succeeded on
`gpt-6.1-sol`: 66,894 input tokens and 2,751 output tokens. Finalized message events supplied the answer
and the whole-response completion was confirmed. Two ChatGPT requests have therefore been made in this
bounded verification, including the unsuccessful initial extraction. Neither used paid API fallback.

Agent review of the pipeline result: the model removed duplicate recommendations, distinguished same-shop
evidence from independent sellers, treated sales/revenue as unknown, and retained two specific low-confidence
experiments. One concerns an adjacent religious audience and is explicitly labeled accordingly, rather than
being presented as a Jewish product. This is a quality check, not independent demand validation.

Parent resume initially failed against a process whose packaged application had been rebuilt in place.
Restarting from an immutable runtime artifact restored resume. Domain services now run private versioned
copies, so future Maven builds do not alter their live classpaths. Database backups and saved-job snapshots
were retained. The parent consumed the successful review and both new product briefs completed, increasing
this run from 16 to 18 briefs and from 1,189 to 1,289 analyzed listings. Product definition used its existing
API route: four calls, USD 0.05961675 incremental commitment; API cumulative commitment is USD 3.81626475.

A scheduling check then found that the global Etsy retry pointer also delayed creation of cohorts that
needed no new Etsy calls. The orchestrator now defers only actual missing-evidence requests. Regression tests
cover saved evidence flowing through briefs during a search quota wait, genuinely missing evidence idling
without calls or writes, and migration of old scheduler projections while preserving the full saved state.
