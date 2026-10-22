package local.myhome.marketresearch.orchestrator.discovery;

import java.time.Duration;
import java.util.*;
import local.myhome.marketresearch.contracts.analysis.Discovery.*;
import local.myhome.marketresearch.contracts.content.ProductDefinition;
import local.myhome.marketresearch.tools.ProviderAccess;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/** Cohort scheduling, bounded evidence enrichment and opportunity handoff. */
final class DiscoveryAnalysis {
    private final ProviderAccess provider;
    private final RestClient analysis;
    private final ObjectMapper json;
    private final int maxShops, shopReviewBudget, listingReviewBudget;

    DiscoveryAnalysis(ProviderAccess provider, RestClient analysis, ObjectMapper json, Environment settings) {
        this.provider = provider;
        this.analysis = analysis;
        this.json = json;
        maxShops = settings.getProperty("research.discovery.max-shops", Integer.class, 50);
        shopReviewBudget = settings.getProperty("research.discovery.max-shop-review-requests", Integer.class, 8);
        listingReviewBudget = settings.getProperty("research.discovery.max-listing-review-requests", Integer.class, 4);
        if (maxShops < 0 || maxShops > 50 || shopReviewBudget < 0 || listingReviewBudget < 0)
            throw new IllegalArgumentException("Invalid evidence limits");
    }

    boolean advance(DiscoveryState state) throws Exception {
        var pending = state.batches.stream().filter(batch -> !"SUCCEEDED".equals(batch.status)).findFirst();
        if (pending.isPresent()) return advanceBatch(state, pending.get());
        if (state.nextSearchAttempt > System.currentTimeMillis()) return false;
        for (var branch : state.queries) {
            var ids = DiscoveryReadiness.pending(state, branch);
            if (ids.size() < state.analysisBatchSize) continue;
            var batch = new DiscoveryState.Batch();
            batch.branch = branch.text;
            batch.ids = ids.stream().limit(state.analysisBatchSize).toList();
            batch.key = state.id + ":business:" + state.iteration + ":" + state.batches.size();
            batch.sampledShops = EvidenceSampler.shops(batch.ids.stream().map(state.listings::get).toList(), Math.min(6, maxShops));
            state.batches.add(batch);
            return true;
        }
        boolean pageBoundary=state.queries.stream().filter(branch->!"DONE".equals(branch.status)).allMatch(branch->branch.pages>=state.pageLimit);
        var remaining=state.listings.keySet().stream().filter(id->!state.processed.contains(id)&&!state.excluded.contains(id))
            .limit(state.analysisBatchSize).toList();
        if(!remaining.isEmpty()&&("EXHAUSTED".equals(state.searchStatus)||pageBoundary)) {
            var batch=new DiscoveryState.Batch();
            var branches=state.queries.stream().filter(branch->branch.listingIds.containsAll(remaining)).toList();
            batch.branch=branches.size()==1?branches.getFirst().text:"RESIDUAL_COVERAGE";
            batch.ids=remaining;batch.key=state.id+":business:"+state.iteration+":"+state.batches.size();
            batch.sampledShops=EvidenceSampler.shops(remaining.stream().map(state.listings::get).toList(),Math.min(6,maxShops));
            state.batches.add(batch);
            return true;
        }
        if (state.reassessmentPending && "EXHAUSTED".equals(state.searchStatus)) {
            var batch = new DiscoveryState.Batch();
            batch.branch = state.query;
            batch.ids = state.listings.keySet().stream().filter(id -> !state.excluded.contains(id)).limit(state.analysisBatchSize).toList();
            if (batch.ids.isEmpty()) {
                state.reassessmentPending = false;
                return true;
            }
            batch.key = state.id + ":reassessment:" + state.iteration;
            batch.sampledShops = EvidenceSampler.shops(batch.ids.stream().map(state.listings::get).toList(), Math.min(6, maxShops));
            batch.status = "REASSESSING";
            state.batches.add(batch);
            state.reassessmentPending = false;
            return true;
        }
        return false;
    }

