package local.myhome.marketresearch.tools;

import java.time.Instant;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** Local maintenance infrastructure. Each service supplies only its own fixed table allowlist. */
public final class ResearchDataClear {
    private ResearchDataClear() {}

    public static void clear(JdbcTemplate database, PlatformTransactionManager manager,
                             Environment settings, ObjectMapper json, List<String> ownedTables) {
        if (settings.getProperty("research.runtime.live-enabled", Boolean.class, false))
            throw new ToolFailure("Research clearing requires disabled providers");
        database.execute("CREATE TABLE IF NOT EXISTS research_reset_history(reset_id VARCHAR(36), entity VARCHAR(100), rows_cleared INT, reset_at VARCHAR(50), PRIMARY KEY(reset_id,entity))");
        database.execute("CREATE TABLE IF NOT EXISTS research_operations_history(reset_id VARCHAR(36), entity VARCHAR(100), ordinal INT, metadata CLOB, PRIMARY KEY(reset_id,entity,ordinal))");
        String resetId = UUID.randomUUID().toString();
        String timestamp = Instant.now().toString();
        var allowedMetadata = Set.of("ID","RUN_ID","STATUS","PHASE","ERROR","CREATED","UPDATED","NEXT_ATTEMPT","FRESH","CACHED","USAGE_JSON");
        new TransactionTemplate(manager).executeWithoutResult(transaction -> {
            for (String table : ownedTables) {
                if (!table.matches("[a-z_]+")) throw new IllegalArgumentException("Invalid owned table name");
                var rows = database.queryForList("SELECT * FROM " + table);
                int ordinal = 0;
                for (var row : rows) {
                    var retained = new LinkedHashMap<String,Object>();
                    for (var field : row.entrySet()) if (allowedMetadata.contains(field.getKey())) {
                        Object value = field.getValue();
                        if (value instanceof java.sql.Clob clob) try { value = clob.getSubString(1, Math.toIntExact(clob.length())); }
                        catch (java.sql.SQLException failure) { throw new IllegalStateException(failure); }
                        retained.put(field.getKey(), value);
                    }
                    // No query, title, source record, prompt, recommendation, draft or report is retained.
                    if (!retained.isEmpty()) database.update("INSERT INTO research_operations_history VALUES(?,?,?,?)",
                        resetId, table, ordinal++, json.writeValueAsString(retained));
                }
                database.update("INSERT INTO research_reset_history VALUES(?,?,?,?)", resetId, table, rows.size(), timestamp);
                database.update("DELETE FROM " + table);
            }
        });
    }
}
