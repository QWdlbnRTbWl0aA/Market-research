package local.myhome.marketresearch.analysis.cohort;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import local.myhome.marketresearch.contracts.analysis.CohortReport;
import local.myhome.marketresearch.contracts.analysis.CohortReport.*;

public final class CohortAnalyzer {
    private CohortAnalyzer() {}

    public static CohortReport analyze(String query, Instant observedAt, List<Map<String,Object>> raw) {
        Map<String, Observation> unique = new LinkedHashMap<>();
        int invalid = 0, duplicates = 0;
        for (var item : raw) {
            var observation = EtsyObservationMapper.map(item);
            if (observation == null) { invalid++; continue; }
            if (unique.putIfAbsent(observation.id(), observation) != null) duplicates++;
        }
        var digital = unique.values().stream().filter(x -> "download".equals(x.type())).toList();
        int physical = (int) unique.values().stream().filter(x -> "physical".equals(x.type())).count();
        var shops = digital.stream().map(Observation::shop).filter(Objects::nonNull)
            .collect(Collectors.groupingBy(Function.identity(), TreeMap::new, Collectors.counting()));
        long knownShops = shops.values().stream().mapToLong(Long::longValue).sum();
        BigDecimal largest = knownShops == 0 ? null : ratio(shops.values().stream().mapToLong(Long::longValue).max().orElse(0), knownShops);
        BigDecimal hhi = knownShops == 0 ? null : shops.values().stream().map(n -> ratio(n, knownShops).pow(2))
            .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(6, RoundingMode.HALF_UP);
        Map<String, Distribution> prices = new TreeMap<>();
        digital.stream().filter(x -> x.price() != null).collect(Collectors.groupingBy(Observation::currency))
            .forEach((currency, values) -> prices.put(currency, distribution(values.stream().map(Observation::price).toList())));
        Map<String, Integer> ages = new LinkedHashMap<>();
        for (var label : List.of("0-30d","31-90d","91-365d","366d+")) ages.put(label, 0);
        int badAge = 0;
        Map<String,Integer> tags = new TreeMap<>(), titles = new TreeMap<>(), automation = new TreeMap<>();
        List<ExperimentCandidate> candidates = new ArrayList<>();
        for (var item : digital) {
            if (item.created() == null || item.created().isAfter(observedAt)) badAge++;
            else {
                long days = Duration.between(item.created(), observedAt).toDays();
                String bucket = days <= 30 ? "0-30d" : days <= 90 ? "31-90d" : days <= 365 ? "91-365d" : "366d+";
                ages.merge(bucket, 1, Integer::sum);
            }
            item.tags().forEach(tag -> tags.merge(tag,1,Integer::sum));
            if (!item.title().isBlank()) titles.merge(item.title(),1,Integer::sum);
            String status = Boolean.TRUE.equals(item.personalizable()) || Boolean.TRUE.equals(item.customizable())
                ? "TEMPLATE_REVIEW" : item.personalizable() == null || item.customizable() == null
                ? "AUTOMATION_UNKNOWN" : "STANDARD_DOWNLOAD";
            automation.merge(status, 1, Integer::sum);
            candidates.add(new ExperimentCandidate(item.id(), status,
                "STANDARD_DOWNLOAD".equals(status)
                ? "Cheap-experiment template candidate; no custom-work flag. Revenue and actual support effort remain unknown."
                : "Estimate reusable automation and manual involvement before choosing a publication template."));
        }
        // This is an automation queue, not a fabricated expected-revenue ranking.
        candidates.sort(Comparator.comparingInt((ExperimentCandidate x) -> switch(x.automationStatus()) {
            case "STANDARD_DOWNLOAD" -> 0; case "AUTOMATION_UNKNOWN" -> 1; default -> 2;
        }).thenComparing(ExperimentCandidate::listingId));
        return new CohortReport("operating-economics-v2", query, observedAt,
            raw.size(), invalid, duplicates, unique.size(), digital.size(), physical,
            unique.size() - digital.size() - physical, shops.size(), digital.size() - (int)knownShops,
            largest, hhi, prices, counter(digital, Observation::views), counter(digital, Observation::favorites),
            (int)digital.stream().filter(x -> Objects.equals(0L,x.views())).count(),
            (int)digital.stream().filter(x -> x.price() == null).count(), badAge, ages, top(tags,30),
            titles.values().stream().mapToInt(n -> Math.max(0,n-1)).sum(), automation, List.copyOf(candidates),
            List.of("Search-ranked convenience sample; not representative of all Etsy demand.",
                "Only download listings enter digital metrics. Unknown listing types remain unknown.",
                "Views and favorites are cumulative proxies, not sales or recurring revenue.",
                "Shop concentration excludes missing shop IDs; missingness is reported.",
                "Age uses original creation time. One snapshot cannot measure current engagement velocity.",
                "Title normalization detects textual overlap, not semantic or visual equivalence.",
                "Automation flags are rough workload indicators, not measured labor costs.",
                "Expected receipts, AI/compute cost and manual minutes require explicit estimates or published-product observations.",
                "No minimum sample, shop-count or observation-window gate is imposed before a cheap experiment."));
    }

    static BigDecimal ratio(long n, long d) {
        return BigDecimal.valueOf(n).divide(BigDecimal.valueOf(d), 8, RoundingMode.HALF_UP);
    }
    private static CounterSummary counter(List<Observation> items, Function<Observation,Long> field) {
        var values = items.stream().map(field).filter(Objects::nonNull).sorted().toList();
        return new CounterSummary(values.size(), items.size()-values.size(),
            values.stream().mapToLong(Long::longValue).sum(),
            values.isEmpty() ? null : percentile(values.stream().map(BigDecimal::valueOf).toList(), new BigDecimal("0.5")));
    }
    private static Distribution distribution(List<BigDecimal> input) {
        var v = input.stream().sorted().toList();
        return new Distribution(v.size(),v.getFirst(),percentile(v,new BigDecimal("0.25")),
            percentile(v,new BigDecimal("0.5")),percentile(v,new BigDecimal("0.75")),v.getLast());
    }
    /** Linear interpolation at (n-1)*p; deterministic for small and large cohorts. */
    private static BigDecimal percentile(List<BigDecimal> sorted, BigDecimal p) {
        BigDecimal index = BigDecimal.valueOf(sorted.size()-1).multiply(p);
        int lower = index.intValue();
        int upper = Math.min(lower+1, sorted.size()-1);
        return sorted.get(lower).add(sorted.get(upper).subtract(sorted.get(lower)).multiply(index.subtract(BigDecimal.valueOf(lower))));
    }
    private static Map<String,Integer> top(Map<String,Integer> input, int limit) {
        Map<String,Integer> result = new LinkedHashMap<>();
        input.entrySet().stream().sorted(Map.Entry.<String,Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
            .limit(limit).forEach(e -> result.put(e.getKey(),e.getValue()));
        return result;
    }
}