    private boolean advanceBatch(DiscoveryState state, DiscoveryState.Batch batch) throws Exception {
        if (batch.request == null) {
            if (state.nextSearchAttempt > System.currentTimeMillis()) return false;
            if (enrich(state, batch)) return true;
            var summaries = new ArrayList<>(state.assessments.stream().skip(Math.max(0, state.assessments.size() - 12))
                .map(report -> report.summary() + " Products: " + report.products().stream().map(Product::name).toList()).toList());
            boolean reassess = "REASSESSING".equals(batch.status);
            if (reassess) summaries.add("Operator-approved change evidence: " + json.writeValueAsString(state.changes));
            var reviews = state.reviews.values().stream().flatMap(Collection::stream).toList();
            batch.request = new Task(reassess ? "REASSESS" : "BUSINESS", state.query,
                batch.ids.stream().map(state.listings::get).toList(), new ArrayList<>(state.shops.values()),
                List.copyOf(state.families), summaries, batch.branch, reviews, List.copyOf(state.opportunities.values()));
            return true; // Freeze the input before the idempotent HTTP side effect.
        }
        if (batch.job == null) {
            var job = analysis.post().uri("/v1/discovery-jobs").header("Idempotency-Key", batch.key)
                .body(batch.request).retrieve().body(TaskView.class);
            batch.job = Objects.requireNonNull(job).id();
            batch.status = "ANALYZING";
            return true;
        }
        if (batch.nextAttemptAt > System.currentTimeMillis()) return false;
        var job = analysis.get().uri("/v1/discovery-jobs/" + batch.job).retrieve().body(TaskView.class);
        DiscoveryFlow.requireJob(job.status());
        boolean changed = !batch.status.equals(job.status()) || batch.nextAttemptAt != job.nextAttemptAt();
        if (!"SUCCEEDED".equals(job.status())) {
            batch.status = job.status();
            batch.nextAttemptAt = job.nextAttemptAt();
            return changed;
        }
        var report = Objects.requireNonNull(job.assessment());
        state.assessments.add(report);
        state.families.addAll(report.families());
        state.queries.stream().filter(branch -> branch.text.equals(batch.branch)).findFirst().ifPresent(branch ->
            branch.stableAnalyses = Boolean.FALSE.equals(report.newInsights()) ? branch.stableAnalyses + 1 : 0);
        for (Listing original : batch.request.listings()) {
            if (DiscoveryFlow.meaningful(original).equals(DiscoveryFlow.meaningful(state.listings.get(original.id()))))
                state.processed.add(original.id());
        }
        batch.status = "SUCCEEDED";
        for (Product product : report.products()) acceptOpportunity(state, batch, product);
        return true;
    }

    private void acceptOpportunity(DiscoveryState state, DiscoveryState.Batch batch, Product product) {
        String id = Objects.requireNonNull(product.opportunityId(), "Resolved opportunity ID is required");
        var existing = state.opportunities.get(id);
        var references = new LinkedHashSet<String>();
        if (existing != null) references.addAll(existing.evidenceIds());
        references.addAll(product.listingIds());
        state.opportunities.put(id, new Opportunity(id, product.existingOpportunityId(), product,
            List.copyOf(references), existing == null ? 1 : existing.revision() + 1));
        if ("ADDITIONAL_EVIDENCE".equals(product.relationship())) return;
        String key = state.id + ":opportunity:" + id;
        if (state.handoffs.stream().anyMatch(handoff -> handoff.key.equals(key))) return;
        var handoff = new DiscoveryState.Handoff();
        handoff.key = key;
        handoff.request = new ProductDefinition.Request(state.id, state.query, product,
            batch.request.listings().stream().filter(listing -> product.listingIds().contains(listing.id())).toList());
        state.handoffs.add(handoff);
    }

    private boolean enrich(DiscoveryState state, DiscoveryState.Batch batch) throws Exception {
        for (String shop : batch.sampledShops) {
            boolean stale = state.shopChecked.getOrDefault(shop, 0L) < System.currentTimeMillis() - Duration.ofDays(7).toMillis();
            if (stale && (state.shops.containsKey(shop) || state.shops.size() < maxShops)) {
                var raw = provider.shop(shop);
                var facts = new LinkedHashMap<String,Object>();
                for (String field : List.of("shop_id","created_timestamp","transaction_sold_count","review_count",
                        "review_average","listing_active_count","digital_listing_count","currency_code"))
                    if (raw.get(field) != null) facts.put(field, raw.get(field));
                if (facts.isEmpty()) throw new IllegalStateException("Invalid shop response");
                facts.put("observedAt", java.time.Instant.now().toString());
                facts.put("sampling", "Branch cohort engagement quantiles; ranked convenience sample, not market-representative");
                state.shops.put(shop, facts);
                state.shopChecked.put(shop, System.currentTimeMillis());
                return true;
            }
        }
        for (String shop : batch.sampledShops.stream().limit(2).toList()) {
            String key = "shop:" + shop;
            long used = state.reviews.keySet().stream().filter(value -> value.startsWith("shop:")).count();
            if (used < shopReviewBudget && !state.reviews.containsKey(key)) {
                state.reviews.put(key, EvidenceSampler.reviews(provider.shopReviews(shop), key));
                return true;
            }
        }
        if (state.reviewedListings.size() < listingReviewBudget) {
            var listings = batch.ids.stream().map(state.listings::get)
                .sorted(Comparator.comparingLong(Listing::favorites).reversed().thenComparing(Listing::id)).toList();
            if (!listings.isEmpty()) {
                String id = listings.getFirst().id();
                if (!state.reviewedListings.contains(id)) {
                    state.reviews.put("listing:" + id, EvidenceSampler.reviews(provider.listingReviews(id), "listing:" + id));
                    state.reviewedListings.add(id);
                    return true;
                }
            }
        }
        return false;
    }
}
