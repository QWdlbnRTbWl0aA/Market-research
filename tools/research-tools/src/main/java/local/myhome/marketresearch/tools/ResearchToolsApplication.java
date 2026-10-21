package local.myhome.marketresearch.tools;

import java.net.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.*;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@SpringBootApplication
@EnableConfigurationProperties(ResearchToolsApplication.Settings.class)
public class ResearchToolsApplication {
    @ConfigurationProperties("research.tools")
    public record Settings(@DefaultValue(".") Path root, @DefaultValue("help") String command,
                           Path credentialsFile, @DefaultValue("false") boolean liveEnabled) {}

    public static void main(String[] args) {
        try (var ignored = new SpringApplicationBuilder(ResearchToolsApplication.class)
                .web(WebApplicationType.NONE).logStartupInfo(false)
                .properties("spring.main.banner-mode=off","logging.level.root=OFF").run(args)) {
            // ApplicationRunner completes before the context closes.
        } catch (Exception e) {
            String message = "Research tool failed; check the command, local inputs, quota and configuration.";
            for (Throwable cause=e; cause!=null; cause=cause.getCause())
                if (cause instanceof ToolFailure) { message=cause.getMessage(); break; }
            System.err.println(message);
            System.exit(1);
        }
    }
    @Bean ApplicationRunner execute(Settings settings, Environment environment, ObjectMapper json) {
        return arguments -> {
            Path root = settings.root().toAbsolutePath().normalize();
            if ("help".equals(settings.command())) {
                System.out.println("Commands: replay (offline), collect and discover (require research.tools.live-enabled=true).");
                return;
            }
            Map<String,Object> result;
            if ("replay".equals(settings.command())) result = new CohortReplay(json).run(root);
            else if ("collect".equals(settings.command()) || "discover".equals(settings.command())) {
                if (!settings.liveEnabled()) throw new ToolFailure("Live research calls are disabled.");
                var factory = new SimpleClientHttpRequestFactory() {
                    @Override protected void prepareConnection(HttpURLConnection connection,String method)
                            throws java.io.IOException {
                        super.prepareConnection(connection,method);
                        // Do not forward the API-key header to a redirect destination.
                        connection.setInstanceFollowRedirects(false);
                    }
                };
                factory.setConnectTimeout(Duration.ofSeconds(15)); factory.setReadTimeout(Duration.ofSeconds(30));
                // Keep the session's configured HTTP proxy path for outbound HTTPS.
                String proxy = environment.getProperty("HTTPS_PROXY",environment.getProperty("HTTP_PROXY",""));
                if (!proxy.isBlank()) {
                    URI uri = URI.create(proxy);
                    factory.setProxy(new Proxy(Proxy.Type.HTTP,new InetSocketAddress(uri.getHost(),uri.getPort()<0 ? 80 : uri.getPort())));
                }
                var client = RestClient.builder().baseUrl("https://openapi.etsy.com/v3/application").requestFactory(factory).build();
                var gateway = new EtsyGateway(client,json,() -> EtsyCredentials.load(settings.credentialsFile(),environment));
                var ledger = new QuotaLedger(root.resolve("data/etsy-discovery"),json,System::currentTimeMillis,Thread::sleep);
                var research = new EtsyResearch(gateway,ledger,json);
                result = "collect".equals(settings.command())
                    ? research.collect(root.resolve("data/etsy-cohort-1000"))
                    : research.discover(root.resolve("data/etsy-discovery"));
            } else throw new ToolFailure("Unknown research tool command.");
            System.out.println(json.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        };
    }
}
