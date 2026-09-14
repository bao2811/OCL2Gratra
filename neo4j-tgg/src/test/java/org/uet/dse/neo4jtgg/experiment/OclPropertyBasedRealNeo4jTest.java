package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
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
import org.uet.dse.neo4jtgg.ocl.OclExecutionPremiseChecker;
import org.uet.dse.neo4jtgg.service.impl.DefaultOclToCypherCompiler;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Seed/replay differential properties over USE and a configured Neo4j matrix
 * member. This test is opt-in because it mutates a real database fixture.
 */
class OclPropertyBasedRealNeo4jTest {
    @Test
    void generatedCertifiedPropertiesAgreeBetweenUseAndNeo4j() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("neo4j.property.it"),
                "Run with -Dneo4j.property.it=true and configured Neo4j");
        long seed = Long.getLong("ocl.property.seed", OclPropertyCaseGenerator.DEFAULT_SEED);
        int count = Integer.getInteger("ocl.property.runtime.cases",
                OclPropertyCaseGenerator.DEFAULT_RUNTIME_CASES);
        Neo4jEnvironmentConfig config = Neo4jEnvironmentConfig.load();
        String modelName = "OclProperty_" + seed + "_" + System.currentTimeMillis();
        String modelKey = CanonicalGraphEncoding.modelKey(modelName);
        connect(config);
        try {
            RuntimeProfile profile = requireRuntimeProfile();
            var model = OclVal47NonVacuityFixture.compileModel(modelName);
            UseSystemApi api = OclVal47NonVacuityFixture.seed(model);
            new CoreModelPushService(new UseModelApi(model)).pushModelToNeo4j();
            ObjectDiff diff = new ObjectSnapshotCompare(api.getSystem()).compareObjects();
            new ObjectPushService(new Neo4jObjectRepository(), api.getSystem()).pushToNeo4j(diff);

            DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
            ViolationSetOracle oracle = new ViolationSetOracle(
                    new UseObjectSideReferenceEvaluator(), this::execute);
            int compared = 0;
            for (OclPropertyCaseGenerator.PropertyCase property
                    : OclPropertyCaseGenerator.generate(seed, count)) {
                var invariant = compiler.parseContextInvariants(property.invariant()).get(0);
                var compiled = compiler.compileInvariantInstrumented(invariant);
                FixturePremiseVerifier.verify(api.getSystem(), compiled);
                OclExecutionPremiseChecker.requireUseSystemScalarClosed(
                        api.getSystem(), compiled.validationAlgebra());
                try (Session session = Neo4jDriverManager.getInstance().openSession()) {
                    OclExecutionPremiseChecker.requireGraphScalarClosed(
                            session, model.name(), compiled.validationAlgebra());
                }
                ViolationOracleResult result = oracle.evaluate(
                        property.context() + "::" + property.id(),
                        OclVal47NonVacuityFixture.contextIds(api, property.context()),
                        api.getSystem(), invariant, property.expression(),
                        compiled.cypher(), compiled.parameters());
                assertTrue(result.completed(), () -> property.replay() + "\n" + result.render());
                assertTrue(result.equivalent(), () -> property.replay() + "\n" + result.render()
                        + "\n" + compiled.cypher() + "\n" + compiled.parameters());
                compared++;
            }
            assertEquals(count, compared);
            System.out.println("OCL_PROPERTY_MATRIX=PASS seed=" + seed + " cases=" + count
                    + " server=" + profile.kernel() + " edition=" + profile.edition()
                    + " database=" + config.database());
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

    private RuntimeProfile requireRuntimeProfile() {
        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            var components = session.run("CALL dbms.components() YIELD name, versions, edition "
                    + "WHERE size(versions) > 0 RETURN name, versions[0] AS version, edition").list();
            assertFalse(components.isEmpty());
            var kernel = components.stream()
                    .filter(record -> record.get("name").asString().contains("Neo4j Kernel"))
                    .findFirst().orElse(components.get(0));
            String version = kernel.get("version").asString();
            String edition = kernel.get("edition").asString().toLowerCase(Locale.ROOT);
            String expectedVersion = System.getProperty("neo4j.matrix.expectedKernel", "").trim();
            String expectedEdition = System.getProperty("neo4j.matrix.expectedEdition", "").trim();
            if (!expectedVersion.isEmpty()) assertEquals(expectedVersion, version, "matrix kernel drift");
            if (!expectedEdition.isEmpty())
                assertEquals(expectedEdition.toLowerCase(Locale.ROOT), edition, "matrix edition drift");
            assertEquals(5L, session.run("CYPHER 5 RETURN 5 AS selectedVersion")
                    .single().get("selectedVersion").asLong());
            return new RuntimeProfile(version, edition);
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
            session.run("MATCH (n {modelKey:$modelKey}) DETACH DELETE n",
                    Map.of("modelKey", modelKey)).consume();
        }
    }

    private record RuntimeProfile(String kernel, String edition) { }
}
