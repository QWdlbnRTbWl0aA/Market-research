package local.myhome.marketresearch.analysis.cohort;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

/** Normalized evidence; missing values remain null rather than becoming zero. */
public record Observation(String id, String shop, String type, String title,
        BigDecimal price, String currency, Long views, Long favorites, Instant created,
        Set<String> tags, Boolean personalizable, Boolean customizable) {}
