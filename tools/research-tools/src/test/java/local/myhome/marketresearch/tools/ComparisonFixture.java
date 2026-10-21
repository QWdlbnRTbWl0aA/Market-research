package local.myhome.marketresearch.tools;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import tools.jackson.databind.ObjectMapper;

final class ComparisonFixture {
    static void create(Path root,ObjectMapper json) throws Exception {
        Path directory=root.resolve("data/halloween-run/comparison");
        PrivateJson.write(directory.resolve("listings.json"),List.of(Map.of("listing_id",1,"shop_id",11,"listing_type","download",
            "title","Halloween original printable","description","Print on A4 or Letter. No editing.","price",Map.of("amount",349,"divisor",100,"currency_code","USD"))),json);
        PrivateJson.write(directory.resolve("shops.json"),List.of(Map.of("shop_id",11,"created_timestamp",1700000000,"transaction_sold_count",45)),json);
        PrivateJson.write(directory.resolve("shop-reviews.json"),Map.of("11",Map.of("count",1,"results",List.of(
            Map.of("listing_id",1,"create_timestamp",1792500000,"rating",5,"review","Useful printable")))),json);
        PrivateJson.write(directory.resolve("listing-reviews.json"),Map.of(),json);
        PrivateJson.write(directory.resolve("provenance.json"),Map.of("1",List.of("halloween printable")),json);
        PrivateJson.write(directory.resolve("manifest.json"),Map.of("status","SUCCEEDED","finishedAt","2026-10-21T21:00:00Z"),json);
        Path baseline=root.resolve("docs/research/2026-10-22-agent-baseline.md");Files.createDirectories(baseline.getParent());
        String text="AGENT_PRIVATE_CONCLUSIONS_NEVER_SEND\n";
        for(String name:List.of("listings.json","shops.json","shop-reviews.json","listing-reviews.json"))text+=FrozenCorpus.sha(Files.readAllBytes(directory.resolve(name)))+"\n";
        Files.writeString(baseline,text);
        PrivateJson.write(root.resolve("data/provider-ledgers/openai-stage.json"),Map.of("committedUsd",new BigDecimal("0.3695175"),"calls",List.of()),json);
    }
    static Map<String,Object> report() {
        Map<String,Object> p=new LinkedHashMap<>();
        for(String key:List.of("id","name","audience","useCase","differentiation","seasonality","automationPlan","manualWorkAssumptions","confidence"))p.put(key,"test "+key);
        p.put("deliverables",List.of("A4 and Letter PDF"));p.put("qaPlan",List.of("Print check"));p.put("counterevidence",List.of("No calibrated probability"));
        p.put("priceUsd",new BigDecimal("3.49"));p.put("monthlySalesNeeded",9);
        p.put("evidence",List.of(Map.of("claim","Comparable exists","listingIds",List.of("1"),"shopIds",List.of("11"),"reviewIds",List.of())));
        p.put("experiment",Map.of("hypothesis","Useful","budgetAssumptions","Unknown human time","measurements",List.of("receipts","human minutes"),"stopOrIterateRule","Configured cap"));
        return Map.of("products",List.of(p),"declinedAlternatives",List.of(),"limitations",List.of("Seasonal"));
    }
}
