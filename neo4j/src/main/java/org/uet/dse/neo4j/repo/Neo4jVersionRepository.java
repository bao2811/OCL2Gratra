package org.uet.dse.neo4j.repo;

import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Value;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.uet.dse.neo4j.repo.query.Neo4jObjectQuery;

/**
 * Handles ModelVersion node queries only.
 * Separated from Neo4jObjectRepository because version metadata is a
 * cross-cutting concern unrelated to object persistence.
 */
public class Neo4jVersionRepository {

    public long getRemoteObjectTimestamp() {
        return queryLongVersion(Neo4jObjectQuery.GET_OBJECT_TIMESTAMP, "ts", 0L);
    }

    public long getRemoteModelHash() {
        return queryLongVersion(Neo4jObjectQuery.GET_MODEL_HASH, "hash", -1L);
    }

    private long queryLongVersion(String cypher, String field, long fallback) {
        String dbName = Neo4jDriverManager.getInstance().getActiveDatabase();
        try (Session session = Neo4jDriverManager.getInstance().getDriver()
                .session(SessionConfig.forDatabase(dbName))) {

            Result result = session.run(cypher);
            if (!result.hasNext()) return fallback;

            Value value = result.single().get(field);
            return value.isNull() ? fallback : value.asLong();

        } catch (Exception e) {
            return fallback;
        }
    }
}