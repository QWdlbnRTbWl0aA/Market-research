package local.myhome.marketresearch.analysis.cohort;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Instant;
import java.util.*;

/** Provider-specific adapter. No seller-user identifiers or descriptions enter analysis. */
public final class EtsyObservationMapper {
    private EtsyObservationMapper() {}

    public static Observation map(Map<String, Object> raw) {
        if (raw == null) return null;
        var id = positiveId(raw.get("listing_id"));
        if (id == null) return null;
        BigDecimal price = null;
        String currency = null;
        if (raw.get("price") instanceof Map<?, ?> money) {
            var amount = decimal(money.get("amount"));
            var divisor = decimal(money.get("divisor"));
            var code = text(money.get("currency_code"));
            if (amount != null && amount.signum() >= 0 && divisor != null && divisor.signum() > 0
                    && code != null && code.matches("[A-Z]{3}")) {
                price = amount.divide(divisor, 8, RoundingMode.HALF_UP).stripTrailingZeros();
                currency = code;
            }
        }
        Instant created = null;
        var timestamp = nonnegativeLong(raw.get("original_creation_timestamp"));
        if (timestamp != null && timestamp > 0) {
            try { created = Instant.ofEpochSecond(timestamp); }
            catch (java.time.DateTimeException ignored) { /* Unusable age remains unknown. */ }
        }
        Set<String> tags = new TreeSet<>();
        if (raw.get("tags") instanceof List<?> values)
            values.stream().map(EtsyObservationMapper::text).filter(Objects::nonNull)
                .map(EtsyObservationMapper::normalize).filter(x -> !x.isBlank()).forEach(tags::add);
        String type = text(raw.get("listing_type"));
        return new Observation(id, positiveId(raw.get("shop_id")), type == null ? null : type.toLowerCase(Locale.ROOT),
            normalize(Objects.requireNonNullElse(text(raw.get("title")), "")), price, currency,
            nonnegativeLong(raw.get("views")), nonnegativeLong(raw.get("num_favorers")), created, Set.copyOf(tags),
            bool(raw.get("is_personalizable")), bool(raw.get("is_customizable")));
    }

    static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
            .replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }
    private static String text(Object value) { return value instanceof String s ? s : null; }
    private static Boolean bool(Object value) { return value instanceof Boolean b ? b : null; }
    private static BigDecimal decimal(Object value) {
        if (!(value instanceof Number)) return null;
        try { return new BigDecimal(value.toString()); }
        catch (NumberFormatException ignored) { return null; }
    }
    private static Long nonnegativeLong(Object value) {
        var number = decimal(value);
        if (number == null || number.signum() < 0) return null;
        try { return number.longValueExact(); }
        catch (ArithmeticException ignored) { return null; }
    }
    private static String positiveId(Object value) {
        var number = nonnegativeLong(value);
        return number == null || number == 0 ? null : number.toString();
    }
}
