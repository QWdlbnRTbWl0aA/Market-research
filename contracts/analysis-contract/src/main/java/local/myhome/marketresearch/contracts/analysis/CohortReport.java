package local.myhome.marketresearch.contracts.analysis;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record CohortReport(
        String methodologyVersion, String query, Instant observedAt,
        int received, int invalidIdentifiers, int duplicates, int uniqueListings,
        int digitalListings, int physicalListings, int unknownTypeListings,
        int digitalShops, int missingDigitalShopIds, BigDecimal largestShopShare, BigDecimal shopHhi,
        Map<String, Distribution> digitalPricesByCurrency,
        CounterSummary digitalViews, CounterSummary digitalFavorites,
        int zeroViewListings, int missingPriceListings, int invalidOrMissingAgeListings,
        Map<String, Integer> digitalAgeBuckets, Map<String, Integer> topTags,
        int repeatedDigitalTitles, Map<String, Integer> automationBuckets,
        List<ExperimentCandidate> experimentCandidates, List<String> limitations) {

    public record Distribution(int count, BigDecimal min, BigDecimal p25, BigDecimal median,
                               BigDecimal p75, BigDecimal max) {}
    public record CounterSummary(int known, int missing, long total, BigDecimal median) {}
    public record ExperimentCandidate(String listingId, String automationStatus, String rationale) {}
}
