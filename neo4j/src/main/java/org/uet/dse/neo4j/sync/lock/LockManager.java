package org.uet.dse.neo4j.sync.lock;

import org.neo4j.driver.*;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;

public class LockManager {
    public static class LockResult {
        public boolean success;
        public String owner;
        public LockResult(boolean s, String o) { this.success = s; this.owner = o; }
    }
    public static LockResult acquireLock(String sessionKey) {
        String dbName = Neo4jDriverManager.getInstance().getActiveDatabase();
        try (Session session = Neo4jDriverManager.getInstance().getDriver().session(SessionConfig.forDatabase(dbName))) {
            String cypher =
                    "MERGE (l:DbLock {id: 'GLOBAL_LOCK'}) " +
                            "ON CREATE SET l.owner = $key, l.time = timestamp() " +
                            "ON MATCH SET l.owner = CASE WHEN timestamp() - l.time > 300000 THEN $key ELSE l.owner END, " +
                            "             l.time = CASE WHEN l.owner = $key THEN timestamp() ELSE l.time END " +
                            "RETURN l.owner = $key AS success, l.owner AS currentOwner";

            Result res = session.run(cypher, Values.parameters("key", sessionKey));
            org.neo4j.driver.Record record = res.single();
            return new LockManager.LockResult(record.get("success").asBoolean(), record.get("currentOwner").asString());
        }
    }


    public static void releaseLock() {
        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            session.run("MATCH (l:DbLock {id: 'GLOBAL_LOCK'}) DELETE l");
        }
    }
}