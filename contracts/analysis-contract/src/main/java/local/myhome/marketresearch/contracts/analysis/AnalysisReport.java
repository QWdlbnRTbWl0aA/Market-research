package local.myhome.marketresearch.contracts.analysis;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
public record AnalysisReport(UUID researchRunId, String query, String source, int sampleSize,
        long digitalListings, long distinctShops, Map<String, BigDecimal> medianPrices,
        String opportunityDecision, List<String> findings) {
    public AnalysisReport {
        if (medianPrices != null) {
            var normalized = new java.util.TreeMap<String, BigDecimal>();
            medianPrices.forEach((currency, price) -> {
                var value = price.stripTrailingZeros();
                normalized.put(currency, value.scale() < 0 ? value.setScale(0) : value);
            });
            medianPrices = java.util.Collections.unmodifiableMap(normalized);
        }
        if (findings != null) findings = List.copyOf(findings);
    }
}
