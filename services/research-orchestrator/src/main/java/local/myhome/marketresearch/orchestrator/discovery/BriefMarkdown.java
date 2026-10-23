package local.myhome.marketresearch.orchestrator.discovery;

import java.util.*;
import tools.jackson.databind.ObjectMapper;

/** Lossless readable field export, including metadata, intent, commercial assumptions and source references. */
final class BriefMarkdown {
    private static final ObjectMapper JSON=new ObjectMapper();
    private BriefMarkdown() {}
    static String render(BriefLibrary.Document document) {
        var result=new StringBuilder("# Product brief\n\nAI-reviewed specification only; not proof of demand or completed production QA.\n\n");
        append(result,JSON.convertValue(document,Map.class),2);
        return result.toString();
    }
    private static void append(StringBuilder output,Object value,int depth) {
        if(value instanceof Map<?,?> map) {
            for(var field:map.entrySet()) {
                output.append("#".repeat(Math.min(depth,6))).append(' ').append(field.getKey()).append("\n\n");
                append(output,field.getValue(),depth+1);
            }
        } else if(value instanceof List<?> list) {
            if(list.isEmpty()) output.append("None.\n\n");
            for(int index=0;index<list.size();index++) {
                Object item=list.get(index);
                if(item instanceof Map<?,?>) {
                    output.append("#".repeat(Math.min(depth,6))).append(" Item ").append(index+1).append("\n\n");
                    append(output,item,depth+1);
                } else output.append("- ").append(text(item)).append("\n\n");
            }
        } else output.append(value==null?"Not specified.":text(value)).append("\n\n");
    }
    private static String text(Object value) {
        return String.valueOf(value).replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
            .replace("[","\\[").replace("]","\\]");
    }
}
