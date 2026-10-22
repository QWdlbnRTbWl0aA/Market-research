package local.myhome.marketresearch.tools;

import java.lang.reflect.*;
import java.util.*;
import local.myhome.marketresearch.contracts.analysis.Discovery;
import local.myhome.marketresearch.contracts.content.ProductDefinition;

/** Provider schema is generated from HTTP response records, not a duplicate handwritten domain schema. */
final class StructuredOutput {
    private StructuredOutput() {}
    static Map<String,Object> format(String purpose) {
        String stage=purpose.split("#",2)[0];
        Class<?> contract=switch(stage) {
            case "discovery-refinement" -> Discovery.Refinement.class;
            case "discovery-business","discovery-review","discovery-group-reassessment" -> Discovery.Assessment.class;
            case "product-definition","product-definition-review" -> ProductDefinition.Brief.class;
            default -> null; // Existing experimental protocols keep their own JSON contracts.
        };
        if(contract==null)return Map.of("type","json_object");
        return Map.of("type","json_schema","json_schema",Map.of("name",contract.getSimpleName(),
            "strict",true,"schema",schema(contract)));
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
