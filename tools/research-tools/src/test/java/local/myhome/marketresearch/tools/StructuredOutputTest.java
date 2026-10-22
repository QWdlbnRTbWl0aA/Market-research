package local.myhome.marketresearch.tools;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class StructuredOutputTest {
    @Test void continuousSchemasRequireEveryFieldAndDoNotAllowCatalogWrappers() {
        var format=StructuredOutput.format("discovery-review#invocation");
        assertEquals("json_schema",format.get("type"));
        var envelope=(Map<?,?>)format.get("json_schema");assertEquals(true,envelope.get("strict"));
        var root=(Map<?,?>)envelope.get("schema");
        var properties=(Map<?,?>)root.get("properties");
        assertEquals(properties.keySet(),new HashSet<>((List<?>)root.get("required")));
        assertEquals(false,root.get("additionalProperties"));
        var products=(Map<?,?>)properties.get("products");assertEquals(5,products.get("maxItems"));
        var product=(Map<?,?>)products.get("items");var fields=(Map<?,?>)product.get("properties");
        assertTrue(fields.containsKey("name"));assertFalse(fields.containsKey("product"));
        assertEquals(List.of("string","null"),((Map<?,?>)fields.get("existingOpportunityId")).get("type"));
    }
    @Test void briefSchemaRequiresCriteriaReadinessAndIntentWithoutChangingExperimentalProtocols() {
        var format=(Map<?,?>)StructuredOutput.format("product-definition").get("json_schema");
        var fields=(Map<?,?>)((Map<?,?>)format.get("schema")).get("properties");
        assertTrue(fields.keySet().containsAll(List.of("criteria","readiness","intentPreserved","unresolvedQuestions")));
        var file=(Map<?,?>)((Map<?,?>)fields.get("files")).get("items");
        assertEquals(false,file.get("additionalProperties"));
        assertEquals("json_object",StructuredOutput.format("legacy-experiment").get("type"));
    }
}
