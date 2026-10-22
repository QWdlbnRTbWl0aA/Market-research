package local.myhome.marketresearch.orchestrator.discovery;

import java.time.*;
import java.util.*;
import local.myhome.marketresearch.contracts.analysis.Discovery;
import local.myhome.marketresearch.contracts.content.ProductDefinition;
import org.springframework.web.client.RestClient;

/** Idempotent content handoff and immutable, evidence-complete archive outbox. */
final class DiscoveryDelivery {
    private final DiscoveryStore store;
    private final DiscoveryNotices notices;
    private final RestClient analysis, content, archive;

    DiscoveryDelivery(DiscoveryStore store, DiscoveryNotices notices, RestClient analysis, RestClient content, RestClient archive) {
        this.store = store;
        this.notices = notices;
        this.analysis = analysis;
        this.content = content;
        this.archive = archive;
    }

    boolean advance(DiscoveryState state) {
        if ("CANCELLATION_REQUESTED".equals(state.status)) {
            cancelJobs(state);
            state.status = "CANCELLED";
            return true;
        }
        if (!"ACTIVE".equals(state.status)) return false;
        for (var handoff : state.handoffs) {
            if ("SUCCEEDED".equals(handoff.status)) continue;
            if (handoff.job == null) {
                var job = content.post().uri("/v1/product-definition-jobs").header("Idempotency-Key", handoff.key)
                    .body(handoff.request).retrieve().body(ProductDefinition.View.class);
                handoff.job = Objects.requireNonNull(job).id();
                handoff.status = "DEFINING";
                return true;
            }
            if (handoff.nextAttemptAt > System.currentTimeMillis()) return false;
            var job = content.get().uri("/v1/product-definition-jobs/" + handoff.job).retrieve().body(ProductDefinition.View.class);
            DiscoveryFlow.requireJob(job.status());
            if (!"SUCCEEDED".equals(job.status())) {
                boolean changed = !handoff.status.equals(job.status()) || handoff.nextAttemptAt != job.nextAttemptAt();
                handoff.status = job.status();
                handoff.nextAttemptAt = job.nextAttemptAt();
                return changed;
            }
            handoff.brief = Objects.requireNonNull(job.brief());
            handoff.status = "SUCCEEDED";
            return true;
        }
        boolean pendingListings = state.listings.keySet().stream()
            .anyMatch(id -> !state.processed.contains(id) && !state.excluded.contains(id));
        if (!state.searchComplete() || pendingListings || state.reassessmentPending
                || state.batches.stream().anyMatch(batch -> !"SUCCEEDED".equals(batch.status))) return false;
        if (state.finishedAt == null) state.finishedAt = Instant.now().toString();
        if (!state.archived) {
            var payload = store.freezeArchive(state, () -> archivePayload(state));
            archive.post().uri("/v1/discovery-archives").body(payload).retrieve().toBodilessEntity();
            state.archived = true;
            return true;
        }
        if (!state.notified) {
            notices.queue(state.id + ":complete:" + state.iteration, "Research ready",
                "Discovery " + state.id + " completed its current sampled scope. " + state.handoffs.size()
                + " product briefs are available. Weekly deterministic monitoring is scheduled; deep reanalysis requires your approval.");
            state.notified = true;
        }
        state.status = "WATCHING";
        state.error = null;
        state.changes.clear();
        state.nextWatch = System.currentTimeMillis() + Duration.ofDays(7).toMillis();
        return true;
    }

    private void cancelJobs(DiscoveryState state) {
        for (var batch : state.batches) if (batch.job != null && !"SUCCEEDED".equals(batch.status))
            analysis.delete().uri("/v1/discovery-jobs/" + batch.job).retrieve().toBodilessEntity();
        for (var branch : state.queries) if (branch.refinementJob != null && "REFINING".equals(branch.status))
            analysis.delete().uri("/v1/discovery-jobs/" + branch.refinementJob).retrieve().toBodilessEntity();
        for (var handoff : state.handoffs) if (handoff.job != null && !"SUCCEEDED".equals(handoff.status))
            content.delete().uri("/v1/product-definition-jobs/" + handoff.job).retrieve().toBodilessEntity();
    }

    private Map<String,Object> archivePayload(DiscoveryState state) {
        var payload = snapshot(state);
        payload.put("sourcePages", store.sourcePages(state));
        var analysisEvidence = new LinkedHashMap<String,Object>();
        for (var branch : state.queries) if (branch.refinementJob != null)
            analysisEvidence.put(branch.refinementJob.toString(),
                analysis.get().uri("/v1/discovery-jobs/" + branch.refinementJob + "/evidence").retrieve().body(Map.class));
        for (var batch : state.batches) if (batch.job != null)
            analysisEvidence.put(batch.job.toString(),
                analysis.get().uri("/v1/discovery-jobs/" + batch.job + "/evidence").retrieve().body(Map.class));
        var contentEvidence = new LinkedHashMap<String,Object>();
        for (var handoff : state.handoffs) if (handoff.job != null)
            contentEvidence.put(handoff.job.toString(),
                content.get().uri("/v1/product-definition-jobs/" + handoff.job + "/evidence").retrieve().body(Map.class));
        payload.put("analysisEvidence", analysisEvidence);
        payload.put("contentEvidence", contentEvidence);
        return payload;
    }

    static Map<String,Object> snapshot(DiscoveryState state) {
        var result = new LinkedHashMap<String,Object>();
        result.put("runId", state.id);
        result.put("iteration", state.iteration);
        result.put("query", state.query);
        result.put("searchStatus", state.searchStatus);
        result.put("samplingPageBudget", state.samplingPageBudget);
        result.put("protocol", Discovery.VERSION);
        result.put("finishedAt", state.finishedAt);
        result.put("queries", state.queries);
        result.put("assessments", state.assessments);
        result.put("productDefinitions", state.handoffs);
        result.put("opportunities", state.opportunities);
        result.put("listings", state.listings);
        result.put("shops", state.shops);
        result.put("reviews", state.reviews);
        result.put("listingCount", state.listings.size());
        result.put("shopCount", state.shops.size());
        result.put("analysisOrigin", "API_PIPELINE");
        result.put("trendContext", state.changes);
        result.put("excludedListingIds", state.excluded);
        result.put("notice", "Sampled coverage, not a census, verified revenue or proof of demand.");
        return result;
    }
}
