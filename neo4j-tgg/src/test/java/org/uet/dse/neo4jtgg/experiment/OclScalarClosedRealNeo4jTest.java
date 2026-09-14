package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Session;
import org.uet.dse.neo4j.encoding.CanonicalGraphEncoding;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.uet.dse.neo4j.manager.SessionManager;
import org.uet.dse.neo4jtgg.ocl.OclScalarClosureChecker;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Opt-in validation of the persisted scalar boundary on the selected Neo4j runtime. */
class OclScalarClosedRealNeo4jTest {
    @Test
    void graphScalarScanRejectsNonFiniteReal() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("neo4j.scalar.it"),
                "Run with -Dneo4j.scalar.it=true and configured Neo4j");
        Neo4jEnvironmentConfig config = Neo4jEnvironmentConfig.load();
        String modelKey = CanonicalGraphEncoding.modelKey("ScalarContract" + System.currentTimeMillis());
        connect(config);
        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            assertEquals(OclScalarClosureChecker.Status.PASS,
                    OclScalarClosureChecker.checkGraph(session, modelKey).status());
            session.run("CREATE (:AttributeValue {modelKey:$modelKey, value:$value})",
                    Map.of("modelKey", modelKey, "value", Double.NaN)).consume();
            assertEquals(OclScalarClosureChecker.Status.FAIL,
                    OclScalarClosureChecker.checkGraph(session, modelKey).status());
            session.run("MATCH (n {modelKey:$modelKey}) DETACH DELETE n",
                    Map.of("modelKey", modelKey)).consume();
            assertEquals(OclScalarClosureChecker.Status.PASS,
                    OclScalarClosureChecker.checkGraph(session, modelKey).status());
        } finally {
            Neo4jDriverManager manager = Neo4jDriverManager.getInstance();
            if (manager != null) manager.close();
        }
    }

    private void connect(Neo4jEnvironmentConfig config) throws Exception {
        Neo4jDriverManager.connect(config.uri(), config.user(), config.password(), config.database(), false, false);
        SessionManager identity = new SessionManager();
        identity.createNewSession();
        Neo4jDriverManager.getInstance().setSessionManager(identity);
    }
}
