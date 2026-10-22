package local.myhome.marketresearch.orchestrator.discovery;

import java.time.*;
import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import local.myhome.marketresearch.contracts.analysis.Discovery;
import local.myhome.marketresearch.contracts.analysis.Discovery.*;
import local.myhome.marketresearch.contracts.content.ProductDefinition;
import local.myhome.marketresearch.tools.ProviderAccess;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
@EnableScheduling
public class DiscoveryFlow {
    private final DiscoveryStore store;private final DiscoveryNotices notices;private final ObjectMapper json;
    private final ProviderAccess provider;private final RestClient analysis,content,archive;
    private final int batchSize,maxQueries,maxPages,maxDepth,maxShops;
    private final ConcurrentHashMap<UUID,ReentrantLock> locks=new ConcurrentHashMap<>();
    @org.springframework.beans.factory.annotation.Autowired
    public DiscoveryFlow(DiscoveryStore store,DiscoveryNotices notices,ObjectMapper json,Environment env,ProviderAccess provider) {
        this.store=store;this.notices=notices;this.json=json;this.provider=provider;
        analysis=client(env.getProperty("market-research.services.analysis-base-url","http://127.0.0.1:8082"));
        content=client(env.getProperty("market-research.services.content-base-url","http://127.0.0.1:8083"));
        archive=client(env.getProperty("market-research.services.archive-base-url","http://127.0.0.1:8084"));
        batchSize=env.getProperty("research.discovery.batch-size",Integer.class,500);
        maxQueries=env.getProperty("research.discovery.max-queries",Integer.class,24);
        maxPages=env.getProperty("research.discovery.max-pages-per-query",Integer.class,20);
        maxDepth=env.getProperty("research.discovery.max-depth",Integer.class,5);
        maxShops=env.getProperty("research.discovery.max-shops",Integer.class,50);
        if(batchSize<1||batchSize>1000||maxQueries<1||maxPages<1||maxPages>120||maxShops<0||maxShops>50)throw new IllegalArgumentException("Invalid discovery limits");
        for(UUID id:store.ids()) {
            var s=store.get(id);
            if(s.queries.stream().anyMatch(q->"FETCHING".equals(q.status))) {
                s.status="BLOCKED";s.error="INTERRUPTED_SEARCH";store.save(s);
            }
        }
    }
    private static RestClient client(String url) {
        var f=new SimpleClientHttpRequestFactory();f.setConnectTimeout(Duration.ofSeconds(2));f.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().baseUrl(url).requestFactory(f).build();
    }
    public DiscoveryState start(String key,String query){return store.create(key,query);}
    public DiscoveryState get(UUID id){return store.get(id);}
    private void each(String worker,Consumer<DiscoveryState> operation) {
        for(UUID id:store.ids()) {
            var lock=locks.computeIfAbsent(id,k->new ReentrantLock());
            if(!lock.tryLock())continue;
            try {
                var s=store.get(id);
                if(s.retryAt.getOrDefault(worker,0L)>System.currentTimeMillis())continue;
                try{operation.accept(s);s.retryCounts.remove(worker);s.retryAt.remove(worker);}
                catch(local.myhome.marketresearch.tools.ProviderDeferred waiting){s.nextSearchAttempt=waiting.retryAt();s.error="KNOWN_QUOTA_WAIT";}
                catch(local.myhome.marketresearch.tools.ProviderBusy waiting){/* Local contention sent no request; defer. */}
                catch(org.springframework.web.client.RestClientResponseException e) {
                    if(e.getStatusCode().is4xxClientError()&&e.getStatusCode().value()!=429)block(s,"DEPENDENCY_REJECTED_REQUEST");
                    else retry(s,worker);
                }
                catch(org.springframework.web.client.RestClientException e){retry(s,worker);}
                catch(Exception e){block(s,"DEPENDENCY_OR_PROVIDER_FAILED");}
                store.save(s);
            }finally{lock.unlock();}
        }
    }
    private void retry(DiscoveryState s,String worker) {
        int count=s.retryCounts.getOrDefault(worker,0)+1;s.retryCounts.put(worker,count);
        if(count>=6){block(s,"DEPENDENCY_RETRIES_EXHAUSTED");return;}
        s.error="DEPENDENCY_UNAVAILABLE";s.retryAt.put(worker,System.currentTimeMillis()+Math.min(30000,500L<<count));
    }
    private void block(DiscoveryState s,String code) {
        if(!"BLOCKED".equals(s.status))s.blockedFrom=s.status;
        s.status="BLOCKED";s.error=code;
        notices.queue(s.id+":blocked:"+s.iteration+":"+code,"Research needs attention",
            "Discovery "+s.id+" stopped with "+code+". Pending work is preserved. Inspect status before explicitly resuming.");
    }
    public void cancel(UUID id) {
        mutate(id,s->{
            if(Set.of("WATCHING","AWAITING_APPROVAL","CANCELLED").contains(s.status)){s.status="CANCELLED";return;}
            s.status="CANCELLATION_REQUESTED";s.error=null;
        });
    }
    public void resume(UUID id) {
        mutate(id,s->{
            if(!"BLOCKED".equals(s.status))throw new ResponseStatusException(HttpStatus.CONFLICT);
            // Explicitly authorize a retry of uncertain external effects, never do it automatically.
            for(var q:s.queries)if("FETCHING".equals(q.status))q.status="OPEN";
            for(var q:s.queries)if(q.refinementJob!=null&&"REFINING".equals(q.status))
                analysis.post().uri("/v1/discovery-jobs/"+q.refinementJob+"/resume").retrieve().toBodilessEntity();
            for(var b:s.batches)if(b.job!=null&&!"SUCCEEDED".equals(b.status))
                analysis.post().uri("/v1/discovery-jobs/"+b.job+"/resume").retrieve().toBodilessEntity();
            for(var h:s.handoffs)if(h.job!=null&&!"SUCCEEDED".equals(h.status))
                content.post().uri("/v1/product-definition-jobs/"+h.job+"/resume").retrieve().toBodilessEntity();
            s.status="WATCHING".equals(s.blockedFrom)?"WATCHING":"ACTIVE";s.error=null;s.retryAt.clear();s.retryCounts.clear();
        });
    }
    public void approve(UUID id) {
        mutate(id,s->{
            if(!"AWAITING_APPROVAL".equals(s.status))throw new ResponseStatusException(HttpStatus.CONFLICT,"No pending trend decision");
            s.iteration++;s.status="ACTIVE";s.searchStatus="ACTIVE";s.error=null;s.stableAnalyses=0;
            s.archived=false;s.notified=false;s.finishedAt=null;s.watchIndex=0;s.reassessmentPending=true;
            for(var q:s.queries)if(q.successful) {
                q.status="OPEN";q.pages=0;q.lowNovelty=0;q.stopReason=null;
                q.refined=false;q.refinementJob=null;q.refinementRequest=null;q.sample.clear();q.baseline.clear();
            }
            // Existing processed IDs stay excluded unless meaningful listing fields changed.
        });
    }
    public void dismiss(UUID id) {
        mutate(id,s->{
            if(!"AWAITING_APPROVAL".equals(s.status))throw new ResponseStatusException(HttpStatus.CONFLICT);
            for(var q:s.queries)if(!q.latest.isEmpty())q.baseline=new LinkedHashMap<>(q.latest);
            s.status="WATCHING";s.changes.clear();s.watchIndex=0;s.nextWatch=System.currentTimeMillis()+Duration.ofDays(7).toMillis();
        });
    }
    private void mutate(UUID id,Consumer<DiscoveryState> action) {
        var lock=locks.computeIfAbsent(id,k->new ReentrantLock());lock.lock();
        try{var s=store.get(id);action.accept(s);store.save(s);}finally{lock.unlock();}
    }
    @Scheduled(fixedDelayString="${research.discovery.search-delay-ms:1000}")
    public void searchTick(){each("SEARCH",s->{
        if(!"ACTIVE".equals(s.status)||!"ACTIVE".equals(s.searchStatus)||s.nextSearchAttempt>System.currentTimeMillis())return;
        var current=s.queries.stream().filter(q->!"DONE".equals(q.status))
            .sorted(Comparator.comparingInt(q->q.pages>=maxPages?1:0)).findFirst();
        if(current.isEmpty()){s.searchStatus="EXHAUSTED";return;}
        var q=current.get();
        if("REFINING".equals(q.status)) {
            if(q.refinementJob==null) {
                if(q.refinementRequest==null) {
                    q.refinementRequest=new Task("REFINE",s.query,List.copyOf(q.sample),List.of(),
                        s.queries.stream().map(x->x.text).toList(),List.copyOf(s.families));store.save(s);
                }
                var job=analysis.post().uri("/v1/discovery-jobs").header("Idempotency-Key",s.id+":refine:"+s.iteration+":"+hash(q.text))
                    .body(q.refinementRequest).retrieve().body(TaskView.class);
                q.refinementJob=Objects.requireNonNull(job).id();return;
            }
            var job=analysis.get().uri("/v1/discovery-jobs/"+q.refinementJob).retrieve().body(TaskView.class);
            q.workerStatus=job.status();q.nextAttemptAt=job.nextAttemptAt();
            requireJob(job.status());
            if(!"SUCCEEDED".equals(job.status()))return;
            var result=Objects.requireNonNull(job.refinement());q.families=result.families();s.families.addAll(result.families());
            s.excluded.addAll(result.excludedListingIds());
            q.usable-=result.excludedListingIds().stream().filter(id->s.listings.containsKey(id)).count();
            q.successful=q.usable>0;
            int added=0;
            for(String suggestion:result.queries()) {
                String phrase=SearchPolicy.normalize(suggestion);
                if(s.queries.stream().anyMatch(x->SearchPolicy.normalize(x.text).equals(phrase)))continue;
                if(s.queries.size()>=maxQueries||q.depth>=maxDepth){block(s,"REFINEMENT_SAFETY_LIMIT");break;}
                s.queries.add(new DiscoveryState.Query(phrase,q));added++;
            }
            q.refined=true;q.status="OPEN";
            if(q.total>1000&&added>0){q.status="DONE";q.stopReason="SPLIT_INTO_FOCUSED_SAMPLES";}
            else if(!q.sample.isEmpty()&&result.excludedListingIds().size()>=q.sample.size()*.8) {q.status="DONE";q.stopReason="SEMANTIC_INTENT_EXHAUSTED";}
            else if(q.returned<100||q.pages*100>=q.total){q.status="DONE";q.stopReason="AVAILABLE_RESULTS_EXHAUSTED";}
            return;
        }
        if(q.pages>=maxPages) {
            boolean analyzing=s.listings.keySet().stream().anyMatch(id->!s.processed.contains(id)&&!s.excluded.contains(id));
            if(analyzing)return; // Let pending cohort interpretations determine marginal insight before declaring a cap.
            block(s,"PAGE_SAFETY_LIMIT");return;
        }
        q.status="FETCHING";store.save(s);
        Map<String,Object> raw;
        try{raw=provider.search(q.text,100,q.pages*100);}
        catch(local.myhome.marketresearch.tools.ProviderDeferred waiting){q.status="OPEN";throw waiting;}
        catch(local.myhome.marketresearch.tools.ProviderBusy waiting){q.status="OPEN";throw waiting;}
        catch(Exception error){q.status="OPEN";throw new IllegalStateException();}
        if(!(raw.get("results") instanceof List<?> rows)||!(raw.get("count") instanceof Number count))
            throw new IllegalStateException("Invalid search response");
        q.total=count.intValue();int fresh=0,usable=0;var sample=new ArrayList<Listing>();
        for(Object item:rows) {
            if(!(item instanceof Map<?,?> map))continue;
            var listing=SearchPolicy.listing(map);if(listing==null)continue;
            usable++;sample.add(listing);
            var previous=s.listings.put(listing.id(),listing);
            if(previous==null){if(!s.excluded.contains(listing.id()))fresh++;}
            else if(!meaningful(previous).equals(meaningful(listing))){s.processed.remove(listing.id());s.excluded.remove(listing.id());}
        }
        q.returned+=rows.size();q.usable+=usable;q.novel+=fresh;q.pages++;
        q.lowNovelty=SearchPolicy.lowNovelty(fresh,rows.size())?q.lowNovelty+1:0;
        if(q.pages==1) {
            q.sample=new ArrayList<>(sample);
            if(q.sample.isEmpty())for(Object item:rows)if(item instanceof Map<?,?> map) {
                var copy=new LinkedHashMap<String,Object>();map.forEach((k,v)->copy.put(k.toString(),v));
                copy.put("listing_type","download");
                var context=SearchPolicy.listing(copy);
                if(context!=null)q.sample.add(new Listing(context.id(),context.shopId(),context.title(),
                    "NON-DIGITAL RESULT: "+context.description(),context.tags(),context.price(),context.currency(),context.views(),context.favorites(),context.createdAt()));
            }
            var watchSample=rows.stream().limit(25).filter(Map.class::isInstance).map(x->SearchPolicy.listing((Map<?,?>)x)).filter(Objects::nonNull).toList();
            q.baseline=SearchPolicy.snapshot(q.total,watchSample,Math.min(rows.size(),25));
        }
        q.successful=q.usable>0;q.status="OPEN";
        if(!q.refined&&!sample.isEmpty())q.status="REFINING";
        else if(rows.size()<100||q.pages*100>=q.total){q.status="DONE";q.stopReason="AVAILABLE_RESULTS_EXHAUSTED";}
        else if(q.lowNovelty>=2){q.status="DONE";q.stopReason="TWO_LOW_NOVELTY_PAGES";}
    });}
    @Scheduled(fixedDelayString="${research.discovery.analysis-delay-ms:700}")
    public void analysisTick(){each("ANALYSIS",s->{
        if(!"ACTIVE".equals(s.status))return;
        var pending=s.batches.stream().filter(b->!"SUCCEEDED".equals(b.status)).findFirst();
        if(pending.isPresent()) {
            var b=pending.get();
            if(b.job==null) {
                var job=analysis.post().uri("/v1/discovery-jobs").header("Idempotency-Key",b.key).body(b.request).retrieve().body(TaskView.class);
                b.job=Objects.requireNonNull(job).id();b.status="ANALYZING";return;
            }
            var job=analysis.get().uri("/v1/discovery-jobs/"+b.job).retrieve().body(TaskView.class);
            if(!"SUCCEEDED".equals(job.status()))b.status=job.status();b.nextAttemptAt=job.nextAttemptAt();
            requireJob(job.status());if(!"SUCCEEDED".equals(job.status()))return;
            var report=Objects.requireNonNull(job.assessment());s.assessments.add(report);s.families.addAll(report.families());
            s.stableAnalyses=Boolean.FALSE.equals(report.newInsights())?s.stableAnalyses+1:0;
            if(s.stableAnalyses>=2&&"ACTIVE".equals(s.searchStatus))s.searchStatus="SATURATING";
            for(var original:b.request.listings())if(meaningful(original).equals(meaningful(s.listings.get(original.id()))))s.processed.add(original.id());
            b.status="SUCCEEDED";
            for(var product:report.products()) {
                String key=s.id+":product:"+hash(json.writeValueAsString(product));
                if(s.handoffs.stream().anyMatch(h->h.key.equals(key)))continue;
                var h=new DiscoveryState.Handoff();h.key=key;
                h.request=new ProductDefinition.Request(s.id,s.query,product,b.request.listings().stream().filter(l->product.listingIds().contains(l.id())).toList());
                s.handoffs.add(h);
            }
            return;
        }
        if(s.nextSearchAttempt>System.currentTimeMillis())return;
        var candidates=s.listings.values().stream().filter(l->!s.processed.contains(l.id())&&!s.excluded.contains(l.id())).limit(batchSize).toList();
        boolean reassess=candidates.isEmpty()&&s.reassessmentPending&&"EXHAUSTED".equals(s.searchStatus);
        if(reassess)candidates=s.listings.values().stream().filter(l->!s.excluded.contains(l.id())).limit(batchSize).toList();
        boolean atPageBoundary=s.queries.stream().filter(q->!"DONE".equals(q.status)).allMatch(q->q.pages>=maxPages);
        if(candidates.size()<batchSize&&!"EXHAUSTED".equals(s.searchStatus)&&!atPageBoundary)return;
        if(candidates.isEmpty())return;
        // Enrich at cohort scale, never create one analysis job per shop or listing.
        var shop=candidates.stream().map(Listing::shopId).distinct()
            .filter(id->s.shops.containsKey(id)?s.shopChecked.getOrDefault(id,0L)<System.currentTimeMillis()-Duration.ofDays(7).toMillis():s.shops.size()<maxShops).findFirst();
        if(shop.isPresent()) {
            try {
                var raw=provider.shop(shop.get());var safe=new LinkedHashMap<String,Object>();
                for(String field:List.of("shop_id","shop_name","create_date","created_timestamp","transaction_sold_count",
                        "listing_active_count","review_count","review_average"))if(raw.get(field)!=null)safe.put(field,raw.get(field));
                if(safe.isEmpty())throw new IllegalStateException("Invalid shop response");
                s.shops.put(shop.get(),safe);s.shopChecked.put(shop.get(),System.currentTimeMillis());return;
            }catch(local.myhome.marketresearch.tools.ProviderDeferred waiting){throw waiting;}
            catch(local.myhome.marketresearch.tools.ProviderBusy waiting){throw waiting;}
            catch(Exception error){throw new IllegalStateException();}
        }
        var b=new DiscoveryState.Batch();b.ids=candidates.stream().map(Listing::id).toList();
        var summaries=new ArrayList<>(s.assessments.stream().map(a->a.summary()+" Products: "+a.products().stream().map(Product::name).toList()).toList());
        if(reassess)summaries.add("Operator-approved trend context: "+json.writeValueAsString(s.changes));
        b.request=new Task(reassess?"REASSESS":"BUSINESS",s.query,candidates,new ArrayList<>(s.shops.values()),List.copyOf(s.families),summaries);
        b.key=s.id+":business:"+s.iteration+":"+s.batches.size();s.batches.add(b);
        if(reassess)s.reassessmentPending=false;
    });}
    @Scheduled(fixedDelayString="${research.discovery.delivery-delay-ms:800}")
    public void deliveryTick(){each("DELIVERY",s->{
        if("CANCELLATION_REQUESTED".equals(s.status)) {
            for(var b:s.batches)if(b.job!=null&&!"SUCCEEDED".equals(b.status))analysis.delete().uri("/v1/discovery-jobs/"+b.job).retrieve().toBodilessEntity();
            for(var q:s.queries)if(q.refinementJob!=null&&"REFINING".equals(q.status))analysis.delete().uri("/v1/discovery-jobs/"+q.refinementJob).retrieve().toBodilessEntity();
            for(var h:s.handoffs)if(h.job!=null&&!"SUCCEEDED".equals(h.status))content.delete().uri("/v1/product-definition-jobs/"+h.job).retrieve().toBodilessEntity();
            s.status="CANCELLED";return;
        }
        if(!"ACTIVE".equals(s.status))return;
        if("SATURATING".equals(s.searchStatus)) {
            for(var q:s.queries)if(!"DONE".equals(q.status)) {
                if(q.refinementJob!=null&&"REFINING".equals(q.status))
                    analysis.delete().uri("/v1/discovery-jobs/"+q.refinementJob).retrieve().toBodilessEntity();
                q.status="DONE";q.stopReason="TWO_COHORTS_WITHOUT_NEW_INSIGHT";
            }
            s.searchStatus="EXHAUSTED";return;
        }
        for(var h:s.handoffs) {
            if("SUCCEEDED".equals(h.status))continue;
            if(h.job==null) {
                var job=content.post().uri("/v1/product-definition-jobs").header("Idempotency-Key",h.key).body(h.request).retrieve().body(ProductDefinition.View.class);
                h.job=Objects.requireNonNull(job).id();h.status="DEFINING";return;
            }
            var job=content.get().uri("/v1/product-definition-jobs/"+h.job).retrieve().body(ProductDefinition.View.class);
            if(!"SUCCEEDED".equals(job.status()))h.status=job.status();h.nextAttemptAt=job.nextAttemptAt();
            requireJob(job.status());if(!"SUCCEEDED".equals(job.status()))return;
            h.brief=Objects.requireNonNull(job.brief());h.status="SUCCEEDED";return;
        }
        if(!"EXHAUSTED".equals(s.searchStatus)||s.listings.keySet().stream().anyMatch(id->!s.processed.contains(id)&&!s.excluded.contains(id))
            ||s.reassessmentPending||s.batches.stream().anyMatch(b->!"SUCCEEDED".equals(b.status)))return;
        if(s.finishedAt==null)s.finishedAt=Instant.now().toString();
        if(!s.archived) {
            archive.post().uri("/v1/discovery-archives").body(snapshot(s)).retrieve().toBodilessEntity();s.archived=true;return;
        }
        if(!s.notified) {
            notices.queue(s.id+":complete:"+s.iteration,"Research ready",
                "Discovery "+s.id+" completed its current search scope. "+s.handoffs.size()+" product briefs are available. Weekly deterministic trend watching is scheduled. No new deep analysis will run without your approval.");
            s.notified=true;
        }
        s.status="WATCHING";s.error=null;s.changes.clear();s.nextWatch=System.currentTimeMillis()+Duration.ofDays(7).toMillis();
    });}
    public Map<String,Object> snapshot(DiscoveryState s) {
        var result=new LinkedHashMap<String,Object>();result.put("runId",s.id);result.put("iteration",s.iteration);
        result.put("query",s.query);result.put("protocol",Discovery.VERSION);result.put("finishedAt",s.finishedAt);
        result.put("queries",s.queries);result.put("assessments",s.assessments);result.put("productDefinitions",s.handoffs);
        result.put("listingCount",s.listings.size());result.put("shopCount",s.shops.size());
        result.put("analysisOrigin","API_PIPELINE");result.put("trendContext",s.changes);result.put("excludedListingIds",s.excluded);
        result.put("notice","Exhausted within sampled search scope, not a census or proof of demand.");
        return result;
    }
    @Scheduled(fixedDelayString="${research.discovery.watch-delay-ms:60000}")
    public void watchTick(){each("WATCH",s->{
        if(!"WATCHING".equals(s.status)||s.nextWatch>System.currentTimeMillis())return;
        var queries=s.queries.stream().filter(q->q.successful&&!"SPLIT_INTO_FOCUSED_SAMPLES".equals(q.stopReason)).toList();
        if(s.watchIndex<queries.size()) {
            var q=queries.get(s.watchIndex);Map<String,Object> raw;
            try{raw=provider.search(q.text,25,0);}catch(Exception e){s.error="TREND_FETCH_FAILED";s.nextWatch=System.currentTimeMillis()+Duration.ofHours(24).toMillis();return;}
            if(!(raw.get("results") instanceof List<?> rows)||!(raw.get("count") instanceof Number count))throw new IllegalStateException();
            var listings=rows.stream().filter(Map.class::isInstance).map(x->SearchPolicy.listing((Map<?,?>)x)).filter(Objects::nonNull).toList();
            q.latest=SearchPolicy.snapshot(count.intValue(),listings,rows.size());
            var delta=SearchPolicy.changes(q.text,SearchPolicy.watchBaseline(q.baseline),q.latest);
            if(Boolean.TRUE.equals(delta.get("significant")))s.changes.add(delta);
            s.watchIndex++;return;
        }
        s.watchIndex=0;s.error=null;
        if(!s.changes.isEmpty()) {
            s.status="AWAITING_APPROVAL";
            notices.queue(s.id+":trend:"+s.iteration+":"+hash(json.writeValueAsString(s.changes)),"Marketplace change detected",
                "Discovery "+s.id+" has a significant search-sample change. Inspect its changes and explicitly approve or dismiss deep analysis. Nothing was sent to a model.");
        } else s.nextWatch=System.currentTimeMillis()+Duration.ofDays(7).toMillis();
    });}
    private static void requireJob(String status){if(Set.of("BLOCKED","FAILED","CANCELLED").contains(status))throw new IllegalStateException("Child job requires attention");}
    static String meaningful(Listing l){return hash(l.title()+"|"+l.description()+"|"+l.tags()+"|"+l.price()+"|"+l.currency()+"|"+l.shopId());}
    static String hash(String s) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))).substring(0,32);}
        catch(Exception e){throw new IllegalStateException(e);}
    }
}
