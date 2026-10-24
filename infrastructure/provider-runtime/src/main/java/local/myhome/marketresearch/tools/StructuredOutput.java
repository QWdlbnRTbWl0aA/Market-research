package local.myhome.marketresearch.tools;

import java.lang.reflect.*;
import java.util.*;
import local.myhome.marketresearch.contracts.analysis.Discovery;
import local.myhome.marketresearch.contracts.content.ProductDefinition;

/** Provider schema is generated from HTTP response records, not a duplicate handwritten domain schema. */
final class StructuredOutput {
    private StructuredOutput() {}
    static Map<String,Object> format(String purpose) {
        return format(purpose, null);
    }
    static Map<String,Object> format(String purpose, Object evidence) {
        String stage=purpose.split("#",2)[0];
        Class<?> contract=switch(stage) {
            case "discovery-refinement" -> Discovery.Refinement.class;
            case "discovery-business","discovery-review","discovery-group-reassessment" -> Discovery.Assessment.class;
            case "product-definition","product-definition-review" -> ProductDefinition.Brief.class;
            default -> null; // Existing experimental protocols keep their own JSON contracts.
        };
        if(contract==null)return Map.of("type","json_object");
        var responseSchema = schema(contract);
        Discovery.Task cohort = evidence instanceof Discovery.Task task ? task
            : evidence instanceof Map<?, ?> context && context.get("cohort") instanceof Discovery.Task task ? task : null;
        if (contract == Discovery.Assessment.class && cohort != null) constrainReferences(responseSchema, cohort);
        return Map.of("type","json_schema","json_schema",Map.of("name",contract.getSimpleName(),
            "strict",true,"schema",responseSchema));
    }
    /** Prevent syntactically valid but invented/copied-out-of-cohort citations at generation time. */
    @SuppressWarnings("unchecked")
    private static void constrainReferences(Map<String,Object> root, Discovery.Task cohort) {
        var properties = (Map<String,Object>) root.get("properties");
        var products = (Map<String,Object>) properties.get("products");
        var product = (Map<String,Object>) products.get("items");
        var fields = (Map<String,Object>) product.get("properties");
        var listingIds = cohort.listings().stream().map(Discovery.Listing::id).distinct().toList();
        fields.put("listingIds", listingIds.isEmpty()
            ? Map.of("type","array","maxItems",0,"items",Map.of("type","string"))
            : Map.of("type","array","minItems",1,"maxItems",20,"items",Map.of("type","string","enum",listingIds)));
        var opportunityIds = new ArrayList<String>(cohort.knownOpportunities().stream().map(o -> o.id()).distinct().toList());
        opportunityIds.add(null);
        fields.put("existingOpportunityId", Map.of("type",List.of("string","null"),"enum",opportunityIds));
    }
    static Map<String,Object> schema(Type type) {
        if(type instanceof ParameterizedType generic && generic.getRawType()==List.class)
            return Map.of("type","array","items",schema(generic.getActualTypeArguments()[0]));
        if(!(type instanceof Class<?> value))throw new IllegalArgumentException("Unsupported response type");
        if(value==String.class)return Map.of("type","string");
        if(value==boolean.class||value==Boolean.class)return Map.of("type","boolean");
        if(value==int.class||value==long.class)return Map.of("type","integer");
        if(Number.class.isAssignableFrom(value))return Map.of("type","number");
        if(!value.isRecord())throw new IllegalArgumentException("Unsupported response contract");
        var properties=new LinkedHashMap<String,Object>();
        for(var component:value.getRecordComponents()) {
            var field=new LinkedHashMap<>(schema(component.getGenericType()));
            if(value==Discovery.Product.class&&Set.of("existingOpportunityId","opportunityId").contains(component.getName()))
                field.put("type",List.of("string","null"));
            if(value==Discovery.Assessment.class&&component.getName().equals("products"))field.put("maxItems",5);
            if(value==Discovery.Refinement.class&&component.getName().equals("queries"))field.put("maxItems",3);
            properties.put(component.getName(),field);
        }
        return Map.of("type","object","properties",properties,"required",List.copyOf(properties.keySet()),"additionalProperties",false);
    }
}
