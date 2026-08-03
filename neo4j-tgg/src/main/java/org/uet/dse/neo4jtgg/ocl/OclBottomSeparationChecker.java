package org.uet.dse.neo4jtgg.ocl;

import org.neo4j.driver.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Runtime witness for the {@code BottomSeparated} execution premise. */
public final class OclBottomSeparationChecker {
    public enum Status {
        PASS,
        FAIL,
        OUT_OF_SCOPE
    }

    public record Result(Status status, List<String> violations) {
        public Result {
            violations = List.copyOf(violations);
        }

        public boolean passed() {
            return status == Status.PASS;
        }
    }

    private OclBottomSeparationChecker() {
    }

    /**
     * Detects use of the reserved bottom marker on graph entities in one model.
     * Neo4j properties cannot themselves be maps, so scalar slots and keys are
     * structurally disjoint from the tagged-map token; this scan protects the
     * reserved marker namespace on nodes and relationships.
     */
    public static Result checkGraph(Session session, String modelKey) {
        if (session == null || modelKey == null || modelKey.isBlank()) {
            return new Result(Status.OUT_OF_SCOPE,
                    List.of("A live session and non-blank modelKey are required"));
        }
        long nodeCollisions = session.run(
                        "MATCH (n {modelKey: $modelKey}) "
                                + "WHERE n.__oclBottom = true RETURN count(n) AS collisions",
                        Map.of("modelKey", modelKey))
                .single().get("collisions").asLong();
        long relationshipCollisions = session.run(
                        "MATCH (left)-[r]->(right) "
                                + "WHERE r.__oclBottom = true "
                                + "AND (r.modelKey = $modelKey OR left.modelKey = $modelKey OR right.modelKey = $modelKey) "
                                + "RETURN count(r) AS collisions",
                        Map.of("modelKey", modelKey))
                .single().get("collisions").asLong();
        List<String> violations = new ArrayList<>();
        if (nodeCollisions > 0) {
            violations.add(nodeCollisions + " model-scoped node(s) use reserved marker " + OclBottomToken.MARKER);
        }
        if (relationshipCollisions > 0) {
            violations.add(relationshipCollisions + " model-scoped relationship(s) use reserved marker "
                    + OclBottomToken.MARKER);
        }
        return new Result(violations.isEmpty() ? Status.PASS : Status.FAIL, violations);
    }

    public static void requireGraphSeparated(Session session, String modelKey) {
        Result result = checkGraph(session, modelKey);
        if (!result.passed()) {
            throw new IllegalStateException("BottomSeparated=" + result.status() + ": "
                    + String.join("; ", result.violations()));
        }
    }
}
