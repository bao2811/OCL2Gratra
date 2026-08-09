package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Session;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.uml.sys.MSystem;
import org.uet.dse.neo4j.encoding.CanonicalGraphEncoding;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.uet.dse.neo4j.manager.SessionManager;
import org.uet.dse.neo4j.repo.Neo4jObjectRepository;
import org.uet.dse.neo4j.sync.model.CoreModelPushService;
import org.uet.dse.neo4j.sync.object.ObjectDiff;
import org.uet.dse.neo4j.sync.object.ObjectPushService;
import org.uet.dse.neo4j.sync.object.ObjectSnapshotCompare;
import org.uet.dse.neo4jtgg.ocl.OclBottomSeparationChecker;
import org.uet.dse.neo4jtgg.ocl.OclExecutionPremiseChecker;
import org.uet.dse.neo4jtgg.ocl.OclScalarClosureChecker;
import org.uet.dse.neo4jtgg.service.impl.DefaultOclToCypherCompiler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real-Neo4j exact-ID oracle for the independent Families and Persons models. */
class FamiliesToPersonsCaseStudyRealNeo4jTest {
    @Test
    void familyCaseAgreesExactlyWithNativeUse() throws Exception {
        runCase("FAMILY_CASE_DIFFERENTIAL_ORACLE",
                FamiliesToPersonsCaseStudyTest.ROOT.resolve("families"),
                "families.use", "families.soil", 3, 7);
    }

    @Test
    void personCaseAgreesExactlyWithNativeUse() throws Exception {
        runCase("PERSON_CASE_DIFFERENTIAL_ORACLE",
                FamiliesToPersonsCaseStudyTest.ROOT.resolve("persons"),
                "persons.use", "persons.soil", 7, 3);
    }

    private void runCase(String evidenceName, Path directory, String modelFile,
                         String soilFile, int expectedMixed, int expectedAllPass) throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("neo4j.families.persons.it"),
                "Run with -Dneo4j.families.persons.it=true and configured Neo4j");
        connect(Neo4jEnvironmentConfig.load());
        try {
            CaseResult result = verify(directory, modelFile, soilFile);
            assertCase(result, 10, expectedMixed, expectedAllPass);
            System.out.println(evidenceName + "=PASS equivalent=10"
                    + " missing=0 spurious=0 vacuity=" + result.vacuity());
        } finally {
            Neo4jDriverManager manager = Neo4jDriverManager.getInstance();
            if (manager != null) manager.close();
        }
    }

    private CaseResult verify(Path directory, String modelFile, String soilFile) throws Exception {
        Map<BenchmarkVacuityStatus, Integer> vacuity = new EnumMap<>(BenchmarkVacuityStatus.class);
        var model = FamiliesToPersonsCaseStudyTest.compileModel(directory.resolve(modelFile));
        MSystem system = FamiliesToPersonsCaseStudyTest.loadSoil(model, directory.resolve(soilFile));
        String modelKey = CanonicalGraphEncoding.modelKey(model.name());
        cleanup(modelKey);
        try {
            new CoreModelPushService(new UseModelApi(model)).pushModelToNeo4j();
            ObjectDiff diff = new ObjectSnapshotCompare(system).compareObjects();
            new ObjectPushService(new Neo4jObjectRepository(), system).pushToNeo4j(diff);
            RepresentationAdequacyEvaluator.Report representation = RepresentationAdequacyEvaluator.evaluate(
                    RepresentationEvaluationRealNeo4jTest.sourceSnapshot(system, model.name()),
                    RepresentationEvaluationRealNeo4jTest.graphSnapshot(modelKey));
            assertTrue(representation.passed(), representation::render);
            try (Session session = Neo4jDriverManager.getInstance().openSession()) {
                OclBottomSeparationChecker.requireGraphSeparated(session, modelKey);
                OclScalarClosureChecker.requireGraphClosed(session, modelKey);
            }

            DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
            var invariants = compiler.parseContextInvariants(Files.readString(directory.resolve("invariants.ocl")));
            Map<String, FamiliesToPersonsCaseStudyTest.ExpectedViolation> expected =
                    FamiliesToPersonsCaseStudyTest.expectedIds(directory.resolve("expected-violations.csv"));
            assertEquals(expected.size(), invariants.size(), directory.toString());
            ViolationSetOracle oracle = new ViolationSetOracle(
                    new UseObjectSideReferenceEvaluator(), this::execute);

            for (var invariant : invariants) {
                String id = invariant.className + "::" + invariant.invName;
                var compiled = compiler.compileInvariantInstrumented(invariant);
                FixturePremiseVerifier.verify(system, compiled);
                try (Session session = Neo4jDriverManager.getInstance().openSession()) {
                    OclExecutionPremiseChecker.requireGraphScalarClosed(
                            session, model.name(), compiled.validationAlgebra());
                }
                ViolationOracleResult result = oracle.evaluate(id,
                        FamiliesToPersonsCaseStudyTest.contextIds(system, invariant.className),
                        system, invariant, compiled.cypher(), compiled.parameters());
                assertTrue(result.completed(), result::render);
                assertTrue(result.equivalent(), () -> result.render() + "\n" + compiled.cypher());
                assertEquals(expected.get(id).ids(), result.referenceViolationIds(), id + " USE");
                assertEquals(expected.get(id).ids(), result.cypherViolationIds(), id + " Neo4j");
                assertEquals(expected.get(id).classification(), result.vacuityStatus(), id);
                vacuity.merge(result.vacuityStatus(), 1, Integer::sum);
            }
            return new CaseResult(invariants.size(), Map.copyOf(vacuity));
        } finally {
            cleanup(modelKey);
        }
    }

    private void assertCase(CaseResult result, int expectedCount, int expectedMixed, int expectedAllPass) {
        assertEquals(expectedCount, result.compared());
        assertEquals(expectedMixed,
                result.vacuity().getOrDefault(BenchmarkVacuityStatus.NON_VACUOUS_MIXED, 0));
        assertEquals(expectedAllPass,
                result.vacuity().getOrDefault(BenchmarkVacuityStatus.ALL_PASS, 0));
        assertEquals(0, result.vacuity().getOrDefault(BenchmarkVacuityStatus.ALL_VIOLATE, 0));
        assertEquals(0, result.vacuity().getOrDefault(BenchmarkVacuityStatus.EMPTY_CONTEXT, 0));
    }

    private record CaseResult(int compared, Map<BenchmarkVacuityStatus, Integer> vacuity) {
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
            session.run("MATCH (n {modelKey:$modelKey}) DETACH DELETE n", Map.of("modelKey", modelKey)).consume();
            session.run("MATCH (m:ManageModel {name:$modelKey}) DETACH DELETE m", Map.of("modelKey", modelKey)).consume();
        }
    }
}
