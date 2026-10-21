package local.myhome.marketresearch.contracts.analysis;

import java.net.URI;
import java.util.UUID;

public record AnalysisResult(UUID researchRunId, AnalysisJobRequest.AnalysisKind kind, URI report) {}

