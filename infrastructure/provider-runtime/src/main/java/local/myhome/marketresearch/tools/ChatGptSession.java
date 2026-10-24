package local.myhome.marketresearch.tools;

import java.net.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.*;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;

/** App-owned OAuth session; never reads Codex/browser credentials. All processes serialize token refresh. */
public final class ChatGptSession {
    static final String ISSUER = "https://auth.openai.com";
    static final String RESOURCE = "https://api.openai.com/v1";
    private static final String PLAN_SCOPE = "chatgpt.tokens.use.direct";
    private final Path file;
    private final ObjectMapper json;
    private final RestClient auth, api;
    private final IdTokenVerifier verifier;
    @FunctionalInterface interface IdTokenVerifier { Jwt verify(String token); }
    @FunctionalInterface private interface Locked<T> { T run() throws Exception; }
    public record Authorization(String authorizationUrl, String redirectUri, long expiresAt) {}

    public ChatGptSession(Environment env, ObjectMapper json) {
        this(Path.of(env.getProperty("research.models.chatgpt-session-file",
                Path.of(System.getProperty("user.home"),"env_files","market-research-chatgpt.json").toString())), json,
            ProviderHttp.client(ISSUER,env), ProviderHttp.client(RESOURCE,env), null);
    }
    ChatGptSession(Path file, ObjectMapper json, RestClient auth, RestClient api, IdTokenVerifier verifier) {
        this.file=file;this.json=json;this.auth=auth;this.api=api;
        this.verifier=verifier==null ? this::verifySignature : verifier;
    }
    private <T> T locked(Locked<T> operation) throws Exception {
        PrivateJson.directory(file.toAbsolutePath().getParent());
        try (var channel=FileChannel.open(file.resolveSibling(file.getFileName()+".lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
             var lock=channel.tryLock()) {
            if(lock==null)throw new ProviderBusy();
            return operation.run();
        } catch(OverlappingFileLockException busy) { throw new ProviderBusy(); }
    }
    @SuppressWarnings("unchecked") private Map<String,Object> read() throws Exception {
        return Files.exists(file) ? json.readValue(Files.readString(file),Map.class) : new LinkedHashMap<>();
    }
    public Map<String,Object> status() throws Exception {
        return locked(() -> {
            var saved=read();var result=new LinkedHashMap<String,Object>();
            result.put("connected",saved.containsKey("accessToken"));
            result.put("planUsageEnabled",scopes(saved).contains(PLAN_SCOPE));
            result.put("account",saved.getOrDefault("email",""));
            result.put("model",saved.getOrDefault("model",""));
            result.put("models",saved.getOrDefault("models",List.of()));
            result.put("loginStatus",saved.getOrDefault("loginStatus","DISCONNECTED"));
            result.put("loginExpiresAt",saved.getOrDefault("loginExpiresAt",0));
            return result;
        });
    }
    public Authorization begin(int port) throws Exception {
        if(port<1024||port>65535)throw new IllegalArgumentException("Invalid callback port");
        return locked(() -> {
            var saved=read();saved.putIfAbsent("hostId","urn:uuid:"+UUID.randomUUID());
            String state=random(),nonce=random(),pkce=random();
            String callback="http://127.0.0.1:"+port+"/auth/callback";
            long expires=System.currentTimeMillis()+600_000;
            saved.put("pending",Map.of("state",state,"nonce",nonce,"verifier",pkce,"callback",callback));
            saved.put("loginStatus","AWAITING_BROWSER");saved.put("loginExpiresAt",expires);
            var parameters=new LinkedHashMap<String,String>();
            parameters.put("client_id",saved.getOrDefault("clientId","dynamic_agent_client").toString());
            if(!saved.containsKey("clientId"))parameters.put("agent_name_hint","Market Research");
            parameters.put("ext_agent_host_id",saved.get("hostId").toString());
            parameters.put("response_type","code");parameters.put("redirect_uri",callback);
            parameters.put("scope","openid profile email offline_access resource.invoke "+PLAN_SCOPE);
            parameters.put("resource",RESOURCE);parameters.put("state",state);parameters.put("nonce",nonce);
            parameters.put("code_challenge_method","S256");
            parameters.put("code_challenge",Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(pkce.getBytes(java.nio.charset.StandardCharsets.US_ASCII))));
            PrivateJson.write(file,saved,json);
            String query=parameters.entrySet().stream().map(e->encode(e.getKey())+"="+encode(e.getValue())).collect(java.util.stream.Collectors.joining("&"));
            return new Authorization(ISSUER+"/api/accounts/authorize?"+query,callback,expires);
        });
    }
    @SuppressWarnings("unchecked") public void complete(Map<String,String> callback) throws Exception {
        locked(() -> {
            var saved=read();var pending=(Map<String,Object>)saved.get("pending");
            if(pending==null||System.currentTimeMillis()>((Number)saved.getOrDefault("loginExpiresAt",0)).longValue()
                    ||!constantTime(Objects.toString(pending.get("state"),""),callback.getOrDefault("state","")))
                throw new ToolFailure("Invalid or expired sign-in callback");
            if(callback.containsKey("error")) {
                saved.remove("pending");saved.put("loginStatus","DECLINED");PrivateJson.write(file,saved,json);
                throw new ToolFailure("ChatGPT authorization was declined");
            }
            String existing=(String)saved.get("clientId"),issued=callback.getOrDefault("client_id",existing);
            if(issued==null||issued.isBlank()||issued.equals("dynamic_agent_client")
                    ||existing!=null&&!existing.equals(issued)||callback.getOrDefault("code","").isBlank())
                throw new ToolFailure("Incomplete or mismatched ChatGPT registration");
            // Retain the issued registration even if code exchange fails; never register a new client on retry.
            saved.put("clientId",issued);saved.remove("pending");saved.put("loginStatus","EXCHANGING");PrivateJson.write(file,saved,json);
            try {
                var tokens=token(Map.of("grant_type","authorization_code","client_id",issued,"code",callback.get("code"),
                    "code_verifier",pending.get("verifier").toString(),"redirect_uri",pending.get("callback").toString(),"resource",RESOURCE));
                String identity=required(tokens,"id_token");Jwt jwt=verifier.verify(identity);
                if(!ISSUER.equals(jwt.getIssuer().toString())||!jwt.getAudience().contains(issued)
                        ||jwt.getExpiresAt()==null||!jwt.getExpiresAt().isAfter(Instant.now())
                        ||!constantTime(Objects.toString(pending.get("nonce"),""),jwt.getClaimAsString("nonce"))
                        ||jwt.getSubject()==null||saved.containsKey("subject")&&!saved.get("subject").equals(jwt.getSubject()))
                    throw new ToolFailure("ChatGPT identity validation failed");
                var authorized=new LinkedHashMap<>(saved);saveTokens(authorized,tokens);
                authorized.put("subject",jwt.getSubject());authorized.put("email",Objects.toString(jwt.getClaimAsString("email"),"Connected account"));
                authorized.put("idToken",identity);authorized.put("loginStatus",scopes(authorized).contains(PLAN_SCOPE)?"CONNECTED":"PLAN_PERMISSION_REQUIRED");
                PrivateJson.write(file,authorized,json);
            } catch(Exception failure) {
                saved.put("loginStatus","SIGN_IN_FAILED");PrivateJson.write(file,saved,json);
                throw new ToolFailure("ChatGPT sign-in failed; start a new sign-in attempt");
            }
            return null;
        });
    }
    private Jwt verifySignature(String identity) {
        var metadata=auth.get().uri("/.well-known/openid-configuration").retrieve().body(Map.class);
        if(metadata==null||!ISSUER.equals(metadata.get("issuer")))throw new ToolFailure("Invalid ChatGPT issuer metadata");
        URI jwks=URI.create(Objects.toString(metadata.get("jwks_uri"),""));
        if(!"https".equals(jwks.getScheme())||!"auth.openai.com".equals(jwks.getHost())||jwks.getUserInfo()!=null)
            throw new ToolFailure("Invalid ChatGPT signing-key endpoint");
        try {
            String keys=auth.get().uri(jwks).retrieve().body(String.class);
            var processor=new DefaultJWTProcessor<SecurityContext>();
            processor.setJWSKeySelector(new JWSVerificationKeySelector<>(Set.of(JWSAlgorithm.RS256,JWSAlgorithm.ES256),
                new ImmutableJWKSet<>(JWKSet.parse(keys))));
            var decoder=new NimbusJwtDecoder(processor);
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
            return decoder.decode(identity);
        } catch(Exception failure) {throw new ToolFailure("ChatGPT identity signature validation failed");}
    }
    private Map<String,Object> token(Map<String,String> values) {
        var form=new LinkedMultiValueMap<String,String>();values.forEach(form::add);
        return auth.post().uri("/api/accounts/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form).retrieve().body(Map.class);
    }
    private void saveTokens(Map<String,Object> saved, Map<String,Object> tokens) {
        saved.put("accessToken",required(tokens,"access_token"));
        if(tokens.containsKey("refresh_token"))saved.put("refreshToken",required(tokens,"refresh_token"));
        if(tokens.containsKey("scope"))saved.put("scopes",Arrays.asList(tokens.get("scope").toString().split(" +")));
        else saved.putIfAbsent("scopes",List.of());
        if(!"Bearer".equalsIgnoreCase(required(tokens,"token_type")))throw new ToolFailure("Invalid ChatGPT token type");
        long seconds=((Number)tokens.getOrDefault("expires_in",3600)).longValue();
        if(seconds<1||seconds>86_400)throw new ToolFailure("Invalid ChatGPT token lifetime");
        saved.put("expiresAt",System.currentTimeMillis()+seconds*1000);
    }
    String accessToken() throws Exception {
        return locked(() -> {
            var saved=read();
            if(!saved.containsKey("accessToken"))throw new ChatGptUnavailable("SIGN_IN_REQUIRED");
            if(!scopes(saved).contains(PLAN_SCOPE))throw new ChatGptUnavailable("PLAN_PERMISSION_REQUIRED");
            if(((Number)saved.getOrDefault("expiresAt",0)).longValue()<System.currentTimeMillis()+120_000) {
                if(!saved.containsKey("refreshToken"))throw new ChatGptUnavailable("SIGN_IN_REQUIRED");
                try {
                    var tokens=token(Map.of("grant_type","refresh_token","client_id",required(saved,"clientId"),
                        "refresh_token",required(saved,"refreshToken"),"resource",RESOURCE));
                    var refreshed=new LinkedHashMap<>(saved);saveTokens(refreshed,tokens);PrivateJson.write(file,refreshed,json);saved=refreshed;
                } catch(Exception failure) { throw new ChatGptUnavailable("SESSION_REFRESH_REQUIRED"); }
                if(!scopes(saved).contains(PLAN_SCOPE))throw new ChatGptUnavailable("PLAN_PERMISSION_REQUIRED");
            }
            return required(saved,"accessToken");
        });
    }
    public List<Map<String,String>> models() throws Exception {
        String access=accessToken();
        var response=api.get().uri("/models").header("Authorization","Bearer "+access).retrieve().body(Map.class);
        if(response==null||!(response.get("models") instanceof List<?> rows))throw new ToolFailure("ChatGPT model catalog unavailable");
        var models=new ArrayList<Map<String,String>>();
        for(Object value:rows)if(value instanceof Map<?,?> row&&"list".equals(row.get("visibility"))
                &&row.get("slug") instanceof String slug&&!slug.isBlank())
            models.add(Map.of("slug",slug,"displayName",Objects.toString(row.get("display_name"),slug)));
        if(models.isEmpty())throw new ToolFailure("No ChatGPT models available to this account");
        locked(() -> {
            var saved=read();saved.put("models",models);
            if(models.stream().noneMatch(model->model.get("slug").equals(saved.get("model"))))saved.put("model",models.getFirst().get("slug"));
            saved.put("catalogCheckedAt",System.currentTimeMillis());PrivateJson.write(file,saved,json);return null;
        });
        return models;
    }
    public void selectModel(String model) throws Exception {
        var choices=models();if(choices.stream().noneMatch(choice->choice.get("slug").equals(model)))throw new ToolFailure("Select an available ChatGPT model");
        locked(() -> {var saved=read();saved.put("model",model);PrivateJson.write(file,saved,json);return null;});
    }
    String model() throws Exception {
        var saved=locked(this::read);
        if(!saved.containsKey("model")||((Number)saved.getOrDefault("catalogCheckedAt",0)).longValue()<System.currentTimeMillis()-3_600_000)models();
        return locked(() -> required(read(),"model"));
    }
    private static List<?> scopes(Map<String,Object> saved) {return saved.get("scopes") instanceof List<?> scopes?scopes:List.of();}
    private static String required(Map<?,?> source,String key) {
        if(source==null||!(source.get(key) instanceof String value)||value.isBlank())throw new ToolFailure("Incomplete ChatGPT session");
        return value;
    }
    private static String random() {byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    private static String encode(String value) {return URLEncoder.encode(value,java.nio.charset.StandardCharsets.UTF_8);}
    private static boolean constantTime(String left,String right) {return right!=null&&MessageDigest.isEqual(left.getBytes(java.nio.charset.StandardCharsets.UTF_8),right.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
}
