package local.myhome.marketresearch.orchestrator.discovery;

import java.util.*;
import local.myhome.marketresearch.contracts.analysis.Discovery.*;
import local.myhome.marketresearch.tools.*;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;

/** Query frontier and branch-local diminishing returns; no business recommendation logic. */
final class DiscoverySearch {
    private final DiscoveryStore store;
    private final ProviderAccess provider;
    private final RestClient analysis;
    private final int maxPages, maxQueries, maxDepth;

    DiscoverySearch(DiscoveryStore store, ProviderAccess provider, RestClient analysis, Environment settings) {
        this.store = store;
        this.provider = provider;
        this.analysis = analysis;
        maxPages = settings.getProperty("research.discovery.max-pages-per-query", Integer.class, 20);
        maxQueries = settings.getProperty("research.discovery.max-queries", Integer.class, 24);
        maxDepth = settings.getProperty("research.discovery.max-depth", Integer.class, 5);
        if (maxPages < 1 || maxPages > 120 || maxQueries < 1 || maxDepth < 0) throw new IllegalArgumentException("Invalid search limits");
    }

    boolean advance(DiscoveryState state) throws Exception {
        if (!"ACTIVE".equals(state.status) || !"ACTIVE".equals(state.searchStatus)) return false;
        if (samplingBudgetReached(state)) {
            // Finish already-submitted model work, but do not buy another refinement at the sample boundary.
            var refining = state.queries.stream().filter(branch -> "REFINING".equals(branch.status)
                && branch.refinementJob != null).findFirst();
            if (refining.isPresent()) {
                if (refining.get().nextAttemptAt > System.currentTimeMillis()) return false;
                return refine(state, refining.get());
            }
            for (var branch : state.queries) if (!"DONE".equals(branch.status)) {
                branch.status = "DEFERRED";
                branch.stopReason = "SAMPLING_PAGE_BUDGET";
            }
            state.searchStatus = "SAMPLING_COMPLETE";
            return true;
        }
        var pending = state.queries.stream().filter(branch -> !"DONE".equals(branch.status))
            .filter(branch -> branch.nextAttemptAt<=System.currentTimeMillis()||!"REFINING".equals(branch.status))
            .sorted(Comparator.comparingInt((DiscoveryState.Query branch) -> branch.pages >= maxPages ? 1 : 0)
                .thenComparingInt(branch -> branch.pages).thenComparingInt(branch -> branch.depth)).findFirst();
        if (pending.isEmpty()) {
            if(state.queries.stream().anyMatch(branch->!"DONE".equals(branch.status)))return false;
            state.searchStatus = state.queries.stream().anyMatch(query -> query.expansionStopReason != null)
                ? "BOUNDED_COMPLETE" : "EXHAUSTED";
            return true;
        }
        var branch = pending.get();
        if (branch.stableAnalyses >= 2) {
            if (branch.refinementJob != null && "REFINING".equals(branch.status))
                analysis.delete().uri("/v1/discovery-jobs/" + branch.refinementJob).retrieve().toBodilessEntity();
            finish(branch, "TWO_BRANCH_COHORTS_WITHOUT_NEW_INSIGHT");
            return true;
        }
        if ("REFINING".equals(branch.status)) return refine(state, branch);
        if (branch.pages >= maxPages) {
            if (!DiscoveryReadiness.pending(state, branch).isEmpty()) return false;
            throw new DiscoveryFlow.LimitReached("PAGE_SAFETY_LIMIT");
        }
        if (state.nextSearchAttempt > System.currentTimeMillis()) return false;
        branch.status = "FETCHING";
        store.save(state); // Crash boundary before a provider request, not an idle write.
        Map<String,Object> response;
        try {
            response = provider.search(branch.text, 100, branch.pages * 100);
        } catch (Exception failure) {
            branch.status = "OPEN";
            // Local contention sent no request, but must undo the persisted crash marker.
            if (failure instanceof ProviderBusy) store.save(state);
            throw failure;
        }
        if (!(response.get("results") instanceof List<?> rows) || !(response.get("count") instanceof Number count))
            throw new IllegalStateException("Invalid Etsy response");
        store.recordPage(state, branch.text, branch.pages * 100, response);
        branch.total = count.intValue();
        int novel = 0;
        var usable = new ArrayList<Listing>();
        for (Object item : rows) {
            if (!(item instanceof Map<?,?> raw)) continue;
            Listing listing = SearchPolicy.listing(raw);
            if (listing == null) continue;
            usable.add(listing);
            branch.listingIds.add(listing.id());
            Listing previous = state.listings.put(listing.id(), listing);
            if (previous == null && !state.excluded.contains(listing.id())) novel++;
            else if (previous != null && !DiscoveryFlow.meaningful(previous).equals(DiscoveryFlow.meaningful(listing))) {
                state.processed.remove(listing.id());
                state.excluded.remove(listing.id());
            }
        }
        branch.returned += rows.size();
        branch.usable += usable.size();
        branch.novel += novel;
        branch.pages++;
        branch.lowNovelty = SearchPolicy.lowNovelty(novel, rows.size()) ? branch.lowNovelty + 1 : 0;
        if (branch.pages == 1) {
            branch.sample = new ArrayList<>(usable);
            // Keep non-digital titles available to refine an initially unproductive intent, never to business analysis.
            if (branch.sample.isEmpty()) addRejectedContext(branch, rows);
            var watch = rows.stream().limit(25).filter(Map.class::isInstance)
                .map(item -> SearchPolicy.listing((Map<?,?>) item)).filter(Objects::nonNull).toList();
            branch.baseline = SearchPolicy.snapshot(branch.total, watch, Math.min(25, rows.size()));
        }
        branch.successful = branch.usable > 0;
        branch.status = "OPEN";
        if (!branch.refined && !branch.sample.isEmpty()) branch.status = "REFINING";
        else if (rows.size() < 100 || branch.pages * 100 >= branch.total) finish(branch, "AVAILABLE_RESULTS_EXHAUSTED");
        else if (branch.lowNovelty >= 2) finish(branch, "TWO_LOW_NOVELTY_PAGES");
        return true;
    }

