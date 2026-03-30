package org.uet.dse.neo4j.repo;

import org.uet.dse.neo4j.mm.graphdb.Tag;
import org.uet.dse.neo4j.mm.graphdb.Vertex;
import org.neo4j.driver.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VertexRepository {
    private final Driver driver;
    private final String database;

    public VertexRepository(Driver driver, String database) {
        this.driver = driver;
        this.database = database;
    }

    public void createVertex(Vertex vertex, Tag... tags) {
        StringBuilder cypher = new StringBuilder("CREATE (v:Vertex");
        for (Tag tag : tags) cypher.append(":").append(tag.getName());
        cypher.append(" {id: $id, name: $name})");

        try (Session session = driver.session(SessionConfig.forDatabase(database))) {
            session.executeWrite(tx -> {
                tx.run(cypher.toString(), Map.of("id", vertex.getId(), "name", vertex.getName()));
                return null;
            });
        }
    }

    public List<Map<String,Object>> findAllVertices() {
        try (Session session = driver.session(SessionConfig.forDatabase(database))) {
            return session.executeRead(tx -> {
                List<Map<String,Object>> results = new ArrayList<>();
                Result res = tx.run("MATCH (v:Vertex) RETURN v.id AS id, v.name AS name, labels(v) AS labels");
                while (res.hasNext()) results.add(res.next().asMap());
                return results;
            });
        }
    }
}
