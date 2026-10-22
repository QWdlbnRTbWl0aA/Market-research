package local.myhome.marketresearch.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.context.annotation.Import(local.myhome.marketresearch.tools.ProviderConfiguration.class)
public class ResearchOrchestratorApplication {
    public static void main(String[] args) {
        SpringApplication.run(ResearchOrchestratorApplication.class, args);
    }
}
