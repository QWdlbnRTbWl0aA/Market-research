package local.myhome.marketresearch.tools;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import local.myhome.marketresearch.contracts.analysis.Discovery.*;

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
    @Test void assessmentReferencesAreRestrictedToTheActualCohortForGenerationAndReview() {
        var listing=new Listing("123","456","Fixture","",List.of(),java.math.BigDecimal.ONE,"USD",0,0,0);
        var opportunity=new Opportunity("known-opportunity",null,null,List.of("old-evidence-id"),1);
        var task=new Task("BUSINESS","fixture",List.of(listing),List.of(),List.of(),List.of(),"branch",List.of(),List.of(opportunity));
        for (String stage:List.of("discovery-business","discovery-review","discovery-group-reassessment")) {
            Object evidence=stage.equals("discovery-review")?Map.of("cohort",task):task;
            var envelope=(Map<?,?>)StructuredOutput.format(stage+"#invocation",evidence).get("json_schema");
            var root=(Map<?,?>)envelope.get("schema");var properties=(Map<?,?>)root.get("properties");
            var product=(Map<?,?>)((Map<?,?>)properties.get("products")).get("items");
            var fields=(Map<?,?>)product.get("properties");
            var citations=(Map<?,?>)fields.get("listingIds");
            assertEquals(List.of("123"),((Map<?,?>)citations.get("items")).get("enum"));
            assertEquals(1,citations.get("minItems"));assertEquals(20,citations.get("maxItems"));
            assertEquals(Arrays.asList("known-opportunity",null),((Map<?,?>)fields.get("existingOpportunityId")).get("enum"));
        }
        assertEquals(List.of("old-evidence-id"),opportunity.evidenceIds(),"Catalog evidence is not a current-cohort allowlist");
    }
    @Test void emptyCohortAndCatalogDoNotCreateInvalidEmptyEnumsOrLeakConstraintsToLaterCalls() {
        var task=new Task("BUSINESS","fixture",List.of(),List.of(),List.of(),List.of());
        var envelope=(Map<?,?>)StructuredOutput.format("discovery-business",task).get("json_schema");
        var root=(Map<?,?>)envelope.get("schema");var properties=(Map<?,?>)root.get("properties");
        var product=(Map<?,?>)((Map<?,?>)properties.get("products")).get("items");
        var fields=(Map<?,?>)product.get("properties");
        assertEquals(0,((Map<?,?>)fields.get("listingIds")).get("maxItems"));
        assertEquals(Collections.singletonList(null),((Map<?,?>)fields.get("existingOpportunityId")).get("enum"));
        var generic=(Map<?,?>)StructuredOutput.format("discovery-business").get("json_schema");
        var genericProperties=(Map<?,?>)((Map<?,?>)generic.get("schema")).get("properties");
        var genericProduct=(Map<?,?>)((Map<?,?>)genericProperties.get("products")).get("items");
        assertFalse(((Map<?,?>)((Map<?,?>)genericProduct.get("properties")).get("existingOpportunityId")).containsKey("enum"));
    }
}
