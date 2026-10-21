package local.myhome.marketresearch.tools;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class LargeSampleCollectorTest {
    @TempDir Path root;
    @Test void collectsBoundedSampleWithExistingLedgerAndStripsBuyerFields() throws Exception {
        var json=new ObjectMapper();var builder=RestClient.builder().baseUrl("https://etsy.invalid");
        var server=MockRestServiceServer.bindTo(builder).build();
        var headers=new HttpHeaders();headers.add("x-limit-per-second","5");headers.add("x-limit-per-day","5000");headers.add("x-remaining-today","1000");
        var listing=Map.of("listing_id",1,"shop_id",11,"listing_type","download","num_favorers",0);
        var other=Map.of("listing_id",2,"shop_id",11,"listing_type","download","num_favorers",1);
        server.expect(requestTo("https://etsy.invalid/openapi-ping")).andRespond(withSuccess("{}",MediaType.APPLICATION_JSON).headers(headers));
        server.expect(requestTo("https://etsy.invalid/listings/active?keywords=halloween+preschool+activity+printable&sort_on=score&limit=100&offset=0"))
            .andRespond(withSuccess(json.writeValueAsString(Map.of("count",2,"results",List.of(listing,other))),MediaType.APPLICATION_JSON).headers(headers));
        server.expect(requestTo("https://etsy.invalid/shops/11")).andRespond(withSuccess("{\"shop_id\":11,\"user_id\":999,\"transaction_sold_count\":10}",MediaType.APPLICATION_JSON).headers(headers));
        var response=json.writeValueAsString(Map.of("count",1,"results",List.of(Map.of("listing_id",2,"buyer_user_id",999,"rating",5,"review","Useful","create_timestamp",1792500000))));
        server.expect(requestTo("https://etsy.invalid/shops/11/reviews?limit=100")).andRespond(withSuccess(response,MediaType.APPLICATION_JSON).headers(headers));
        server.expect(requestTo("https://etsy.invalid/listings/2/reviews?limit=100")).andRespond(withSuccess(response,MediaType.APPLICATION_JSON).headers(headers));
        var clock=new AtomicLong(1_000_000L);
        var ledger=new QuotaLedger(root.resolve("data/etsy-discovery"),json,clock::get,millis->clock.addAndGet(millis));
        PrivateJson.write(root.resolve("data/halloween-run/old.json"),Map.of("old",true),json);
        var result=new LargeSampleCollector(root,new EtsyGateway(builder.build(),json,()->"test-only"),ledger,json,
            new LargeSampleCollector.Limits(2,1,2,1,30,6)).run();
        assertEquals("SUCCEEDED",result.get("status"));assertEquals(5,result.get("totalEtsyCalls"));
        assertFalse(Files.exists(root.resolve("data/halloween-run/old.json")));
        assertFalse(Files.readString(root.resolve("data/halloween-run/comparison/shop-reviews.json")).contains("buyer_user_id"));
        assertFalse(Files.readString(root.resolve("data/halloween-run/comparison/shops.json")).contains("user_id"));
        assertTrue(Files.exists(root.resolve("data/etsy-discovery/budget.json")));server.verify();
    }
}
