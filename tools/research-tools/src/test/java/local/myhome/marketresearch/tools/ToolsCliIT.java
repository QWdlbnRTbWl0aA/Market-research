package local.myhome.marketresearch.tools;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class ToolsCliIT {
    @TempDir Path directory;
    private Process start(Path root,Path output,String... arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of(
            Path.of(System.getProperty("java.home"),"bin","java").toString(),"-jar",
            root.resolve("tools/research-tools/target/research-tools-0.1.0-SNAPSHOT.jar").toString()));
        command.addAll(List.of(arguments));
        return new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
    }
    @Test void bootsWithoutCredentialsAndRefusesLiveCallsUnlessExplicitlyEnabled() throws Exception {
        Path root = Path.of(System.getProperty("project.root")), output = directory.resolve("cli.log");
        var help = start(root,output);
        assertTrue(help.waitFor(25,TimeUnit.SECONDS)); assertEquals(0,help.exitValue());
        assertTrue(Files.readString(output).contains("Commands: replay"));
        var blocked = start(root,output,"--research.tools.command=collect","--research.tools.root="+directory);
        assertTrue(blocked.waitFor(25,TimeUnit.SECONDS)); assertEquals(1,blocked.exitValue());
        assertFalse(Files.exists(directory.resolve("data/etsy-discovery/budget.json")));
    }
    @Test void replaysThousandSyntheticListingsThroughPackagedAnalysisService() throws Exception {
        Path root = Path.of(System.getProperty("project.root"));
        Path temporaryRoot = directory.resolve("project");
        // The test's project contains only fresh synthetic evidence and links to packaged services.
        Files.createDirectories(temporaryRoot);
        Files.createSymbolicLink(temporaryRoot.resolve("services"),root.resolve("services"));
        List<Map<String,Object>> listings = new ArrayList<>();
        for (int i=1;i<=1000;i++) listings.add(Map.of("listing_id",i,"shop_id",1,"title","Halloween",
            "listing_type","download","price",Map.of("amount",300,"divisor",100,"currency_code","USD"),
            "is_personalizable",false,"is_customizable",false));
        var json = new ObjectMapper();
        PrivateJson.write(temporaryRoot.resolve("data/etsy-cohort-1000/input.json"),Map.of("query","halloween",
            "observedAt","2026-10-21T00:00:00Z","listings",listings),json);
        Path output = directory.resolve("replay.log");
        var process = start(root,output,"--research.tools.command=replay","--research.tools.root="+temporaryRoot);
        assertTrue(process.waitFor(40,TimeUnit.SECONDS)); assertEquals(0,process.exitValue());
        Map<?,?> report = json.readValue(Files.readString(temporaryRoot.resolve("data/etsy-cohort-1000/report.json")),Map.class);
        assertEquals(1000,report.get("uniqueListings"));
        assertEquals(1000,report.get("digitalListings"));
        assertEquals(0,((Map<?,?>)report.get("testRun")).get("llmCalls"));
        assertFalse(Files.exists(temporaryRoot.resolve("data/etsy-discovery/budget.json")));
    }
}
