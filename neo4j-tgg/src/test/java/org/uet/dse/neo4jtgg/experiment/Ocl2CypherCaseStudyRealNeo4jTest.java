package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Session;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.sys.MSystem;
import org.uet.dse.neo4j.encoding.CanonicalGraphEncoding;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.uet.dse.neo4j.manager.SessionManager;
import org.uet.dse.neo4j.repo.Neo4jObjectRepository;
import org.uet.dse.neo4j.sync.model.CoreModelPushService;
import org.uet.dse.neo4j.sync.object.ObjectDiff;
import org.uet.dse.neo4j.sync.object.ObjectPushService;
import org.uet.dse.neo4j.sync.object.ObjectSnapshotCompare;
import org.uet.dse.neo4jtgg.service.impl.DefaultOclToCypherCompiler;

import java.nio.file.Files;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real-Neo4j two-sided oracle for the interaction-rich Company case study. */
class Ocl2CypherCaseStudyRealNeo4jTest {
    @Test
    void companyCaseReturnsExactlyTheObjectSideViolationIds() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("neo4j.company.case.it"),
                "Run with -Dneo4j.company.case.it=true and configured Neo4j");
        Neo4jEnvironmentConfig config = Neo4jEnvironmentConfig.load();
        MModel model = Ocl2CypherCaseStudyTest.compileModel(
                Ocl2CypherCaseStudyTest.CASE_STUDY.resolve("company.use"));
        MSystem system = Ocl2CypherCaseStudyTest.loadSoil(model,
                Ocl2CypherCaseStudyTest.CASE_STUDY.resolve("company.soil"));
        String modelKey = CanonicalGraphEncoding.modelKey(model.name());
        connect(config);
        cleanup(modelKey);
        try {
            new CoreModelPushService(new UseModelApi(model)).pushModelToNeo4j();
            ObjectDiff diff = new ObjectSnapshotCompare(system).compareObjects();
            new ObjectPushService(new Neo4jObjectRepository(), system).pushToNeo4j(diff);
            RepresentationAdequacyEvaluator.Report representation = RepresentationAdequacyEvaluator.evaluate(
                    RepresentationEvaluationRealNeo4jTest.sourceSnapshot(system, model.name()),
                    RepresentationEvaluationRealNeo4jTest.graphSnapshot(modelKey));
            assertTrue(representation.passed(), representation::render);

            DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
            var invariants = compiler.parseContextInvariants(Files.readString(
                    Ocl2CypherCaseStudyTest.CASE_STUDY.resolve("invariants.ocl")));
            ViolationSetOracle oracle = new ViolationSetOracle(
                    new UseObjectSideReferenceEvaluator(), this::execute);
            Map<BenchmarkVacuityStatus, Integer> vacuity =
                    new EnumMap<>(BenchmarkVacuityStatus.class);

            assertEquals(26, invariants.size());
            for (var invariant : invariants) {
                var compiled = compiler.compileInvariantInstrumented(invariant);
                FixturePremiseVerifier.verify(system, compiled);
                String caseId = invariant.className + "::" + invariant.invName;
                ViolationOracleResult result = oracle.evaluate(caseId,
                        Ocl2CypherCaseStudyTest.contextIds(system, invariant.className),
                        system, invariant, compiled.cypher(), compiled.parameters());
                assertTrue(result.completed(), result::render);
                assertTrue(result.equivalent(), () -> result.render() + "\n" + compiled.cypher());
                vacuity.merge(result.vacuityStatus(), 1, Integer::sum);
            }
            assertEquals(18, vacuity.getOrDefault(BenchmarkVacuityStatus.NON_VACUOUS_MIXED, 0));
            assertEquals(8, vacuity.getOrDefault(BenchmarkVacuityStatus.ALL_PASS, 0));
            assertEquals(0, vacuity.getOrDefault(BenchmarkVacuityStatus.EMPTY_CONTEXT, 0));
            System.out.println("COMPANY_CASE_DIFFERENTIAL_ORACLE=PASS equivalent=26"
                    + " missing=0 spurious=0 vacuity=" + vacuity);
        } finally {
            cleanup(modelKey);
            Neo4jDriverManager manager = Neo4jDriverManager.getInstance();
            if (manager != null) manager.close();
        }
    }

    private Set<String> execute(String cypher, Map<String, Object> parameters) {
        Set<String> ids = new LinkedHashSet<>();
        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            session.run("EXPLAIN " + cypher, parameters).consume();
            var result = session.run(cypher, parameters);
            while (result.hasNext()) ids.add(result.next().get("useId").asString());
        }
        return Set.copyOf(ids);
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
            session.run("MATCH (n {modelKey:$modelKey}) DETACH DELETE n",
                    Map.of("modelKey", modelKey)).consume();
            session.run("MATCH (m:ManageModel {name:$modelKey}) DETACH DELETE m",
                    Map.of("modelKey", modelKey)).consume();
        }
    }
}
