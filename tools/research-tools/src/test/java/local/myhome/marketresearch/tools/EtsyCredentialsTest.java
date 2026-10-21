package local.myhome.marketresearch.tools;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

class EtsyCredentialsTest {
    @TempDir Path directory;
    @Test void readsQuotedEnvFileWithoutShellEvaluation() throws Exception {
        Path file = directory.resolve("test.env");
        Files.writeString(file,"export ETSY_API_KEY='test-key'\nETSY_API_SECRET=\"test=secret\"\n");
        assertEquals("test-key:test=secret",EtsyCredentials.load(file,new MockEnvironment()));
    }
    @Test void supportsSpringEnvironmentWithoutCredentialFile() {
        var environment = new MockEnvironment().withProperty("ETSY_API_KEY","test-key").withProperty("ETSY_API_SECRET","test-secret");
        assertEquals("test-key:test-secret",EtsyCredentials.load(null,environment));
    }
    @Test void missingConfigurationHasSafeError() {
        var error = assertThrows(ToolFailure.class,() -> EtsyCredentials.load(directory.resolve("private-file"),new MockEnvironment()));
        assertFalse(error.getMessage().contains(directory.toString()));
    }
}
