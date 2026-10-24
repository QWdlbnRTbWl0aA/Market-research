# Search expansion and worker isolation recovery

The full investigation stopped at 100 accepted search phrases. The former expansion-limit exception marked the entire investigation BLOCKED, preventing pagination, business analysis and product definition from continuing.

Query-count and depth limits now prevent only further expansion. Existing phrases retain their page pointers; already-submitted refinement results are consumed, and unaccepted suggestions are preserved as `deferredQueries`. At a cap, a phrase with no refinement job skips buying one and continues its own pagination. Partial expansion does not mark the parent pool superseded. Query metadata exposes `expansionStopReason`. After accepted coverage and downstream work drain, `BOUNDED_COMPLETE` permits archival without claiming natural market exhaustion.

A blocked asynchronous child job now durably pauses only its owning worker through `workerErrors`. Other workers continue. Explicit resume reuses saved job resources and clears worker pauses; uncertain provider effects are never automatically retried. Paused workers do not poll or write idle aggregates, failed cohorts never count as processed, cancellation remains available, and unresolved work prevents archival.

Recovery also fixes repeated subtraction of semantic exclusions when a legacy saved refinement result is replayed. Usable counts are recomputed from the frozen first-page sample.

Regression coverage includes capped pagination through analysis, briefs and complete source archival; both query-count and depth limits; legacy blocked-run recovery; persisted deferred suggestions and original evidence; idempotent exclusions; durable worker isolation; explicit retry of the same child job; and cancellation of paused workers. Maven verification uses fixture providers, without paid calls.

Operational recovery uses a stopped-orchestrator database backup plus private snapshots of the investigation and historical provider ledgers. Deployment preserves evidence, processed/excluded IDs, opportunities, briefs, requests, jobs and pagination pointers. No provider request or spending cap is raised. SMTP reporting now displays paused workers and no longer labels an unrelated failure as an OpenAI quota wait merely because its balance is low.

Page safety limits and transport/unknown orchestration failures remain visible investigation stops. Search expansion limits and asynchronous child-job failures have the independent behavior described above; this patch is not a general retry of arbitrary external effects.

Deployment verification passed the full `mvn verify` reactor, including 15 workflow integration tests. The stopped database and backup had matching checksums. After restart, investigation results matched the saved snapshot apart from new default metadata; the entire named allowance file was unchanged. After resume, original listing identities, all 100 accepted phrases, 16 completed briefs and the historical allowance-call prefix were verified preserved.

The live investigation remained ACTIVE and fetched 41 more listings, reaching 2,932. Its next business draft completed but review was refused locally before a provider request: even the request-body lower bound, excluding response-schema overhead, required a USD 0.2504055 reservation while USD 0.243352 remained. The ANALYSIS worker consequently paused independently. Search continued processing accepted branches and applying expansion limits, rather than inheriting that pause. The saved draft is pending review, not counted as analyzed. No allowance was increased or historical call changed. A stage-status SMTP report was accepted; scheduled reporting remains active.
