package local.myhome.marketresearch.contracts.job;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

public record JobView(
        UUID jobId,
        JobStatus status,
        int progressPercent,
        String phase,
        URI result,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt) {}

