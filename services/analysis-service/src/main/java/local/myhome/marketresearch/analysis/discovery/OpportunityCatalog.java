package local.myhome.marketresearch.analysis.discovery;

import java.util.*;
import local.myhome.marketresearch.contracts.analysis.Discovery.*;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

/** Semantic relationships are decided by cohort reasoning/review, not similarity regexes or content hashes. */
final class OpportunityCatalog {
    private final JdbcTemplate database;
    private final ObjectMapper json;

    OpportunityCatalog(JdbcTemplate database, ObjectMapper json) {
        this.database = database;
        this.json = json;
        database.execute("CREATE TABLE IF NOT EXISTS discovery_opportunities(scope VARCHAR(64), id VARCHAR(80), opportunity CLOB, support CLOB, PRIMARY KEY(scope,id))");
    }

    List<Opportunity> list(String query) {
        return database.query("SELECT opportunity FROM discovery_opportunities WHERE scope=? ORDER BY id",
            (row, index) -> json.readValue(row.getString(1), Opportunity.class), scope(query));
    }

    Assessment accept(String jobId, Task task, Assessment assessment) {
        var catalog = new LinkedHashMap<String, Opportunity>();
        list(task.query()).forEach(opportunity -> catalog.put(opportunity.id(), opportunity));
        var resolved = new ArrayList<Product>();
        int index = 0;
        for (Product submitted : assessment.products()) {
            Product product=submitted;
            if("NEW".equals(product.relationship())) {
                var equivalent=catalog.values().stream().filter(value->sameIntent(value.product(),submitted)).findFirst();
                if(equivalent.isPresent())product=product.related("ADDITIONAL_EVIDENCE",equivalent.get().id(),
                    "Exact buyer/use-case/contents/differentiation match; wording, price or references alone do not create a new opportunity");
            }
            String relationship = product.relationship();
            Opportunity existing = catalog.get(product.existingOpportunityId());
            if (!"NEW".equals(relationship) && existing == null)
                throw new IllegalArgumentException("Unknown existing opportunity");
            String id = "ADDITIONAL_EVIDENCE".equals(relationship) ? existing.id()
                : UUID.nameUUIDFromBytes((jobId + ":" + index).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
            var evidenceIds = new LinkedHashSet<String>();
            if (existing != null) evidenceIds.addAll(existing.evidenceIds());
            evidenceIds.addAll(product.listingIds());
            Product canonical = "ADDITIONAL_EVIDENCE".equals(relationship) ? existing.product() : product.resolved(id);
            String parent = "MEANINGFUL_VARIANT".equals(relationship) ? existing.id()
                : existing == null ? null : existing.parentId();
            var opportunity = new Opportunity(id, parent, canonical, List.copyOf(evidenceIds),
                "ADDITIONAL_EVIDENCE".equals(relationship) ? existing.revision() + 1 : 1);
            var support = new LinkedHashMap<String, Object>();
            var old = database.query("SELECT support FROM discovery_opportunities WHERE scope=? AND id=?",
                (row, number) -> json.readValue(row.getString(1), Map.class), scope(task.query()), id);
            if (!old.isEmpty()) support.putAll(old.getFirst());
            Product acceptedProduct=product;
            var cited = task.listings().stream().filter(listing -> acceptedProduct.listingIds().contains(listing.id())).toList();
            support.put(jobId, Map.of("listings", cited, "shops", task.shops(), "reviews", task.reviews(),
                "branch", Objects.toString(task.branch(), task.query()), "protocol", local.myhome.marketresearch.contracts.analysis.Discovery.VERSION));
            database.update("MERGE INTO discovery_opportunities KEY(scope,id) VALUES(?,?,?,?)",
                scope(task.query()), id, json.writeValueAsString(opportunity), json.writeValueAsString(support));
            catalog.put(id, opportunity);
            resolved.add(product.resolved(id));
            index++;
        }
        return new Assessment(assessment.summary(), assessment.families(), resolved, assessment.limitations(),
            assessment.newInsights(), assessment.marginalValueReason());
    }

    Map<String, Object> supportingEvidence(Task task, Assessment result) {
        var support = new LinkedHashMap<String, Object>();
        for (Product product : result.products()) {
            for (String id : List.of(Objects.toString(product.opportunityId(), ""), Objects.toString(product.existingOpportunityId(), ""))) {
                if (id.isBlank() || support.containsKey(id)) continue;
                database.query("SELECT support FROM discovery_opportunities WHERE scope=? AND id=?",
                    row -> { support.put(id, json.readValue(row.getString(1), Map.class)); }, scope(task.query()), id);
            }
        }
        return support;
    }

    private String scope(String query) {
        return DiscoveryJobs.hash(query.strip().toLowerCase(Locale.ROOT));
    }
    private boolean sameIntent(Product left,Product right) {
        return normalized(left.audience()).equals(normalized(right.audience()))
            &&normalized(left.useCase()).equals(normalized(right.useCase()))
            &&normalized(left.differentiation()).equals(normalized(right.differentiation()))
            &&left.deliverables().stream().map(this::normalized).sorted().toList()
                .equals(right.deliverables().stream().map(this::normalized).sorted().toList());
    }
    private String normalized(String value){return value.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");}
}
