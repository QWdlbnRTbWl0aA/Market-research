package local.myhome.marketresearch.tools;

import java.math.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import tools.jackson.databind.ObjectMapper;

/** Evidence projection/retrieval only. No opportunity scores, business ranking or revenue estimates. */
final class FrozenCorpus {
    final Map<String,Map<String,Object>> listings=new LinkedHashMap<>(), shops=new LinkedHashMap<>(),reviews=new LinkedHashMap<>();
    final Map<String,String> hashes=new LinkedHashMap<>();
    final Map<String,Object> manifest;
    private final Map<String,List<String>> provenance;
    final Instant observedAt;
    @SuppressWarnings("unchecked")
    FrozenCorpus(Path root,ObjectMapper json) throws Exception {
        Path directory=root.resolve("data/halloween-run/comparison");
        Path baseline=root.resolve("docs/research/2026-10-22-agent-baseline.md");
        if(!Files.isRegularFile(baseline))throw new ToolFailure("A frozen agent baseline is required before provider analysis.");
        String frozen=Files.readString(baseline);
        for(String name:List.of("listings.json","shops.json","shop-reviews.json","listing-reviews.json")) {
            String digest=sha(Files.readAllBytes(directory.resolve(name)));
            if(!frozen.contains(digest))throw new ToolFailure("Corpus differs from frozen agent baseline; no model call sent.");
            hashes.put(name,digest);
        }
        hashes.put("provenance.json",sha(Files.readAllBytes(directory.resolve("provenance.json"))));
        manifest=json.readValue(Files.readString(directory.resolve("manifest.json")),Map.class);
        if(!"SUCCEEDED".equals(manifest.get("status")))throw new ToolFailure("Collection has not completed.");
        observedAt=Instant.parse(manifest.get("finishedAt").toString());
        provenance=json.readValue(Files.readString(directory.resolve("provenance.json")),Map.class);
        for(var row:readRows(directory.resolve("listings.json"),json)) {
            if(!"download".equals(row.get("listing_type")))throw new ToolFailure("Frozen corpus contains a non-digital listing.");
            if(listings.put(String.valueOf(row.get("listing_id")),row)!=null)throw new ToolFailure("Duplicate frozen listing.");
        }
        for(var row:readRows(directory.resolve("shops.json"),json))shops.put(String.valueOf(row.get("shop_id")),row);
        Map<String,Object> raw=json.readValue(Files.readString(directory.resolve("shop-reviews.json")),Map.class);
        for(var entry:raw.entrySet())addReviews(entry.getKey(),(Map<String,Object>)entry.getValue(),null);
        raw=json.readValue(Files.readString(directory.resolve("listing-reviews.json")),Map.class);
        for(var entry:raw.entrySet()) {
            var listing=listings.get(entry.getKey());
            if(listing!=null)addReviews(String.valueOf(listing.get("shop_id")),(Map<String,Object>)entry.getValue(),entry.getKey());
        }
    }
    @SuppressWarnings("unchecked") private static List<Map<String,Object>> readRows(Path file,ObjectMapper json) throws Exception {
        return json.readValue(Files.readString(file),List.class);
    }
    @SuppressWarnings("unchecked") private void addReviews(String shop,Map<String,Object> response,String suppliedListing) throws Exception {
        if(!(response.get("results") instanceof List<?> rows))throw new ToolFailure("Invalid review evidence.");
        List<Map<String,Object>> selected=new ArrayList<>();
        var typed=rows.stream().map(r->(Map<String,Object>)r).toList();
        selected.addAll(typed.stream().sorted(Comparator.comparingLong(FrozenCorpus::reviewTime).reversed()).limit(2).toList());
        typed.stream().min(Comparator.comparingDouble(r->((Number)r.getOrDefault("rating",5)).doubleValue())).ifPresent(selected::add);
        // Preserve every returned review of an in-cohort listing (small in this sample).
        selected.addAll(typed.stream().filter(r->listings.containsKey(String.valueOf(r.get("listing_id")))).toList());
        for(var row:selected) {
            String listing=String.valueOf(row.getOrDefault("listing_id",suppliedListing));
            String text=String.valueOf(row.getOrDefault("review",""));long at=reviewTime(row);
            String id="R"+sha((shop+"|"+listing+"|"+at+"|"+text).getBytes(java.nio.charset.StandardCharsets.UTF_8)).substring(0,20);
            var value=new LinkedHashMap<String,Object>();value.put("id",id);value.put("shopId",shop);value.put("listingId",listing);
            value.put("listingInCorpus",listings.containsKey(listing));value.put("at",at);
            value.put("recent30Days",at>=observedAt.minusSeconds(30L*86400).getEpochSecond()&&at<=observedAt.getEpochSecond());
            value.put("rating",row.get("rating"));value.put("text",clip(text,400));
            value.put("scope",suppliedListing==null?"SHOP_REVIEW_SAMPLE":"TARGETED_LISTING_REVIEW_SAMPLE");
            value.put("responseCount",response.get("count"));value.put("returned",rows.size());reviews.putIfAbsent(id,value);
        }
    }
    static long reviewTime(Map<String,Object> row) {
        return row.getOrDefault("create_timestamp",row.getOrDefault("created_timestamp",0)) instanceof Number n?n.longValue():0;
    }
    List<Map<String,Object>> compactListings() {
        List<Map<String,Object>> result=new ArrayList<>();
        for(var entry:listings.entrySet()) {
            var row=entry.getValue();var value=new LinkedHashMap<String,Object>();
            value.put("id",entry.getKey());value.put("shopId",String.valueOf(row.get("shop_id")));
            value.put("title",clip(String.valueOf(row.getOrDefault("title","")),150));
            value.put("price",row.get("price"));value.put("favorites",row.get("num_favorers"));value.put("views",row.get("views"));
            value.put("created",row.get("original_creation_timestamp"));value.put("personalizable",row.get("is_personalizable"));
            if(row.get("tags") instanceof List<?> tags)value.put("tags",tags.stream().limit(3).toList());
            result.add(value);
        }
        return result;
    }
    Map<String,Object> context() {
        return Map.of("observedAt",observedAt.toString(),"sourceHashes",hashes,"corpusListings",listings.size(),
            "shopProfiles",shops.values(),"reviewExcerpts",reviews.values(),
            "limits",List.of("Score-ranked convenience sample; shops selected across within-query engagement quantiles, not randomly.",
                "Only 50 shops profiled, not all represented sellers. Catalog titles are self-descriptions, not verified catalog composition.",
                "Review dates are not purchase dates; excerpts are bounded, selected for latest/low ratings and in-corpus matches.",
                "Shop lifetime transactions are not monthly product sales or revenue. No FX rates, actual revenue or new-seller conversion supplied.",
                "Unrelated shop reviews must not be attributed to the candidate product. Zero reviews does not prove zero sales."));
    }
    Map<String,Object> dossier(Collection<Map<String,Object>> scans) {
        Set<String> cited=new LinkedHashSet<>();
        scans.forEach(scan->collectListingIds(scan,cited));
        if(cited.isEmpty()||cited.size()>100)throw new ToolFailure("Shortlist retrieval requires 1..100 existing listing citations.");
        List<Map<String,Object>> rows=new ArrayList<>();
        for(String id:cited) {
            var raw=listings.get(id);if(raw==null)throw new ToolFailure("Unknown shortlist listing.");
            var row=new LinkedHashMap<String,Object>();row.put("id",id);row.put("shopId",String.valueOf(raw.get("shop_id")));
            for(String key:List.of("title","price","tags","views","num_favorers","original_creation_timestamp","is_personalizable","is_customizable"))
                if(raw.get(key)!=null)row.put(key,raw.get(key));
            row.put("description",clip(String.valueOf(raw.getOrDefault("description","")),1600));
            row.put("queries",provenance.getOrDefault(id,List.of()));rows.add(row);
        }
        return Map.of("context",context(),"sourceListings",rows);
    }
    private static void collectListingIds(Object node,Set<String> ids) {
        if(node instanceof Map<?,?> map)for(var e:map.entrySet()) {
            if("listingIds".equals(e.getKey())&&e.getValue() instanceof List<?> values)values.forEach(v->ids.add(v.toString()));
            else collectListingIds(e.getValue(),ids);
        } else if(node instanceof List<?> list)list.forEach(v->collectListingIds(v,ids));
    }
    Map<String,Object> repairEvidence(Object response) {
        Set<String> ids=new LinkedHashSet<>();collectListingIds(response,ids);
        var known=compactListings().stream().filter(row->ids.contains(row.get("id").toString())).limit(100).toList();
        return Map.of("context",context(),"knownCandidateListings",known,
            "instruction","Remove unsupported citations and claims; these source records and the context are authoritative. Do not invent replacements.");
    }
    void citations(Object node) {walk(node,0);}
    private void walk(Object node,int depth) {
        if(depth>14)throw new ToolFailure("Model output nesting exceeded.");
        if(node instanceof Map<?,?> map) {
            if(map.size()>40)throw new ToolFailure("Model output fields exceeded.");
            for(var e:map.entrySet()) {
                String key=e.getKey().toString();
                if(Set.of("listingIds","shopIds","reviewIds").contains(key)) {
                    if(!(e.getValue() instanceof List<?> ids)||ids.size()>12)throw new ToolFailure("Invalid model citations.");
                    Map<String,?> known=key.equals("listingIds")?listings:reviews;
                    for(var id:ids) {
                        boolean exists=id!=null&&(key.equals("shopIds")
                            ?shops.containsKey(id.toString())||listings.values().stream().anyMatch(row->id.toString().equals(String.valueOf(row.get("shop_id"))))
                            :known.containsKey(id.toString()));
                        if(!exists)throw new ToolFailure("Model cited unknown "+key+" evidence.");
                    }
                }
                walk(e.getValue(),depth+1);
            }
        } else if(node instanceof List<?> list) {
            if(list.size()>60)throw new ToolFailure("Model output array exceeded.");list.forEach(v->walk(v,depth+1));
        } else if(node instanceof String text&&text.length()>6000)throw new ToolFailure("Model output text exceeded.");
    }
    static String clip(String value,int max){return value.substring(0,Math.min(value.length(),max));}
    static String sha(byte[] bytes) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
}
