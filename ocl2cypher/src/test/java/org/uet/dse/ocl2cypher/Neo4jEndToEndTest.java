package org.uet.dse.ocl2cypher;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.uet.dse.ocl2cypher.api.CoreOracle;
import org.uet.dse.ocl2cypher.core.CoreLowering;
import org.uet.dse.ocl2cypher.cypher.CypherAst;
import org.uet.dse.ocl2cypher.cypher.Realization;
import org.uet.dse.ocl2cypher.execution.Neo4jExecutionAdapter;
import org.uet.dse.ocl2cypher.execution.Neo4jExecutionAdapter.ExecutionRequest;
import org.uet.dse.ocl2cypher.graph.GraphBuilder;
import org.uet.dse.ocl2cypher.qcyp.QCypTranslator;
import org.uet.dse.ocl2cypher.qcyp.QQuery;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.runtime.OclValue;
import org.uet.dse.ocl2cypher.source.omg.OmgAs;
import org.uet.dse.ocl2cypher.source.model.SchemaModel;
import org.uet.dse.ocl2cypher.source.model.Snapshot;
import org.uet.dse.ocl2cypher.source.model.UmlAttribute;
import org.uet.dse.ocl2cypher.source.model.UmlClass;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end oracle: {@code Core violations == Neo4j-executed violations}.
 *
 * <p>This is the closing edge of the differential — {@code Core} evaluates over
 * {@code SM,SN}; {@code R}/{@code S} lower to Cypher; the adapter materializes
 * the SAME {@code G} into Neo4j and runs the query. Agreement here transitively
 * validates the whole chain against a real engine, so a bottom carried only as a
 * tag (missing attribute) has to reach the same violation set on the database as
 * in the source oracle.
 *
 * <p>Database writes require {@code OCL2CYPHER_RUN_NEO4J_E2E=true} and explicit
 * {@code NEO4J_URI/USER/PASSWORD/DB}. Once opted in, connection/authentication
 * failures fail the test instead of skipping it. Use a disposable test database.
 * Each run owns a random model namespace; materialization never targets "m".
 * The fixture remains in that namespace after execution for inspection.
 */
class Neo4jEndToEndTest {

    private static String env(String k, String dflt) {
        String v = System.getenv(k);
        return v == null || v.isBlank() ? dflt : v;
    }

    private static String requiredEnv(String name) {
        String value = env(name, null);
        assertNotNull(value, "E2E requires environment variable " + name);
        return value;
    }

    private static String freshModelKey() {
        return "ocl2cypher-e2e-" + java.util.UUID.randomUUID();
    }

    private ExecutionRequest buildRequest(String ocl, SchemaModel sm, Snapshot sn) {
        var fe = org.uet.dse.ocl2cypher.api.FrontendCompiler.compile(ocl, sm);
        assertTrue(fe.isSuccess(), () -> "frontend: " + fe.diagnostics());
        OmgAs.OmgDocument doc = fe.value().get(0);
        var low = CoreLowering.lower(sm, doc, doc.constraints.get(0));
        assertTrue(low.isSuccess(), () -> "lowering: " + low.diagnostics());
        var g = GraphBuilder.build(sm, sn);
        assertTrue(g.isSuccess(), () -> "graph: " + g.diagnostics());
        var q = QCypTranslator.translate(low.value());
        assertTrue(q.isSuccess(), () -> "translate: " + q.diagnostics());
        var r = Realization.realize(q.value(), g.value().graph(), CypherAst.Dialect.CYPHER_5);
        assertTrue(r.isSuccess(), () -> "realize: " + r.diagnostics());
        // Repo convention: NEO4J_URI / NEO4J_USER / NEO4J_PASSWORD / NEO4J_DB.
        return new ExecutionRequest(r.value(), g.value().graph(),
                requiredEnv("NEO4J_URI"),
                requiredEnv("NEO4J_DB"),
                requiredEnv("NEO4J_USER"),
                requiredEnv("NEO4J_PASSWORD"),
                Map.of());
    }

    private List<String> executeIntegration(ExecutionRequest req) {
        assertTrue(req.graph().modelKey().startsWith("ocl2cypher-e2e-"),
                "refuse materialization outside E2E namespace");
        var availability = Neo4jExecutionAdapter.checkAvailability(
                new Neo4jExecutionAdapter.ExecutionConfig(req.uri(), req.database(),
                        req.user(), req.password()));
        assertTrue(availability.isSuccess(),
                "Neo4j E2E was enabled but connectivity/authentication failed; check explicit test configuration");
        var result = Neo4jExecutionAdapter.executeWithMaterializedGraph(req);
        assertTrue(result.isSuccess(),
                () -> "Neo4j is reachable but materialization/query/decode failed: "
                        + result.primaryDiagnostic());
        return result.value().violationIds();
    }

    @Test
    void nameInvariantViolationsMatchNeo4j() {
        Assumptions.assumeTrue("true".equalsIgnoreCase(env("OCL2CYPHER_RUN_NEO4J_E2E", "false")),
                "Set OCL2CYPHER_RUN_NEO4J_E2E=true to enable database writes in a disposable test database");
        SchemaModel sm = SchemaModel.builder(freshModelKey())
                .clazz(UmlClass.of("Person"))
                .attribute(UmlAttribute.of("Person", "name", OclType.STRING))
                .build();
        Snapshot sn = Snapshot.builder()
                .object("alice", "Person")
                .attribute("alice", "name", new OclValue.StringValue("Alice"))
                .object("bob", "Person")
                .attribute("bob", "name", new OclValue.StringValue("Bob"))
                .object("carol", "Person") // missing name → bottom → violation
                .build();
        String ocl = "context Person inv NamedAlice: self.name = 'Alice'";

        List<String> core = CoreOracle.violationsOclEq(ocl, sm, sn);
        List<String> neo = executeIntegration(buildRequest(ocl, sm, sn));

        assertEquals(Set.copyOf(core), Set.copyOf(neo),
                "Neo4j violation set must equal the Core oracle, bottom included");
        assertEquals(Set.of("bob", "carol"), Set.copyOf(neo));
    }

    @Test
    void fixtureNamespacesAreUniqueAndNotTheApplicationModel() {
        String first = freshModelKey();
        String second = freshModelKey();
        assertNotEquals(first, second);
        assertNotEquals("m", first);
        assertTrue(first.startsWith("ocl2cypher-e2e-"));
        assertDoesNotThrow(() -> java.util.UUID.fromString(first.substring("ocl2cypher-e2e-".length())));
    }
}
