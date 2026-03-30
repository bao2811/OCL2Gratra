package org.uet.dse.neo4j.repo;

import org.neo4j.driver.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TagRepository {
    private final Driver driver;
    private final String database;

    public TagRepository(Driver driver, String database) {
        this.driver = driver;
        this.database = database;
    }

    public void createTag(String tagName) {
        String cypher = "MERGE (t:" + tagName + " {__tag__: $tagName})";
        try (Session session = driver.session(SessionConfig.forDatabase(database))) {
            session.executeWrite(tx -> {
                tx.run(cypher, Map.of("tagName", tagName));
                return null;
            });
        }
    }


    public List<String> findAllTags() {
        try (Session session = driver.session(SessionConfig.forDatabase(database))) {
            return session.executeRead(tx -> {
                List<String> tags = new ArrayList<>();
                Result res = tx.run("MATCH (t) WHERE exists(t.__tag__) RETURN labels(t) AS labels");
                while (res.hasNext()) {
                    List<Object> labels = res.next().get("labels").asList();
                    for (Object label : labels) {
                        String lbl = label.toString();
                        if (!lbl.equals("Vertex") && !tags.contains(lbl)) {
                            tags.add(lbl);
                        }
                    }
                }
                return tags;
            });
        }
    }
}
