package local.myhome.marketresearch.tools;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
class ProductionForecastTest {
    @Test void forecastsOnlyThisRunAndIncludesOutputReasoningTokenCost() {
        var ledger=Map.<String,Object>of("calls",List.of(
            Map.of("at","2026-10-21T00:00:00Z","status","SETTLED","inputTokens",999999,"outputTokens",999999),
            Map.of("at","2026-10-21T01:00:10Z","status","SETTLED","inputTokens",1000,"outputTokens",2000)));
        var manifest=Map.<String,Object>of("status","SUCCEEDED","startedAt","2026-10-21T01:00:00Z","finishedAt","2026-10-21T01:01:00Z","newEtsyCalls",17);
        var forecast=ProductionForecast.daily(ledger,manifest);
        assertEquals(1,forecast.get("observedGenerationCalls"));
        assertEquals(0,new BigDecimal("0.00975").compareTo((BigDecimal)forecast.get("estimatedOpenAiUsdPerDay")));
        assertEquals(0,new BigDecimal("0.2925").compareTo((BigDecimal)forecast.get("estimatedOpenAiUsdPer30Days")));
    }
    @Test void failedRunsCannotBecomeDailyProductionForecasts() {
        assertThrows(ToolFailure.class,()->ProductionForecast.daily(Map.of(),Map.of("status","FAILED")));
    }
}
