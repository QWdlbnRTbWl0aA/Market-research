package local.myhome.marketresearch.tools;

import java.time.Duration;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Operator interface for the durable workflow. Local service owns the work after this process exits. */
final class DiscoveryCli {
    static Map<String,Object> run(String command,Environment env) {
        var f=new SimpleClientHttpRequestFactory();f.setConnectTimeout(Duration.ofSeconds(2));f.setReadTimeout(Duration.ofSeconds(10));
        var http=RestClient.builder().baseUrl(env.getProperty("research.discovery.url","http://127.0.0.1:8081")).requestFactory(f).build();
        String path="/v1/discoveries";HttpMethod method=HttpMethod.GET;Object body=null;
        String id=env.getProperty("research.discovery.id",""),key=env.getProperty("research.discovery.key","");
        if(command.equals("search-start")) {
            String query=env.getProperty("research.discovery.query","");
            if(query.isBlank()||key.isBlank())throw new ToolFailure("Supply research.discovery.query and a stable research.discovery.key.");
            method=HttpMethod.POST;body=Map.of("query",query);
        } else if(command.equals("search-list")||command.equals("search-notifications")) {
            if(command.endsWith("notifications"))path+="/notifications";
        } else {
            try{UUID.fromString(id);}catch(Exception e){throw new ToolFailure("Supply a valid research.discovery.id.");}
            path+="/"+id;
            switch(command) {
                case "search-status" -> {}
                case "search-results" -> path+="/results";
                case "search-resume" -> {path+="/resume";method=HttpMethod.POST;}
                case "search-approve-trend" -> {path+="/approve-trend";method=HttpMethod.POST;}
                case "search-dismiss-trend" -> {path+="/dismiss-trend";method=HttpMethod.POST;}
                case "search-cancel" -> method=HttpMethod.DELETE;
                default -> throw new ToolFailure("Unknown discovery command.");
            }
        }
        var request=http.method(method).uri(path);
        if(!key.isBlank())request.header("Idempotency-Key",key);
        if(body!=null)request.body(body);
        try {
            return request.exchange((req,response)->{
                if(!response.getStatusCode().is2xxSuccessful())throw new ToolFailure("Discovery service returned HTTP "+response.getStatusCode().value()+".");
                byte[] bytes=response.getBody().readAllBytes();
                if(bytes.length==0)return Map.of("accepted",true);
                return Map.of("result",new tools.jackson.databind.ObjectMapper().readValue(bytes,Object.class));
            });
        }catch(ToolFailure failure){throw failure;}
        catch(Exception failure){throw new ToolFailure("Discovery service unavailable; no provider call was made by this CLI.");}
    }
}
