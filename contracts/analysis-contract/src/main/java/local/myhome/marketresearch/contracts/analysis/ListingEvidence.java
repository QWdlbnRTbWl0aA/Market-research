package local.myhome.marketresearch.contracts.analysis;
import java.math.BigDecimal;
public record ListingEvidence(String listingId, String shopId, String title, String listingType,
        BigDecimal price, String currency, Long views, Long favorites) {
    public ListingEvidence {
        if (listingId == null || listingId.isBlank() || shopId == null || shopId.isBlank()
                || title == null || listingType == null
                || (price != null && (price.signum() < 0 || currency == null))
                || (views != null && views < 0) || (favorites != null && favorites < 0))
            throw new IllegalArgumentException("Invalid listing evidence");
    }
}
