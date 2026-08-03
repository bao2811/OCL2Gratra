package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Test;
import org.uet.dse.neo4jtgg.ocl.ir.OclCypherRenderer;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Neo4jCypherAstBridgeTest {
    @Test
    void cypher5ParserBuildsInternalAstForEveryFormalCorpusQuery() {
        assertEquals("cypher-parser-factory-2026.06.0.jar", Neo4jCypherAstBridge.parserArtifact());
        assertEquals(47, OclValFragmentCoverageTest.admittedCases().size(),
                "Certified OCL corpus size changed without updating the parser agreement evidence");
        OclCypherPlanFormalTreeAgreementTest corpus = new OclCypherPlanFormalTreeAgreementTest();
        OclCypherRenderer renderer = new OclCypherRenderer("OclValCoverage");
        Set<String> observedProducts = new LinkedHashSet<>();
        int parsed = 0;
        for (OclCypherPlanFormalTreeAgreementTest.NamedPlan item : corpus.namedCompilationCorpus()) {
            String cypher = renderer.renderInvariant(item.plan()).cypher();
            Neo4jCypherAstBridge.Observation first = Neo4jCypherAstBridge.parse(cypher);
            Neo4jCypherAstBridge.Observation second = Neo4jCypherAstBridge.parse(cypher);
            assertEquals(first.canonicalTree(), second.canonicalTree(), item.name());
            assertEquals(first.sha256(), second.sha256(), item.name());
            assertTrue(first.hasAnyType("SingleQuery"), item.name());
            assertTrue(first.hasAnyType("Return"), item.name());
            assertFalse(first.canonicalTree().isBlank(), item.name());
            observedProducts.addAll(first.productNames());
            parsed++;
        }
        assertEquals(52, parsed);
        assertTrue(observedProducts.containsAll(Set.of("SingleQuery", "Match", "Return",
                "RelationshipPattern", "Property", "ExplicitParameter")), observedProducts.toString());
        System.out.println("Neo4j Cypher 5 AST agreement: 52/52 queries, parser artifact "
                + Neo4jCypherAstBridge.parserArtifact());
    }

    @Test
    void nestedAliasShadowingRemainsExplicitInTheNeo4jAstProjection() {
        String cypher = "RETURN any(x IN [1] WHERE any(x IN [x] WHERE x = 1)) AS value";
        Neo4jCypherAstBridge.Observation observation = Neo4jCypherAstBridge.parse(cypher);
        assertEquals(2, observation.count("AnyIterablePredicate"));
        assertEquals(2, observation.count("FilterScope"));
        assertTrue(observation.canonicalTree().contains("Variable(name=\"x\""));

        String renamedInner = "RETURN any(x IN [1] WHERE any(y IN [x] WHERE y = 1)) AS value";
        assertFalse(observation.sha256().equals(Neo4jCypherAstBridge.parse(renamedInner).sha256()),
                "AST projection must not erase nested alias names or their scope nodes");
    }

    @Test
    void actualParserRejectsMalformedGeneratedFragmentAndSeparatesSemanticMutants() {
        assertThrows(RuntimeException.class,
                () -> Neo4jCypherAstBridge.parse("MATCH (self RETURN self AS value"));
        assertThrows(RuntimeException.class,
                () -> Neo4jCypherAstBridge.parse("RETURN CASE WHEN true THEN 1 AS value"));

        var outgoing = Neo4jCypherAstBridge.parse(
                "MATCH (self:Object) RETURN [(self)-[r]->(target:Object) | target] AS value");
        var incoming = Neo4jCypherAstBridge.parse(
                "MATCH (self:Object) RETURN [(self)<-[r]-(target:Object) | target] AS value");
        assertFalse(outgoing.sha256().equals(incoming.sha256()),
                "Neo4j AST projection must preserve relationship direction");

        var count = Neo4jCypherAstBridge.parse(
                "RETURN COUNT { MATCH (n)-[r]->(m) RETURN m } AS value");
        var collect = Neo4jCypherAstBridge.parse(
                "RETURN COLLECT { MATCH (n)-[r]->(m) RETURN m } AS value");
        assertTrue(count.hasAnyType("CountExpression"));
        assertTrue(collect.hasAnyType("CollectExpression"));
        assertFalse(count.sha256().equals(collect.sha256()),
                "Neo4j AST projection must preserve COUNT/COLLECT distinction");
    }
}
