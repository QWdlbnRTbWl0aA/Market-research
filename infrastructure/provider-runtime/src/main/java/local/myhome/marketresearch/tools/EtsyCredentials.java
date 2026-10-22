package local.myhome.marketresearch.tools;

import java.nio.file.*;
import java.util.*;
import org.springframework.core.env.Environment;

final class EtsyCredentials {
    private EtsyCredentials() {}
    static String load(Path file, Environment environment) {
        Map<String,String> values = new HashMap<>();
        try {
            if (file != null) for (String raw : Files.readAllLines(file)) {
                String line = raw.trim().replaceFirst("^export\\s+","");
                if (line.startsWith("#") || !line.contains("=")) continue;
                int split = line.indexOf('=');
                String value = line.substring(split+1).trim();
                if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'"))))
                    value = value.substring(1,value.length()-1);
                values.put(line.substring(0,split).trim(),value);
            }
            String key = values.getOrDefault("ETSY_API_KEY",environment.getProperty("ETSY_API_KEY",""));
            String secret = values.getOrDefault("ETSY_API_SECRET",environment.getProperty("ETSY_API_SECRET",""));
            if (key.isBlank() || secret.isBlank()) throw new ToolFailure("Etsy credentials are missing.");
            return key+":"+secret;
        } catch (java.io.IOException e) { throw new ToolFailure("Cannot read configured credential file."); }
    }
}
