package local.myhome.marketresearch.tools;
import java.nio.file.*;
import java.util.*;

/** Only known research outputs are disposable; operational ledgers never enter the allowlist. */
final class RunWorkspace {
    private RunWorkspace() {}
    static int clean(Path root) throws Exception {
        Path data=root.toAbsolutePath().normalize().resolve("data");
        if(Files.isSymbolicLink(data))throw new ToolFailure("Data directory is a symlink; cleanup stopped.");
        int removed=0;
        for(String name:List.of("halloween-run","etsy-cohort-1000","etsy-discovery")) {
            Path target=data.resolve(name);
            if(!Files.exists(target,LinkOption.NOFOLLOW_LINKS)) continue;
            if(Files.isSymbolicLink(target) || !Files.isDirectory(target))
                throw new ToolFailure("Research output directory is not a regular directory.");
            List<Path> files;
            try(var walk=Files.walk(target)) { files=walk.sorted(Comparator.reverseOrder()).toList(); }
            if(files.stream().anyMatch(Files::isSymbolicLink))
                throw new ToolFailure("Research output contains a symlink; cleanup stopped.");
            for(Path path:files) {
                if(path.equals(target)) continue;
                if(name.equals("etsy-discovery") && Set.of("budget.json","budget.lock").contains(path.getFileName().toString())) continue;
                if(Files.isRegularFile(path)) {Files.delete(path);removed++;}
                else if(Files.isDirectory(path)) Files.delete(path);
                else throw new ToolFailure("Unexpected research output type; cleanup stopped.");
            }
        }
        return removed;
    }
}
