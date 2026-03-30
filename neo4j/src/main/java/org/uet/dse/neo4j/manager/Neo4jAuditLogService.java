package org.uet.dse.neo4j.manager;

import org.neo4j.driver.Session;
import org.neo4j.driver.Values;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Neo4jAuditLogService {
    private static Neo4jAuditLogService instance;
    // Executor đơn luồng để đảm bảo thứ tự log (FIFO)
    private static final ExecutorService logExecutor = Executors.newSingleThreadExecutor();

    private Neo4jAuditLogService() {}

    public static synchronized Neo4jAuditLogService getInstance() {
        if (instance == null) instance = new Neo4jAuditLogService();
        return instance;
    }

    /**
     * Ghi log bất đồng bộ (Non-blocking)
     */
    public static void log(String actionType, String category, String target, String details) {
        SessionManager sm = Neo4jDriverManager.getInstance().getSessionManager();
        String user = sm.getUserDisplayName();
        String sessionKey = sm.getSessionKey();
        long timestamp = System.currentTimeMillis();

        logExecutor.submit(() -> {
            try (Session session = Neo4jDriverManager.getInstance().openSession()) {
                String cypher = 
                    "MATCH (meta:MetaNode {name: 'NodeActionLog'}) " +
                    "CREATE (l:ActionLog { " +
                    "  user: $user, " +
                    "  timestamp: $ts, " +
                    "  sessionKey: $key, " +
                    "  actionType: $type, " +
                    "  category: $cat, " +
                    "  target: $target, " +
                    "  details: $details " +
                    "}) " +
                    "MERGE (l)-[:InstanceOf]->(meta)";

                session.run(cypher, Values.parameters(
                    "user", user, 
                    "ts", timestamp, 
                    "key", sessionKey, 
                    "type", actionType, 
                    "cat", category, 
                    "target", target, 
                    "details", details
                ));
            } catch (Exception e) {
                System.err.println("Async Logging Failed: " + e.getMessage());
            }
        });
    }

    public void shutdown() {
        logExecutor.shutdown();
    }
}