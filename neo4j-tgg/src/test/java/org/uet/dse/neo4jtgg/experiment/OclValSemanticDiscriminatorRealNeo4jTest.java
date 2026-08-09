package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Session;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-backend discriminator for semantic boundaries that ordinary feature
 * coverage does not expose: total bottom equality, set-image collect, and the
 * present/absent singleton Set view of scalar to-one navigation.
 */
class OclValSemanticDiscriminatorRealNeo4jTest {
    @Test
    void duplicateCollectFixtureDistinguishesRawBagFromCanonicalSetImage() throws Exception {
        MModel model = compileModel("OfflineOclValDiscriminator");
        UseSystemApi api = seed(model);
        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        var invariant = compiler.parseContextInvariants(
                "context Company inv CollectUsesSetImage: "
                        + "self.employee->collect(e | e.age)->size() = 1").get(0);
        compiler.compileInvariantInstrumented(invariant);
        UseObjectSideReferenceEvaluator use = new UseObjectSideReferenceEvaluator();

        Set<String> rawBagViolations = use.violationIds(api.getSystem(), invariant,
                "self.employee->collect(e | e.age)->size() = 1");
        Set<String> canonicalSetViolations = use.violationIds(api.getSystem(), invariant,
                "self.employee->collect(e | e.age)->asSet()->size() = 1");

        assertEquals(Set.of("companyDuplicate", "companyDistinct"), rawBagViolations);
        assertEquals(Set.of("companyDistinct"), canonicalSetViolations);
    }

