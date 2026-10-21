package local.myhome.marketresearch.contracts.analysis;

import java.net.URI;
import java.util.UUID;

public record AnalysisJobRequest(UUID researchRunId, AnalysisKind kind, URI evidencePackage) {

    public enum AnalysisKind {
        INITIAL_METRICS,
        BUSINESS_OPPORTUNITY,
        PRODUCT_DEFINITION
    }
}

