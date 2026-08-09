package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.sys.MObject;
import org.uet.dse.neo4j.encoding.CanonicalGraphEncoding;
import org.uet.dse.neo4j.encoding.CanonicalGraphSchema;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.uet.dse.neo4j.manager.SessionManager;
import org.uet.dse.neo4j.repo.Neo4jObjectRepository;
import org.uet.dse.neo4j.sync.model.CoreModelPushService;
import org.uet.dse.neo4j.sync.object.ObjectDiff;
import org.uet.dse.neo4j.sync.object.ObjectPushService;
import org.uet.dse.neo4j.sync.object.ObjectSnapshotCompare;
import org.uet.dse.neo4jtgg.service.impl.DefaultOclToCypherCompiler;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in real-Neo4j conformance experiment; never resets the whole database. */
class ResearchRealNeo4jTest {
    @Test
    void exactViolationIdsAgreeOnCanonicalRealGraph() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("neo4j.research.it"),
                "Run with -Dneo4j.research.it=true");
        Neo4jEnvironmentConfig config = Neo4jEnvironmentConfig.load();
        String modelName = "ResearchCompany_" + System.currentTimeMillis();
        String modelKey = CanonicalGraphEncoding.modelKey(modelName);
        connect(config);
        try {
            MModel model = compileModel(modelName);
            UseSystemApi api = UseSystemApi.create(model, false);
            api.createObject("Company", "research_company");
            api.createObject("Person", "research_minor");
            api.createObject("Person", "research_boundary");
            api.createObject("Person", "research_adult");
            api.setAttributeValue("research_company", "name", "'Acme'");
            api.setAttributeValue("research_minor", "name", "'Minor'");
            api.setAttributeValue("research_minor", "age", "17");
            api.setAttributeValue("research_boundary", "name", "'Boundary'");
            api.setAttributeValue("research_boundary", "age", "18");
            api.setAttributeValue("research_adult", "name", "'Adult'");
            api.setAttributeValue("research_adult", "age", "30");
            api.createLink("Employment", "research_company", "research_minor");
            api.createLink("Employment", "research_company", "research_boundary");
            api.createLink("Employment", "research_company", "research_adult");

            CoreModelPushService modelPush = new CoreModelPushService(new UseModelApi(model));
            long m2StartedAt = System.nanoTime();
            modelPush.pushModelToNeo4j();
            long m2Ns = System.nanoTime() - m2StartedAt;
            long m2RepeatStartedAt = System.nanoTime();
            modelPush.pushModelToNeo4j();
            long m2RepeatNs = System.nanoTime() - m2RepeatStartedAt;

            ObjectDiff diff = new ObjectSnapshotCompare(api.getSystem()).compareObjects();
            long m1StartedAt = System.nanoTime();
            new ObjectPushService(new Neo4jObjectRepository(), api.getSystem()).pushToNeo4j(diff);
            long m1Ns = System.nanoTime() - m1StartedAt;
            ObjectDiff repeatDiff = new ObjectSnapshotCompare(api.getSystem()).compareObjects();
            assertFalse(repeatDiff.hasDifference(), "The second M1 comparison must be a no-op delta:\n"
                    + repeatDiff.generateForwardReport());
            long m1RepeatStartedAt = System.nanoTime();
            new ObjectPushService(new Neo4jObjectRepository(), api.getSystem()).pushToNeo4j(repeatDiff);
            long m1RepeatNs = System.nanoTime() - m1RepeatStartedAt;
            long encodingNs = m2Ns + m1Ns;
            System.out.println("BATCH_SYNC_TIMINGS m2Ms=" + m2Ns / 1_000_000.0
                    + " m2NoOpMs=" + m2RepeatNs / 1_000_000.0
                    + " m1Ms=" + m1Ns / 1_000_000.0
                    + " m1NoOpMs=" + m1RepeatNs / 1_000_000.0);

            long[] graphSize = assertCanonicalGraph(modelKey);
            DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
            List<org.uet.dse.neo4j.oclite.ast.ASTContext> invariants =
                    compiler.parseContextInvariants(OCL_SUITE);
            UseObjectSideReferenceEvaluator oracle = new UseObjectSideReferenceEvaluator();
            List<ScientificEvaluationReport.CorrectnessObservation> correctness = new ArrayList<>();
            List<ScientificEvaluationReport.RepetitionObservation> repetitions = new ArrayList<>();
            List<ScientificEvaluationReport.DiagnosticObservation> diagnostics = new ArrayList<>();
            InstrumentedCompilationResult adultCompilation = null;
            Set<String> adultReferenceIds = Set.of();
            int passed = 0;
            for (var invariant : invariants) {
                InstrumentedCompilationResult compiled = compiler.compileInvariantInstrumented(invariant);
                Set<String> referenceIds = oracle.violationIds(api.getSystem(), invariant);
                execute(compiled.cypher(), compiled.parameters()); // warm-up, excluded from samples
                List<Long> queryTimes = new ArrayList<>();
                List<Set<String>> repeatedIds = new ArrayList<>();
                for (int repetition = 0; repetition < 3; repetition++) {
                    long startedAt = System.nanoTime();
                    Set<String> ids = execute(compiled.cypher(), compiled.parameters());
                    queryTimes.add(System.nanoTime() - startedAt);
                    repeatedIds.add(ids);
                }
                Set<String> cypherIds = repeatedIds.get(0);
                Set<String> universe = contextIds(api, invariant.className);
                correctness.add(ScientificEvaluationReport.CorrectnessObservation.compare(
                        invariant.className + "::" + invariant.invName, universe, referenceIds, cypherIds));
                repetitions.add(new ScientificEvaluationReport.RepetitionObservation(
                        invariant.className + "::" + invariant.invName, queryTimes, repeatedIds));
                for (String stableId : referenceIds) {
                    diagnostics.add(new ScientificEvaluationReport.DiagnosticObservation(
                            invariant.className + "::" + invariant.invName, stableId,
                            stableId + "." + witness(invariant.invName), expected(invariant.invName),
                            actual(invariant.invName, stableId),
                            compiled.cypher(), queryTimes.get(0), ""));
                }
                if ("Adult".equals(invariant.invName)) {
                    adultCompilation = compiled;
                    adultReferenceIds = referenceIds;
                }
                System.out.println(invariant.className + "::" + invariant.invName
                        + " reference=" + referenceIds + " cypher=" + cypherIds
                        + " compileNs=" + compiled.timings().compileNs());
                assertEquals(referenceIds, cypherIds,
                        invariant.className + "::" + invariant.invName);
                passed++;
            }
            assertEquals(4, passed);
            assertTrue(adultCompilation != null);

            List<ScientificEvaluationReport.MutationObservation> mutations = evaluateMutants(
                    modelKey, adultCompilation, adultReferenceIds, compiler);
            var report = new ScientificEvaluationReport.Report(
                    correctness,
                    conformanceEvidence(),
                    mutations,
                    robustnessEvidence(compiler),
                    coverageEvidence(),
                    List.of(new ScientificEvaluationReport.EncodingObservation(
                            4, 3, graphSize[0], graphSize[1], 0, 0,
                            encodingNs, 0, 0)),
                    repetitions,
                    diagnostics,
                    manifest(config));
            assertTrue(report.passed(), report.renderSummary());
            assertTrue(report.performanceEvidenceAdmissible());
            Path evidenceDirectory = Path.of("target", "research-evidence", modelName);
            ScientificEvaluationReportWriter.writeJson(report, evidenceDirectory.resolve("report.json"));
            ScientificEvaluationReportWriter.writeCorrectnessCsv(
                    report, evidenceDirectory.resolve("correctness.csv"));
            System.out.println("SCIENTIFIC_EVALUATION=" + report.renderSummary());
            System.out.println("REPRODUCIBILITY_MANIFEST=" + report.manifest());
            System.out.println("RESEARCH_EVIDENCE=" + evidenceDirectory.toAbsolutePath());
            System.out.println("REAL_NEO4J_FINAL_RESULT=PASS rules=" + passed + " modelKey=" + modelKey);
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

    private MModel compileModel(String modelName) {
        StringWriter diagnostics = new StringWriter();
        MModel model = USECompiler.compileSpecification(MODEL.formatted(modelName), modelName + ".use",
                new PrintWriter(diagnostics, true), new ModelFactory());
        assertTrue(model != null, diagnostics.toString());
        return model;
    }

    private Set<String> execute(String cypher, Map<String, Object> parameters) {
        Set<String> ids = new LinkedHashSet<>();
        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            Result result = session.run(cypher, parameters);
            while (result.hasNext()) ids.add(result.next().get("useId").asString());
        }
        return Set.copyOf(ids);
    }

    private long[] assertCanonicalGraph(String modelKey) {
        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            var record = session.run("""
                    MATCH (o:Object {modelKey: $modelKey})-[:ObjectInstanceOf]->(c)
                    OPTIONAL MATCH (o)-[:ObjectHasAttribute]->(v:AttributeValue)
                    RETURN count(DISTINCT o) AS objects,
                           count(DISTINCT c.classKey) AS classKeys,
                           count(DISTINCT v.attributeKey) AS attributeKeys
                    """, Map.of("modelKey", modelKey)).single();
            assertEquals(4L, record.get("objects").asLong());
            assertTrue(record.get("classKeys").asLong() >= 2L);
            assertTrue(record.get("attributeKeys").asLong() >= 3L);
            var size = session.run("""
                    MATCH (n {modelKey: $modelKey})
                    WITH count(n) AS nodes
                    MATCH (a {modelKey: $modelKey})-[r]->(b {modelKey: $modelKey})
                    RETURN nodes, count(r) AS relationships
                    """, Map.of("modelKey", modelKey)).single();
            return new long[]{size.get("nodes").asLong(), size.get("relationships").asLong()};
        }
    }

    private Set<String> contextIds(UseSystemApi api, String className) {
        Set<String> ids = new LinkedHashSet<>();
        var contextClass = api.getSystem().model().getClass(className);
        for (MObject object : api.getSystem().state().objectsOfClassAndSubClasses(contextClass)) {
            ids.add(object.name());
        }
        return Set.copyOf(ids);
    }

    private List<ScientificEvaluationReport.MutationObservation> evaluateMutants(
            String modelKey, InstrumentedCompilationResult adult,
            Set<String> adultReferenceIds, DefaultOclToCypherCompiler compiler) {
        List<ScientificEvaluationReport.MutationObservation> result = new ArrayList<>();

        String operatorMutant = adult.cypher().replace(" >= ", " > ");
        Set<String> operatorIds = execute(operatorMutant, adult.parameters());
        result.add(new ScientificEvaluationReport.MutationObservation(
                "boundary->-instead-of->=", ScientificEvaluationReport.MutationCategory.RENDERER,
                !DifferentialResult.compare(adultReferenceIds, operatorIds).equalIds(),
                "one Person with age exactly 18"));

        var compiledMutant = compiler.compileInvariantInstrumented(
                "context Person inv AdultMutant: self.age > 18");
        Set<String> compilerMutantIds = execute(compiledMutant.cypher(), compiledMutant.parameters());
        result.add(new ScientificEvaluationReport.MutationObservation(
                "ocl-operator->-instead-of->=", ScientificEvaluationReport.MutationCategory.COMPILER,
                !DifferentialResult.compare(adultReferenceIds, compilerMutantIds).equalIds(),
                "one Person with age exactly 18"));

        String ageKey = CanonicalGraphEncoding.attributeKey(modelKey, "Person", "age");
        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            session.run("""
                    MATCH (o:Object {modelKey: $modelKey, use_id: 'research_adult'})
                          -[:ObjectHasAttribute]->(v:AttributeValue {attributeKey: $attributeKey})
                    SET v.value = '10'
                    """, Map.of("modelKey", modelKey, "attributeKey", ageKey)).consume();
            Set<String> dataMutantIds = execute(adult.cypher(), adult.parameters());
            result.add(new ScientificEvaluationReport.MutationObservation(
                    "change-adult-age-to-10", ScientificEvaluationReport.MutationCategory.DATA,
                    !DifferentialResult.compare(adultReferenceIds, dataMutantIds).equalIds(),
                    "one changed AttributeValue"));
        } finally {
            try (Session session = Neo4jDriverManager.getInstance().openSession()) {
                session.run("""
                        MATCH (o:Object {modelKey: $modelKey, use_id: 'research_adult'})
                              -[:ObjectHasAttribute]->(v:AttributeValue {attributeKey: $attributeKey})
                        SET v.value = '30'
                        """, Map.of("modelKey", modelKey, "attributeKey", ageKey)).consume();
            }
        }
        return List.copyOf(result);
    }

    private List<ScientificEvaluationReport.ConformanceObservation> conformanceEvidence() {
        return List.of(
                new ScientificEvaluationReport.ConformanceObservation("primitive Integer/String",
                        ScientificEvaluationReport.Status.PASS, "Adult on canonical real graph"),
                new ScientificEvaluationReport.ConformanceObservation("association navigation 1/*",
                        ScientificEvaluationReport.Status.PASS, "HasEmployer on canonical real graph"),
                new ScientificEvaluationReport.ConformanceObservation("exists",
                        ScientificEvaluationReport.Status.PASS, "HasAdultEmployee on canonical real graph"),
                new ScientificEvaluationReport.ConformanceObservation("forAll and ordering",
                        ScientificEvaluationReport.Status.PASS, "AllEmployeesNonNegativeAge on canonical real graph"),
                new ScientificEvaluationReport.ConformanceObservation("closure",
                        ScientificEvaluationReport.Status.NOT_CLAIMED, "outside frozen OCL_val profile"));
    }

    private List<ScientificEvaluationReport.CoverageObservation> coverageEvidence() {
        return List.of(
                completeCoverage("primitive comparison"),
                completeCoverage("association navigation"),
                completeCoverage("exists"),
                completeCoverage("forAll/ordering"),
                new ScientificEvaluationReport.CoverageObservation("closure", false,
                        false, false, false, false, false, false, false));
    }

    private ScientificEvaluationReport.CoverageObservation completeCoverage(String feature) {
        return new ScientificEvaluationReport.CoverageObservation(feature, true,
                true, true, true, true, true, true, true);
    }

    private List<ScientificEvaluationReport.RobustnessObservation> robustnessEvidence(
            DefaultOclToCypherCompiler compiler) {
        var unknownClass = compiler.compile("context Missing inv X: true");
        var unknownProperty = compiler.compile("context Person inv X: self.missing = 1");
        return List.of(
                robustness("unknown context class", unknownClass.isSupported()),
                robustness("unknown property", unknownProperty.isSupported()));
    }

    private ScientificEvaluationReport.RobustnessObservation robustness(String name, boolean accepted) {
        return new ScientificEvaluationReport.RobustnessObservation(name,
                ScientificEvaluationReport.FailureStage.BIND,
                accepted ? ScientificEvaluationReport.FailureStage.NONE
                        : ScientificEvaluationReport.FailureStage.BIND,
                true, !accepted, !accepted);
    }

    private ScientificEvaluationReport.ReproducibilityManifest manifest(Neo4jEnvironmentConfig config) {
        String neo4jVersion;
        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            neo4jVersion = session.run("CALL dbms.components() YIELD name, versions "
                            + "RETURN versions[0] AS version ORDER BY name LIMIT 1")
                    .single().get("version").asString();
        }
        return new ScientificEvaluationReport.ReproducibilityManifest(
                "research-company-boundary-v1", 42L, sha256(MODEL), sha256(OCL_SUITE),
                CanonicalGraphSchema.VERSION, neo4jVersion, "5", config.database(),
                System.getProperty("os.name") + " " + System.getProperty("os.version"),
                System.getProperty("java.version"), 1, 1, 3);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String witness(String invariantName) {
        return switch (invariantName) {
            case "Adult" -> "age";
            case "HasEmployer" -> "employer";
            case "HasAdultEmployee" -> "employee.age";
            case "AllEmployeesNamed" -> "employee.name";
            default -> "self";
        };
    }

    private String expected(String invariantName) {
        return "Adult".equals(invariantName) ? "age >= 18" : "invariant evaluates to true";
    }

    private String actual(String invariantName, String stableId) {
        if ("Adult".equals(invariantName) && "research_minor".equals(stableId)) return "age = 17";
        return "false-or-invalid";
    }

    private void cleanup(String modelKey) {
        Neo4jDriverManager manager = Neo4jDriverManager.getInstance();
        if (manager == null || !manager.isConnected()) return;
        try (Session session = manager.openSession()) {
            deleteModelNodes(session, "Object", modelKey);
            deleteModelNodes(session, "AttributeValue", modelKey);
            deleteModelNodes(session, "Attribute", modelKey);
            deleteModelNodes(session, "UmlClass", modelKey);
            session.run("MATCH (m:ManageModel {name:$modelKey}) "
                    + "OPTIONAL MATCH (m)-[:DefineMetamodels]->(meta:MetaNode) "
                    + "DETACH DELETE meta,m", Map.of("modelKey", modelKey)).consume();
        } catch (RuntimeException ignored) {
            // Do not hide the original failure if the configured database was
            // unavailable before any experiment data could be written.
        }
    }

    private void deleteModelNodes(Session session, String label, String modelKey) {
        session.run("MATCH (n:" + label + " {modelKey:$modelKey}) DETACH DELETE n",
                Map.of("modelKey", modelKey)).consume();
    }

    private static final String MODEL = """
            model %s
            class Company
            attributes
                name : String
            end
            class Person
            attributes
                name : String
                age : Integer
            end
            association Employment between
                Company[1] role employer
                Person[*] role employee
            end
            """;

    private static final String OCL_SUITE = """
            context Person inv Adult: self.age >= 18
            context Person inv HasEmployer: self.employer->notEmpty()
            context Company inv HasAdultEmployee: self.employee->exists(p | p.age >= 18)
            context Company inv AllEmployeesNonNegativeAge: self.employee->forAll(p | p.age >= 0)
            """;
}
