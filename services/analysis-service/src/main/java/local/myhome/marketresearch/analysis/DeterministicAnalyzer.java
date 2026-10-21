package local.myhome.marketresearch.analysis;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import local.myhome.marketresearch.contracts.analysis.*;

public final class DeterministicAnalyzer {
    private DeterministicAnalyzer() {}
    public static AnalysisReport analyze(SimulationRequest request) {
        var listings = request.listings().stream()
                .collect(Collectors.toMap(ListingEvidence::listingId, x -> x, (a,b) -> a))
                .values().stream().toList();
        var digital = listings.stream().filter(x -> "download".equals(x.listingType())).toList();
        Map<String, BigDecimal> medians = new TreeMap<>();
        digital.stream().filter(x -> x.price() != null && x.currency() != null)
            .collect(Collectors.groupingBy(ListingEvidence::currency)).forEach((currency, group) -> {
                var prices = group.stream().map(ListingEvidence::price).sorted().toList();
                int n = prices.size();
                medians.put(currency, n % 2 == 1 ? prices.get(n / 2) :
                    prices.get(n / 2 - 1).add(prices.get(n / 2)).divide(BigDecimal.valueOf(2)));
            });
        return new AnalysisReport(request.researchRunId(), request.query(), "SYNTHETIC_FIXTURE",
            listings.size(), digital.size(), digital.stream().map(ListingEvidence::shopId).distinct().count(),
            medians, "INSUFFICIENT_EVIDENCE", List.of(
                "Synthetic data validates the workflow; it cannot validate market demand.",
                "Views and favorites are engagement proxies, not verified sales.",
                "Production cost is excluded; demand, differentiation, discoverability and timing remain relevant."));
    }
}
