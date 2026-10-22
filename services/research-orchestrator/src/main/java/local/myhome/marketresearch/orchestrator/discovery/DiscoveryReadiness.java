package local.myhome.marketresearch.orchestrator.discovery;

import java.util.*;

/** Small scheduling projections. Idle/waiting aggregates are not loaded or serialized by workers. */
final class DiscoveryReadiness {
    static final long IDLE = Long.MAX_VALUE;
    private DiscoveryReadiness() {}

    static long next(DiscoveryState state, String worker) {
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
        var branches=state.queries.stream().filter(branch->!"DONE".equals(branch.status)).toList();
        if(branches.isEmpty())return state.nextSearchAttempt;
        return branches.stream().filter(branch->branch.stableAnalyses>=2||branch.pages<state.pageLimit||pending(state,branch).isEmpty())
            .mapToLong(branch->Math.max(state.nextSearchAttempt,"REFINING".equals(branch.status)?branch.nextAttemptAt:0)).min().orElse(IDLE);
    }

    private static long analysis(DiscoveryState state) {
        if (!"ACTIVE".equals(state.status)) return IDLE;
        var batch = state.batches.stream().filter(value -> !"SUCCEEDED".equals(value.status)).findFirst();
        if (batch.isPresent()) return Math.max(batch.get().nextAttemptAt, batch.get().request == null ? state.nextSearchAttempt : 0);
        if (state.reassessmentPending && "EXHAUSTED".equals(state.searchStatus)) return 0;
        boolean eligible=state.queries.stream().anyMatch(branch->pending(state,branch).size()>=state.analysisBatchSize);
        boolean residual=state.listings.keySet().stream().anyMatch(id->!state.processed.contains(id)&&!state.excluded.contains(id));
        boolean pageBoundary=state.queries.stream().filter(branch->!"DONE".equals(branch.status)).allMatch(branch->branch.pages>=state.pageLimit);
        return eligible||residual&&("EXHAUSTED".equals(state.searchStatus)||pageBoundary)?state.nextSearchAttempt:IDLE;
    }

    private static long delivery(DiscoveryState state) {
        if ("CANCELLATION_REQUESTED".equals(state.status)) return 0;
        if (!"ACTIVE".equals(state.status)) return IDLE;
        var handoff = state.handoffs.stream().filter(value -> !"SUCCEEDED".equals(value.status)).findFirst();
        if (handoff.isPresent()) return handoff.get().nextAttemptAt;
        boolean drained = state.listings.keySet().stream().allMatch(id -> state.processed.contains(id) || state.excluded.contains(id))
            && state.batches.stream().allMatch(value -> "SUCCEEDED".equals(value.status));
        return "EXHAUSTED".equals(state.searchStatus) && drained && !state.reassessmentPending ? 0 : IDLE;
    }
}
