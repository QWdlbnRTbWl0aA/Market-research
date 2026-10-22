package local.myhome.marketresearch.orchestrator.discovery;

import java.time.Duration;
import java.util.*;
import local.myhome.marketresearch.tools.ProviderAccess;
import tools.jackson.databind.ObjectMapper;

/** Deterministic monitoring only. This component has no model adapter or deep-research authorization. */
final class DiscoveryMonitoring {
    private final ProviderAccess provider;
    private final DiscoveryNotices notices;
    private final ObjectMapper json;

    DiscoveryMonitoring(ProviderAccess provider, DiscoveryNotices notices, ObjectMapper json) {
        this.provider = provider;
        this.notices = notices;
        this.json = json;
    }

    boolean advance(DiscoveryState state) throws Exception {
        if (!"WATCHING".equals(state.status) || state.nextWatch > System.currentTimeMillis()) return false;
        var queries = state.queries.stream().filter(branch -> branch.successful
            && !"SPLIT_INTO_FOCUSED_SAMPLES".equals(branch.stopReason)).toList();
        if (state.watchIndex < queries.size()) {
            var branch = queries.get(state.watchIndex);
            var response = provider.search(branch.text, 25, 0);
            if (!(response.get("results") instanceof List<?> rows) || !(response.get("count") instanceof Number count))
                throw new IllegalStateException("Invalid trend response");
            var listings = rows.stream().filter(Map.class::isInstance)
                .map(row -> SearchPolicy.listing((Map<?,?>) row)).filter(Objects::nonNull).toList();
            branch.latest = SearchPolicy.snapshot(count.intValue(), listings, rows.size());
            var delta = SearchPolicy.changes(branch.text, SearchPolicy.watchBaseline(branch.baseline), branch.latest);
            if (Boolean.TRUE.equals(delta.get("significant"))) state.changes.add(delta);
            state.watchIndex++;
            return true;
        }
        state.watchIndex = 0;
        state.error = null;
        if (!state.changes.isEmpty()) {
            state.status = "AWAITING_APPROVAL";
            notices.queue(state.id + ":trend:" + state.iteration + ":" + DiscoveryFlow.hash(json.writeValueAsString(state.changes)),
                "Marketplace change detected", "Discovery " + state.id
                + " has a significant search-sample change. Approve or dismiss it explicitly. No model was called.");
        } else state.nextWatch = System.currentTimeMillis() + Duration.ofDays(7).toMillis();
        return true;
    }
}
