package local.myhome.marketresearch.orchestrator.discovery;

import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import local.myhome.marketresearch.contracts.analysis.Discovery.Listing;
import local.myhome.marketresearch.tools.*;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/** Run lifecycle and durable retries; each stage owns its own behavior. */
@Service
@EnableScheduling
public class DiscoveryFlow {
    @FunctionalInterface private interface Stage { boolean advance(DiscoveryState state) throws Exception; }
    static final class LimitReached extends RuntimeException {
        final String code;
        LimitReached(String code) { this.code = code; }
    }
    private final DiscoveryStore store;
    private final DiscoveryNotices notices;
    private final RestClient analysis, content;
    private final DiscoverySearch search;
    private final DiscoveryAnalysis business;
    private final DiscoveryDelivery delivery;
    private final DiscoveryMonitoring monitoring;
    private final int batchSize, pageLimit, samplingPageBudget;
    private final ConcurrentHashMap<UUID,ReentrantLock> locks = new ConcurrentHashMap<>();

    public DiscoveryFlow(DiscoveryStore store, DiscoveryNotices notices, ObjectMapper json, Environment settings, ProviderAccess provider) {
        this.store = store;
        this.notices = notices;
        analysis = client(settings.getProperty("market-research.services.analysis-base-url", "http://127.0.0.1:8082"));
        content = client(settings.getProperty("market-research.services.content-base-url", "http://127.0.0.1:8083"));
        var archive = client(settings.getProperty("market-research.services.archive-base-url", "http://127.0.0.1:8084"));
        batchSize = settings.getProperty("research.discovery.batch-size", Integer.class, 500);
        pageLimit = settings.getProperty("research.discovery.max-pages-per-query", Integer.class, 20);
        samplingPageBudget = settings.getProperty("research.discovery.sampling-page-budget", Integer.class, 0);
        if (samplingPageBudget < 0) throw new IllegalArgumentException("Invalid sampling budget");
        if (batchSize < 1 || batchSize > 1000) throw new IllegalArgumentException("Invalid cohort size");
        search = new DiscoverySearch(store, provider, analysis, settings);
        business = new DiscoveryAnalysis(provider, analysis, json, settings);
        delivery = new DiscoveryDelivery(store, notices, analysis, content, archive);
        monitoring = new DiscoveryMonitoring(provider, notices, json);
        for (UUID id : store.interruptedIds()) {
            var state = store.get(id);
            if (state.queries.stream().anyMatch(branch -> "FETCHING".equals(branch.status))) {
                block(state, "INTERRUPTED_SEARCH");
                store.save(state);
            }
        }
    }

    private static RestClient client(String url) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    public DiscoveryState start(String key, String query) {
        var state = store.create(key, query);
        if (state.analysisBatchSize != batchSize || state.pageLimit != pageLimit || state.samplingPageBudget != samplingPageBudget) {
            state.analysisBatchSize = batchSize;
            state.pageLimit = pageLimit;
            state.samplingPageBudget = samplingPageBudget;
            store.save(state);
        }
        return state;
    }
    public DiscoveryState get(UUID id) { return store.get(id); }
    public Map<String,Object> snapshot(DiscoveryState state) { return DiscoveryDelivery.snapshot(state); }

    @Scheduled(fixedDelayString="${research.discovery.search-delay-ms:1000}")
    public void searchTick() { each("SEARCH", search::advance); }
    @Scheduled(fixedDelayString="${research.discovery.analysis-delay-ms:700}")
    public void analysisTick() { each("ANALYSIS", business::advance); }
    @Scheduled(fixedDelayString="${research.discovery.delivery-delay-ms:800}")
    public void deliveryTick() { each("DELIVERY", delivery::advance); }
    @Scheduled(fixedDelayString="${research.discovery.watch-delay-ms:60000}")
    public void watchTick() { each("WATCH", monitoring::advance); }

    private void each(String worker, Stage operation) {
        for (UUID id : store.ready(worker)) {
            var lock = locks.computeIfAbsent(id, ignored -> new ReentrantLock());
            if (!lock.tryLock()) continue;
            try {
                var state = store.get(id);
                if (DiscoveryReadiness.next(state, worker) > System.currentTimeMillis()) continue;
                boolean changed;
                try {
                    changed = operation.advance(state);
                    changed |= state.retryCounts.remove(worker) != null;
                    changed |= state.retryAt.remove(worker) != null;
                } catch (ProviderBusy waiting) {
                    changed = false;
                } catch (ProviderDeferred waiting) {
                    if ("WATCH".equals(worker)) state.nextWatch = waiting.retryAt();
                    else state.nextSearchAttempt = waiting.retryAt();
                    state.error = "KNOWN_QUOTA_WAIT";
                    changed = true;
                } catch (LimitReached limit) {
                    block(state, limit.code);
                    changed = true;
                } catch (RestClientResponseException failure) {
                    if (failure.getStatusCode().is4xxClientError() && failure.getStatusCode().value() != 429)
                        block(state, "DEPENDENCY_REJECTED_REQUEST");
                    else retry(state, worker);
                    changed = true;
                } catch (RestClientException failure) {
                    retry(state, worker);
                    changed = true;
                } catch (Exception failure) {
                    block(state, "DEPENDENCY_OR_PROVIDER_FAILED");
                    changed = true;
                }
                if (changed) store.save(state);
            } finally {
                lock.unlock();
            }
        }
    }

