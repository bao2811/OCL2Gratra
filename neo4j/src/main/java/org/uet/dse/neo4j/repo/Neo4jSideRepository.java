package org.uet.dse.neo4j.repo;

import org.neo4j.driver.Driver;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.uet.dse.neo4j.sync.object.ObjectSyncCoordinator;

public class Neo4jSideRepository {
    private final Driver driver;

    public Neo4jSideRepository() {
        this.driver = Neo4jDriverManager.getInstance().getDriver();
    }
    
    public void logAction(org.neo4j.driver.TransactionContext tx, String actionType, String category, String target, String details) {
        String user = Neo4jDriverManager.getInstance().getSessionManager().getUserDisplayName();
        String sessionKey = Neo4jDriverManager.getInstance().getSessionManager().getSessionKey();

        String cypher =
                "MATCH (meta:MetaNode {name: 'NodeActionLog'}) " +
                        "CREATE (l:ActionLog { " +
                        "  user: $user, " +
                        "  timestamp: timestamp(), " +
                        "  sessionKey: $key, " +
                        "  actionType: $type, " +
                        "  category: $cat, " +
                        "  target: $target, " +
                        "  details: $details " +
                        "}) " +
                        "MERGE (l)-[:InstanceOf]->(meta)";

        tx.run(cypher, org.neo4j.driver.Values.parameters(
                "user", user, "key", sessionKey, "type", actionType,
                "cat", category, "target", target, "details", details
        ));
    }

}
