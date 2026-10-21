package local.myhome.marketresearch.tools;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import local.myhome.marketresearch.contracts.analysis.MarketResearch.*;

class ResearchStageTest {
    @TempDir Path root;
    ObjectMapper json=new ObjectMapper();
    @Test void missingClassificationCanBeRepairedButUnknownAndDuplicateIdsCannot() {
        var rows=List.<Map<String,Object>>of(Map.of("listing_id",1L),Map.of("listing_id",2L));
        var groups=List.of(new Group("games","Games","halloween bingo printable"));
        var first=new Assignment("1","games",true);
        assertEquals(List.of(rows.get(1)),HalloweenResearchRun.missingAssignments(rows,groups,List.of(first)));
        assertThrows(ToolFailure.class,()->HalloweenResearchRun.validate(rows,groups,List.of(first)));
        assertThrows(ToolFailure.class,()->HalloweenResearchRun.missingAssignments(rows,groups,List.of(first,first)));
        assertThrows(ToolFailure.class,()->HalloweenResearchRun.missingAssignments(rows,groups,List.of(new Assignment("3","games",true))));
        assertDoesNotThrow(()->HalloweenResearchRun.validate(rows,groups,List.of(first,new Assignment("2","games",false))));
    }
    @Test void cleanupRemovesOnlyResearchAndPreservesOperationalCountersAndFixtures() throws Exception {
        PrivateJson.write(root.resolve("data/etsy-cohort-1000/input.json"),Map.of("old",true),json);
        PrivateJson.write(root.resolve("data/halloween-run/report.json"),Map.of("old",true),json);
        PrivateJson.write(root.resolve("data/etsy-discovery/budget.json"),Map.of("calls",List.of(1)),json);
        PrivateJson.write(root.resolve("data/provider-ledgers/openai-stage.json"),Map.of("committedUsd",1),json);
        PrivateJson.write(root.resolve("fixtures/evidence.json"),Map.of("fixture",true),json);
        assertEquals(2,RunWorkspace.clean(root));
        assertFalse(Files.exists(root.resolve("data/etsy-cohort-1000/input.json")));
        assertTrue(Files.exists(root.resolve("data/etsy-discovery/budget.json")));
        assertTrue(Files.exists(root.resolve("data/provider-ledgers/openai-stage.json")));
        assertTrue(Files.exists(root.resolve("fixtures/evidence.json")));
    }
    @Test void cleanupRefusesSymlinksWithoutTouchingTheirTargets() throws Exception {
        PrivateJson.directory(root.resolve("data"));
        Path external=root.resolve("external");Files.createDirectories(external);Files.writeString(external.resolve("keep"),"keep");
        Files.createSymbolicLink(root.resolve("data/halloween-run"),external);
        assertThrows(ToolFailure.class,()->RunWorkspace.clean(root));assertTrue(Files.exists(external.resolve("keep")));
    }
    @Test void budgetBlocksOverTwoDollarsBeforeSendingCall() throws Exception {
        Path directory=root.resolve("ledger");
        PrivateJson.write(directory.resolve("openai-stage.json"),Map.of("committedUsd",new BigDecimal("1.99"),"calls",List.of()),json);
        var sent=new AtomicBoolean();
        assertThrows(ToolFailure.class,()->new ModelBudget(directory,json).openai("test",1000,8000,()->{
            sent.set(true);return new ModelBudget.Outcome(Map.of(),10,10);
        }));
        assertFalse(sent.get());
    }
    @Test void ambiguousFailuresRetainReservationAndSuccessfulCallsSettleUsage() throws Exception {
        Path directory=root.resolve("ledger");
        var budget=new ModelBudget(directory,json);
        assertThrows(RuntimeException.class,()->budget.openai("failed",1000,8000,()->{throw new RuntimeException("transport");}));
        Map<?,?> reserved=json.readValue(Files.readString(directory.resolve("openai-stage.json")),Map.class);
        assertTrue(new BigDecimal(reserved.get("committedUsd").toString()).signum()>0);
        budget.openai("success",1000,8000,()->new ModelBudget.Outcome(Map.of("ok",true),100,200));
        Map<?,?> settled=json.readValue(Files.readString(directory.resolve("openai-stage.json")),Map.class);
        BigDecimal expected=new BigDecimal(reserved.get("committedUsd").toString()).add(ModelBudget.cost(100,200));
        assertEquals(0,expected.compareTo(new BigDecimal(settled.get("committedUsd").toString())));
    }
}
