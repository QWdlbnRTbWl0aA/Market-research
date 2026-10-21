package local.myhome.marketresearch.contracts.archive;

import java.net.URI;
import java.util.UUID;

public record PublishListingPackageRequest(UUID researchRunId, URI manifest) {}

