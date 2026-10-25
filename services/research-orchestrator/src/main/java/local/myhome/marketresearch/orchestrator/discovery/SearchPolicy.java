package local.myhome.marketresearch.orchestrator.discovery;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import local.myhome.marketresearch.contracts.analysis.Discovery.Listing;
import local.myhome.marketresearch.tools.UnicodeText;

/** Preparation and coverage measurements, deliberately not business opportunity scoring. */
public final class SearchPolicy {
    private SearchPolicy() {}
    public static String normalize(String q){return q.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");}
    public static boolean lowNovelty(int fresh,int returned){return returned==0||(double)fresh/returned<.10;}
    public static Listing listing(Map<?,?> row) {
        if(!("download".equals(row.get("listing_type"))||Boolean.TRUE.equals(row.get("is_digital"))))return null;
        String id=id(row.get("listing_id")),shop=id(row.get("shop_id"));
        if(id==null||shop==null||!(row.get("title") instanceof String title)||title.isBlank())return null;
        BigDecimal price=null;String currency=null;
        if(row.get("price") instanceof Map<?,?> p) {
            try {
                var divisor=new BigDecimal(p.get("divisor").toString());
                var amount=new BigDecimal(p.get("amount").toString());
                if(divisor.signum()>0&&amount.signum()>=0){price=amount.divide(divisor,4,RoundingMode.HALF_UP);currency=String.valueOf(p.get("currency_code"));}
            }catch(RuntimeException ignored){/* Unknown money stays unknown. */}
        }
        List<String> tags=row.get("tags") instanceof List<?> t?t.stream().map(Object::toString).sorted().limit(30).toList():List.of();
        return new Listing(id,shop,clip(title,300),clip(Objects.toString(row.get("description"),""),2500),
            tags,price,currency,number(row.get("views")),number(row.get("num_favorers")),number(row.get("original_creation_timestamp")));
    }
    static String clip(String s,int n){return UnicodeText.prefix(s,n);}
    static String id(Object o){return o instanceof Number n&&n.longValue()>0?Long.toString(n.longValue()):null;}
    static long number(Object o){return o instanceof Number n?Math.max(0,n.longValue()):0;}
    public static Map<String,Object> snapshot(int count,List<Listing> listings,int returned) {
        var m=new LinkedHashMap<String,Object>();
        m.put("count",count);m.put("returned",returned);m.put("digitalShare",returned==0?0:(double)listings.size()/returned);
        m.put("ids",listings.stream().map(Listing::id).toList());
        var prices=listings.stream().filter(l->"USD".equals(l.currency())&&l.price()!=null).map(Listing::price).sorted().toList();
        if(!prices.isEmpty())m.put("medianUsd",prices.get(prices.size()/2));
        return m;
    }
    public static Map<String,Object> changes(String query,Map<String,Object> baseline,Map<String,Object> fresh) {
        double before=((Number)baseline.getOrDefault("count",0)).doubleValue(),after=((Number)fresh.getOrDefault("count",0)).doubleValue();
        var oldIds=new HashSet<>((List<?>)baseline.getOrDefault("ids",List.of()));
        var newIds=new HashSet<>((List<?>)fresh.getOrDefault("ids",List.of()));
        long common=newIds.stream().filter(oldIds::contains).count();
        double churn=newIds.isEmpty()?oldIds.isEmpty()?0:1:1-(double)common/newIds.size();
        double countDelta=Math.abs(after-before)/Math.max(1,before);
        double priceDelta=0;
        if(baseline.get("medianUsd") instanceof Number old && fresh.get("medianUsd") instanceof Number now && old.doubleValue()>0)
            priceDelta=Math.abs(now.doubleValue()-old.doubleValue())/old.doubleValue();
        double digitalDelta=Math.abs(((Number)fresh.getOrDefault("digitalShare",0)).doubleValue()
            -((Number)baseline.getOrDefault("digitalShare",0)).doubleValue());
        boolean significant=(Math.abs(after-before)>=20&&countDelta>=.25)||(newIds.size()>=10&&churn>=.60)
            ||priceDelta>=.25||digitalDelta>=.30||(!oldIds.isEmpty()&&newIds.isEmpty());
        return Map.of("query",query,"significant",significant,"countRelativeChange",countDelta,
            "sampleChurn",churn,"medianUsdRelativeChange",priceDelta,"digitalShareChange",digitalDelta,
            "interpretation","Search-sample change, not demonstrated demand or sales");
    }
    public static Map<String,Object> watchBaseline(Map<String,Object> initial) {
        var copy=new LinkedHashMap<String,Object>(initial);
        copy.put("ids",((List<?>)copy.getOrDefault("ids",List.of())).stream().limit(25).toList());
        return copy;
    }
}
