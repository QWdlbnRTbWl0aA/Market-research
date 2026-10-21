package local.myhome.marketresearch.tools;

import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Starts only local packaged services. Child logs remain private and never reach tool output. */
public final class ServiceProcess implements AutoCloseable {
    private final Process process;
    private final Path log;
    private final URI base;
    private final long started = System.nanoTime();
    private Duration cpuAtStop = Duration.ZERO;
    private long stopped;

    public ServiceProcess(Path root, String artifact, Path directory, int port, Map<String,String> config) throws Exception {
        PrivateJson.directory(directory);
        log = Files.createTempFile(directory,"service-",".log");
        Path jar = root.resolve("services").resolve(artifact).resolve("target").resolve(artifact+"-0.1.0-SNAPSHOT.jar");
        if (!Files.isRegularFile(jar)) throw new ToolFailure("Build service JARs with mvn verify before replay.");
        List<String> command = new ArrayList<>(List.of(
            Path.of(System.getProperty("java.home"),"bin","java").toString(),"-jar",jar.toAbsolutePath().toString(),
            "--server.address=127.0.0.1","--server.port="+port,"--logging.level.root=OFF","--spring.main.banner-mode=off"));
        config.forEach((k,v) -> command.add("--"+k+"="+v));
        process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true)
            .redirectOutput(log.toFile()).start();
        base = URI.create("http://127.0.0.1:"+port);
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()) {
            long deadline = System.nanoTime()+Duration.ofSeconds(25).toNanos();
            while (System.nanoTime()<deadline) {
                if (!process.isAlive()) throw new ToolFailure("Local service exited before readiness.");
                try {
                    var response = client.send(HttpRequest.newBuilder(base.resolve("/v1/readiness-test"))
                        .timeout(Duration.ofSeconds(1)).GET().build(),HttpResponse.BodyHandlers.discarding());
                    if (response.statusCode()==404) return;
                } catch (java.io.IOException ignored) { /* Still starting. */ }
                Thread.sleep(100);
            }
            throw new ToolFailure("Local service readiness timed out.");
        } catch (Exception e) {
            close();
            throw e;
        }
    }
    public URI base() { return base; }
    public double wallSeconds() { return ((stopped==0 ? System.nanoTime() : stopped)-started)/1_000_000_000.0; }
    public double cpuSeconds() { return cpuAtStop.toNanos()/1_000_000_000.0; }
    public void crash() throws Exception { stop(true); }
    private void stop(boolean force) throws Exception {
        if (!process.isAlive()) return;
        cpuAtStop = process.info().totalCpuDuration().orElse(Duration.ZERO);
        if (force) process.destroyForcibly(); else process.destroy();
        if (!process.waitFor(10,TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor(10,TimeUnit.SECONDS);
        }
        stopped = System.nanoTime();
        Files.deleteIfExists(log);
    }
    @Override public void close() throws Exception { stop(false); Files.deleteIfExists(log); }
    public static int freePort() throws java.io.IOException {
        try (var socket = new java.net.ServerSocket(0,0,InetAddress.getLoopbackAddress())) { return socket.getLocalPort(); }
    }
}
