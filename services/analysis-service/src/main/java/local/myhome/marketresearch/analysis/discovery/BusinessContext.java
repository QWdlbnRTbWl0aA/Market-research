package local.myhome.marketresearch.analysis.discovery;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import local.myhome.marketresearch.contracts.analysis.Discovery.*;

/** Bounded previews of prior reasoning; source requests and canonical opportunities stay intact. */
final class BusinessContext {
    private BusinessContext() {}

    static List<String> summaries(List<String> summaries) {
        return summaries.stream().skip(Math.max(0,summaries.size()-6)).map(value->preview(value,600)).toList();
    }

    static List<Opportunity> opportunities(List<Opportunity> opportunities) {
        return opportunities.stream().map(opportunity->{
            Product product=opportunity.product();
            // Preserve complete semantic identity. Repeated operating plans and caveats are previews.
            var preview=new Product(product.name(),product.audience(),product.useCase(),product.deliverables(),
                product.differentiation(),product.proposedPriceUsd(),preview(product.recurringRevenueHypothesis(),64),
                preview(product.automationPlan(),64),preview(product.manualWorkAssumptions(),64),product.seasonality(),
                product.listingIds(),product.counterevidence().stream().limit(2).map(value->preview(value,64)).toList(),
                product.confidence(),preview(product.confidenceExplanation(),64),preview(product.experiment(),64),
                product.relationship(),product.existingOpportunityId(),preview(product.changeReason(),64),product.opportunityId());
            return new Opportunity(opportunity.id(),opportunity.parentId(),preview,opportunity.evidenceIds(),opportunity.revision());
        }).toList();
    }

    static List<Map<String,Object>> shops(List<Listing> listings,List<Map<String,Object>> shops) {
        var ids=listings.stream().map(Listing::shopId).collect(Collectors.toSet());
        return shops.stream().filter(shop->ids.contains(Objects.toString(shop.get("shop_id"),""))).toList();
    }

    static List<Map<String,Object>> reviews(List<Listing> listings,List<Map<String,Object>> reviews) {
        var sources=listings.stream().flatMap(listing->java.util.stream.Stream.of("listing:"+listing.id(),"shop:"+listing.shopId()))
            .collect(Collectors.toSet());
        var ids=listings.stream().map(Listing::id).collect(Collectors.toSet());
        return reviews.stream().filter(review->sources.contains(Objects.toString(review.get("source"),""))
            ||ids.contains(Objects.toString(review.get("listing_id"),""))).toList();
    }

    static String preview(String value,int limit) {
        if(value==null||value.length()<=limit)return value;
        return value.substring(0,limit)+" [preview; full record retained]";
    }
}
