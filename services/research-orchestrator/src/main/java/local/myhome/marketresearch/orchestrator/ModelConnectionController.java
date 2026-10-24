package local.myhome.marketresearch.orchestrator;

import com.sun.net.httpserver.*;
import jakarta.annotation.PreDestroy;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import local.myhome.marketresearch.tools.ChatGptSession;
import tools.jackson.databind.ObjectMapper;

/** Connects this application's ChatGPT session. Tokens never enter the browser or research database. */
@RestController
@RequestMapping("/v1/model-connection")
public class ModelConnectionController {
    private final ChatGptSession session;
    private final Environment settings;
    private HttpServer callback;
    public ModelConnectionController(Environment settings,ObjectMapper json) {
        this.settings=settings;this.session=new ChatGptSession(settings,json);
    }
    @ModelAttribute public void preventCaching(jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");response.setHeader("Referrer-Policy","no-referrer");
    }
    @GetMapping public Map<String,Object> status() throws Exception {
        var result=new LinkedHashMap<>(session.status());
        result.put("primary",settings.getProperty("research.runtime.analysis-model-provider","chatgpt"));
        result.put("apiFallback",settings.getProperty("research.runtime.chatgpt-api-fallback",Boolean.class,true));
        result.put("callbackPort",settings.getProperty("research.models.chatgpt-callback-port",Integer.class,1456));
        return result;
    }
    @PostMapping(path="/sign-in",consumes=MediaType.APPLICATION_JSON_VALUE)
    public synchronized ChatGptSession.Authorization signIn(@RequestHeader("X-Research-Action") String action) throws Exception {
        requireAction(action);
        int port=settings.getProperty("research.models.chatgpt-callback-port",Integer.class,1456);
        if(callback==null) {
            callback=HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),port),0);
            callback.createContext("/auth/callback",this::complete);
            callback.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());callback.start();
        }
        return session.begin(port);
    }
    @PostMapping(path="/models",consumes=MediaType.APPLICATION_JSON_VALUE)
    public List<Map<String,String>> models(@RequestHeader("X-Research-Action") String action) throws Exception {
        requireAction(action);return session.models();
    }
    public record ModelChoice(String model) {}
    @PostMapping(path="/model",consumes=MediaType.APPLICATION_JSON_VALUE)
    public void select(@RequestHeader("X-Research-Action") String action,@RequestBody ModelChoice choice) throws Exception {
        requireAction(action);session.selectModel(choice.model());
    }
    @ExceptionHandler(Exception.class) public ResponseEntity<Map<String,String>> failure() {
        return ResponseEntity.badRequest().body(Map.of("error","Connection action failed. Check sign-in, callback forwarding and ChatGPT plan permission; then retry."));
    }
    private void complete(HttpExchange exchange) throws java.io.IOException {
        int status=400;String message="Sign-in failed or expired. Return to the app and start again.";
        if("GET".equals(exchange.getRequestMethod())&&"/auth/callback".equals(exchange.getRequestURI().getPath())) {
            try {
                var parameters=new LinkedHashMap<String,String>();
                String query=exchange.getRequestURI().getRawQuery();
                if(query!=null)for(String entry:query.split("&")) {
                    String[] parts=entry.split("=",2);
                    String key=URLDecoder.decode(parts[0],StandardCharsets.UTF_8);
                    if(parameters.putIfAbsent(key,parts.length==2?URLDecoder.decode(parts[1],StandardCharsets.UTF_8):"")!=null)
                        throw new IllegalArgumentException("Duplicate callback parameter");
                }
                session.complete(parameters);status=200;message="ChatGPT connection saved. Return to the app, then load and select an available model.";
            } catch(Exception ignored) { /* Never log callback URLs, authorization codes or provider error bodies. */ }
        }
        byte[] body=("<!doctype html><meta charset=\"utf-8\"><title>ChatGPT connection</title><p>"+message+"</p>").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type","text/html; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control","no-store");exchange.getResponseHeaders().set("Referrer-Policy","no-referrer");
        exchange.sendResponseHeaders(status,body.length);try(var output=exchange.getResponseBody()){output.write(body);}finally{exchange.close();}
    }
    private void requireAction(String action) {if(!"model-connection".equals(action))throw new IllegalArgumentException("Invalid connection action");}
    @PreDestroy public synchronized void close(){if(callback!=null){callback.stop(0);callback=null;}}
}
