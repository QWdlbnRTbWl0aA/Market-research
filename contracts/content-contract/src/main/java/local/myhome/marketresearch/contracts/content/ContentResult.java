package local.myhome.marketresearch.contracts.content;

import java.net.URI;
import java.util.UUID;

public record ContentResult(UUID researchRunId, URI packageManifest) {}

