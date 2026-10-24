# ChatGPT connection deployment

The operator selected ChatGPT plan usage as the default for refinement, business analysis and review,
with OpenAI API fallback. This implements that preference through the application's own supported
Sign in with ChatGPT registration and public Responses transport. Product definition is unchanged.

Validation: full `mvn verify` passed, together with seven buildless frontend tests. Coverage includes real
JWT signature validation, state/nonce and callback replay checks, private session storage, rotating token
refresh, model discovery, response completion, retained uncertain streams, explicit subscription-limit
rejection, named-allowance fallback and no fallback after uncertain inference. HTTP integration verifies
that connection controls neither expose tokens nor launch research. Fixtures make no live model calls.

Before deployment, private research, job-evidence and ledger snapshots were taken. Orchestrator and
analysis services were stopped for offline database backups with matching checksums. After restart,
research results, the saved pending analysis job and the complete named API allowance file matched the
snapshots exactly. No cache, usage record, source data or completed brief was erased.

The live connection endpoint reports primary `chatgpt`, API fallback enabled and `DISCONNECTED`, ready
for the operator's browser authorization. Services and SMTP reporting remain running. No research job
was resumed automatically during deployment, and no live inference was performed. API commitment stays
USD 3.756648 of the existing USD 4 cap. ChatGPT eligibility, model availability and live inference still
require user sign-in and a bounded verification run; plan usage is not assumed unlimited or cost-free.

The remote-browser guide uses the documented loopback callback through an operator-managed SSH forward.
Credential files and all machine/network identifiers remain outside the repository.
