package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Session;
import org.uet.dse.neo4j.encoding.CanonicalGraphEncoding;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.uet.dse.neo4j.manager.SessionManager;
import org.uet.dse.neo4jtgg.ocl.OclBottomSeparationChecker;
import org.uet.dse.neo4jtgg.ocl.OclBottomToken;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Opt-in runtime evidence for the BottomSeparated premise and set cardinality boundary. */
class OclBottomSeparatedRealNeo4jTest {
    @Test
    void graphCollisionScanAndNullRepresentationHoldOnSelectedNeo4j() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("neo4j.bottom.it"),
                "Run with -Dneo4j.bottom.it=true and configured Neo4j");
        Neo4jEnvironmentConfig config = Neo4jEnvironmentConfig.load();
        String modelKey = CanonicalGraphEncoding.modelKey("BottomContract" + System.currentTimeMillis());
        connect(config);
        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            assertEquals(OclBottomSeparationChecker.Status.PASS,
                    OclBottomSeparationChecker.checkGraph(session, modelKey).status());

            var cardinality = session.run("UNWIND [null, null, 1] AS raw "
                            + "WITH DISTINCT coalesce(raw, $bottom) AS represented "
                            + "RETURN count(represented) AS countAll, count(DISTINCT represented) AS countDistinct",
                    Map.of("bottom", OclBottomToken.value())).single();
            assertEquals(2L, cardinality.get("countAll").asLong());
            assertEquals(2L, cardinality.get("countDistinct").asLong());

            session.run("CREATE (:BottomCollision {modelKey:$modelKey, __oclBottom:true})",
                    Map.of("modelKey", modelKey)).consume();
            assertEquals(OclBottomSeparationChecker.Status.FAIL,
                    OclBottomSeparationChecker.checkGraph(session, modelKey).status());
            session.run("MATCH (n {modelKey:$modelKey}) DETACH DELETE n",
                    Map.of("modelKey", modelKey)).consume();
            assertEquals(OclBottomSeparationChecker.Status.PASS,
                    OclBottomSeparationChecker.checkGraph(session, modelKey).status());
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
