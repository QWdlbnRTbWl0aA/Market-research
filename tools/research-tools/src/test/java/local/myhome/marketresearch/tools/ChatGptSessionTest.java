package local.myhome.marketresearch.tools;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import java.net.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class ChatGptSessionTest {
    @TempDir Path root;
    final ObjectMapper json=new ObjectMapper();
    Path file;MockRestServiceServer auth,api;ChatGptSession session;
    @BeforeEach void setup() {
        file=root.resolve("session.json");var authBuilder=RestClient.builder().baseUrl("https://auth.invalid");
        var apiBuilder=RestClient.builder().baseUrl("https://api.invalid/v1");
        auth=MockRestServiceServer.bindTo(authBuilder).build();api=MockRestServiceServer.bindTo(apiBuilder).build();
        session=new ChatGptSession(file,json,authBuilder.build(),apiBuilder.build(),token -> {
            try {
                var saved=read();var pendingNonce=(String)saved.get("expectedNonce");
                return Jwt.withTokenValue(token).header("alg","RS256").issuer(ChatGptSession.ISSUER).subject("fixture-subject")
                    .audience(List.of("issued-client")).expiresAt(Instant.now().plusSeconds(3600))
                    .claim("nonce",pendingNonce).claim("email","fixture@example.invalid").build();
            }catch(Exception failure){throw new IllegalStateException(failure);}
        });
    }
    Map<String,Object> read() throws Exception{return json.readValue(Files.readString(file),Map.class);}
    Map<String,String> callback(boolean correctNonce) throws Exception {
        var saved=read();var pending=(Map<?,?>)saved.get("pending");
        saved.put("expectedNonce",correctNonce?pending.get("nonce"):"wrong-nonce");PrivateJson.write(file,saved,json);
        return Map.of("state",pending.get("state").toString(),"client_id","issued-client","code","fixture-code");
    }
    String tokens(String scope) {return json.writeValueAsString(Map.of("id_token","signed-fixture","access_token","access-fixture",
        "refresh_token","refresh-fixture","token_type","Bearer","expires_in",3600,"scope",scope));}
    @Test void firstSignInValidatesStateAndNonceAndStoresOnlyAnAppOwnedProtectedSession() throws Exception {
        var login=session.begin(1456);var first=read();
        String query=URLDecoder.decode(URI.create(login.authorizationUrl()).getRawQuery(),java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(query.contains("client_id=dynamic_agent_client"));assertTrue(query.contains("code_challenge_method=S256"));
        assertTrue(query.contains("redirect_uri=http://127.0.0.1:1456/auth/callback"));
        var values=callback(true);
        assertThrows(ToolFailure.class,()->session.complete(Map.of("state","invalid")),"Wrong state must not reach token exchange");
        auth.expect(requestTo("https://auth.invalid/api/accounts/oauth/token"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("client_id=issued-client")))
            .andRespond(withSuccess(tokens("openid chatgpt.tokens.use.direct offline_access"),MediaType.APPLICATION_JSON));
        session.complete(values);assertEquals("access-fixture",session.accessToken());
        assertEquals(true,session.status().get("planUsageEnabled"));assertFalse(session.status().containsKey("accessToken"));
        assertFalse(session.status().containsKey("hostId"));assertFalse(read().containsKey("pending"));
        assertThrows(ToolFailure.class,()->session.complete(values),"Callback cannot be replayed");
        var returning=session.begin(1456);assertTrue(returning.authorizationUrl().contains("client_id=issued-client"));
        assertEquals(first.get("hostId"),read().get("hostId"));
        if(Files.getFileStore(file).supportsFileAttributeView("posix"))
            assertEquals("rw-------",java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(file)));
        auth.verify();api.verify();
    }
    @Test void invalidIdentityNeverInstallsTokensAndIdentityOnlyConsentCannotPowerInference() throws Exception {
        session.begin(1456);var invalid=callback(false);
        auth.expect(requestTo("https://auth.invalid/api/accounts/oauth/token")).andRespond(withSuccess(tokens("chatgpt.tokens.use.direct"),MediaType.APPLICATION_JSON));
        auth.expect(requestTo("https://auth.invalid/api/accounts/oauth/token")).andRespond(withSuccess(tokens("openid profile email"),MediaType.APPLICATION_JSON));
        assertThrows(ToolFailure.class,()->session.complete(invalid));assertFalse(read().containsKey("accessToken"));
        session.begin(1456);var valid=callback(true);
        session.complete(valid);assertEquals(false,session.status().get("planUsageEnabled"));
        assertThrows(ChatGptUnavailable.class,session::accessToken);auth.verify();api.verify();
    }
    @Test void tokenRotationAndCatalogSelectionSurviveRestartWithoutCrossingApiLedgers() throws Exception {
        PrivateJson.write(file,Map.of("clientId","issued-client","hostId","urn:uuid:fixture","accessToken","expired",
            "refreshToken","refresh-old","expiresAt",0,"scopes",List.of("chatgpt.tokens.use.direct")),json);
        auth.expect(requestTo("https://auth.invalid/api/accounts/oauth/token"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("refresh_token=refresh-old")))
            .andRespond(withSuccess("{\"access_token\":\"rotated-access\",\"refresh_token\":\"rotated-refresh\",\"token_type\":\"Bearer\",\"expires_in\":3600}",MediaType.APPLICATION_JSON));
        assertEquals("rotated-access",session.accessToken());assertEquals("rotated-access",session.accessToken());
        assertEquals("rotated-refresh",read().get("refreshToken"));assertEquals("urn:uuid:fixture",read().get("hostId"));
        api.expect(requestTo("https://api.invalid/v1/models")).andExpect(header("Authorization","Bearer rotated-access"))
            .andRespond(withSuccess("{\"models\":[{\"slug\":\"available-model\",\"display_name\":\"Available\",\"visibility\":\"list\"},{\"slug\":\"hidden\",\"visibility\":\"hidden\"}]}",MediaType.APPLICATION_JSON));
        assertEquals(1,session.models().size());assertEquals("available-model",session.model());
        assertEquals("available-model",session.status().get("model"));
        assertFalse(Files.exists(root.resolve("openai-stage.json")));auth.verify();api.verify();
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={true,false})
    void realJwtSignatureIsRequiredBeforeCredentialsAreInstalled(boolean validSignature) throws Exception {
        var builder=RestClient.builder().baseUrl("https://auth.invalid");
        var signedAuth=MockRestServiceServer.bindTo(builder).build();
        var actual=new ChatGptSession(file,json,builder.build(),RestClient.create("https://unused.invalid"),null);
        actual.begin(1456);var values=callback(true);var pending=(Map<?,?>)read().get("pending");
        var trusted=new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(2048).keyID("fixture-key").generate();
        var signing=validSignature?trusted:new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(2048).keyID("fixture-key").generate();
        var claims=new com.nimbusds.jwt.JWTClaimsSet.Builder().issuer(ChatGptSession.ISSUER).subject("fixture-subject")
            .audience("issued-client").expirationTime(Date.from(Instant.now().plusSeconds(3600)))
            .claim("nonce",pending.get("nonce")).claim("email","fixture@example.invalid").build();
        var identity=new com.nimbusds.jwt.SignedJWT(new com.nimbusds.jose.JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.RS256).keyID("fixture-key").build(),claims);
        identity.sign(new com.nimbusds.jose.crypto.RSASSASigner(signing));
        var tokenResponse=json.readValue(tokens("chatgpt.tokens.use.direct"),Map.class);tokenResponse.put("id_token",identity.serialize());
        signedAuth.expect(requestTo("https://auth.invalid/api/accounts/oauth/token"))
            .andRespond(withSuccess(json.writeValueAsString(tokenResponse),MediaType.APPLICATION_JSON));
        signedAuth.expect(requestTo("https://auth.invalid/.well-known/openid-configuration"))
            .andRespond(withSuccess(json.writeValueAsString(Map.of("issuer",ChatGptSession.ISSUER,"jwks_uri","https://auth.openai.com/fixture-jwks")),MediaType.APPLICATION_JSON));
        signedAuth.expect(requestTo("https://auth.openai.com/fixture-jwks"))
            .andRespond(withSuccess(new com.nimbusds.jose.jwk.JWKSet(trusted.toPublicJWK()).toString(),MediaType.APPLICATION_JSON));
        if(validSignature){actual.complete(values);assertEquals(true,actual.status().get("connected"));}
        else {assertThrows(ToolFailure.class,()->actual.complete(values));assertFalse(read().containsKey("accessToken"));}
        signedAuth.verify();
    }
}
