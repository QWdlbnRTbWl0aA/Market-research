package local.myhome.marketresearch.tools;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.springframework.web.client.RestClient;
import local.myhome.marketresearch.contracts.analysis.MarketResearch.*;
import tools.jackson.databind.ObjectMapper;

/** Bounded end-to-end research command. Public HTTP analysis operations remain short and deterministic. */
final class HalloweenResearchRun {
    private final Path root; private final EtsyGateway etsy; private final QuotaLedger ledger;
    private final ResearchModels models; private final ObjectMapper json;
    HalloweenResearchRun(Path root,EtsyGateway etsy,QuotaLedger ledger,ResearchModels models,ObjectMapper json) {
        this.root=root;this.etsy=etsy;this.ledger=ledger;this.models=models;this.json=json;
    }
    @SuppressWarnings("unchecked")
    Map<String,Object> run() throws Exception {
        int removed=RunWorkspace.clean(root);
        Path output=root.resolve("data/halloween-run");
        PrivateJson.directory(output);
        Path temporary=Files.createTempDirectory("market-analysis-");
        Map<String,Object> manifest=new LinkedHashMap<>();
        manifest.put("startedAt",Instant.now().toString());manifest.put("cleanedResearchFiles",removed);
        manifest.put("targetMonthlyUsd",30);manifest.put("maxNewEtsyCalls",20);manifest.put("status","RUNNING");
        try(var service=new ServiceProcess(root,"analysis-service",temporary,ServiceProcess.freePort(),
                Map.of("spring.datasource.url","jdbc:h2:mem:halloween-business"))) {
            var analysis=RestClient.create(service.base().toString());
            return ledger.locked(QuotaLedger.DAILY_REQUEST_LIMIT,20,budget -> {
                int before=budget.priorAttempts();
                try {
                    etsy.get("/openapi-ping",budget,true);
                    manifest.put("etsyBeforeQuota",budget.quota());
                    if(budget.number("x-remaining-today")<30) throw new ToolFailure("Etsy daily reserve cannot cover bounded research.");
                    var seed=search("halloween printable",60,budget,output.resolve("seed.json"));
                    List<Map<String,Object>> all=new ArrayList<>(rows(seed));
                    var eligible=digital(all);
                    System.out.println("{\"phase\":\"refining_seed\"}");
                    var semantic=models.ask("seed-refinement", """
                        Group these digital Halloween listings by product/use case, marking junk or irrelevant items unusable.
                        Return JSON: {groups:[{id,label,query}],assignments:[{listingId,groupId,usable}]}.
                        Create at most 3 specific product groups, IDs lowercase letters/digits/hyphens.
                        Each query is an actual Etsy keyword phrase: 3-6 words, letters/numbers/spaces/hyphens only.
                        NEVER use descriptive sentences, comma-separated lists, semicolons, OR, or boolean syntax.
                        Example query: halloween preschool scavenger hunt printable.
                        Queries must contain halloween and one specific product/use case, not every category at once.
                        Mark legitimate products outside the selected group scope unusable for this focused experiment.
                        Every provided listingId (string) must occur exactly once in assignments.
                        Even unusable listings must name a valid group. Do not generate listings or revenue estimates.
                        """,digest(eligible));
                    PrivateJson.write(output.resolve("seed-semantics.json"),semantic,json);
                    var groups=parseGroups(semantic);
                    var assignments=parseAssignments(semantic);
                    completeAssignments(eligible,groups,assignments,output,"seed");
                    long count=((Number)seed.getOrDefault("count",0)).longValue();
                    var plan=analysis.post().uri("/v1/refinement-plans")
                        .body(new RefinementRequest("halloween printable",count,20,all,groups,assignments))
                        .retrieve().body(RefinementPlan.class);
                    if(plan==null || plan.searches().isEmpty()) throw new ToolFailure("Model did not supply usable refinement queries.");
                    PrivateJson.write(output.resolve("refinement-plan.json"),plan,json);
                    List<Map<String,Object>> focused=new ArrayList<>();
                    List<Set<String>> focusedIds=new ArrayList<>();
                    for(var child:plan.searches()) {
                        var response=search(child.query(),100,budget,output.resolve("focused-"+child.groupId()+".json"));
                        var sample=rows(response);all.addAll(sample);
                        long childCount=((Number)response.getOrDefault("count",0)).longValue();
                        focused.add(new LinkedHashMap<>(Map.of("groupId",child.groupId(),"query",child.query(),"count",childCount,
                            "estimatedPages",(long)Math.ceil(childCount/100.0),"furtherSplitNeeded",childCount>2000,
                            "returned",sample.size(),"digital",digital(sample).size())));
                        Set<String> childIds=new HashSet<>();
                        digital(sample).forEach(r->childIds.add(String.valueOf(r.get("listing_id"))));
                        focusedIds.add(childIds);
                    }
                    var oversized=focused.stream().filter(row->Boolean.TRUE.equals(row.get("furtherSplitNeeded"))).toList();
                    if(!oversized.isEmpty()) {
                        var narrowed=models.ask("bounded-search-split", """
                            These literal Etsy searches still have too many results. Return JSON {groups:[{id,label,query}]}.
                            Keep the supplied group IDs and labels; provide at most one narrower query per oversized group.
                            Each query must have 4-8 literal words, contain halloween, and use only letters/numbers/spaces/hyphens.
                            Add a specific product, audience/age or use case. Do NOT use generic lists, prose, OR or punctuation.
                            Example refinements: halloween preschool handprint keepsake printable; halloween classroom bingo cards;
                            halloween teal pumpkin allergy sign printable. Prefer focused pools below 2000 results.
                            Do not invent counts; these queries will be probed. No more than three queries.
                            """,Map.of("groups",groups,"oversizedSearches",oversized));
                        PrivateJson.write(output.resolve("narrowed-queries.json"),narrowed,json);
                        var proposed=parseGroups(narrowed);
                        var checked=analysis.post().uri("/v1/refinement-plans")
                            .body(new RefinementRequest("halloween printable",count,20,List.of(),proposed,List.of()))
                            .retrieve().body(RefinementPlan.class);
                        Set<String> knownGroups=new HashSet<>();groups.forEach(g->knownGroups.add(g.id()));
                        if(checked!=null)for(var child:checked.searches()) {
                            if(!knownGroups.contains(child.groupId()))throw new ToolFailure("Narrowing invented a group identity.");
                            var response=search(child.query(),100,budget,output.resolve("narrowed-"+child.groupId()+".json"));
                            var sample=rows(response);all.addAll(sample);
                            long childCount=((Number)response.getOrDefault("count",0)).longValue();
                            focused.add(new LinkedHashMap<>(Map.of("groupId",child.groupId(),"query",child.query(),
                                "count",childCount,"estimatedPages",(long)Math.ceil(childCount/100.0),
                                "furtherSplitNeeded",childCount>2000,"returned",sample.size(),"digital",digital(sample).size(),"depth",2)));
                            Set<String> childIds=new HashSet<>();
                            digital(sample).forEach(r->childIds.add(String.valueOf(r.get("listing_id"))));
                            focusedIds.add(childIds);
                        }
                    }
                    Map<String,Map<String,Object>> unique=new LinkedHashMap<>();
                    for(var row:all) if("download".equals(row.get("listing_type")))
                        unique.putIfAbsent(String.valueOf(row.get("listing_id")),row);
                    Set<String> classified=new HashSet<>();
                    assignments.forEach(a->classified.add(a.listingId()));
                    var pending=unique.entrySet().stream().filter(e->!classified.contains(e.getKey())).map(Map.Entry::getValue).toList();
                    for(int offset=0;offset<pending.size();offset+=100) {
                        var batch=pending.subList(offset,Math.min(offset+100,pending.size()));
                        var classifiedBatch=models.ask("focused-classification", """
                            Assign each digital listing to an existing group and mark irrelevant/junk items unusable.
                            Return JSON {assignments:[{listingId,groupId,usable}]} with every supplied listingId exactly once.
                            Use only the supplied group IDs; do not add groups. This is classification, not revenue estimation.
                            """,Map.of("groups",groups,"listings",digest(batch)));
                        PrivateJson.write(output.resolve("classified-"+offset+".json"),classifiedBatch,json);
                        var additional=parseAssignments(classifiedBatch);
                        completeAssignments(batch,groups,additional,output,"batch-"+offset);
                        assignments.addAll(additional);
                    }
                    Map<String,Assignment> assignedById=new HashMap<>();
                    assignments.forEach(a->assignedById.put(a.listingId(),a));
                    Set<String> seenInQueries=new HashSet<>();
                    eligible.forEach(row->seenInQueries.add(String.valueOf(row.get("listing_id"))));
                    for(int i=0;i<focused.size();i++) {
                        long usable=focusedIds.get(i).stream().filter(id->assignedById.get(id)!=null&&assignedById.get(id).usable()).count();
                        long novel=focusedIds.get(i).stream().filter(id->!seenInQueries.contains(id))
                            .filter(id->assignedById.get(id)!=null&&assignedById.get(id).usable()).count();
                        focused.get(i).put("usablePerQuery",usable);
                        focused.get(i).put("newUsablePerQuery",novel);
                        seenInQueries.addAll(focusedIds.get(i));
                    }
                    List<Map<String,Object>> shops=new ArrayList<>();
                    Map<String,Map<String,Object>> reviews=new LinkedHashMap<>();
                    Set<String> enrichedShops=new HashSet<>();
                    // Sample near the engagement median, rather than selecting hit listings.
                    for(var group:groups) {
                        List<Map<String,Object>> candidates=assignments.stream().filter(a->a.usable()&&a.groupId().equals(group.id()))
                            .map(a->unique.get(a.listingId())).filter(Objects::nonNull)
                            .filter(r->r.get("shop_id")!=null).sorted(Comparator.comparingLong(HalloweenResearchRun::favorites)).toList();
                        var usd=candidates.stream().filter(r->r.get("price") instanceof Map<?,?> money
                            && "USD".equals(money.get("currency_code"))).toList();
                        if(!usd.isEmpty()) candidates=usd;
                        List<Map<String,Object>> centered=new ArrayList<>();
                        for(int index=candidates.size()/2;index<candidates.size();index++)centered.add(candidates.get(index));
                        for(int index=candidates.size()/2-1;index>=0;index--)centered.add(candidates.get(index));
                        Set<String> sampledShops=new HashSet<>();
                        for(var listing:centered) {
                            String shop=String.valueOf(listing.get("shop_id")),id=String.valueOf(listing.get("listing_id"));
                            if(!sampledShops.add(shop)) continue;
                            if(enrichedShops.add(shop)) {
                                var details=etsy.get("/shops/"+shop,budget,false);
                                shops.add(selectShop(details));
                                PrivateJson.write(output.resolve("shop-"+shop+".json"),selectShop(details),json);
                            }
                            long since=Instant.now().minus(Duration.ofDays(30)).getEpochSecond();
                            var response=etsy.get("/listings/"+id+"/reviews?limit=100&min_created="+since,budget,false);
                            // Keep only dates/transaction identity/rating; no buyer identifiers or review prose needed.
                            var sanitized=sanitizeReviews(response);
                            reviews.put(id,sanitized);PrivateJson.write(output.resolve("reviews-"+id+".json"),sanitized,json);
                            if(sampledShops.size()==2) break;
                        }
                    }
                    var request=new BusinessRequest(Instant.now(),new BigDecimal("30"),new ArrayList<>(unique.values()),
                        groups,assignments,shops,reviews,Map.of());
                    var report=analysis.post().uri("/v1/business-analyses").body(request).retrieve().body(BusinessReport.class);
                    if(report==null) throw new ToolFailure("Business analyzer returned no report.");
                    PrivateJson.write(output.resolve("business-evidence.json"),report,json);
                    System.out.println("{\"phase\":\"reviewing_business_evidence\"}");
                    var reasoning=models.ask("business-reasoning", """
                        Evaluate modest digital product experiments aiming for a few tens of USD monthly, not hit listings.
                        Use supplied deterministic financial evidence and shop context. Never invent shop revenue or a probability.
                        Review-rate revenue scenarios are uncalibrated assumptions, not observed sales or credible bounds.
                        Listing counts NEVER validate demand. Shop lifetime transactions NEVER establish monthly category revenue.
                        Use LOW confidence unless at least two independent shops have recent reviews for the group.
                        Halloween is seasonal; new sellers do not inherit competitor traffic. Production is cheap but manual work matters.
                        TEST means a small automated learning experiment, NOT a prediction of achieving the revenue target.
                        Missing reviews, competition and missing demand proof ALONE never justify DEFER.
                        Do not assume signs/decor or any other digital format needs manual customization. Unknown work stays unknown.
                        DEFER requires a specific evidence-backed rights, support, automation or operating-cost obstacle,
                        or explicit portfolio budget prioritization; do not invent these obstacles from the category name.
                        Favor non-personalized reusable templates; never require stronger pre-production demand proof.
                        Learn mainly from published orders, receipts, exposure and intervention time, not favorites alone.
                        Return JSON {recommendations:[{groupId,decision,confidence,rationale,differentiator,automationPlan,
                        risks:[string],citedListingIds:[string],citedShopIds:[string]}]}.
                        decision TEST or DEFER; confidence LOW or MEDIUM only. Cite IDs present in evidence.
                        Favor cheap automated learning; weak evidence need not block a small experiment.
                        """,Map.of("financialEvidence",report,"productExamples",businessExamples(groups,assignments,unique)));
                    PrivateJson.write(output.resolve("model-business-review.json"),reasoning,json);
                    var completed=new BusinessRequest(request.observedAt(),request.targetMonthlyUsd(),request.listings(),
                        groups,assignments,shops,reviews,reasoning);
                    var finalReport=analysis.post().uri("/v1/business-analyses").body(completed).retrieve().body(BusinessReport.class);
                    PrivateJson.write(output.resolve("business-report.json"),finalReport,json);
                    manifest.put("status","SUCCEEDED");manifest.put("seedCount",count);
                    manifest.put("focusedSearches",focused);manifest.put("uniqueDigitalListings",unique.size());
                    manifest.put("shopEvidence",shops.size());manifest.put("reviewedListings",reviews.size());
                    manifest.put("usableDigitalListings",assignments.stream().filter(Assignment::usable).count());
                    manifest.put("newEtsyCalls",budget.priorAttempts()-before);manifest.put("etsyAfterQuota",budget.quota());
                    return manifest;
                } finally {
                    manifest.put("newEtsyCalls",budget.priorAttempts()-before);
                    manifest.put("finishedAt",Instant.now().toString());
                    if(!"SUCCEEDED".equals(manifest.get("status")))manifest.put("status","FAILED");
                    PrivateJson.write(output.resolve("manifest.json"),manifest,json);
                }
            });
        } finally {
            try(var walk=Files.walk(temporary)){for(var file:walk.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(file);}
        }
    }

    private Map<String,Object> search(String query,int limit,QuotaLedger.Budget budget,Path output) throws Exception {
        var value=etsy.get("/listings/active?keywords="+URLEncoder.encode(query,StandardCharsets.UTF_8)
            +"&sort_on=score&limit="+limit,budget,false);
        PrivateJson.write(output,value,json);return value;
    }
    @SuppressWarnings("unchecked") private static List<Map<String,Object>> rows(Map<String,Object> response) {
        if(!(response.get("results") instanceof List<?> values))throw new ToolFailure("Invalid Etsy search response.");
        return values.stream().filter(Map.class::isInstance).map(r->(Map<String,Object>)r).toList();
    }
    private static List<Map<String,Object>> digital(List<Map<String,Object>> listings) {
        Map<String,Map<String,Object>> result=new LinkedHashMap<>();
        for(var row:listings)if("download".equals(row.get("listing_type")) && row.get("listing_id")!=null)
            result.putIfAbsent(String.valueOf(row.get("listing_id")),row);
        return new ArrayList<>(result.values());
    }
    private static List<Map<String,Object>> digest(List<Map<String,Object>> listings) {
        return listings.stream().map(row->Map.<String,Object>of("listingId",String.valueOf(row.get("listing_id")),
            "title",String.valueOf(row.getOrDefault("title","")).substring(0,Math.min(180,String.valueOf(row.getOrDefault("title","")).length())),
            "tags",row.getOrDefault("tags",List.of()))).toList();
    }
    private static List<Map<String,Object>> businessExamples(List<Group> groups,List<Assignment> assignments,
            Map<String,Map<String,Object>> listings) {
        List<Map<String,Object>> result=new ArrayList<>();
        for(var group:groups) {
            var examples=assignments.stream().filter(a->a.usable()&&a.groupId().equals(group.id()))
                .map(a->listings.get(a.listingId())).filter(Objects::nonNull).limit(6).map(row->{
                    Map<String,Object> example=new LinkedHashMap<>(digest(List.of(row)).getFirst());
                    for(String key:List.of("is_personalizable","is_customizable"))
                        if(row.get(key)!=null)example.put(key,row.get(key));
                    return example;
                }).toList();
            result.add(Map.of("groupId",group.id(),"examples",examples));
        }
        return result;
    }
    @SuppressWarnings("unchecked") private List<Group> parseGroups(Map<String,Object> value) {
        if(!(value.get("groups") instanceof List<?> groups) || groups.isEmpty() || groups.size()>3)
            throw new ToolFailure("Invalid semantic groups.");
        return groups.stream().map(g->json.convertValue(g,Group.class)).toList();
    }
    private List<Assignment> parseAssignments(Map<String,Object> value) {
        if(!(value.get("assignments") instanceof List<?> assignments))throw new ToolFailure("Missing model assignments.");
        for(var row:assignments) if(!(row instanceof Map<?,?> item) || !(item.get("usable") instanceof Boolean))
            throw new ToolFailure("Invalid model usability assignment.");
        return new ArrayList<>(assignments.stream().map(a->json.convertValue(a,Assignment.class)).toList());
    }
    private void completeAssignments(List<Map<String,Object>> rows,List<Group> groups,List<Assignment> assignments,
            Path output,String label) throws Exception {
        var missing=missingAssignments(rows,groups,assignments);
        if(!missing.isEmpty()) {
            // One bounded semantic repair, not a transport retry or an unbounded research loop.
            var repair=models.ask("classification-repair", """
                Classify ONLY these previously omitted listings using the supplied groups.
                Return JSON {assignments:[{listingId,groupId,usable}]}.
                Include every supplied listingId exactly once. No other IDs or new groups.
                """,Map.of("groups",groups,"listings",digest(missing)));
            PrivateJson.write(output.resolve("repair-"+label+".json"),repair,json);
            assignments.addAll(parseAssignments(repair));
        }
        validate(rows,groups,assignments);
    }
    static List<Map<String,Object>> missingAssignments(List<Map<String,Object>> rows,List<Group> groups,List<Assignment> assignments) {
        Set<String> ids=new HashSet<>(),seen=new HashSet<>(),groupIds=new HashSet<>();
        rows.forEach(r->ids.add(String.valueOf(r.get("listing_id"))));
        groups.forEach(g->groupIds.add(g.id()));
        for(var a:assignments)if(!ids.contains(a.listingId()) || !groupIds.contains(a.groupId()) || !seen.add(a.listingId()))
            throw new ToolFailure("Model cited duplicate/unknown classification evidence.");
        return rows.stream().filter(r->!seen.contains(String.valueOf(r.get("listing_id")))).toList();
    }
    static void validate(List<Map<String,Object>> rows,List<Group> groups,List<Assignment> assignments) {
        if(!missingAssignments(rows,groups,assignments).isEmpty())throw new ToolFailure("Model omitted listing classification evidence.");
    }
    private static long favorites(Map<String,Object> listing) {
        return listing.get("num_favorers") instanceof Number n?n.longValue():0;
    }
    private static Map<String,Object> selectShop(Map<String,Object> raw) {
        Map<String,Object> result=new LinkedHashMap<>();
        for(String key:List.of("shop_id","transaction_sold_count","review_count","review_average","created_timestamp","currency_code"))
            result.put(key,raw.get(key));
        return result;
    }
    private static Map<String,Object> sanitizeReviews(Map<String,Object> response) {
        List<Map<String,Object>> result=new ArrayList<>();
        for(var raw:rows(response)) {
            Map<String,Object> item=new LinkedHashMap<>();
            for(var key:List.of("transaction_id","listing_id","create_timestamp","created_timestamp","rating"))
                if(raw.get(key)!=null)item.put(key,raw.get(key));
            result.add(item);
        }
        Map<String,Object> sanitized=new LinkedHashMap<>();sanitized.put("count",response.get("count"));sanitized.put("results",result);
        return sanitized;
    }
}
