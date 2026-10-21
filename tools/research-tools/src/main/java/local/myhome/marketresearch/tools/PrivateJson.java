package local.myhome.marketresearch.tools;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import tools.jackson.databind.ObjectMapper;

final class PrivateJson {
    private PrivateJson() {}
    static void directory(Path path) throws IOException {
        Files.createDirectories(path);
        if (Files.getFileStore(path).supportsFileAttributeView("posix"))
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"));
    }
    static void write(Path path, Object value, ObjectMapper json) throws IOException {
        directory(path.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(path.toAbsolutePath().getParent(), ".research-", ".json");
        try {
            if (Files.getFileStore(temporary).supportsFileAttributeView("posix"))
                Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString("rw-------"));
            Files.writeString(temporary, json.writerWithDefaultPrettyPrinter().writeValueAsString(value));
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
}
