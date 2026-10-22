package local.myhome.marketresearch.tools;
import java.nio.file.*;
import java.util.*;

final class SecretFiles {
    private SecretFiles() {}
    static String get(Path file,String name) {
        try {
            for (String raw : Files.readAllLines(file)) {
                String line=raw.trim().replaceFirst("^export\\s+","");
                if (line.startsWith("#") || !line.contains("=")) continue;
                int split=line.indexOf('=');
                if (!line.substring(0,split).trim().equals(name)) continue;
                String value=line.substring(split+1).trim();
                if (value.length()>1 && ((value.startsWith("\"")&&value.endsWith("\"")) || (value.startsWith("'")&&value.endsWith("'"))))
                    value=value.substring(1,value.length()-1);
                if (!value.isBlank()) return value;
            }
        } catch (Exception ignored) { }
        throw new ToolFailure("Required provider credential is unavailable.");
    }
}