    private boolean refine(DiscoveryState state, DiscoveryState.Query branch) {
        if (branch.refinementJob == null) {
            // Expansion capacity is independent of pagination and downstream analysis.
            // Do not purchase another refinement when none of its suggestions can be accepted.
            String limit = expansionLimit(state, branch);
            if (limit != null) {
                branch.expansionStopReason = limit;
                finishRefinement(branch);
                return true;
            }
            if (branch.refinementRequest == null) {
                branch.refinementRequest = new Task("REFINE", state.query, branch.sample, List.of(),
                    state.queries.stream().map(value -> value.text).toList(), List.copyOf(state.families),
                    branch.text, List.of(), List.of());
                store.save(state);
            }
            var job = analysis.post().uri("/v1/discovery-jobs")
                .header("Idempotency-Key", state.id + ":refine:" + state.iteration + ":" + DiscoveryFlow.hash(branch.text))
                .body(branch.refinementRequest).retrieve().body(TaskView.class);
            branch.refinementJob = Objects.requireNonNull(job).id();
            return true;
        }
        var job = analysis.get().uri("/v1/discovery-jobs/" + branch.refinementJob).retrieve().body(TaskView.class);
        DiscoveryFlow.requireJob(job.status());
        boolean changed = !Objects.equals(branch.workerStatus, job.status()) || branch.nextAttemptAt != job.nextAttemptAt();
        branch.workerStatus = job.status();
        branch.nextAttemptAt = job.nextAttemptAt();
        if (!"SUCCEEDED".equals(job.status())) return changed;
        var result = Objects.requireNonNull(job.refinement());
        branch.families = result.families();
        state.families.addAll(result.families());
        state.excluded.addAll(result.excludedListingIds());
        // A legacy cap failure may already have applied this result before blocking.
        // Recompute from the frozen first-page sample instead of subtracting twice on resume.
        branch.usable = (int) branch.sample.stream().filter(listing -> branch.listingIds.contains(listing.id()))
            .filter(listing -> !result.excludedListingIds().contains(listing.id())).count();
        branch.successful = branch.usable > 0;
        int added = 0;
        for (String suggestion : result.queries()) {
            String query = SearchPolicy.normalize(suggestion);
            if (state.queries.stream().anyMatch(existing -> SearchPolicy.normalize(existing.text).equals(query))) continue;
            String limit = expansionLimit(state, branch);
            if (limit != null) {
                branch.expansionStopReason = limit;
                if (!branch.deferredQueries.contains(query)) branch.deferredQueries.add(query);
                continue;
            }
            state.queries.add(new DiscoveryState.Query(query, branch));
            added++;
        }
        finishRefinement(branch);
        if (branch.expansionStopReason == null && branch.total > 1000 && added > 0) finish(branch, "SPLIT_INTO_FOCUSED_SAMPLES");
        else if (!branch.sample.isEmpty() && result.excludedListingIds().size() >= branch.sample.size() * .8)
            finish(branch, "SEMANTIC_INTENT_EXHAUSTED");
        else if (branch.returned < 100 || branch.pages * 100 >= branch.total) finish(branch, "AVAILABLE_RESULTS_EXHAUSTED");
        return true;
    }

    private String expansionLimit(DiscoveryState state, DiscoveryState.Query branch) {
        if (state.queries.size() >= maxQueries) return "QUERY_EXPANSION_LIMIT";
        if (branch.depth >= maxDepth) return "DEPTH_EXPANSION_LIMIT";
        return null;
    }

    private void finishRefinement(DiscoveryState.Query branch) {
        branch.refined = true;
        branch.status = "OPEN";
        branch.nextAttemptAt = 0;
        if (branch.returned < 100 || branch.pages * 100 >= branch.total)
            finish(branch, "AVAILABLE_RESULTS_EXHAUSTED");
    }

    private void addRejectedContext(DiscoveryState.Query branch, List<?> rows) {
        for (Object item : rows) if (item instanceof Map<?,?> raw) {
            var copy = new LinkedHashMap<String,Object>();
            raw.forEach((key,value) -> copy.put(key.toString(), value));
            copy.put("listing_type", "download");
            Listing source = SearchPolicy.listing(copy);
            if (source != null) branch.sample.add(new Listing(source.id(), source.shopId(), source.title(),
                "NON-DIGITAL RESULT: " + source.description(), source.tags(), source.price(), source.currency(),
                source.views(), source.favorites(), source.createdAt()));
        }
    }

    private void finish(DiscoveryState.Query branch, String reason) {
        branch.status = "DONE";
        branch.stopReason = reason;
    }

    private static boolean samplingBudgetReached(DiscoveryState state) {
        return state.samplingPageBudget > 0
            && state.queries.stream().mapToInt(branch -> branch.pages).sum() >= state.samplingPageBudget;
    }
}
