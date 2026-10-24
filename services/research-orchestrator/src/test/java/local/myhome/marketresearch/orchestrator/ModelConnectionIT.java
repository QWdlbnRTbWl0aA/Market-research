package local.myhome.marketresearch.orchestrator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;
import local.myhome.marketresearch.tools.ProviderAccess;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:model-connection;DB_CLOSE_DELAY=-1","research.discovery.workers-enabled=false",
    "research.notifications.delay-ms=3600000","research.runtime.live-enabled=false","research.runtime.paid-enabled=false"})
class ModelConnectionIT {
    @TempDir static Path root;
    @LocalServerPort int port;
    @MockitoBean ProviderAccess provider;
    static final int callbackPort=availablePort();
    static int availablePort(){try(var socket=new java.net.ServerSocket(0,1,InetAddress.getLoopbackAddress())){return socket.getLocalPort();}catch(Exception error){throw new IllegalStateException(error);}}
    @DynamicPropertySource static void settings(DynamicPropertyRegistry properties) {
        properties.add("research.models.chatgpt-session-file",()->root.resolve("session.json").toString());
        properties.add("research.models.chatgpt-callback-port",()->callbackPort);
    }
    @Test void browserConnectionNeverReturnsTokensOrStartsResearchAndCallbackStaysOnLoopback() throws Exception {
        var http=RestClient.create("http://127.0.0.1:"+port);
        var before=http.get().uri("/v1/model-connection").retrieve().toEntity(Map.class);
        assertEquals("no-store",before.getHeaders().getCacheControl());assertEquals(false,before.getBody().get("connected"));
        assertEquals("chatgpt",before.getBody().get("primary"));
        var rejected=http.post().uri("/v1/model-connection/sign-in").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .body(Map.of()).exchange((request,response)->response.getStatusCode().value());
        assertEquals(400,rejected,"Missing explicit action header is rejected");
        var login=http.post().uri("/v1/model-connection/sign-in").header("X-Research-Action","model-connection")
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(Map.of()).retrieve().body(Map.class);
        assertEquals("auth.openai.com",URI.create(login.get("authorizationUrl").toString()).getHost());
        assertEquals("http://127.0.0.1:"+callbackPort+"/auth/callback",login.get("redirectUri"));
        String saved=Files.readString(root.resolve("session.json"));
        var callback=RestClient.create("http://127.0.0.1:"+callbackPort);
        int callbackStatus=callback.get().uri("/auth/callback?state=wrong&code=fixture")
            .exchange((request,response)->response.getStatusCode().value());
        assertEquals(400,callbackStatus);
        assertEquals(saved,Files.readString(root.resolve("session.json")),"Invalid callbacks cannot replace the pending authorization");
        String status=http.get().uri("/v1/model-connection").retrieve().body(String.class);
        assertFalse(status.contains("verifier"));assertFalse(status.contains("hostId"));assertFalse(status.contains("accessToken"));
        String page=http.get().uri("/").retrieve().body(String.class);assertTrue(page.contains("Continue with ChatGPT"));
        verifyNoInteractions(provider);
    }
}
