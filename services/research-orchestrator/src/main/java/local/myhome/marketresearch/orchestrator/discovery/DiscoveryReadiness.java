package local.myhome.marketresearch.orchestrator.discovery;

import java.util.*;

/** Small scheduling projections. Idle/waiting aggregates are not loaded or serialized by workers. */
final class DiscoveryReadiness {
    static final long IDLE = Long.MAX_VALUE;
    private DiscoveryReadiness() {}

    static long next(DiscoveryState state, String worker) {
        if (state.workerErrors.containsKey(worker)
                && !("DELIVERY".equals(worker) && "CANCELLATION_REQUESTED".equals(state.status))) return IDLE;
        long due = switch (worker) {
            case "WATCH" -> "WATCHING".equals(state.status) ? state.nextWatch : IDLE;
            case "SEARCH" -> search(state);
            case "ANALYSIS" -> analysis(state);
            case "DELIVERY" -> delivery(state);
            default -> throw new IllegalArgumentException("Unknown worker");
        };
        return due == IDLE ? IDLE : Math.max(due, state.retryAt.getOrDefault(worker, 0L));
    }

    static List<String> pending(DiscoveryState state, DiscoveryState.Query branch) {
        return branch.listingIds.stream().filter(id -> !state.processed.contains(id) && !state.excluded.contains(id)).toList();
    }

    private static long search(DiscoveryState state) {
        if (!"ACTIVE".equals(state.status) || !"ACTIVE".equals(state.searchStatus)) return IDLE;
        if (state.samplingPageBudget > 0 && state.queries.stream().mapToInt(branch -> branch.pages).sum() >= state.samplingPageBudget)
            return state.queries.stream().filter(branch -> "REFINING".equals(branch.status) && branch.refinementJob != null)
                .mapToLong(branch -> branch.nextAttemptAt).min().orElse(0);
        var branches=state.queries.stream().filter(branch->!"DONE".equals(branch.status)).toList();
        if(branches.isEmpty())return state.nextSearchAttempt;
        boolean capacity=SearchFrontier.pendingListings(state)<state.pendingListingLimit;
        return branches.stream().filter(branch->branch.stableAnalyses>=2||"REFINING".equals(branch.status)
                ||branch.pages>=state.pageLimit&&pending(state,branch).isEmpty()||capacity&&branch.pages<state.pageLimit)
            .mapToLong(branch->"REFINING".equals(branch.status)?branch.nextAttemptAt:
                branch.stableAnalyses>=2?0:state.nextSearchAttempt).min().orElse(IDLE);
    }

    private static long analysis(DiscoveryState state) {
        if (!"ACTIVE".equals(state.status)) return IDLE;
        var batch = state.batches.stream().filter(value -> !"SUCCEEDED".equals(value.status)).findFirst();
        if (batch.isPresent()) return batch.get().nextAttemptAt;
        if (state.reassessmentPending && state.searchComplete()) return 0;
        boolean eligible=state.queries.stream().anyMatch(branch->pending(state,branch).size()>=state.analysisBatchSize);
        boolean residual=state.listings.keySet().stream().anyMatch(id->!state.processed.contains(id)&&!state.excluded.contains(id));
        boolean pageBoundary=state.queries.stream().filter(branch->!"DONE".equals(branch.status)).allMatch(branch->branch.pages>=state.pageLimit);
        boolean fullMixedCohort=SearchFrontier.pendingListings(state)>=state.analysisBatchSize;
        return eligible||fullMixedCohort||residual&&(state.searchComplete()||pageBoundary)?0:IDLE;
    }

    private static long delivery(DiscoveryState state) {
        if ("CANCELLATION_REQUESTED".equals(state.status)) return 0;
        if (!"ACTIVE".equals(state.status)) return IDLE;
        var handoff = state.handoffs.stream().filter(value -> !"SUCCEEDED".equals(value.status)).findFirst();
        if (handoff.isPresent()) return handoff.get().nextAttemptAt;
        boolean drained = state.listings.keySet().stream().allMatch(id -> state.processed.contains(id) || state.excluded.contains(id))
            && state.batches.stream().allMatch(value -> "SUCCEEDED".equals(value.status));
        return state.searchComplete() && drained && !state.reassessmentPending ? 0 : IDLE;
    }
}
