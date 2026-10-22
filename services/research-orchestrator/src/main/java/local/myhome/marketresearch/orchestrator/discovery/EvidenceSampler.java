package local.myhome.marketresearch.orchestrator.discovery;

import java.util.*;
import local.myhome.marketresearch.contracts.analysis.Discovery.Listing;

/** Spread a small convenience sample over low/mid/high engagement; never call it a market census. */
final class EvidenceSampler {
    private EvidenceSampler() {}

    static List<String> shops(List<Listing> listings, int limit) {
        var engagement = new TreeMap<String,Long>();
        for (var listing : listings) engagement.merge(listing.shopId(), listing.favorites(), Math::max);
        var ordered = engagement.keySet().stream()
            .sorted(Comparator.comparingLong((String id) -> engagement.get(id)).thenComparing(id -> id)).toList();
        int count = Math.min(limit, ordered.size());
        var selected = new LinkedHashSet<String>();
        for (int index = 0; index < count; index++) {
            int position = count == 1 ? ordered.size() / 2 : (int) Math.round(index * (ordered.size() - 1.0) / (count - 1));
            selected.add(ordered.get(position));
        }
        return List.copyOf(selected);
    }

    static List<Map<String,Object>> reviews(Map<String,Object> response, String source) {
        if (response == null || !(response.get("results") instanceof List<?> rows)) throw new IllegalStateException("Invalid review response");
        var sanitized = new ArrayList<Map<String,Object>>();
        String observedAt = java.time.Instant.now().toString();
        for (Object item : rows.stream().limit(25).toList()) if (item instanceof Map<?,?> row) {
            var review = new LinkedHashMap<String,Object>();
            for (String field : List.of("listing_id", "create_timestamp", "created_timestamp", "rating", "review", "language"))
                if (row.get(field) != null) review.put(field, row.get(field));
            review.put("source", source);
            review.put("observedAt", observedAt);
            review.put("evidenceId", DiscoveryFlow.fullHash(source + ":" + review));
            sanitized.add(review);
        }
        return sanitized;
    }
}
