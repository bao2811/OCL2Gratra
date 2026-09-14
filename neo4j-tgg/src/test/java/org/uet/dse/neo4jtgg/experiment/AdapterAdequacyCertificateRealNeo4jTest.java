package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.api.UseSystemApi;
import org.uet.dse.neo4j.encoding.CanonicalGraphEncoding;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.uet.dse.neo4j.manager.SessionManager;
import org.uet.dse.neo4j.repo.Neo4jObjectRepository;
import org.uet.dse.neo4j.sync.model.CoreModelPushService;
import org.uet.dse.neo4j.sync.object.ObjectDiff;
import org.uet.dse.neo4j.sync.object.ObjectPushService;
import org.uet.dse.neo4j.sync.object.ObjectSnapshotCompare;
import org.uet.dse.neo4jtgg.service.impl.DefaultOclToCypherCompiler;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Production-encoder, same-transaction execution witness for PO-21. */
class AdapterAdequacyCertificateRealNeo4jTest {
    @Test
    void certificateAndQueriesObserveOneNeo4jSnapshot() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("neo4j.adapter.certificate.it"),
                "Run with -Dneo4j.adapter.certificate.it=true and configured Neo4j");
        Neo4jEnvironmentConfig config = Neo4jEnvironmentConfig.load();
        String modelName = "AdapterCertificate_" + System.currentTimeMillis();
        var model = OclVal47NonVacuityFixture.compileModel(modelName);
        UseSystemApi api = OclVal47NonVacuityFixture.seed(model);
        String modelKey = CanonicalGraphEncoding.modelKey(modelName);
        connect(config);
        cleanup(modelKey);
        try {
            new CoreModelPushService(new UseModelApi(model)).pushModelToNeo4j();
            ObjectDiff diff = new ObjectSnapshotCompare(api.getSystem()).compareObjects();
            new ObjectPushService(new Neo4jObjectRepository(), api.getSystem()).pushToNeo4j(diff);
            DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
            List<InstrumentedCompilationResult> plans = List.of(
                    compiler.compileInvariantInstrumented(
                            "context Person inv AdapterAttributeProbe: self.name <> ''"),
                    compiler.compileInvariantInstrumented(
                            "context Library inv AdapterNavigationProbe: "
                                    + "self.book['A1']->notEmpty() and Book.allInstances()->notEmpty()"));
            var sourceStart = AdapterAdequacySnapshotReader.source(api.getSystem(), modelName);
            AdapterAdequacyCertificate certificate;
            try (Session session = Neo4jDriverManager.getInstance().openSession();
                 Transaction transaction = session.beginTransaction()) {
                var graphStart = AdapterAdequacySnapshotReader.graph(transaction, modelKey);
                for (InstrumentedCompilationResult plan : plans) {
                    transaction.run(plan.cypher(), plan.parameters()).consume();
                }
                var graphEnd = AdapterAdequacySnapshotReader.graph(transaction, modelKey);
                var sourceEnd = AdapterAdequacySnapshotReader.source(api.getSystem(), modelName);
                certificate = AdapterAdequacyCertificate.issue(
                        new AdapterAdequacyCertificate.AdapterAdequacySnapshot(
                                "neo4j-read-transaction-" + modelName, modelName, modelKey,
                                "OclCypherRenderer-direct-v1", sourceStart, sourceEnd,
                                graphStart, graphEnd, plans));
                transaction.commit();
            }
            assertTrue(certificate.representationReport().passed());
            assertTrue(certificate.rendererReport().passed());
            assertEquals(6, certificate.observationDiffs().size());

            try (Session session = Neo4jDriverManager.getInstance().openSession();
                 Transaction transaction = session.beginTransaction()) {
                transaction.run("MATCH (c:UmlClass {modelKey:$modelKey}) REMOVE c:UmlClass",
                        Map.of("modelKey", modelKey)).consume();
                var graphWithoutClassLabel = AdapterAdequacySnapshotReader.graph(transaction, modelKey);
                var sourceEnd = AdapterAdequacySnapshotReader.source(api.getSystem(), modelName);
                assertThrows(IllegalStateException.class, () -> AdapterAdequacyCertificate.issue(
                        new AdapterAdequacyCertificate.AdapterAdequacySnapshot(
                                "neo4j-missing-umlclass-label-" + modelName, modelName, modelKey,
                                "OclCypherRenderer-direct-v1", sourceStart, sourceEnd,
                                graphWithoutClassLabel, graphWithoutClassLabel, plans)));
                for (InstrumentedCompilationResult plan : plans) {
                    assertTrue(transaction.run(plan.cypher(), plan.parameters()).list().isEmpty(),
                            "Renderer must not observe a class node after :UmlClass is removed");
                }
            }
            System.out.println("ADAPTER_ADEQUACY_CERTIFICATE=PASS snapshot=" + certificate.snapshotId()
                    + " observations=6 plans=" + plans.size() + " labelMutation=KILLED");
        } finally {
            cleanup(modelKey);
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

    private void cleanup(String modelKey) {
        Neo4jDriverManager manager = Neo4jDriverManager.getInstance();
        if (manager == null || !manager.isConnected()) return;
        try (Session session = manager.openSession()) {
            session.run("MATCH (n {modelKey:$modelKey}) DETACH DELETE n", Map.of("modelKey", modelKey)).consume();
            session.run("MATCH (m:ManageModel {name:$modelKey}) DETACH DELETE m", Map.of("modelKey", modelKey)).consume();
        }
    }
}
