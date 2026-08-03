package org.uet.dse.neo4jtgg.experiment;

import java.util.Map;
import java.util.Set;

/** Executes a generated Cypher invariant query and returns its stable IDs. */
@FunctionalInterface
public interface CypherViolationSetEvaluator {
    Set<String> violationIds(String cypher, Map<String, Object> parameters);
}
