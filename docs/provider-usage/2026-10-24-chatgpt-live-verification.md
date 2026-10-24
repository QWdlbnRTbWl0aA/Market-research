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
