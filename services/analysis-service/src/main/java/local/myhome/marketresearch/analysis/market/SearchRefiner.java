package local.myhome.marketresearch.analysis.market;

import java.math.BigDecimal;
import java.util.*;
import local.myhome.marketresearch.contracts.analysis.MarketResearch.*;

/** Small, budget-oriented planner. Semantics come from a model, counts remain deterministic. */
public final class SearchRefiner {
    private SearchRefiner() {}
    public static RefinementPlan refine(RefinementRequest request) {
        if (request.seed()==null || request.seed().isBlank() || request.resultCount()<0 || request.maxPages()<1
                || request.listings()==null || request.groups()==null || request.assignments()==null)
            throw new IllegalArgumentException("Invalid refinement request");
        Map<String,Map<String,Object>> digital=new HashMap<>();
        for (var row:request.listings()) if ("download".equals(row.get("listing_type")))
            digital.put(String.valueOf(row.get("listing_id")),row);
        Set<String> usable=new HashSet<>();
        for (var assignment:request.assignments())
            if (assignment.usable() && digital.containsKey(assignment.listingId())) usable.add(assignment.listingId());
        long pages=(long)Math.ceil(request.resultCount()/100.0);
        boolean split=pages>request.maxPages();
        List<FocusedSearch> searches=new ArrayList<>();
        Set<String> queries=new HashSet<>(),ids=new HashSet<>();
        String anchor=request.seed().strip().toLowerCase(Locale.ROOT).split(" ")[0];
        for (var group:request.groups()) {
            if (searches.size()==3) break;
            if (group.id()==null || !group.id().matches("[a-z0-9-]{1,40}") || !ids.add(group.id())
                    || group.label()==null || group.label().length()>100 || group.query()==null) continue;
            String query=group.query().strip().toLowerCase(Locale.ROOT);
            if (query.length()<5 || query.length()>100 || query.split("\\s+").length>8
                    || !query.chars().allMatch(c->Character.isLetterOrDigit(c)||Character.isWhitespace(c)||c=='-')
                    || !query.contains(anchor) || !queries.add(query)) continue;
            searches.add(new FocusedSearch(group.id(),query,pages,split));
        }
        return new RefinementPlan(pages,request.listings().size(),digital.size(),usable.size(),
            BigDecimal.valueOf(usable.size()),List.copyOf(searches),List.of(
                "One sampled search call; usablePerRequest counts semantically accepted digital listings in that sample.",
                "Search counts are not demand. Child probes measure their own size/yield; overlap must be deduplicated.",
                "At most three child searches. Large children are marked for another bounded split, never fully enumerated."));
    }
}
