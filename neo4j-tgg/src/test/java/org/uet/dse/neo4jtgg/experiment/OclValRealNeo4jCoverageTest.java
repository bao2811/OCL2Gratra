package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.uml.mm.MModel;
import org.uet.dse.neo4j.encoding.CanonicalGraphEncoding;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.uet.dse.neo4j.manager.SessionManager;
import org.uet.dse.neo4j.repo.Neo4jObjectRepository;
import org.uet.dse.neo4j.sync.model.CoreModelPushService;
import org.uet.dse.neo4j.sync.object.ObjectDiff;
import org.uet.dse.neo4j.sync.object.ObjectPushService;
import org.uet.dse.neo4j.sync.object.ObjectSnapshotCompare;
import org.uet.dse.neo4jtgg.service.impl.DefaultOclToCypherCompiler;
import org.uet.dse.neo4jtgg.ocl.OclBottomSeparationChecker;
import org.uet.dse.neo4jtgg.ocl.OclExecutionPremiseChecker;
import org.uet.dse.neo4jtgg.ocl.OclScalarClosureChecker;

import java.util.LinkedHashSet;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in differential execution of every frozen OCL_val coverage case on Neo4j. */
class OclValRealNeo4jCoverageTest {
    @Test
    void allAdmittedConstructsAgreeWithUseOnRealNeo4j() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("neo4j.oclval47.it"),
                "Run with -Dneo4j.oclval47.it=true and configured Neo4j");
        Neo4jEnvironmentConfig config = Neo4jEnvironmentConfig.load();
        assertEquals(Cypher5ValAssumptionMatrix.DATABASE, config.database(),
                "OCL47 runtime evidence database drift");
        String modelName = "OclVal47_" + System.currentTimeMillis();
        String modelKey = CanonicalGraphEncoding.modelKey(modelName);
        connect(config);
        try {
            requireSelectedRuntimeProfile();
            MModel model = OclVal47NonVacuityFixture.compileModel(modelName);
            UseSystemApi api = OclVal47NonVacuityFixture.seed(model);
            Map<String, OclVal47NonVacuityFixture.ExpectedCase> expected =
                    OclVal47NonVacuityFixture.expectations();
            new CoreModelPushService(new UseModelApi(model)).pushModelToNeo4j();
            ObjectDiff diff = new ObjectSnapshotCompare(api.getSystem()).compareObjects();
            new ObjectPushService(new Neo4jObjectRepository(), api.getSystem()).pushToNeo4j(diff);

            DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
            try (Session premiseSession = Neo4jDriverManager.getInstance().openSession()) {
                OclBottomSeparationChecker.requireGraphSeparated(premiseSession, modelKey);
                OclScalarClosureChecker.requireGraphClosed(premiseSession, modelKey);
            }
            ViolationSetOracle oracle = new ViolationSetOracle(
                    new UseObjectSideReferenceEvaluator(), this::execute);
            int compared = 0;
            Set<String> newConstructs = new LinkedHashSet<>();
            Map<BenchmarkVacuityStatus, Integer> vacuity = new EnumMap<>(BenchmarkVacuityStatus.class);
            for (OclValFragmentCoverageTest.CoverageCase testCase
                    : OclValFragmentCoverageTest.admittedCases()) {
                var invariant = compiler.parseContextInvariants(testCase.ocl()).get(0);
                var compiled = compiler.compileInvariantInstrumented(invariant);
                FixturePremiseVerifier.verify(api.getSystem(), compiled);
                try (Session premiseSession = Neo4jDriverManager.getInstance().openSession()) {
                    OclExecutionPremiseChecker.requireGraphScalarClosed(
                            premiseSession, model.name(), compiled.validationAlgebra());
                }
                String caseId = invariant.className + "::" + invariant.invName;
                OclVal47NonVacuityFixture.ExpectedCase expectedCase = expected.get(caseId);
                assertNotNull(expectedCase, "Missing reviewed expectation for " + caseId);
                ViolationOracleResult result = oracle.evaluate(
                        caseId, OclVal47NonVacuityFixture.contextIds(api, invariant.className),
                        api.getSystem(), invariant,
                        OclVal47NonVacuityFixture.expressionSource(testCase.ocl()),
                        compiled.cypher(), compiled.parameters());
                assertTrue(result.completed(), result::render);
                assertTrue(result.equivalent(),
                        () -> result.render() + "\n" + compiled.cypher());
                assertEquals(expectedCase.expectedViolations(), result.referenceViolationIds(),
                        caseId + " reviewed object-side IDs");
                assertEquals(expectedCase.expectedViolations(), result.cypherViolationIds(),
                        caseId + " real-Neo4j IDs");
                assertEquals(expectedCase.expectedClass(), result.vacuityStatus(), caseId);
                vacuity.merge(result.vacuityStatus(), 1, Integer::sum);
                if (Set.of("xor", "Set literal", "union", "intersection", "asSet", "isUnique")
                        .contains(testCase.feature())) {
                    newConstructs.add(testCase.feature());
                }
                compared++;
            }
            assertEquals(47, compared);
            assertEquals(47, expected.size());
            assertEquals(0, vacuity.getOrDefault(BenchmarkVacuityStatus.EMPTY_CONTEXT, 0));
            assertEquals(0, vacuity.getOrDefault(BenchmarkVacuityStatus.ALL_VIOLATE, 0));
            assertEquals(19, vacuity.getOrDefault(BenchmarkVacuityStatus.ALL_PASS, 0));
            assertEquals(28, vacuity.getOrDefault(BenchmarkVacuityStatus.NON_VACUOUS_MIXED, 0));
            assertEquals(Set.of("xor", "Set literal", "union", "intersection", "asSet", "isUnique"),
                    newConstructs);
            System.out.println("OCLVAL47_DIFFERENTIAL_ORACLE=PASS equivalent=" + compared
                    + " profile=" + CanonicalGraphEncoding.PROFILE_ID
                    + " vacuity=" + vacuity);
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

    private void connect(Neo4jEnvironmentConfig config) throws Exception {
        Neo4jDriverManager.connect(config.uri(), config.user(), config.password(), config.database(), false, false);
        SessionManager identity = new SessionManager();
        identity.createNewSession();
        Neo4jDriverManager.getInstance().setSessionManager(identity);
    }

    private void requireSelectedRuntimeProfile() {
        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            var components = session.run("CALL dbms.components() YIELD name, versions, edition "
                    + "WHERE size(versions) > 0 RETURN name, versions[0] AS version, edition").list();
            assertFalse(components.isEmpty());
            var kernel = components.stream()
                    .filter(record -> record.get("name").asString().contains("Neo4j Kernel"))
                    .findFirst().orElse(components.get(0));
            assertEquals(Cypher5ValAssumptionMatrix.NEO4J_KERNEL,
                    kernel.get("version").asString(), "OCL47 Neo4j Kernel evidence drift");
            assertEquals(Cypher5ValAssumptionMatrix.EDITION,
                    kernel.get("edition").asString().toLowerCase(Locale.ROOT),
                    "OCL47 Neo4j edition evidence drift");
            assertEquals(5L, session.run("CYPHER 5 RETURN 5 AS selectedVersion")
                    .single().get("selectedVersion").asLong());
        }
    }

    private void cleanup(String modelKey) {
        Neo4jDriverManager manager = Neo4jDriverManager.getInstance();
        if (manager == null || !manager.isConnected()) return;
        try (Session session = manager.openSession()) {
            session.run("MATCH (n {modelKey:$modelKey}) DETACH DELETE n", Map.of("modelKey", modelKey)).consume();
        }
    }
}
