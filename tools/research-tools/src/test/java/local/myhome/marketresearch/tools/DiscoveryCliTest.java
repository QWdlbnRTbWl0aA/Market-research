package local.myhome.marketresearch.tools;
import static org.junit.jupiter.api.Assertions.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;
class DiscoveryCliTest {
    @Test void operatorCliStartsAndControlsDurableWorkWithoutCallingProviders() throws Exception {
        var json=new ObjectMapper();var body=new AtomicReference<Map<?,?>>();var route=new AtomicReference<String>();
        var server=HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),0);
        server.createContext("/",x->{
            route.set(x.getRequestMethod()+" "+x.getRequestURI().getPath());
            if(x.getRequestMethod().equals("POST")&&x.getRequestURI().getPath().equals("/v1/discoveries")) {
                body.set(json.readValue(x.getRequestBody(),Map.class));assertEquals("stable",x.getRequestHeaders().getFirst("Idempotency-Key"));
                byte[] result=json.writeValueAsBytes(Map.of("id",UUID.randomUUID(),"status","ACTIVE"));
                x.getResponseHeaders().add("Content-Type","application/json");x.sendResponseHeaders(202,result.length);x.getResponseBody().write(result);
            }else x.sendResponseHeaders(202,-1);
            x.close();
        });server.start();
        try {
            var env=new MockEnvironment().withProperty("research.discovery.url","http://127.0.0.1:"+server.getAddress().getPort())
                .withProperty("research.discovery.query","Jewish printables").withProperty("research.discovery.key","stable")
                .withProperty("research.discovery.id",UUID.randomUUID().toString());
            assertTrue(DiscoveryCli.run("search-start",env).containsKey("result"));assertEquals("Jewish printables",body.get().get("query"));
            assertEquals(true,DiscoveryCli.run("search-approve-trend",env).get("accepted"));assertTrue(route.get().endsWith("/approve-trend"));
            DiscoveryCli.run("search-cancel",env);assertTrue(route.get().startsWith("DELETE "));
            assertThrows(ToolFailure.class,()->DiscoveryCli.run("search-start",new MockEnvironment()));
        }finally{server.stop(0);}
    }
}
