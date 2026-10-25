package local.myhome.marketresearch.orchestrator.discovery;

import java.util.*;

/** Advances bounded query passes without dropping evidence or declaring arbitrary scope caps exhausted. */
final class SearchFrontier {
    private SearchFrontier() {}
    static boolean skippedRefinement(DiscoveryState.Query query,int maxDepth) {
        return "QUERY_EXPANSION_LIMIT".equals(query.expansionStopReason)&&query.refinementJob==null
            &&!query.sample.isEmpty()&&query.usable>0&&query.novel>0&&query.depth<maxDepth
            &&query.stableAnalyses<2&&query.lowNovelty<2;
    }
    static boolean nextPass(DiscoveryState state,int passSize,int maxDepth) {
        var known=new LinkedHashSet<>(state.queries.stream().map(query->SearchPolicy.normalize(query.text)).toList());
        var parents=List.copyOf(state.queries);
        boolean queued=parents.stream().anyMatch(query->skippedRefinement(query,maxDepth)
            ||query.depth<maxDepth&&query.deferredQueries.stream().anyMatch(suggestion->!known.contains(SearchPolicy.normalize(suggestion))));
        if(!queued)return false;
        state.searchPass++;state.passQueryStart=state.queries.size();state.searchStatus="ACTIVE";
        for(var parent:parents) {
            if(parent.depth<maxDepth)for(String suggestion:parent.deferredQueries) {
                String normalized=SearchPolicy.normalize(suggestion);
                if(known.contains(normalized))continue;
                if(state.queries.size()-state.passQueryStart>=passSize)break;
                state.queries.add(new DiscoveryState.Query(normalized,parent));known.add(normalized);
            }
            if(skippedRefinement(parent,maxDepth)) {
                parent.expansionStopReason=null;parent.refined=false;parent.status="REFINING";
                parent.stopReason=null;parent.nextAttemptAt=0;
            } else if("QUERY_EXPANSION_LIMIT".equals(parent.expansionStopReason)
                    &&parent.deferredQueries.stream().allMatch(suggestion->known.contains(SearchPolicy.normalize(suggestion)))) {
                parent.expansionStopReason=parent.refinementJob==null?"NO_MARGINAL_VALUE":null;
            }
        }
        return true;
    }
    static long pendingListings(DiscoveryState state) {
        return state.listings.keySet().stream().filter(id->!state.processed.contains(id)&&!state.excluded.contains(id)).count();
    }
    static Map<String,Object> coverage(DiscoveryState state) {
        var known=new HashSet<>(state.queries.stream().map(query->SearchPolicy.normalize(query.text)).toList());
        long pendingSuggestions=state.queries.stream().flatMap(query->query.deferredQueries.stream()).map(SearchPolicy::normalize)
            .distinct().filter(query->!known.contains(query)).count();
        return Map.of("searchPass",state.searchPass,"acceptedPhrases",state.queries.size(),
            "fetchedPhrases",state.queries.stream().filter(query->query.pages>0).count(),
            "pendingSuggestions",pendingSuggestions,"deferredRefinements",state.queries.stream()
                .filter(query->"QUERY_EXPANSION_LIMIT".equals(query.expansionStopReason)&&query.refinementJob==null).count(),
            "safetyLimitedBranches",state.queries.stream().filter(query->"DEPTH_EXPANSION_LIMIT".equals(query.expansionStopReason)).count(),
            "pendingListingLimit",state.pendingListingLimit);
    }
}
