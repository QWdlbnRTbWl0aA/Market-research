package local.myhome.marketresearch.contracts.content;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

public record ContentJobRequest(UUID researchRunId, URI productDefinition, Set<ContentKind> outputs) {

    public enum ContentKind {
        LISTING_TEXT,
        PRODUCT_VISUALS,
        MOCK_UPS,
        GENERATION_MATERIAL
    }
}

