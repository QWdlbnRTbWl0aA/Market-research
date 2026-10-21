package local.myhome.marketresearch.tools;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.function.Predicate;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/** Real packaged services and persistent temporary databases; never uses external APIs. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WorkflowIT {
    @TempDir static Path directory;
    static final Map<String,ServiceProcess> processes = new HashMap<>();
    static final Map<String,Integer> ports = new HashMap<>();
    static final Map<String,String> artifacts = Map.of("orchestrator","research-orchestrator",
        "analysis","analysis-service","archive","research-archive-service");
    static final ObjectMapper json = new ObjectMapper();
    static Path root;
    record Reply(int status, Map<String,Object> body) {}
    @BeforeAll static void startServices() throws Exception {
        root = Path.of(System.getProperty("project.root"));
        for (String name : artifacts.keySet()) ports.put(name,ServiceProcess.freePort());
        for (String name : List.of("analysis","archive","orchestrator")) start(name);
    }
    static void start(String name) throws Exception {
        Map<String,String> settings = new HashMap<>();
        settings.put("spring.datasource.url","jdbc:h2:file:"+directory.resolve(name).toAbsolutePath()+";DB_CLOSE_ON_EXIT=FALSE;WRITE_DELAY=0");
        settings.put("market-research.services.analysis-base-url","http://127.0.0.1:"+ports.get("analysis"));
        settings.put("market-research.services.archive-base-url","http://127.0.0.1:"+ports.get("archive"));
        processes.put(name,new ServiceProcess(root,artifacts.get(name),directory,ports.get(name),settings));
    }
    static void stop(String name, boolean crash) throws Exception {
        var process = processes.remove(name);
        if (process != null) {
            try { if (crash) process.crash(); } finally { process.close(); }
        }
    }
    @AfterAll static void closeServices() throws Exception {
        for (String name : List.copyOf(processes.keySet())) stop(name,false);
    }
    @SuppressWarnings("unchecked")
    static Reply call(String service, HttpMethod method, String path, Object body, String key) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2)); factory.setReadTimeout(Duration.ofSeconds(5));
        var request = RestClient.builder().baseUrl("http://127.0.0.1:"+ports.get(service)).requestFactory(factory)
            .build().method(method).uri(path);
        if (key != null) request.header("Idempotency-Key",key);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).body(body);
        return request.exchange((req,response) -> new Reply(response.getStatusCode().value(),
            response.getStatusCode().is2xxSuccessful() && method != HttpMethod.DELETE
                ? json.readValue(response.getBody(),Map.class) : Map.of()));
    }
    static Map<String,Object> input(String scenario) {
        return Map.of("query","halloween scavenger hunt printable","scenario",scenario);
    }
    static Map<String,Object> submit(String scenario,String key) {
        var response = call("orchestrator",HttpMethod.POST,"/v1/research-runs",input(scenario),key);
        assertEquals(202,response.status()); return response.body();
    }
    static Map<String,Object> await(String id, Predicate<Map<String,Object>> predicate, int seconds) throws Exception {
        long deadline = System.nanoTime()+Duration.ofSeconds(seconds).toNanos();
        while (System.nanoTime()<deadline) {
            var reply = call("orchestrator",HttpMethod.GET,"/v1/research-runs/"+id,null,null);
            assertEquals(200,reply.status());
            if (predicate.test(reply.body())) return reply.body();
            if (Set.of("FAILED","CANCELLED","SUCCEEDED").contains(reply.body().get("status")))
                fail("Unexpected workflow terminal state: "+reply.body().get("status"));
            Thread.sleep(150);
        }
        throw new AssertionError("Workflow did not reach expected state");
    }
    static Map<String,Object> awaitStatus(String id,String status,int seconds) throws Exception {
        return await(id,run -> status.equals(run.get("status")),seconds);
    }

    @Test @Order(1) void successfulReportAndIdempotentRunAndArchiveActions() throws Exception {
        String key = UUID.randomUUID().toString();
        var run = submit("FAST",key);
        String id = run.get("id").toString();
        assertEquals(id,submit("FAST",key).get("id"));
        assertEquals(409,call("orchestrator",HttpMethod.POST,"/v1/research-runs",
            Map.of("query","different","scenario","FAST"),key).status());
        awaitStatus(id,"SUCCEEDED",15);
        var report = call("orchestrator",HttpMethod.GET,"/v1/research-runs/"+id+"/report",null,null);
        assertEquals(200,report.status()); assertEquals("SYNTHETIC_FIXTURE",report.body().get("source"));
        assertEquals(3,report.body().get("digitalListings")); assertEquals(3,report.body().get("distinctShops"));
        var prices = (Map<?,?>)report.body().get("medianPrices");
        assertEquals(4,((Number)prices.get("USD")).intValue());
        assertEquals(4,((Number)prices.get("EUR")).intValue());
        assertEquals("INSUFFICIENT_EVIDENCE",report.body().get("opportunityDecision"));
        assertEquals(201,call("archive",HttpMethod.POST,"/v1/research-reports",report.body(),null).status());
        var changed = new HashMap<>(report.body()); changed.put("source","DIFFERENT");
        assertEquals(409,call("archive",HttpMethod.POST,"/v1/research-reports",changed,null).status());
    }

    @Test @Order(2) void failureAndCancellationRemainExplicit() throws Exception {
        var failed = submit("FAIL",UUID.randomUUID().toString());
        assertEquals("ANALYSIS_FAILED",awaitStatus(failed.get("id").toString(),"FAILED",15).get("errorCode"));
        var slow = submit("SLOW",UUID.randomUUID().toString());
        String id = slow.get("id").toString();
        awaitStatus(id,"ANALYZING",15);
        assertEquals(202,call("orchestrator",HttpMethod.DELETE,"/v1/research-runs/"+id,null,null).status());
        awaitStatus(id,"CANCELLED",15);
    }

    @Test @Order(3) void hardCrashesPreserveWorkflowJobIdentityAndArchiveReport() throws Exception {
        String key = UUID.randomUUID().toString();
        String id = submit("SLOW",key).get("id").toString();
        var slow = awaitStatus(id,"ANALYZING",15);
        String job = slow.get("analysisJobId").toString();
        stop("orchestrator",true); stop("analysis",true);
        start("analysis"); start("orchestrator");
        awaitStatus(id,"SUCCEEDED",45);
        var replay = submit("SLOW",key);
        assertEquals(id,replay.get("id")); assertEquals(job,replay.get("analysisJobId"));
        stop("archive",true); start("archive");
        assertEquals(200,call("orchestrator",HttpMethod.GET,"/v1/research-runs/"+id+"/report",null,null).status());
    }

    @Test @Order(4) void persistedBackoffRecoversAfterDependencyOutage() throws Exception {
        stop("analysis",false);
        String id = submit("FAST",UUID.randomUUID().toString()).get("id").toString();
        await(id,run -> ((Number)run.get("retryCount")).intValue()>0,10);
        start("analysis");
        awaitStatus(id,"SUCCEEDED",20);
    }
}
