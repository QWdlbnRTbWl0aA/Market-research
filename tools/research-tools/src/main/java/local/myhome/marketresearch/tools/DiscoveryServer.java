package local.myhome.marketresearch.tools;

import java.nio.file.Path;
import java.util.*;
import org.springframework.core.env.Environment;

/** Foreground supervisor for the four local services. Closing it stops only its own children. */
final class DiscoveryServer {
    static void run(Path root,Environment env) throws Exception {
        var processes=Collections.synchronizedList(new ArrayList<ServiceProcess>());
        Runnable close=()->{synchronized(processes){for(var process:processes)try{process.close();}catch(Exception ignored){}}};
        Thread hook=new Thread(close,"local-services-shutdown");Runtime.getRuntime().addShutdownHook(hook);
        try {
            Map<String,Integer> ports=Map.of(
                "research-orchestrator",env.getProperty("research.runtime.port.orchestrator",Integer.class,8081),
                "analysis-service",env.getProperty("research.runtime.port.analysis",Integer.class,8082),
                "content-service",env.getProperty("research.runtime.port.content",Integer.class,8083),
                "research-archive-service",env.getProperty("research.runtime.port.archive",Integer.class,8084));
            Map<String,String> databases=Map.of("research-orchestrator","orchestrator","analysis-service","analysis","content-service","content","research-archive-service","archive");
            for(String name:List.of("analysis-service","content-service","research-archive-service","research-orchestrator")) {
                var config=new HashMap<String,String>();
                config.put("spring.datasource.url","jdbc:h2:file:"+root.resolve("data/"+databases.get(name))+";DB_CLOSE_ON_EXIT=FALSE;WRITE_DELAY=0");
                config.put("research.runtime.root",root.toString());
                for(String service:List.of("analysis","content","archive"))
                    config.put("market-research.services."+service+"-base-url","http://127.0.0.1:"+ports.get(
                        service.equals("archive")?"research-archive-service":service+"-service"));
                for(String setting:List.of("research.runtime.live-enabled","research.runtime.model-provider","research.runtime.openai-ceiling-usd",
                        "research.runtime.paid-enabled","research.runtime.openai-allowance-id",
                        "research.runtime.etsy-daily-cap","research.models.gemini-credentials-file","research.models.open-ai-credentials-file",
                        "research.models.gemini-free-confirmed","research.models.gemini-model","research.models.open-ai-model",
                        "research.tools.credentials-file","research.discovery.batch-size","research.discovery.max-queries",
                        "research.discovery.max-pages-per-query","research.discovery.max-depth","research.discovery.max-shops",
                        "research.discovery.max-shop-review-requests","research.discovery.max-listing-review-requests")) {
                    String value=env.getProperty(setting);if(value!=null)config.put(setting,value);
                }
                // SMTP values should be supplied via inherited environment or a private Spring config file, never CLI secrets.
                String configuration=env.getProperty("research.runtime.config-file");
                if(configuration!=null)config.put("spring.config.additional-location","file:"+Path.of(configuration).toAbsolutePath());
                processes.add(new ServiceProcess(root,name,root.resolve("data/runtime-services/"+name),ports.get(name),config));
            }
            System.out.println("Local services ready. Use search-start/status/results in another terminal. Ctrl-C stops these services; durable state is retained.");
            while(true) {
                Thread.sleep(1000);
                if(processes.stream().anyMatch(p->!p.alive()))throw new ToolFailure("A local service stopped. Durable jobs are preserved; restart serve.");
            }
        }finally{close.run();Runtime.getRuntime().removeShutdownHook(hook);}
    }
}