    @Test
    void nullAndDuplicateCollectFollowTheCanonicalProfile() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("neo4j.oclval.discriminator.it"),
                "Run with -Dneo4j.oclval.discriminator.it=true and configured Neo4j");
        Neo4jEnvironmentConfig config = Neo4jEnvironmentConfig.load();
        String modelName = "OclValDiscriminator_" + System.currentTimeMillis();
        MModel model = compileModel(modelName);
        UseSystemApi api = seed(model);
        String modelKey = CanonicalGraphEncoding.modelKey(modelName);
        connect(config);
        cleanup(modelKey);
        try {
            new CoreModelPushService(new UseModelApi(model)).pushModelToNeo4j();
            ObjectDiff diff = new ObjectSnapshotCompare(api.getSystem()).compareObjects();
            new ObjectPushService(new Neo4jObjectRepository(), api.getSystem()).pushToNeo4j(diff);
            RepresentationAdequacyEvaluator.Report representation = RepresentationAdequacyEvaluator.evaluate(
                    RepresentationEvaluationRealNeo4jTest.sourceSnapshot(api.getSystem(), modelName),
                    RepresentationEvaluationRealNeo4jTest.graphSnapshot(modelKey));
            assertTrue(representation.passed(), representation::render);

            DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
            var invariants = compiler.parseContextInvariants(OCL_SUITE);
            assertEquals(17, invariants.size());
            ViolationSetOracle oracle = new ViolationSetOracle(
                    new UseObjectSideReferenceEvaluator(), this::execute);
            Map<String, String> referenceExpressions = Map.ofEntries(
                    Map.entry("NullEqualsNull", "null = null"),
                    Map.entry("DefinedValueDiffersFromNull", "'defined' <> null"),
                    Map.entry("VoidEqSelf", "self = null"),
                    Map.entry("VoidEqSet", "Set{1} <> null"),
                    Map.entry("VoidIfInteger", "(if true then null else 1 endif) = null"),
                    Map.entry("VoidIfString", "(if false then 'defined' else null endif) <> null"),
                    Map.entry("VoidIfEntity", "(if true then null else self endif) = null"),
                    Map.entry("VoidIfSet", "(if true then null else Set{1} endif) = null"),
                    Map.entry("VoidIfBoth", "(if true then null else null endif) = null"),
                    Map.entry("VoidSetInteger", "Set{null,1}->includes(null)"),
                    Map.entry("VoidSetString", "Set{'defined',null}->includes(null)"),
                    Map.entry("VoidSetEntity", "Set{null,self}->includes(null)"),
                    Map.entry("VoidLet", "let x = null in x = null"),
                    Map.entry("CollectUsesSetImage",
                            "self.employee->collect(e | e.age)->asSet()->size() = 1"),
                    Map.entry("ToOneAsSet", "self.employer->asSet()->size() = 1"),
                    Map.entry("ToOneSelect", "self.employer->asSet()->select(c | c.name <> '')->size() = 1"),
                    Map.entry("ToOneLetSize", "let n = self.employer->size() in n = 1"));
            Map<String, Set<String>> expected = Map.ofEntries(
                    Map.entry("NullEqualsNull", Set.of()),
                    Map.entry("DefinedValueDiffersFromNull", Set.of()),
                    Map.entry("VoidEqSelf", Set.of("companyDuplicate", "companyDistinct")),
                    Map.entry("VoidEqSet", Set.of()),
                    Map.entry("VoidIfInteger", Set.of()),
                    Map.entry("VoidIfString", Set.of("companyDuplicate", "companyDistinct")),
                    Map.entry("VoidIfEntity", Set.of()),
                    Map.entry("VoidIfSet", Set.of()),
                    Map.entry("VoidIfBoth", Set.of()),
                    Map.entry("VoidSetInteger", Set.of()),
                    Map.entry("VoidSetString", Set.of()),
                    Map.entry("VoidSetEntity", Set.of()),
                    Map.entry("VoidLet", Set.of()),
                    Map.entry("CollectUsesSetImage", Set.of("companyDistinct")),
                    Map.entry("ToOneAsSet", Set.of("unemployed")),
                    Map.entry("ToOneSelect", Set.of("unemployed")),
                    Map.entry("ToOneLetSize", Set.of("unemployed")));

            for (var invariant : invariants) {
                var compiled = compiler.compileInvariantInstrumented(invariant);
                FixturePremiseVerifier.verify(api.getSystem(), compiled);
                try (Session session = Neo4jDriverManager.getInstance().openSession()) {
                    OclExecutionPremiseChecker.requireGraphScalarClosed(
                            session, modelName, compiled.validationAlgebra());
                }
                ViolationOracleResult result = oracle.evaluate(
                        invariant.className + "::" + invariant.invName,
                        invariant.className.equals("Company")
                                ? Set.of("companyDuplicate", "companyDistinct")
                                : Set.of("duplicateA", "duplicateB", "distinctA", "distinctB", "unemployed"),
                        api.getSystem(), invariant, referenceExpressions.get(invariant.invName),
                        compiled.cypher(), compiled.parameters());
                assertTrue(result.completed(), result::render);
                assertTrue(result.equivalent(), () -> result.render() + "\n" + compiled.cypher());
                assertEquals(expected.get(invariant.invName), result.referenceViolationIds(), invariant.invName);
                assertEquals(expected.get(invariant.invName), result.cypherViolationIds(), invariant.invName);
            }
            System.out.println("OCLVAL_SEMANTIC_DISCRIMINATORS=PASS cases=17 voidContexts=13 "
                    + "duplicateCollect=1 toOneCollectionView=3");
        } finally {
            cleanup(modelKey);
            Neo4jDriverManager manager = Neo4jDriverManager.getInstance();
            if (manager != null) manager.close();
        }
    }

    private MModel compileModel(String modelName) {
        String specification = """
                model %s
                class Company
                attributes
                    name : String
                end
                class Person
                attributes
                    age : Integer
                end
                association Employment between
                    Company[0..1] role employer
                    Person[*] role employee
                end
                """.formatted(modelName);
        StringWriter diagnostics = new StringWriter();
        MModel model = USECompiler.compileSpecification(specification, modelName + ".use",
                new PrintWriter(diagnostics, true), new ModelFactory());
        assertNotNull(model, diagnostics.toString());
        return model;
    }

    private UseSystemApi seed(MModel model) throws Exception {
        UseSystemApi api = UseSystemApi.create(model, false);
        createCompany(api, "companyDuplicate");
        createCompany(api, "companyDistinct");
        createPerson(api, "duplicateA", 20, "companyDuplicate");
        createPerson(api, "duplicateB", 20, "companyDuplicate");
        createPerson(api, "distinctA", 20, "companyDistinct");
        createPerson(api, "distinctB", 21, "companyDistinct");
        createPerson(api, "unemployed", 19, null);
        return api;
    }

    private void createCompany(UseSystemApi api, String id) throws Exception {
        api.createObject("Company", id);
        api.setAttributeValue(id, "name", "'" + id + "'");
    }

    private void createPerson(UseSystemApi api, String id, int age, String employer) throws Exception {
        api.createObject("Person", id);
        api.setAttributeValue(id, "age", Integer.toString(age));
        if (employer != null) api.createLink("Employment", employer, id);
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

    private static final String OCL_SUITE = """
             context Company inv NullEqualsNull: null = null
             context Company inv DefinedValueDiffersFromNull: 'defined' <> null
             context Company inv VoidEqSelf: self = null
             context Company inv VoidEqSet: Set{1} <> null
             context Company inv VoidIfInteger: (if true then null else 1 endif) = null
             context Company inv VoidIfString: (if false then 'defined' else null endif) <> null
             context Company inv VoidIfEntity: (if true then null else self endif) = null
             context Company inv VoidIfSet: (if true then null else Set{1} endif) = null
             context Company inv VoidIfBoth: (if true then null else null endif) = null
             context Company inv VoidSetInteger: Set{null,1}->includes(null)
             context Company inv VoidSetString: Set{'defined',null}->includes(null)
             context Company inv VoidSetEntity: Set{null,self}->includes(null)
             context Company inv VoidLet: let x = null in x = null
             context Company inv CollectUsesSetImage: self.employee->collect(e | e.age)->size() = 1
            context Person inv ToOneAsSet: self.employer->asSet()->size() = 1
            context Person inv ToOneSelect: self.employer->asSet()->select(c | c.name <> '')->size() = 1
            context Person inv ToOneLetSize: let n = self.employer->size() in n = 1
            """;
}
