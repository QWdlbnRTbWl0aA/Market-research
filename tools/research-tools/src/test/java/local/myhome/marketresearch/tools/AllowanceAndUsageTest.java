package local.myhome.marketresearch.tools;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;

class AllowanceAndUsageTest {
    @TempDir Path root;
    ObjectMapper json=new ObjectMapper();
    @Test void newAllowanceDoesNotResetHistoryAndMissingCapCannotAuthorizeAnySpending() throws Exception {
        Path directory=root.resolve("data/provider-ledgers");
        PrivateJson.write(directory.resolve("openai-stage.json"),Map.of("committedUsd",1.2,"calls",List.of(Map.of("status","SETTLED"))),json);
        byte[] historical=Files.readAllBytes(directory.resolve("openai-stage.json"));
        OpenAiAllowance.start(root,"pending",null,json);
        assertThrows(ToolFailure.class,()->new ModelBudget(directory,json,10000,"pending"));
        OpenAiAllowance.confirm(root,"pending",new BigDecimal("2"),json);
        assertThrows(ToolFailure.class,()->OpenAiAllowance.confirm(root,"pending",new BigDecimal("2"),json));
        OpenAiAllowance.start(root,"fresh",new BigDecimal("3"),json);
        var budget=new ModelBudget(directory,json,10000,"fresh");
        budget.openai("fixture-stage",1000,100,()->new ModelBudget.Outcome(Map.of("ok",true),100,20));
        assertArrayEquals(historical,Files.readAllBytes(directory.resolve("openai-stage.json")));
        var allowance=json.readValue(Files.readString(directory.resolve("allowances/fresh.json")),Map.class);
        assertEquals(1,((List<?>)allowance.get("calls")).size());
        assertThrows(ToolFailure.class,()->OpenAiAllowance.start(root,"fresh",new BigDecimal("3"),json));
        assertThrows(ToolFailure.class,()->OpenAiAllowance.start(root,"../escape",BigDecimal.ONE,json));
    }
    @Test void separateAllowanceRetainsUncertainReservationsAndEnforcesItsOwnCeiling() throws Exception {
        var directory=root.resolve("data/provider-ledgers");
        OpenAiAllowance.start(root,"tiny",new BigDecimal(".0001"),json);
        var sent=new AtomicBoolean();
        assertThrows(ToolFailure.class,()->new ModelBudget(directory,json,10000,"tiny").openai("blocked",1000,100,()->{
            sent.set(true);return new ModelBudget.Outcome(Map.of(),1,1);
        }));
        assertFalse(sent.get());
        OpenAiAllowance.start(root,"uncertain",BigDecimal.ONE,json);
        assertThrows(RuntimeException.class,()->new ModelBudget(directory,json,10000,"uncertain").openai("failed",1000,100,()->{throw new RuntimeException();}));
        var state=json.readValue(Files.readString(directory.resolve("allowances/uncertain.json")),Map.class);
        assertTrue(new BigDecimal(state.get("committedUsd").toString()).signum()>0);
    }
    @Test void perStageUsageDistinguishesKnownTokensFromUnknownAttempts() throws Exception {
        Path usage=root.resolve("data/provider-ledgers/stage-usage");
        PrivateJson.write(usage.resolve("one.json"),Map.of("stage","analysis","tokenUsageKnown",true,
            "inputTokens",100,"outputTokens",20,"cachedInputTokens",30,"estimatedUsd",.001,"durationMillis",10),json);
        PrivateJson.write(usage.resolve("two.json"),Map.of("stage","analysis","tokenUsageKnown",false,"durationMillis",20),json);
        var report=StageUsageReport.read(root,json);
        var analysis=(Map<?,?>)((Map<?,?>)report.get("stages")).get("analysis");
        assertEquals(2L,analysis.get("invocations"));assertEquals(1L,analysis.get("unknownUsageAttempts"));
        assertEquals(100L,analysis.get("inputTokens"));assertEquals(30L,analysis.get("cachedInputTokens"));
        assertNull(analysis.get("estimatedUsd"));assertNotNull(analysis.get("knownEstimatedUsd"));
    }
    @Test void clearingOwnedResearchRetainsOperationalMetadataButNeverPayloads() {
        var source=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");
        var db=new JdbcTemplate(source);
        db.execute("CREATE TABLE owned_jobs(id VARCHAR(30),status VARCHAR(30),payload CLOB,result CLOB,usage_json CLOB)");
        db.update("INSERT INTO owned_jobs VALUES('job','SUCCEEDED','OLD BUSINESS CONCLUSIONS','OLD RAW DATA','{\"inputTokens\":100}')");
        ResearchDataClear.clear(db,new DataSourceTransactionManager(source),new MockEnvironment(),json,List.of("owned_jobs"));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM owned_jobs",Integer.class));
        String retained=db.queryForObject("SELECT metadata FROM research_operations_history",String.class);
        assertTrue(retained.contains("SUCCEEDED"));assertTrue(retained.contains("inputTokens"));
        assertFalse(retained.contains("CONCLUSIONS"));assertFalse(retained.contains("RAW DATA"));
        assertEquals(1,db.queryForObject("SELECT rows_cleared FROM research_reset_history",Integer.class));
        assertThrows(ToolFailure.class,()->ResearchDataClear.clear(db,new DataSourceTransactionManager(source),
            new MockEnvironment().withProperty("research.runtime.live-enabled","true"),json,List.of("owned_jobs")));
    }
}
