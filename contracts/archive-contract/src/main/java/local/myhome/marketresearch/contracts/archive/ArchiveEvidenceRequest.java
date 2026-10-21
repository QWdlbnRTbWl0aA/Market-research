package local.myhome.marketresearch.contracts.archive;

import java.net.URI;
import java.util.UUID;

public record ArchiveEvidenceRequest(UUID researchRunId, String evidenceKind, URI source, String provenance) {}

