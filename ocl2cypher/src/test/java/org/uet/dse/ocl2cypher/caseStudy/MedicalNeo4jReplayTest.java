package org.uet.dse.ocl2cypher.caseStudy;

import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.uet.dse.ocl2cypher.cypher.CypherAst.GeneratedArtifact;
import org.uet.dse.ocl2cypher.diagnostics.Result;
import org.uet.dse.ocl2cypher.diagnostics.Stage;
import org.uet.dse.ocl2cypher.execution.Neo4jExecutionAdapter;
import org.uet.dse.ocl2cypher.graph.GraphModel;

/** Opt-in Medical execution: one graph materialization for all 36 invariants. */
@Execution(ExecutionMode.SAME_THREAD)
class MedicalNeo4jReplayTest {
    private static final SharedGraph GRAPH = new SharedGraph();

    @AfterAll
    static void closeGraph() {
        GRAPH.close();
    }

    @TestFactory
    Stream<DynamicTest> medicalRealizationsMatchNeo4j() throws Exception {
        return MedicalInvariantReplayTest.cases(true, GRAPH);
    }

    static final class SharedGraph implements AutoCloseable {
        final String namespace = "ocl2cypher-e2e-medical-" + UUID.randomUUID();
        private Driver driver;
        private Session session;
        private boolean attempted;
        private RuntimeException setupFailure;

        synchronized Result<Neo4jExecutionAdapter.ExecutionResult> execute(
                GeneratedArtifact artifact, GraphModel graph) {
            if (!namespace.equals(graph.modelKey())) {
                throw new IllegalArgumentException("wrong Medical suite namespace");
            }
            if (!attempted) {
                attempted = true;
                try {
                    driver = GraphDatabase.driver(
                            MedicalInvariantReplayTest.required("NEO4J_URI"),
                            AuthTokens.basic(
                                    MedicalInvariantReplayTest.required("NEO4J_USER"),
                                    MedicalInvariantReplayTest.required("NEO4J_PASSWORD")));
                    session = driver.session(SessionConfig.forDatabase(
                            MedicalInvariantReplayTest.required("NEO4J_DB")));
                    Neo4jExecutionAdapter.materializeGraph(session, graph);
                    System.out.println("Medical graph loaded once; namespace=" + namespace);
                } catch (RuntimeException error) {
                    setupFailure = error;
                }
            }
            assertNull(setupFailure,
                    "Medical shared-graph setup failed; check connection and constraints");
            if (setupFailure != null) {
                return Result.failure(Stage.EXECUTION, "EXECUTION_FAILED",
                        setupFailure.getMessage());
            }
            return Neo4jExecutionAdapter.execute(session, artifact, Map.of());
        }

        @Override
        public synchronized void close() {
            try {
                if (session != null) session.close();
            } finally {
                if (driver != null) driver.close();
            }
        }
    }
}
