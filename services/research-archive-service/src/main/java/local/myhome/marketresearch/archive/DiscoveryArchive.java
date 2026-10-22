package local.myhome.marketresearch.archive;
import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
/** Archive a completed discovery iteration, never expose database operations. */
@RestController
@RequestMapping("/v1/discovery-archives")
public class DiscoveryArchive {
    private final JdbcTemplate db;private final ObjectMapper json;
    public DiscoveryArchive(JdbcTemplate db,ObjectMapper json) {
        this.db=db;this.json=json;
        db.execute("CREATE TABLE IF NOT EXISTS discovery_archives(run_id VARCHAR(36), iteration INT, snapshot CLOB, PRIMARY KEY(run_id,iteration))");
    }
    @PostMapping public synchronized ResponseEntity<Void> archive(@RequestBody Map<String,Object> snapshot) {
        UUID id;int iteration;
        try {
            id=UUID.fromString(snapshot.get("runId").toString());iteration=((Number)snapshot.get("iteration")).intValue();
            if(iteration<1||snapshot.get("finishedAt")==null||!(snapshot.get("query") instanceof String)
                ||!(snapshot.get("assessments") instanceof List<?>)||!(snapshot.get("productDefinitions") instanceof List<?>))
                throw new IllegalArgumentException();
        }catch(RuntimeException error){throw new ResponseStatusException(HttpStatus.BAD_REQUEST);}
        String payload=json.writeValueAsString(snapshot);
        var rows=db.queryForList("SELECT snapshot FROM discovery_archives WHERE run_id=? AND iteration=?",id.toString(),iteration);
        if(!rows.isEmpty()&&!payload.equals(db.queryForObject("SELECT snapshot FROM discovery_archives WHERE run_id=? AND iteration=?",String.class,id.toString(),iteration)))throw new ResponseStatusException(HttpStatus.CONFLICT);
        if(rows.isEmpty())db.update("INSERT INTO discovery_archives VALUES(?,?,?)",id.toString(),iteration,payload);
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/{id}/{iteration}") public Map<?,?> get(@PathVariable UUID id,@PathVariable int iteration) {
        var rows=db.queryForList("SELECT snapshot FROM discovery_archives WHERE run_id=? AND iteration=?",id.toString(),iteration);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return json.readValue(db.queryForObject("SELECT snapshot FROM discovery_archives WHERE run_id=? AND iteration=?",String.class,id.toString(),iteration),Map.class);
    }
}
