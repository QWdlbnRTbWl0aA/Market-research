package local.myhome.marketresearch.analysis.cohort;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import local.myhome.marketresearch.contracts.analysis.CohortReport;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/v1/etsy-cohort-analyses")
public class CohortController {
    public record Request(String query, Instant observedAt, List<Map<String,Object>> listings) {}
    @PostMapping
    public CohortReport analyze(@RequestBody Request request) {
        if (request.query() == null || request.query().isBlank() || request.query().length() > 300
                || request.observedAt() == null || request.observedAt().isAfter(Instant.now().plusSeconds(60))
                || request.listings() == null || request.listings().isEmpty() || request.listings().size() > 1000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide query, observation time, and 1..1000 listings");
        return CohortAnalyzer.analyze(request.query().trim(), request.observedAt(), request.listings());
    }
}
