package local.myhome.marketresearch.tools;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Explicit offline cleanup. Owned service processes clear their own schemas, retaining operational projections. */
final class FreshInvestigation {
    private FreshInvestigation() {}

    static Map<String,Object> run(Path root) throws Exception {
        root = root.toAbsolutePath().normalize();
        if(Files.isSymbolicLink(root)||!Files.isRegularFile(root.resolve("pom.xml"))||!Files.isDirectory(root.resolve("services"))
                ||!Files.readString(root.resolve("pom.xml")).contains("<artifactId>market-research-parent</artifactId>"))
            throw new ToolFailure("Fresh cleanup requires the validated project root");
        if (Files.isSymbolicLink(root.resolve("data"))) throw new ToolFailure("Refusing symlinked data directory");
        int databases = 0;
        for (var service : Map.of("research-orchestrator","orchestrator","analysis-service","analysis",
                "content-service","content","research-archive-service","archive").entrySet()) {
            for (Path database : List.of(root.resolve("data/" + service.getValue()),
                    root.resolve("services/" + service.getKey() + "/data/" + service.getValue()))) {
                Path file = Path.of(database + ".mv.db");
                if (!Files.exists(file)) continue;
                if (Files.isSymbolicLink(file) || Files.isSymbolicLink(database.getParent()))
                    throw new ToolFailure("Refusing symlinked service database");
                Path output = Files.createTempFile("research-clear-", ".log");
                try {
                    var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                        "-jar", root.resolve("services/" + service.getKey() + "/target/" + service.getKey() + "-0.1.0-SNAPSHOT.jar").toString(),
                        "--research.maintenance.clear=true", "--research.runtime.live-enabled=false", "--research.runtime.paid-enabled=false",
                        "--research.notifications.smtp-enabled=false", "--spring.main.web-application-type=none",
                        "--logging.level.root=OFF", "--spring.main.banner-mode=off",
                        "--market-research.services.analysis-base-url=http://127.0.0.1:0",
                        "--market-research.services.content-base-url=http://127.0.0.1:0",
                        "--market-research.services.archive-base-url=http://127.0.0.1:0",
                        "--spring.datasource.url=jdbc:h2:file:" + database + ";DB_CLOSE_ON_EXIT=FALSE;WRITE_DELAY=0")
                        .directory(root.toFile()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
                    if (!process.waitFor(30, TimeUnit.SECONDS)) {
                        process.destroy();
                        if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly();
                        throw new ToolFailure("Research clearing timed out; stop running services before retrying.");
                    }
                    if (process.exitValue() != 0) throw new ToolFailure("Service research clearing failed; history and quota ledgers were not reset.");
                    databases++;
                } finally { Files.deleteIfExists(output); }
            }
        }
        int files = RunWorkspace.clean(root);
        Path responses = root.resolve("data/provider-ledgers/model-responses");
        if (Files.exists(responses, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(responses)) throw new ToolFailure("Refusing symlinked model responses");
            List<Path> targets;
            try (var walk = Files.walk(responses)) { targets = walk.sorted(Comparator.reverseOrder()).toList(); }
            if (targets.stream().anyMatch(Files::isSymbolicLink)) throw new ToolFailure("Refusing symlinked response artifact");
            for (Path target : targets) {
                Files.delete(target);
                files++;
            }
        }
        return Map.of("researchFilesRemoved", files, "serviceDatabasesCleared", databases,
            "usageLedgers", "Preserved", "operationalHistory", "Preserved without research payloads",
            "nextRun", "Fresh investigation; no prior conclusions or source records");
    }
}
