package org.uet.dse.ocl2cypher.caseStudy;

import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/** Opt-in only. One committed graph per suite; one read transaction per admitted invariant. */
@org.junit.jupiter.api.parallel.Execution(org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD)
class CarRentalNeo4jReplayTest {
    private static final SharedGraph GRAPH = new SharedGraph();
    @org.junit.jupiter.api.AfterAll static void closeGraph() { GRAPH.close(); }
    @TestFactory Stream<DynamicTest> successfulRealizationsMatchNeo4j() throws Exception {
        return CarRentalInvariantReplayTest.cases(true, GRAPH);
    }

    static final class SharedGraph implements AutoCloseable {
        final String namespace = "ocl2cypher-e2e-carrental-" + java.util.UUID.randomUUID();
        private org.neo4j.driver.Driver driver;
        private org.neo4j.driver.Session session;
        private boolean attempted;
        private RuntimeException setupFailure;

        synchronized org.uet.dse.ocl2cypher.diagnostics.Result<org.uet.dse.ocl2cypher.execution.Neo4jExecutionAdapter.ExecutionResult>
                execute(org.uet.dse.ocl2cypher.execution.Neo4jExecutionAdapter.ExecutionRequest request) {
            if (!namespace.equals(request.graph().modelKey())) throw new IllegalArgumentException("Wrong suite namespace");
            if (!attempted) {
                attempted = true;
                try {
                    driver = org.neo4j.driver.GraphDatabase.driver(request.uri(),
                            org.neo4j.driver.AuthTokens.basic(request.user(), request.password()));
                    session = driver.session(org.neo4j.driver.SessionConfig.forDatabase(request.database()));
                    org.uet.dse.ocl2cypher.execution.Neo4jExecutionAdapter.materializeGraph(session, request.graph());
                    System.out.println("CarRental graph loaded once; namespace=" + namespace);
                } catch (RuntimeException e) { setupFailure = e; }
            }
            if (setupFailure != null) return org.uet.dse.ocl2cypher.diagnostics.Result.failure(
                    org.uet.dse.ocl2cypher.diagnostics.Stage.EXECUTION, "EXECUTION_FAILED",
                    "Shared graph setup failed: " + setupFailure.getMessage());
            return org.uet.dse.ocl2cypher.execution.Neo4jExecutionAdapter.execute(session, request.artifact(), request.extraParameters());
        }
        public synchronized void close() {
            try { if (session != null) session.close(); }
            finally { if (driver != null) driver.close(); }
        }
    }
}
