package local.myhome.marketresearch.analysis.market;

import java.math.*;
import java.time.*;
import java.util.*;
import local.myhome.marketresearch.contracts.analysis.MarketResearch.*;
import local.myhome.marketresearch.contracts.analysis.MarketResearch.Comparable;

/** Core financial evidence calculations. A public shop sales counter is never treated as revenue. */
public final class BusinessAnalyzer {
    private BusinessAnalyzer() {}
    public static BusinessReport analyze(BusinessRequest request) {
        if (request.observedAt()==null || request.targetMonthlyUsd()==null || request.targetMonthlyUsd().signum()<=0
                || request.listings()==null || request.groups()==null || request.assignments()==null
                || request.shops()==null || request.reviews()==null)
            throw new IllegalArgumentException("Incomplete business evidence");
        Map<String,Map<String,Object>> listings=new LinkedHashMap<>(),shops=new HashMap<>();
        for (var row:request.listings()) if ("download".equals(row.get("listing_type")))
            listings.putIfAbsent(String.valueOf(row.get("listing_id")),row);
        for (var shop:request.shops()) shops.put(String.valueOf(shop.get("shop_id")),shop);
        Map<String,Set<String>> members=new HashMap<>();
        Set<String> assigned=new HashSet<>();
        Set<String> groupIds=new HashSet<>();
        for(var group:request.groups()) if(!groupIds.add(group.id())) throw new IllegalArgumentException("Duplicate group");
        for(var assignment:request.assignments()) {
            if (!groupIds.contains(assignment.groupId()) || !listings.containsKey(assignment.listingId()))
                throw new IllegalArgumentException("Model cited unknown evidence");
            if (!assigned.add(assignment.listingId())) throw new IllegalArgumentException("Duplicate listing assignment");
            if (assignment.usable()) members.computeIfAbsent(assignment.groupId(),key->new LinkedHashSet<>()).add(assignment.listingId());
        }
        List<Opportunity> results=new ArrayList<>();
        for (var group:request.groups()) {
            List<BigDecimal> prices=new ArrayList<>();
            Set<String> groupShops=new HashSet<>(),reviewShops=new HashSet<>();
            List<Comparable> comparables=new ArrayList<>();
            for (var id:members.getOrDefault(group.id(),Set.of())) {
                var row=listings.get(id);
                String shop=row.get("shop_id")==null?null:String.valueOf(row.get("shop_id"));
                if(shop!=null) groupShops.add(shop);
                BigDecimal price=usdPrice(row);
                if(price!=null) prices.add(price);
                var review=request.reviews().get(id);
                if (review==null) continue;
                List<?> raw=review.get("results") instanceof List<?> rows ? rows : List.of();
                Set<String> reviewKeys=new HashSet<>();
                int recent=0;
                for (var item:raw) if (item instanceof Map<?,?> r) {
                    Long timestamp=number(r.get("create_timestamp"));
                    if(timestamp==null) timestamp=number(r.get("created_timestamp"));
                    if(timestamp==null || timestamp<request.observedAt().minus(Duration.ofDays(30)).getEpochSecond()
                            || timestamp>request.observedAt().getEpochSecond()) continue;
                    String key=String.valueOf(r.getOrDefault("transaction_id",null))+"|"+timestamp+"|"+r.get("review");
                    if(reviewKeys.add(key)) recent++;
                }
                if(recent>0 && shop!=null) reviewShops.add(shop);
                Long count=number(review.get("count"));
                boolean complete=count!=null && count<=raw.size();
                Integer required=price==null || price.signum()==0?null:request.targetMonthlyUsd().divide(price,0,RoundingMode.CEILING).intValueExact();
                comparables.add(new Comparable(id,shop,price,required,recent,complete,
                    price==null || recent==0 ? null : price.multiply(BigDecimal.valueOf(recent*10L)),
                    price==null || recent==0 ? null : price.multiply(BigDecimal.valueOf(recent*50L))));
            }
            prices.sort(Comparator.naturalOrder());
            BigDecimal median=prices.isEmpty()?null:prices.size()%2==1?prices.get(prices.size()/2)
                :prices.get(prices.size()/2-1).add(prices.get(prices.size()/2)).divide(BigDecimal.valueOf(2));
            Integer needed=median==null || median.signum()==0?null:request.targetMonthlyUsd().divide(median,0,RoundingMode.CEILING).intValueExact();
            long sales=0; boolean observed=false;
            for(var id:groupShops) {
                var shop=shops.get(id);
                if(shop==null) continue;
                Long value=number(shop.get("transaction_sold_count"));
                if(value!=null) {sales+=value;observed=true;}
            }
            String band=reviewShops.size()>=2 ? "MULTI_SHOP_RECENT_PURCHASE_PROXY"
                :reviewShops.size()==1?"SINGLE_SHOP_RECENT_PURCHASE_PROXY":"EXPLORATORY_CHEAP_TEST";
            results.add(new Opportunity(group.id(),group.label(),members.getOrDefault(group.id(),Set.of()).size(),
                groupShops.size(),median,needed,reviewShops.size(),observed?sales:null,null,band,List.copyOf(comparables),
                List.of("Review-rate scenarios assume 2% or 10% of purchases produce a review; uncalibrated, not forecasts.",
                    "Zero observed reviews does not imply zero sales. Results may be capped at 100 reviews.",
                    "Shop lifetime transactions cover all products and all years, not this group's monthly revenue.",
                    "USD prices only; other currencies are not silently converted.",
                    "A new listing does not inherit a competitor's traffic, conversion or sales.")));
        }
        results.sort(Comparator.comparingInt(Opportunity::shopsWithRecentReviews).reversed()
            .thenComparing(Comparator.comparingInt(Opportunity::distinctShops).reversed()));
        var interpretation=request.modelInterpretation()==null?Map.<String,Object>of():request.modelInterpretation();
        validateInterpretation(interpretation,groupIds,listings,shops,members);
        interpretation=capConfidence(interpretation,results);
        return new BusinessReport("modest-recurring-revenue-v1",request.targetMonthlyUsd(),List.copyOf(results),interpretation,
            List.of("Public Etsy endpoints do not provide competitors' verified monthly revenue.",
                "Purchase proxies and explicit scenarios support bounded experiments; no calibrated probability is claimed.",
                "The target is modest monthly gross receipts, not a hit listing or a profit guarantee.",
                "Optimize portfolio receipts against AI/API, compute and manual involvement, including ongoing support.",
                "Halloween evidence is seasonal and cannot be annualized uniformly. No image generation or publication occurs."));
    }
    private static void validateInterpretation(Map<String,Object> interpretation,Set<String> groups,
            Map<String,Map<String,Object>> listings,Map<String,Map<String,Object>> shops,Map<String,Set<String>> members) {
        if(interpretation.isEmpty()) return;
        if(!interpretation.keySet().equals(Set.of("recommendations")))throw new IllegalArgumentException("Unknown model fields");
        if(!(interpretation.get("recommendations") instanceof List<?> rows) || rows.size()>groups.size())
            throw new IllegalArgumentException("Invalid model interpretation");
        Set<String> seen=new HashSet<>();
        for(var row:rows) {
            if(!(row instanceof Map<?,?> value)) throw new IllegalArgumentException("Invalid model interpretation");
            String group=String.valueOf(value.get("groupId"));
            if(!groups.contains(group) || !seen.add(group)) throw new IllegalArgumentException("Unknown model group");
            if(!("TEST".equals(value.get("decision")) || "DEFER".equals(value.get("decision")))
                    || !("LOW".equals(value.get("confidence")) || "MEDIUM".equals(value.get("confidence")))
                    || !value.keySet().equals(Set.of("groupId","decision","confidence","rationale","differentiator",
                        "automationPlan","risks","citedListingIds","citedShopIds")))
                throw new IllegalArgumentException("Invalid model recommendation schema");
            for(String field:List.of("rationale","differentiator","automationPlan"))
                if(!(value.get(field) instanceof String text) || text.length()>4000)
                    throw new IllegalArgumentException("Invalid model rationale");
            if(!(value.get("risks") instanceof List<?> risks) || risks.size()>12
                    || risks.stream().anyMatch(r->!(r instanceof String)))
                throw new IllegalArgumentException("Invalid model risks");
            for(String field:List.of("citedListingIds","citedShopIds")) {
                if(!(value.get(field) instanceof List<?> ids)) throw new IllegalArgumentException("Missing evidence citations");
                for(var id:ids) {
                    String key=String.valueOf(id);
                    if(!(field.equals("citedListingIds")?listings:shops).containsKey(key))
                        throw new IllegalArgumentException("Unknown model citation");
                    if(field.equals("citedListingIds") && !members.getOrDefault(group,Set.of()).contains(key))
                        throw new IllegalArgumentException("Cross-group citation");
                }
            }
        }
    }
    private static Map<String,Object> capConfidence(Map<String,Object> interpretation,List<Opportunity> opportunities) {
        if(interpretation.isEmpty())return interpretation;
        Map<String,Integer> corroboration=new HashMap<>();
        opportunities.forEach(o->corroboration.put(o.groupId(),o.shopsWithRecentReviews()));
        List<Map<String,Object>> recommendations=new ArrayList<>();
        for(var raw:(List<?>)interpretation.get("recommendations")) {
            Map<String,Object> recommendation=new LinkedHashMap<>();
            ((Map<?,?>)raw).forEach((key,value)->recommendation.put(key.toString(),value));
            if("MEDIUM".equals(recommendation.get("confidence")) && corroboration.get(recommendation.get("groupId"))<2) {
                recommendation.put("confidence","LOW");
                List<String> risks=new ArrayList<>();
                for(var risk:(List<?>)recommendation.get("risks"))risks.add(risk.toString());
                risks.add("Confidence capped at LOW: fewer than two independent shops with recent purchase proxies.");
                recommendation.put("risks",risks);
            }
            recommendations.add(recommendation);
        }
        return Map.of("recommendations",recommendations);
    }
    public static BigDecimal usdPrice(Map<String,Object> row) {
        if(!(row.get("price") instanceof Map<?,?> money) || !"USD".equals(money.get("currency_code"))) return null;
        try {
            BigDecimal amount=new BigDecimal(money.get("amount").toString()),divisor=new BigDecimal(money.get("divisor").toString());
            return amount.signum()<0 || divisor.signum()<=0?null:amount.divide(divisor,8,RoundingMode.HALF_UP).stripTrailingZeros();
        } catch(RuntimeException e) {return null;}
    }
    private static Long number(Object value) {
        if(!(value instanceof Number)) return null;
        try {long n=new BigDecimal(value.toString()).longValueExact();return n<0?null:n;}
        catch(RuntimeException e){return null;}
    }
}