    private void retry(DiscoveryState state, String worker) {
        int attempts = state.retryCounts.getOrDefault(worker, 0) + 1;
        state.retryCounts.put(worker, attempts);
        if (attempts >= 6) block(state, "DEPENDENCY_RETRIES_EXHAUSTED");
        else {
            state.error = "DEPENDENCY_UNAVAILABLE";
            state.retryAt.put(worker, System.currentTimeMillis() + Math.min(30000, 500L << attempts));
        }
    }

    private void block(DiscoveryState state, String code) {
        if (!"BLOCKED".equals(state.status)) state.blockedFrom = state.status;
        state.status = "BLOCKED";
        state.error = code;
        notices.queue(state.id + ":blocked:" + state.iteration + ":" + code, "Research needs attention",
            "Discovery " + state.id + " stopped with " + code + ". Pending work is preserved.");
    }

    public void cancel(UUID id) {
        mutate(id, state -> {
            if (Set.of("WATCHING","AWAITING_APPROVAL","CANCELLED").contains(state.status)) {
                state.status = "CANCELLED";
                return;
            }
            state.status = "CANCELLATION_REQUESTED";
            state.error = null;
        });
    }
    public void reviewBriefs(UUID id,String key) {
        if (key==null||key.isBlank()||key.length()>100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        mutate(id, state -> {
            if (state.archived || !Set.of("ACTIVE","BLOCKED").contains(state.status))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Review before immutable archival");
            for (var handoff : state.handoffs) {
                if (handoff.job==null) continue;
                var job=content.get().uri("/v1/product-definition-jobs/"+handoff.job).retrieve()
                    .body(local.myhome.marketresearch.contracts.content.ProductDefinition.View.class);
                if (!"SUCCEEDED".equals(job.status())) continue;
                content.post().uri("/v1/product-definition-jobs/"+handoff.job+"/review")
                    .header("Idempotency-Key",key+":"+handoff.job).retrieve().toBodilessEntity();
                handoff.status="DEFINING";
                handoff.brief=null;
            }
        });
    }
    public void resume(UUID id) {
        mutate(id, state -> {
            if (!"BLOCKED".equals(state.status)) throw new ResponseStatusException(HttpStatus.CONFLICT);
            // Explicitly authorize a retry of uncertain external effects, never do it automatically.
            for (var branch : state.queries) {
                if ("FETCHING".equals(branch.status)) branch.status = "OPEN";
                if (branch.refinementJob != null && "REFINING".equals(branch.status))
                    analysis.post().uri("/v1/discovery-jobs/" + branch.refinementJob + "/resume").retrieve().toBodilessEntity();
            }
            for (var batch : state.batches) {
                if (batch.job != null && !"SUCCEEDED".equals(batch.status))
                    analysis.post().uri("/v1/discovery-jobs/" + batch.job + "/resume").retrieve().toBodilessEntity();
            }
            for (var handoff : state.handoffs) {
                if (handoff.job != null && !"SUCCEEDED".equals(handoff.status))
                    content.post().uri("/v1/product-definition-jobs/" + handoff.job + "/resume").retrieve().toBodilessEntity();
            }
            state.status = "WATCHING".equals(state.blockedFrom) ? "WATCHING" : "ACTIVE";
            state.error = null;
            state.retryAt.clear();
            state.retryCounts.clear();
        });
    }
    public void approve(UUID id) {
        mutate(id, state -> {
            if (!"AWAITING_APPROVAL".equals(state.status))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"No pending trend decision");
            state.iteration++;
            state.status = "ACTIVE";
            state.searchStatus = "ACTIVE";
            state.error = null;
            state.archived = false;
            state.notified = false;
            state.finishedAt = null;
            state.watchIndex = 0;
            state.reassessmentPending = true;
            for (var branch : state.queries) {
                if (!branch.successful || "SPLIT_INTO_FOCUSED_SAMPLES".equals(branch.stopReason)) continue;
                branch.status = "OPEN";
                branch.pages = 0;
                branch.lowNovelty = 0;
                branch.stopReason = null;
                branch.returned = 0;
                branch.usable = 0;
                branch.novel = 0;
                branch.total = 0;
                branch.stableAnalyses = 0;
                branch.nextAttemptAt = 0;
                branch.workerStatus = null;
                branch.refined = false;
                branch.refinementJob = null;
                branch.refinementRequest = null;
                branch.sample.clear();
                branch.baseline.clear();
            }
            // Existing processed IDs stay excluded unless meaningful listing fields changed.
        });
    }
    public void dismiss(UUID id) {
        mutate(id, state -> {
            if (!"AWAITING_APPROVAL".equals(state.status)) throw new ResponseStatusException(HttpStatus.CONFLICT);
            for (var branch : state.queries) {
                if (!branch.latest.isEmpty()) branch.baseline = new LinkedHashMap<>(branch.latest);
            }
            state.status = "WATCHING";
            state.changes.clear();
            state.watchIndex = 0;
            state.nextWatch = System.currentTimeMillis() + Duration.ofDays(7).toMillis();
        });
    }
    private void mutate(UUID id, Consumer<DiscoveryState> action) {
        var lock = locks.computeIfAbsent(id, ignored -> new ReentrantLock());
        lock.lock();
        try {
            var state = store.get(id);
            action.accept(state);
            store.save(state);
        } finally {
            lock.unlock();
        }
    }

    static void requireJob(String status) {
        if (status == null || Set.of("BLOCKED","FAILED","CANCELLED").contains(status))
            throw new IllegalStateException("Child job requires attention");
    }
    static String meaningful(Listing listing) {
        return hash(listing.title() + "|" + listing.description() + "|" + listing.tags() + "|" + listing.price()
            + "|" + listing.currency() + "|" + listing.shopId());
    }
    static String fullHash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
    }
    static String hash(String text) { return fullHash(text).substring(0,32); }
}
