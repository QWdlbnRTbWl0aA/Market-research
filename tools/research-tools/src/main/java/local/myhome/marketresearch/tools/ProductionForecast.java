package local.myhome.marketresearch.tools;

import java.math.*;
import java.time.Instant;
import java.util.*;

final class ProductionForecast {
    private ProductionForecast() {}
    static Map<String,Object> daily(Map<String,Object> ledger,Map<String,Object> manifest) {
        if(!"SUCCEEDED".equals(manifest.get("status")))
            throw new ToolFailure("Production forecast requires a completed research run.");
        Instant start=Instant.parse(manifest.get("startedAt").toString());
        Instant end=Instant.parse(manifest.get("finishedAt").toString());
        long input=0,output=0;int calls=0,uncertain=0;
        for(var raw:(List<?>)ledger.getOrDefault("calls",List.of())) {
            var event=(Map<?,?>)raw;
            Instant at=Instant.parse(event.get("at").toString());
            if(at.isBefore(start)||at.isAfter(end))continue;
            if(!"SETTLED".equals(event.get("status"))) {uncertain++;continue;}
            input+=((Number)event.get("inputTokens")).longValue();
            output+=((Number)event.get("outputTokens")).longValue();calls++;
        }
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("assumptions",List.of("One fresh Halloween seed, three focused samples, and up to three second-pass narrow samples each day.",
            "Same token volume as this run, thirty days per month; no cache discount assumed.",
            "Text analysis only: excludes product/image generation, publishing, promotion and marketplace selling fees.",
            "Model reasoning/output tokens are included. This is list-price arithmetic, not an account invoice.",
            "Human time and compute must be added using configured rates; they are not assumed free.",
            "Production execution is not scheduled or authorized beyond the existing USD 2 development ceiling."));
        result.put("observedGenerationCalls",calls);result.put("observedInputTokens",input);result.put("observedOutputTokens",output);
        result.put("uncertainCallsInWindow",uncertain);
        result.put("estimatedOpenAiUsdPerDay",calls==0?null:ModelBudget.cost(input,output));
        result.put("estimatedOpenAiUsdPer30Days",calls==0?null:ModelBudget.cost(input,output).multiply(BigDecimal.valueOf(30)));
        // Nine normal calls plus at most one repair for each of seven classification batches.
        BigDecimal upper=ModelBudget.cost(154096,8000).multiply(BigDecimal.valueOf(16));
        result.put("configuredWorstCaseOpenAiUsdPerDay",upper);
        result.put("configuredWorstCaseOpenAiUsdPer30Days",upper.multiply(BigDecimal.valueOf(30)));
        result.put("observedEtsyRequestsPerDay",manifest.get("newEtsyCalls"));
        result.put("maximumEtsyRequestsPerDay",20);
        result.put("geminiFreeScenario","Zero token charge only if the account is confirmed free-tier and available quota covers the workload; not verified here.");
        result.put("computeFormula","active process-hours * configured compute rate + allocated idle/shared infrastructure");
        result.put("humanFormula","manual minutes per day / 60 * hourly value * 30 days");
        result.put("humanIllustrationUsdPer30Days",15);
        result.put("humanIllustrationAssumptions","Illustration only: one minute/day at USD 30/hour; not measured or a user-configured rate.");
        return result;
    }
}
