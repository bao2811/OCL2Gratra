package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Result;
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

/** Real-Neo4j differential execution of the comprehensive correctness fixture. */
class ComprehensiveUmlOclRealNeo4jTest {
    @Test
    void returnsExactlyTheObjectSideViolationSetForEveryInvariant() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("neo4j.comprehensive.it"),
                "Run with -Dneo4j.comprehensive.it=true and configured Neo4j");
        Neo4jEnvironmentConfig config = Neo4jEnvironmentConfig.load();
        MModel model = ComprehensiveUmlOclCaseStudyTest.compileModel(
                ComprehensiveUmlOclCaseStudyTest.CASE_STUDY.resolve("comprehensive.use"));
        MSystem system = ComprehensiveUmlOclCaseStudyTest.loadSoil(model,
                ComprehensiveUmlOclCaseStudyTest.CASE_STUDY.resolve("comprehensive.soil"));
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
                    ComprehensiveUmlOclCaseStudyTest.CASE_STUDY.resolve("invariants.ocl")));
            ViolationSetOracle oracle = new ViolationSetOracle(
                    new UseObjectSideReferenceEvaluator(), this::execute);
            Map<BenchmarkVacuityStatus, Integer> vacuity = new EnumMap<>(BenchmarkVacuityStatus.class);
            String selectedCase = System.getProperty("neo4j.comprehensive.case", "").trim();
            int executedCases = 0;

            assertEquals(30, invariants.size());
            for (var invariant : invariants) {
                String caseId = invariant.className + "::" + invariant.invName;
                if (!selectedCase.isEmpty() && !selectedCase.equals(caseId)) continue;
                executedCases++;
                long caseStart = System.nanoTime();
                System.out.println("COMPREHENSIVE_CASE=START id=" + caseId);
                System.out.flush();
                var compiled = compiler.compileInvariantInstrumented(invariant);
                FixturePremiseVerifier.verify(system, compiled);
                ViolationOracleResult result = oracle.evaluate(caseId,
                        ComprehensiveUmlOclCaseStudyTest.contextIds(system, invariant.className),
                        system, invariant, compiled.cypher(), compiled.parameters());
                assertTrue(result.completed(), result::render);
                assertTrue(result.equivalent(), () -> result.render() + "\nmemberships="
                        + memberships() + "\n" + compiled.cypher());
                vacuity.merge(result.vacuityStatus(), 1, Integer::sum);
                System.out.println("COMPREHENSIVE_CASE=PASS id=" + caseId
                        + " elapsedMs=" + ((System.nanoTime() - caseStart) / 1_000_000L));
                System.out.flush();
            }
            if (selectedCase.isEmpty()) {
                assertEquals(30, executedCases);
                assertEquals(26, vacuity.getOrDefault(BenchmarkVacuityStatus.NON_VACUOUS_MIXED, 0));
                assertEquals(4, vacuity.getOrDefault(BenchmarkVacuityStatus.ALL_PASS, 0));
                assertEquals(0, vacuity.getOrDefault(BenchmarkVacuityStatus.EMPTY_CONTEXT, 0));
            } else {
                assertEquals(1, executedCases, "Unknown comprehensive case: " + selectedCase);
            }
            System.out.println("COMPREHENSIVE_DIFFERENTIAL_ORACLE=PASS equivalent=" + executedCases
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
            Result result = session.run(cypher, parameters);
            while (result.hasNext()) ids.add(result.next().get("useId").asString());
        }
        return Set.copyOf(ids);
    }

    private Map<String, Set<String>> memberships() {
        Map<String, Set<String>> result = new java.util.LinkedHashMap<>();
        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            Result rows = session.run("MATCH (o:Object)-[:ObjectInstanceOf]->(c:UmlClass) "
                    + "RETURN o.use_id AS id, collect(c.classKey) AS keys ORDER BY id");
            while (rows.hasNext()) {
                var row = rows.next();
                result.put(row.get("id").asString(), Set.copyOf(row.get("keys").asList(v -> v.asString())));
            }
        }
        return result;
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
            // Removing model-scoped nodes invalidates the persisted M2 hash as
            // well; otherwise a repeated test run may incorrectly skip M2 push.
            session.run("MATCH (m:ManageModel {name:$modelKey}) DETACH DELETE m",
                    Map.of("modelKey", modelKey)).consume();
        }
    }
}
