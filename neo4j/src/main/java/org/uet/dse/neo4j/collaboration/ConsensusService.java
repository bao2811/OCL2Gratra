package org.uet.dse.neo4j.collaboration;

import org.neo4j.driver.*;
import org.neo4j.driver.Record;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import java.util.Map;

public class ConsensusService {
    private static ConsensusService instance;
    private ConsensusService() {}
    public static synchronized ConsensusService getInstance() {
        if (instance == null) instance = new ConsensusService();
        return instance;
    }

    public enum VoteType { APPROVE, REJECT, POSTPONE }


    public void castVote(String proposalId, VoteType vote) {
        String user = Neo4jDriverManager.getInstance().getSessionManager().getUserDisplayName();
        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            session.run("MATCH (p:ModelProposal {id: $pid}) " +
                            "MERGE (u:SessionUser {name: $user}) " +
                            "ON CREATE SET u.status = 'ONLINE', u.lastActive = timestamp() " +
                            "MERGE (u)-[v:VOTED]->(p) SET v.type = $vtype",
                    Map.of("pid", proposalId, "user", user, "vtype", vote.name()));
        }
    }

}