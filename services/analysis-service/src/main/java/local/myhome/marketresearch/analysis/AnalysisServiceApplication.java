package local.myhome.marketresearch.analysis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.context.annotation.Import(local.myhome.marketresearch.tools.ProviderConfiguration.class)
public class AnalysisServiceApplication {
    public static void main(String[] args) {
        var context = SpringApplication.run(AnalysisServiceApplication.class, args);
        if (context.getEnvironment().getProperty("research.maintenance.clear", Boolean.class, false)) context.close();
    }

    @org.springframework.context.annotation.Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="research.maintenance.clear",havingValue="true")
    org.springframework.boot.ApplicationRunner clearResearch(org.springframework.jdbc.core.JdbcTemplate database,
            org.springframework.transaction.PlatformTransactionManager manager, org.springframework.core.env.Environment settings,
            tools.jackson.databind.ObjectMapper json) {
        return arguments -> local.myhome.marketresearch.tools.ResearchDataClear.clear(database, manager, settings, json,
            java.util.List.of("analysis_jobs","discovery_jobs","discovery_cache","discovery_refinement_cache","discovery_opportunities"));
    }
}
