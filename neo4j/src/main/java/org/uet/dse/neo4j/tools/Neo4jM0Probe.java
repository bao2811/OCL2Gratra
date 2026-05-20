package org.uet.dse.neo4j.tools;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Values;

import java.util.List;

public final class Neo4jM0Probe {
    private Neo4jM0Probe() {
    }

    public static void main(String[] args) {
        String uri = args.length > 0 ? args[0] : "bolt://127.0.0.1:7687";
        String user = args.length > 1 ? args[1] : "neo4j";
        String password = args.length > 2 ? args[2] : "bao12345";
        String database = args.length > 3 ? args[3] : "demo";

        List<String> ids = List.of(
                "familyRegister1", "Simpson", "Homer", "Marge", "Bart", "Lisa",
                "Maggie", "Flanders", "Ned", "Rod", "Todd");

        try (Driver driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password));
             Session session = driver.session(SessionConfig.forDatabase(database))) {
            for (int i = 0; i < 6; i++) {
                Result result = session.run(
                        "MATCH (o) WHERE o.use_id IN $ids " +
                                "RETURN o.use_id AS id, labels(o) AS labels ORDER BY id",
                        Values.parameters("ids", ids));
                List<Record> rows = result.list();
                System.out.println("probe " + i + ": count=" + rows.size());
                for (Record row : rows) {
                    System.out.println("  " + row.get("id").asString() + " " + row.get("labels"));
                }
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
