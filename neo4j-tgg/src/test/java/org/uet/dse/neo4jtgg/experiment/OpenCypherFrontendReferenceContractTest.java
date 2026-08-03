package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins the parser artifact and executable Cypher 5 forms used by the PO-14 bridge. */
class OpenCypherFrontendReferenceContractTest {
    @Test
    void pinnedCypher5ParserAcceptsEveryReferencedGrammarAndAstForm() {
        assertEquals("cypher-parser-factory-2026.06.0.jar", Neo4jCypherAstBridge.parserArtifact());

        Map<String, String> queries = new LinkedHashMap<>();
        queries.put("UnionAll", "RETURN 1 AS value UNION ALL RETURN 2 AS value");
        queries.put("Match", "OPTIONAL MATCH (n)-[r]->(m) RETURN m AS value");
        queries.put("With", "WITH 1 AS x WHERE x = 1 RETURN x AS value");
        queries.put("ExistsExpression", "RETURN EXISTS { MATCH (n) RETURN n } AS value");
        queries.put("CountExpression", "RETURN COUNT { MATCH (n) RETURN n } AS value");
        queries.put("CollectExpression", "RETURN COLLECT { MATCH (n) RETURN n } AS value");
        queries.put("ListComprehension", "RETURN [x IN [1, 2] WHERE x > 1 | x] AS value");
        queries.put("PatternComprehension", "MATCH (n) RETURN [(n)-->(m) | m] AS value");
        queries.put("ExplicitParameter", "RETURN $value AS value");

        queries.forEach((expectedAstType, query) -> {
            Neo4jCypherAstBridge.Observation observation = Neo4jCypherAstBridge.parse(query);
            assertTrue(observation.hasAnyType(expectedAstType),
                    () -> expectedAstType + " absent from " + observation.canonicalTree());
            assertTrue(observation.hasAnyType("SingleQuery"), expectedAstType);
            assertTrue(observation.hasAnyType("Return"), expectedAstType);
        });
    }
}
