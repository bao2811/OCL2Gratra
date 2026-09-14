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
import org.uet.dse.neo4jtgg.ocl.OclScalarClosureChecker;
import org.uet.dse.neo4jtgg.service.impl.DefaultOclToCypherCompiler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in differential execution against a real Neo4j instance. */
class MedicalAndCarRentalRealNeo4jTest {
    @Test
    void medicalSystemMatchesUseForEveryProductionSupportedInvariant() throws Exception {
        runCase("MEDICAL_YTE", Path.of("..", "examples", "medical-system-yte"),
                "medical-system.use", "medical-system.soil", "invariants.ocl",
                "expected-violations.csv", "expected-production-gaps.txt", 13);
    }

    @Test
    void medicalNestedDiscriminatorsMatchUseOnRealNeo4j() throws Exception {
        runCase("MEDICAL_YTE_NESTED", Path.of("..", "examples", "medical-system-yte"),
                "medical-system.use", "medical-system.soil", "nested-discriminators.ocl",
                "expected-nested-discriminator-violations.csv",
                "expected-nested-production-gaps.txt", 14);
    }

    @Test
    void carRentalMatchesUseForEveryProductionSupportedInvariant() throws Exception {
        runCase("CAR_RENTAL", Path.of("..", "examples", "carrental"),
                "carrentalmodel.use", "carrental.soil", "invariants.ocl",
                "expected-violations.csv", "expected-production-gaps.txt", 10);
    }

    @Test
    void extendedCarRentalMatchesUseForEveryProductionSupportedInvariant() throws Exception {
        runCase("CAR_RENTAL_EXTENDED", Path.of("..", "examples", "carrental"),
                "carrentalmodel.use", "carrental-experiment.soil", "invariants-extended.ocl",
                "expected-violations-extended.csv", "expected-production-gaps-extended.txt", 25);
    }

    private void runCase(String evidenceName, Path directory, String modelFile,
                         String soilFile, String invariantFile, String expectedFile,
                         String expectedGapFile, int expectedExecuted) throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("neo4j.medical.carrental.it"),
                "Run with -Dneo4j.medical.carrental.it=true and configured Neo4j");
        connect(Neo4jEnvironmentConfig.load());
        var model = MedicalAndCarRentalCaseStudyTest.compileModel(directory.resolve(modelFile));
        MSystem system = MedicalAndCarRentalCaseStudyTest.loadSoil(model, directory.resolve(soilFile));
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

            String ocl = Files.readString(directory.resolve(invariantFile));
            DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
            var invariants = compiler.parseContextInvariants(ocl);
            var rules = compiler.compileFile(ocl).getRuleResults();
            Map<String, Set<String>> expected = MedicalAndCarRentalCaseStudyTest.expectedIds(
                    directory.resolve(expectedFile));
            Set<String> expectedGaps = new LinkedHashSet<>(Files.readAllLines(
                    directory.resolve(expectedGapFile)));
            ViolationSetOracle oracle = new ViolationSetOracle(
                    new UseObjectSideReferenceEvaluator(), this::execute);
            int executed = 0;

            assertEquals(invariants.size(), rules.size());
            for (int index = 0; index < invariants.size(); index++) {
                var invariant = invariants.get(index);
                var rule = rules.get(index);
                String id = invariant.className + "::" + invariant.invName;
                if (!rule.isSupported()) {
                    assertTrue(expectedGaps.contains(id), () -> id + ": " + rule.getReason());
                    continue;
                }
                executed++;
                ViolationOracleResult result = oracle.evaluate(id,
                        MedicalAndCarRentalCaseStudyTest.contextIds(system, invariant.className),
                        system, invariant, rule.getCypher(), rule.getParameters());
                assertTrue(result.completed(), result::render);
                assertTrue(result.equivalent(), () -> result.render() + "\n" + rule.getCypher());
                assertEquals(expected.get(id), result.referenceViolationIds(), id + " USE");
                assertEquals(expected.get(id), result.cypherViolationIds(), id + " Neo4j");
                System.out.println(evidenceName + "_CASE=PASS id=" + id
                        + " violations=" + result.cypherViolationIds());
            }
            assertEquals(expectedExecuted, executed);
            System.out.println(evidenceName + "_DIFFERENTIAL_ORACLE=PASS equivalent=" + executed
                    + " productionGaps=" + expectedGaps.size() + " missing=0 spurious=0");
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
        Neo4jDriverManager.connect(config.uri(), config.user(), config.password(),
                config.database(), false, false);
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
