package local.myhome.marketresearch.archive;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
class DiscoveryArchiveTest {
    @Test void completedIterationArchivesIdempotentlyAndRejectsOverwrites() {
        var db=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa",""));
        var archive=new DiscoveryArchive(db,new ObjectMapper());UUID id=UUID.randomUUID();
        var snapshot=new LinkedHashMap<String,Object>(Map.of("runId",id.toString(),"iteration",1,"query","TEST_FIXTURE broad query",
            "finishedAt","2026-10-22T00:00:00Z","assessments",List.of(),"productDefinitions",List.of()));
        assertEquals(204,archive.archive(snapshot).getStatusCode().value());archive.archive(snapshot);
        assertEquals(snapshot,archive.get(id,1));snapshot.put("query","Changed");
        assertThrows(ResponseStatusException.class,()->archive.archive(snapshot));
        snapshot.put("iteration",2);archive.archive(snapshot);assertEquals("Changed",archive.get(id,2).get("query"));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM discovery_archives",Integer.class));
    }
}
