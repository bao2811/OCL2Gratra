package org.uet.dse.neo4j.repo;

import org.uet.dse.neo4j.mm.graphdb.Edge;
import org.neo4j.driver.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EdgeRepository {
    private final Driver driver;
    private final String database;

    public EdgeRepository(Driver driver, String database) {
        this.driver = driver;
        this.database = database;
    }

    public void createEdge(Edge edge) {
        String cypher = "MATCH (a:Vertex {id: $fromId}), (b:Vertex {id: $toId}) " +
                        "CREATE (a)-[r:" + edge.getType() + "]->(b)";

        try (Session session = driver.session(SessionConfig.forDatabase(database))) {
            session.executeWrite(tx -> {
                tx.run(cypher, Map.of("fromId", edge.getFromVertexId(), "toId", edge.getToVertexId()));
                return null;
            });
        }
    }

    public List<Map<String,Object>> findAllEdges() {
        try (Session session = driver.session(SessionConfig.forDatabase(database))) {
            return session.executeRead(tx -> {
                List<Map<String,Object>> results = new ArrayList<>();
                Result res = tx.run("MATCH (a:Vertex)-[r]->(b:Vertex) " +
                        "RETURN a.id AS fromId, b.id AS toId, type(r) AS type");
                while (res.hasNext()) results.add(res.next().asMap());
                return results;
            });
        }
    }
}
