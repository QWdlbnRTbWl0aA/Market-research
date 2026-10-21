package local.myhome.marketresearch.contracts.analysis;
import java.util.List;
import java.util.UUID;
public record SimulationRequest(UUID researchRunId, String query, String scenario, List<ListingEvidence> listings) {
    public SimulationRequest {
        if (researchRunId == null || query == null || query.isBlank() || scenario == null
                || !java.util.Set.of("FAST", "SLOW", "FAIL").contains(scenario)
                || listings == null || listings.isEmpty() || listings.size() > 1000)
            throw new IllegalArgumentException("Invalid simulation request");
        listings = List.copyOf(listings);
    }
}
