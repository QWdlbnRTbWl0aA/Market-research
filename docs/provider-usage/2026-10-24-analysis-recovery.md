# Continuous investigation recovery

The investigation paused because assessment responses cited listings outside the current cohort and malformed existing-opportunity IDs. Repeating the unchanged request did not reliably resolve this.

Explicit analysis resume now adds validation feedback with the exact allowed IDs and the rejected references. OpenAI assessment response schemas additionally constrain listing citations and existing-opportunity references to the actual call's cohort/catalog. Review uses its own supplied cohort. Local validation remains mandatory; no response is silently repaired or accepted with missing support. Recovery introduces no automatic paid retry loop or spending authorization.

Both changes passed the full `mvn verify` reactor, including HTTP resume, restart recovery, phase preservation, append-only rejected-output retention, and schema regression tests. Tests use mocked providers.

The analysis service was stopped for an offline database backup and independently restarted. Before resuming, the original request, draft, call-output history, usage and discovery results matched the saved snapshots. After live recovery, the original request, earlier output prefix, historical allowance-call prefix, unchanged allowance cap, and all previously completed product definitions were verified intact. Private backups and operational snapshots remain outside Git.

The previously blocked batch completed generation and review on the same job resource. At the recovery checkpoint, the investigation was ACTIVE with 1,567 listings, 692 analyzed listings, 46 shop profiles and 14 completed briefs. OpenAI cumulative commitment was USD 2.486841 against the unchanged USD 4 allowance; subsequent work continues against its remaining balance. These are checkpoint values, not final investigation totals.

Only the analysis service needed restarting. The viewer, other services and operational SMTP reporting remain active. Overall investigation blocking on a future child-job failure is unchanged; broader worker-isolation changes are outside this recovery patch. New safety/provider failures still stop visibly rather than pretending research was exhausted.
