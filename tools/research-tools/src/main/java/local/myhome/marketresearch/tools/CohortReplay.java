package local.myhome.marketresearch.tools;

import java.nio.file.*;
import java.util.*;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

public final class CohortReplay {
    private final ObjectMapper json;
    public CohortReplay(ObjectMapper json) { this.json = json; }

    @SuppressWarnings("unchecked")
    public Map<String,Object> run(Path root) throws Exception {
        Path input = root.resolve("data/etsy-cohort-1000/input.json");
        if (!Files.isRegularFile(input)) throw new ToolFailure("No saved cohort available for replay.");
        byte[] payload = Files.readAllBytes(input);
        Path directory = Files.createTempDirectory("research-replay-");
        try {
            Map<String,Object> report;
            Map<String,Object> usage = new LinkedHashMap<>();
            try (var service = new ServiceProcess(root,"analysis-service",directory,ServiceProcess.freePort(),
                    Map.of("spring.datasource.url","jdbc:h2:mem:cohort-replay"))) {
                long started = System.nanoTime();
                report = RestClient.create(service.base().toString()).post().uri("/v1/etsy-cohort-analyses")
                    .contentType(MediaType.APPLICATION_JSON).body(payload).retrieve().body(Map.class);
                if (report == null || ((Number)report.get("received")).intValue()!=1000
                        || ((Number)report.get("uniqueListings")).intValue()!=1000)
                    throw new ToolFailure("Saved cohort failed the 1000-listing replay check.");
                usage.put("analysisHttpSeconds",(System.nanoTime()-started)/1_000_000_000.0);
                usage.put("llmCalls",0); usage.put("aiTokens",0); usage.put("imageGenerationCalls",0);
                usage.put("operatingCost","Not monetized: compute rate and human time valuation not configured.");
                service.close();
                usage.put("serviceWallSeconds",service.wallSeconds());
                usage.put("serviceCpuSeconds",service.cpuSeconds());
                usage.put("cpuMeasurement","Java ProcessHandle CPU duration observed immediately before shutdown.");
            }
            report.put("testRun",usage);
            PrivateJson.write(input.resolveSibling("report.json"),report,json);
            return Map.of("received",report.get("received"),"uniqueListings",report.get("uniqueListings"),
                "digitalListings",report.get("digitalListings"),"testRun",usage);
        } finally {
            // This directory is created solely for the replay process; no research is stored here.
            try (var files = Files.walk(directory)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
            }
        }
    }
}
