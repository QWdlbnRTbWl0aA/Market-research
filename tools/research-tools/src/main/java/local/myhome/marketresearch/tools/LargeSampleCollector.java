package local.myhome.marketresearch.tools;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import tools.jackson.databind.ObjectMapper;

/** Repeatable version of the unchanged-adapter runtime collection used before the frozen agent baseline. */
final class LargeSampleCollector {
    static final List<String> QUERIES=List.of("halloween preschool activity printable","halloween classroom bingo printable",
        "halloween scavenger hunt printable","halloween party games printable","halloween handprint craft printable",
        "halloween teal pumpkin sign printable","halloween invitation editable","halloween svg bundle","halloween clipart png","halloween planner digital");
    record Limits(int listings,int shops,int pages,int listingReviews,int localCap,int maxNewCalls) {
        Limits {if(listings<1||shops<1||pages<1||listingReviews<0||localCap<maxNewCalls||maxNewCalls<1)throw new IllegalArgumentException("Invalid collection limits");}
        static Limits standard(){return new Limits(5000,50,100,20,QuotaLedger.DAILY_REQUEST_LIMIT,250);}
    }
    private final Path root;private final EtsyGateway gateway;private final QuotaLedger ledger;private final ObjectMapper json;
    private final Limits limits;
    LargeSampleCollector(Path root,EtsyGateway gateway,QuotaLedger ledger,ObjectMapper json){this(root,gateway,ledger,json,Limits.standard());}
    LargeSampleCollector(Path root,EtsyGateway gateway,QuotaLedger ledger,ObjectMapper json,Limits limits){
        this.root=root;this.gateway=gateway;this.ledger=ledger;this.json=json;this.limits=limits;
    }
    Map<String,Object> run() throws Exception {
        // Check the local budget before discarding the existing corpus; startup makes no provider requests.
        return ledger.locked(limits.localCap(),limits.maxNewCalls(),budget->{
            int removed=RunWorkspace.clean(root),before=budget.priorAttempts();Path out=root.resolve("data/halloween-run/comparison");
            Map<String,Object> manifest=new LinkedHashMap<>();manifest.put("startedAt",Instant.now().toString());
            manifest.put("status","COLLECTING");manifest.put("cleanedResearchFiles",removed);manifest.put("maxNewRequests",limits.maxNewCalls());
            var listings=new LinkedHashMap<String,Map<String,Object>>();var provenance=new LinkedHashMap<String,Set<String>>();
            int pages=0;
            try {
                get("/openapi-ping",budget,before,true);manifest.put("beforeQuota",budget.quota());
                if(budget.number("x-remaining-today")<limits.maxNewCalls()+10)throw new ToolFailure("Provider quota cannot cover collection and reserve.");
                for(int q=0;q<QUERIES.size()&&listings.size()<limits.listings()&&pages<limits.pages();q++) {
                    String query=QUERIES.get(q);int added=0;
                    for(int p=0;p<8&&added<500&&listings.size()<limits.listings()&&pages<limits.pages();p++) {
                        var response=search(query,p,budget,before);PrivateJson.write(out.resolve("page-"+q+"-"+p+".json"),response,json);pages++;
                        int previous=listings.size();add(rows(response),query,listings,provenance);added+=listings.size()-previous;
                        manifest.put("digitalListings",listings.size());manifest.put("searchPages",pages);PrivateJson.write(out.resolve("manifest.json"),manifest,json);
                        if(rows(response).size()<100)break;
                    }
                }
                for(int p=0;listings.size()<limits.listings()&&p<20&&pages<limits.pages();p++) {
                    var response=search("halloween printable",p,budget,before);PrivateJson.write(out.resolve("page-supplement-"+p+".json"),response,json);pages++;
                    add(rows(response),"halloween printable",listings,provenance);if(rows(response).size()<100)break;
                }
                if(listings.size()!=limits.listings())throw new ToolFailure("Collection target not reached within request bounds.");
                PrivateJson.write(out.resolve("listings.json"),listings.values(),json);PrivateJson.write(out.resolve("provenance.json"),provenance,json);
                Set<String> sampled=new LinkedHashSet<>();
                for(String query:QUERIES) {
                    var candidates=listings.values().stream().filter(r->r.get("shop_id")!=null&&provenance.get(String.valueOf(r.get("listing_id"))).contains(query))
                        .sorted(Comparator.comparingLong(LargeSampleCollector::favorites)).toList();
                    for(double fraction:new double[]{.1,.3,.5,.7,.9})if(!candidates.isEmpty())sampled.add(String.valueOf(candidates.get(Math.min(candidates.size()-1,(int)(candidates.size()*fraction))).get("shop_id")));
                }
                for(var row:listings.values())if(sampled.size()<limits.shops()&&row.get("shop_id")!=null)sampled.add(String.valueOf(row.get("shop_id")));
                if(sampled.size()<limits.shops())throw new ToolFailure("Insufficient distinct shops; no synthetic padding.");
                List<Map<String,Object>> shops=new ArrayList<>();Map<String,Object> reviews=new LinkedHashMap<>();
                for(String shop:sampled.stream().limit(limits.shops()).toList()) {
                    var raw=get("/shops/"+shop,budget,before,false);var safe=new LinkedHashMap<String,Object>();
                    for(String key:List.of("shop_id","created_timestamp","transaction_sold_count","review_count","review_average","currency_code","title","listing_active_count","digital_listing_count","is_vacation"))
                        if(raw.get(key)!=null)safe.put(key,raw.get(key));
                    shops.add(safe);PrivateJson.write(out.resolve("shops.json"),shops,json);
                    reviews.put(shop,sanitize(get("/shops/"+shop+"/reviews?limit=100",budget,before,false),null));PrivateJson.write(out.resolve("shop-reviews.json"),reviews,json);
                    manifest.put("shops",shops.size());
                }
                Set<String> targeted=new LinkedHashSet<>();
                for(String query:provenance.values().stream().flatMap(Collection::stream).distinct().toList())
                    listings.values().stream().filter(r->provenance.get(String.valueOf(r.get("listing_id"))).contains(query))
                        .sorted(Comparator.comparingLong(LargeSampleCollector::favorites).reversed()).limit(2).forEach(r->targeted.add(String.valueOf(r.get("listing_id"))));
                for(String id:listings.keySet())if(targeted.size()<limits.listingReviews())targeted.add(id);
                Map<String,Object> direct=new LinkedHashMap<>();
                for(String id:targeted.stream().limit(limits.listingReviews()).toList()) {
                    direct.put(id,sanitize(get("/listings/"+id+"/reviews?limit=100",budget,before,false),id));PrivateJson.write(out.resolve("listing-reviews.json"),direct,json);
                }
                manifest.put("digitalListings",listings.size());manifest.put("searchPages",pages);manifest.put("targetedListingReviews",direct.size());
                manifest.put("sampling","Ten query families, score-ranked pages; shop engagement quantiles; targeted high-engagement listing reviews. Not representative.");
                manifest.put("status","SUCCEEDED");return manifest;
            } finally {
                if(!"SUCCEEDED".equals(manifest.get("status")))manifest.put("status","FAILED");
                manifest.put("finishedAt",Instant.now().toString());manifest.put("totalEtsyCalls",budget.priorAttempts()-before);
                manifest.put("afterQuota",budget.quota());PrivateJson.write(out.resolve("manifest.json"),manifest,json);
            }
        });
    }
    private Map<String,Object> search(String query,int page,QuotaLedger.Budget budget,int before) throws Exception {
        return get("/listings/active?keywords="+URLEncoder.encode(query,StandardCharsets.UTF_8)+"&sort_on=score&limit=100&offset="+(page*100),budget,before,false);
    }
    private Map<String,Object> get(String path,QuotaLedger.Budget budget,int before,boolean probe) throws Exception {
        if(budget.priorAttempts()-before>=limits.maxNewCalls())throw new ToolFailure("Assignment Etsy call bound reached.");
        return gateway.get(path,budget,probe);
    }
    private void add(List<Map<String,Object>> rows,String query,Map<String,Map<String,Object>> selected,Map<String,Set<String>> provenance) {
        for(var row:rows)if("download".equals(row.get("listing_type"))&&row.get("listing_id")!=null) {
            String id=String.valueOf(row.get("listing_id"));provenance.computeIfAbsent(id,k->new LinkedHashSet<>()).add(query);
            if(selected.size()<limits.listings())selected.putIfAbsent(id,row);
        }
    }
    static long favorites(Map<String,Object> row){return row.get("num_favorers") instanceof Number n?n.longValue():0;}
    @SuppressWarnings("unchecked") private static List<Map<String,Object>> rows(Map<String,Object> response) {
        if(!(response.get("results") instanceof List<?> rows))throw new ToolFailure("Unexpected Etsy results shape.");
        return rows.stream().map(row->(Map<String,Object>)row).toList();
    }
    static Map<String,Object> sanitize(Map<String,Object> response,String listing) {
        List<Map<String,Object>> safe=new ArrayList<>();
        for(var row:rows(response)) {
            var item=new LinkedHashMap<String,Object>();
            for(String key:List.of("listing_id","create_timestamp","created_timestamp","rating","review","language"))if(row.get(key)!=null)item.put(key,row.get(key));
            if(listing!=null)item.putIfAbsent("listing_id",Long.valueOf(listing));safe.add(item);
        }
        return Map.of("count",response.get("count"),"results",safe);
    }
}
