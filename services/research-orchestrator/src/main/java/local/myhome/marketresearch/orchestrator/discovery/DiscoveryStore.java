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
        db.update("INSERT INTO discoveries VALUES(?,?,?,?)",state.id.toString(),key,normalized,json.writeValueAsString(state));
        return state;
    }
    public DiscoveryState get(UUID id) {
        var rows=db.queryForList("SELECT state FROM discoveries WHERE id=?",id.toString());
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return json.readValue(db.queryForObject("SELECT state FROM discoveries WHERE id=?",String.class,id.toString()),DiscoveryState.class);
    }
    public void save(DiscoveryState state){db.update("UPDATE discoveries SET state=? WHERE id=?",json.writeValueAsString(state),state.id.toString());}
    public List<UUID> ids(){return db.queryForList("SELECT id FROM discoveries",String.class).stream().map(UUID::fromString).toList();}
}
