package local.myhome.marketresearch.orchestrator.discovery;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

@Component
public class DiscoveryStore {
    private final JdbcTemplate db;private final ObjectMapper json;
    public DiscoveryStore(JdbcTemplate db,ObjectMapper json) {
        this.db=db;this.json=json;
        db.execute("CREATE TABLE IF NOT EXISTS discoveries(id VARCHAR(36) PRIMARY KEY, idem VARCHAR(200) UNIQUE, query VARCHAR(300), state CLOB)");
        db.execute("ALTER TABLE discoveries ADD COLUMN IF NOT EXISTS projection_version INT DEFAULT 0");
        db.execute("ALTER TABLE discoveries ADD COLUMN IF NOT EXISTS interrupted BOOLEAN DEFAULT FALSE");
        for(String worker:List.of("SEARCH","ANALYSIS","DELIVERY","WATCH")) {
            db.execute("ALTER TABLE discoveries ADD COLUMN IF NOT EXISTS "+worker+"_due BIGINT DEFAULT "+Long.MAX_VALUE);
            db.execute("CREATE INDEX IF NOT EXISTS discoveries_"+worker+"_due ON discoveries("+worker+"_due)");
        }
        db.execute("CREATE TABLE IF NOT EXISTS discovery_source_pages(run_id VARCHAR(36), iteration INT, source_key VARCHAR(100), evidence CLOB, PRIMARY KEY(run_id,iteration,source_key))");
        db.execute("CREATE TABLE IF NOT EXISTS discovery_archive_outbox(run_id VARCHAR(36), iteration INT, payload CLOB, PRIMARY KEY(run_id,iteration))");
        for(String id:db.queryForList("SELECT id FROM discoveries WHERE projection_version<3",String.class))
            save(get(UUID.fromString(id))); // Migrate once, never deserialize idle aggregates on later startups.
    }
    public synchronized DiscoveryState create(String key,String query) {
        if(key==null||key.isBlank()||key.length()>200||query==null||query.isBlank()||query.length()>300)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        String normalized=SearchPolicy.normalize(query);
        var rows=db.queryForList("SELECT id,query FROM discoveries WHERE idem=?",key);
        if(!rows.isEmpty()) {
            if(!normalized.equals(rows.getFirst().get("QUERY")))throw new ResponseStatusException(HttpStatus.CONFLICT);
            return get(UUID.fromString(rows.getFirst().get("ID").toString()));
        }
        var state=new DiscoveryState();state.id=UUID.randomUUID();state.query=normalized;
        state.queries.add(new DiscoveryState.Query(normalized,null));
        db.update("INSERT INTO discoveries(id,idem,query,state) VALUES(?,?,?,?)",state.id.toString(),key,normalized,json.writeValueAsString(state));
        save(state);
        return state;
    }
    public DiscoveryState get(UUID id) {
        var rows=db.query("SELECT state FROM discoveries WHERE id=?",(row,index)->row.getString(1),id.toString());
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return json.readValue(rows.getFirst(),DiscoveryState.class);
    }
    public void save(DiscoveryState state){
        db.update("UPDATE discoveries SET state=?,SEARCH_due=?,ANALYSIS_due=?,DELIVERY_due=?,WATCH_due=?,projection_version=3,interrupted=? WHERE id=?",
            json.writeValueAsString(state),DiscoveryReadiness.next(state,"SEARCH"),DiscoveryReadiness.next(state,"ANALYSIS"),
            DiscoveryReadiness.next(state,"DELIVERY"),DiscoveryReadiness.next(state,"WATCH"),
            state.queries.stream().anyMatch(branch->"FETCHING".equals(branch.status)),state.id.toString());
    }
    public List<UUID> interruptedIds() {
        return db.queryForList("SELECT id FROM discoveries WHERE interrupted=TRUE",String.class).stream().map(UUID::fromString).toList();
    }
    public List<UUID> ready(String worker) {
        if(!List.of("SEARCH","ANALYSIS","DELIVERY","WATCH").contains(worker))throw new IllegalArgumentException();
        return db.queryForList("SELECT id FROM discoveries WHERE "+worker+"_due<=? ORDER BY "+worker+"_due FETCH FIRST 100 ROWS ONLY",
            String.class,System.currentTimeMillis()).stream().map(UUID::fromString).toList();
    }
    public void recordPage(DiscoveryState state,String query,int offset,Map<String,Object> response) {
        var facts=new LinkedHashMap<String,Object>();facts.put("count",response.get("count"));
        var rows=new ArrayList<Map<String,Object>>();
        if(response.get("results") instanceof List<?> source)for(Object item:source)if(item instanceof Map<?,?> row) {
            var safe=new LinkedHashMap<String,Object>();
            for(String field:List.of("listing_id","shop_id","title","description","tags","listing_type","is_digital","price","views",
                    "num_favorers","original_creation_timestamp","last_modified_timestamp"))if(row.get(field)!=null)safe.put(field,row.get(field));
            rows.add(safe);
        }
        facts.put("results",rows);
        var record=Map.of("query",query,"offset",offset,"observedAt",java.time.Instant.now().toString(),
            "sha256",DiscoveryFlow.fullHash(json.writeValueAsString(facts)),"response",facts);
        db.update("MERGE INTO discovery_source_pages KEY(run_id,iteration,source_key) VALUES(?,?,?,?)",
            state.id.toString(),state.iteration,DiscoveryFlow.hash(query)+":"+offset,json.writeValueAsString(record));
    }
    public List<Map<String,Object>> sourcePages(DiscoveryState state) {
        return db.query("SELECT evidence FROM discovery_source_pages WHERE run_id=? AND iteration=? ORDER BY source_key",
            (row,index)->json.readValue(row.getString(1),Map.class),state.id.toString(),state.iteration);
    }
    public Map<String,Object> freezeArchive(DiscoveryState state,java.util.function.Supplier<Map<String,Object>> build) {
        var rows=db.query("SELECT payload FROM discovery_archive_outbox WHERE run_id=? AND iteration=?",
            (row,index)->row.getString(1),state.id.toString(),state.iteration);
        if(!rows.isEmpty())return json.readValue(rows.getFirst(),Map.class);
        var payload=build.get();
        String serialized=json.writeValueAsString(payload);
        db.update("INSERT INTO discovery_archive_outbox VALUES(?,?,?)",state.id.toString(),state.iteration,serialized);
        return json.readValue(serialized,Map.class);
    }
    public List<UUID> ids(){return db.queryForList("SELECT id FROM discoveries",String.class).stream().map(UUID::fromString).toList();}
}
