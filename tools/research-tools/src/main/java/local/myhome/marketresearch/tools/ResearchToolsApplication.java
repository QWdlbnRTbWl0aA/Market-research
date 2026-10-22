package local.myhome.marketresearch.tools;

import java.net.*;
import java.nio.file.Path;
import java.nio.file.Files;
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
@EnableConfigurationProperties({ResearchToolsApplication.Settings.class,ModelSettings.class,ResearchToolsApplication.ComparisonSettings.class})
public class ResearchToolsApplication {
    @ConfigurationProperties("research.tools")
    public record Settings(@DefaultValue(".") Path root, @DefaultValue("help") String command,
                           Path credentialsFile, @DefaultValue("false") boolean liveEnabled) {}
    @ConfigurationProperties("research.comparison")
    public record ComparisonSettings(@DefaultValue("gemini") String provider) {}

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
    @Bean ApplicationRunner execute(Settings settings, ModelSettings models, ComparisonSettings comparison, Environment environment, ObjectMapper json) {
        return arguments -> {
            Path root = settings.root().toAbsolutePath().normalize();
            if("serve".equals(settings.command())){DiscoveryServer.run(root,environment);return;}
            if ("help".equals(settings.command())) {
                System.out.println("Commands: search-start, search-list, search-status, search-results, search-resume, search-cancel, search-approve-trend, search-dismiss-trend, search-notifications. Supply research.discovery.query/key or id. Maintenance: new-allowance, confirm-allowance, usage-report, fresh-investigation (destructive research-data reset; stop services first). Experimental commands: replay/forecast/comparison-prepare (offline), provider-check (metadata), compare/collect-comparison/research/collect/discover (live-enabled required).");
                return;
            }
            Map<String,Object> result;
            if("mail-report".equals(settings.command()))result=ReportMail.send(
                Path.of(environment.getRequiredProperty("research.report.smtp-file")),
                Path.of(environment.getRequiredProperty("research.report.file")),
                environment.getRequiredProperty("research.report.to"),
                environment.getRequiredProperty("research.report.subject"));
            else if("fresh-investigation".equals(settings.command())) {
                if(settings.liveEnabled())throw new ToolFailure("Fresh cleanup requires live tools disabled.");
                result=FreshInvestigation.run(root);
            }
            else if("new-allowance".equals(settings.command())) {
                String cap=environment.getProperty("research.allowance.usd");
                result=OpenAiAllowance.start(root,environment.getRequiredProperty("research.allowance.id"),
                    cap==null?null:new java.math.BigDecimal(cap),json);
            }
            else if("confirm-allowance".equals(settings.command()))result=OpenAiAllowance.confirm(root,
                environment.getRequiredProperty("research.allowance.id"),new java.math.BigDecimal(environment.getRequiredProperty("research.allowance.usd")),json);
            else if("usage-report".equals(settings.command()))result=StageUsageReport.read(root,json);
            else if (settings.command().startsWith("search-")) result=DiscoveryCli.run(settings.command(),environment);
            else if ("provider-check".equals(settings.command())) {
                result = new ModelPreflight(ProviderHttp.client("https://generativelanguage.googleapis.com/v1beta",environment),
                    ProviderHttp.client("https://api.openai.com/v1",environment),json,models).run(root);
            }
            else if ("replay".equals(settings.command())) result = new CohortReplay(json).run(root);
            else if ("comparison-prepare".equals(settings.command()) || "compare".equals(settings.command())) {
                if("compare".equals(settings.command())&&!settings.liveEnabled())throw new ToolFailure("Live model comparison calls are disabled.");
                var corpus=new FrozenCorpus(root,json);
                var ceiling=ProductComparison.spendingCeiling(root,json);
                if("comparison-prepare".equals(settings.command())) {
                    var compact=corpus.compactListings();var sizes=new java.util.ArrayList<Integer>();
                    for(int offset=0;offset<compact.size();offset+=1667)sizes.add(json.writeValueAsBytes(
                        Map.of("context",corpus.context(),"listings",compact.subList(offset,Math.min(offset+1667,compact.size())))).length);
                    result=Map.of("sourceHashes",corpus.hashes,"listings",compact.size(),"shops",corpus.shops.size(),
                        "reviewExcerpts",corpus.reviews.size(),"scanInputBytes",sizes,"openAiCeilingUsd",ceiling,"protocol",ProductReasoning.VERSION);
                    PrivateJson.write(root.resolve("data/halloween-run/comparison/prepared.json"),result,json);
                } else {
                    if(!settings.liveEnabled())throw new ToolFailure("Live model comparison calls are disabled.");
                    String provider=comparison.provider();
                    if(!java.util.Set.of("gemini","openai").contains(provider))throw new ToolFailure("Choose gemini or openai explicitly.");
                    Path output=Files.createTempDirectory(root.resolve("data/halloween-run/comparison"),"api-"+provider+"-");
                    var reasoning=new ResearchModels(ProviderHttp.client("https://api.openai.com/v1",environment),
                        ProviderHttp.client("https://generativelanguage.googleapis.com/v1beta",environment),models,json,root)
                        .scoped(environment.getProperty("research.runtime.openai-allowance-id"),environment.getProperty("research.runtime.paid-enabled",Boolean.class,false));
                    result=new ProductComparison(corpus,json,(purpose,instructions,evidence)->
                        reasoning.askOn(provider,purpose,instructions,evidence,ceiling,750_000),output).run(provider);
                }
            }
            else if ("forecast".equals(settings.command())) {
                result=ProductionForecast.daily(
                    json.readValue(Files.readString(root.resolve("data/provider-ledgers/openai-stage.json")),Map.class),
                    json.readValue(Files.readString(root.resolve("data/halloween-run/manifest.json")),Map.class));
                PrivateJson.write(root.resolve("data/halloween-run/production-forecast.json"),result,json);
            }
            else if ("collect".equals(settings.command()) || "discover".equals(settings.command()) || "research".equals(settings.command()) || "collect-comparison".equals(settings.command())) {
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
                if("collect-comparison".equals(settings.command()))result=new LargeSampleCollector(root,gateway,ledger,json).run();
                else if ("research".equals(settings.command())) {
                    var reasoning=new ResearchModels(ProviderHttp.client("https://api.openai.com/v1",environment),
                        ProviderHttp.client("https://generativelanguage.googleapis.com/v1beta",environment),models,json,root)
                        .scoped(environment.getProperty("research.runtime.openai-allowance-id"),environment.getProperty("research.runtime.paid-enabled",Boolean.class,false));
                    result=new HalloweenResearchRun(root,gateway,ledger,reasoning,json).run();
                } else {
                    RunWorkspace.clean(root);
                    result = "collect".equals(settings.command())
                        ? research.collect(root.resolve("data/etsy-cohort-1000"))
                        : research.discover(root.resolve("data/etsy-discovery"));
                }
            } else throw new ToolFailure("Unknown research tool command.");
            System.out.println(json.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        };
    }
}
