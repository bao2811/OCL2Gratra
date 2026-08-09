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
import org.uet.dse.neo4j.sync.model.CoreModelPushService;
import org.uet.dse.neo4jtgg.service.impl.DefaultOclToCypherCompiler;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Opt-in correctness-at-scale experiment on a real Neo4j database. */
class ResearchScaleRealNeo4jTest {
    private static final int CLEANUP_BATCH = 5_000;

    @Test
    void exactIdsAgreeForLargeCanonicalDataset() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("neo4j.scale.it"),
                "Run with -Dneo4j.scale.it=true");
        int employeesPerCompany = Integer.getInteger("neo4j.scale.employees", 20);
        int batchSize = Integer.getInteger("neo4j.scale.batch", CanonicalBatchInsertExecutor.DEFAULT_BATCH_SIZE);
        int warmups = Integer.getInteger("neo4j.scale.warmups", 1);
        int samples = Integer.getInteger("neo4j.scale.samples", 5);
        List<Integer> scalePoints = scalePoints();
        Neo4jEnvironmentConfig config = Neo4jEnvironmentConfig.load();
        connect(config);
        cleanupStaleScaleModels();
        if (Boolean.getBoolean("neo4j.scale.cleanupOnly")) {
            Neo4jDriverManager.getInstance().close();
            System.out.println("SCALE_CLEANUP_RESULT=PASS");
            return;
        }
        try {
            System.out.println("SCALE_COMPARISON_HEADER=objects,companies,employees,warmups,samples,"
                    + "useMedianMs,useP95Ms,cypherMedianMs,cypherP95Ms,agreements");
            for (int companies : scalePoints) {
                runScale(companies, employeesPerCompany, batchSize, warmups, samples);
            }
        } finally {
            Neo4jDriverManager manager = Neo4jDriverManager.getInstance();
            if (manager != null) manager.close();
        }
    }

    private void runScale(int companies, int employeesPerCompany, int batchSize,
                          int warmups, int samples) throws Exception {
        int persons = companies * employeesPerCompany;
        int objects = companies + persons;
        String modelName = "ResearchScale_" + objects + "_" + System.currentTimeMillis();
        String modelKey = CanonicalGraphEncoding.modelKey(modelName);
        long totalStart = System.nanoTime();
        try {
            MModel model = compileModel(modelName);
            long referenceBuildStart = System.nanoTime();
            UseSystemApi reference = buildReference(model, companies, employeesPerCompany);
            long referenceBuildNs = System.nanoTime() - referenceBuildStart;

            long modelPushStart = System.nanoTime();
            new CoreModelPushService(new UseModelApi(model)).pushModelToNeo4j();
            long modelPushNs = System.nanoTime() - modelPushStart;
            List<CanonicalBatchInsertExecutor.StageTiming> loadTimings =
                    bulkLoad(modelName, companies, employeesPerCompany, batchSize);
            long graphLoadNs = modelPushNs + CanonicalBatchInsertExecutor.totalNs(loadTimings);
            assertGraphCounts(modelKey, objects, persons + companies);

            DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
            List<org.uet.dse.neo4j.oclite.ast.ASTContext> invariants =
                    compiler.parseContextInvariants(OCL_SUITE);
            assertEquals(10, invariants.size(), "Both baselines are defined for exactly ten invariants");
            UseObjectSideReferenceEvaluator useOracle = new UseObjectSideReferenceEvaluator();
            GraphEvaluationMetrics.LatencyCollector latency = new GraphEvaluationMetrics.LatencyCollector();
            List<Long> allUseNs = new ArrayList<>();
            List<Long> allCypherNs = new ArrayList<>();
            long compileNs = 0;
            int passed = 0;

            for (var invariant : invariants) {
                InstrumentedCompilationResult compiled = compiler.compileInvariantInstrumented(invariant);
                compileNs += compiled.timings().compileNs();
                latency.add("validation-compile", compiled.timings().compileNs());
                Set<String> generatedIds = expectedIds(invariant.invName, companies, employeesPerCompany);

                for (int i = 0; i < warmups; i++) {
                    assertEquals(generatedIds, useOracle.violationIds(reference.getSystem(), invariant));
                    assertEquals(generatedIds, execute(compiled.cypher(), compiled.parameters()));
                }

                Set<String> useIds = Set.of();
                Set<String> cypherIds = Set.of();
                List<Long> ruleUseNs = new ArrayList<>();
                List<Long> ruleCypherNs = new ArrayList<>();
                for (int sample = 0; sample < samples; sample++) {
                    long useStart = System.nanoTime();
                    useIds = useOracle.violationIds(reference.getSystem(), invariant);
                    long useNs = System.nanoTime() - useStart;
                    ruleUseNs.add(useNs);
                    allUseNs.add(useNs);

                    long queryStart = System.nanoTime();
                    cypherIds = execute(compiled.cypher(), compiled.parameters());
                    long queryNs = System.nanoTime() - queryStart;
                    ruleCypherNs.add(queryNs);
                    allCypherNs.add(queryNs);
                    latency.add("validation-query", queryNs);

                    assertEquals(generatedIds, useIds,
                            "Generator/USE disagreement for " + invariant.invName);
                    assertEquals(useIds, cypherIds,
                            invariant.className + "::" + invariant.invName + " sample " + sample);
                }
                passed++;
                System.out.println("SCALE_RULE=" + objects + "," + invariant.className + "::"
                        + invariant.invName + ",violations=" + useIds.size()
                        + ",useMedianMs=" + millis(percentile(ruleUseNs, 0.50))
                        + ",cypherMedianMs=" + millis(percentile(ruleCypherNs, 0.50)));
            }

            GraphEvaluationMetrics metrics = collectScaleMetrics(modelKey, objects, persons,
                    companies, graphLoadNs, latency);
            System.out.println("SCALE_METRICS_HEADER=" + GraphEvaluationMetrics.csvHeader());
            System.out.println("SCALE_METRICS=" + metrics.toCsvRow(
                    objects + "-objects", CanonicalGraphEncoding.PROFILE_ID));
            System.out.println("SCALE_COMPARISON=" + objects + "," + companies + ","
                    + employeesPerCompany + "," + warmups + "," + samples + ","
                    + millis(percentile(allUseNs, 0.50)) + "," + millis(percentile(allUseNs, 0.95)) + ","
                    + millis(percentile(allCypherNs, 0.50)) + "," + millis(percentile(allCypherNs, 0.95)) + ","
                    + passed);
            System.out.println("SCALE_FINAL_RESULT=PASS rules=" + passed
                    + " objects=" + objects + " relationships=" + (persons + companies)
                    + " batchSize=" + batchSize
                    + " oracle=USE+deterministic-generator"
                    + " referenceBuildMs=" + millis(referenceBuildNs)
                    + " modelPushMs=" + millis(modelPushNs)
                    + " graphLoadMs=" + millis(graphLoadNs)
                    + " compileMs=" + millis(compileNs)
                    + " totalMs=" + millis(System.nanoTime() - totalStart));
        } finally {
            cleanup(modelKey);
        }
    }

    private List<Integer> scalePoints() {
        String configured = System.getProperty("neo4j.scale.points", "50,250,500");
        List<Integer> points = new ArrayList<>();
        for (String item : configured.split(",")) {
            int point = Integer.parseInt(item.trim());
            if (point <= 0) throw new IllegalArgumentException("Scale points must be positive: " + configured);
            points.add(point);
        }
        if (points.isEmpty()) throw new IllegalArgumentException("At least one scale point is required");
        return List.copyOf(points);
    }

    private long percentile(List<Long> samples, double quantile) {
        if (samples.isEmpty()) throw new IllegalArgumentException("No latency samples");
        List<Long> sorted = samples.stream().sorted().toList();
        int index = (int) Math.ceil(quantile * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private Set<String> expectedIds(String invariantName, int companies, int employeesPerCompany) {
        Set<String> ids = new LinkedHashSet<>();
        if ("HasSenior".equals(invariantName) || "SeniorSelect".equals(invariantName)) {
            for (int company = 1; company <= companies; company++) {
                boolean found = false;
                int first = (company - 1) * employeesPerCompany + 1;
                for (int offset = 0; offset < employeesPerCompany; offset++) {
                    if (18 + ((first + offset) % 45) > 50) { found = true; break; }
                }
                if (!found) ids.add("scale_company_" + company);
            }
        } else if ("HighSalaryExists".equals(invariantName)) {
            for (int company = 1; company <= companies; company++) {
                boolean found = false;
                int first = (company - 1) * employeesPerCompany + 1;
                for (int offset = 0; offset < employeesPerCompany; offset++) {
                    int person = first + offset;
                    if (2_000 + (person * 137) % 7_000 > 5_000) { found = true; break; }
                }
                if (!found) ids.add("scale_company_" + company);
            }
        } else if (!Set.of("HasEmployees", "WorkingAge", "EmployeeCount", "CompanyNameNonEmpty",
                "HasEmployer", "AdultWorker", "AllSalariesNonNegative").contains(invariantName)) {
            throw new IllegalArgumentException("No deterministic oracle for invariant " + invariantName);
        }
        return Set.copyOf(ids);
    }

    private UseSystemApi buildReference(MModel model, int companies, int employeesPerCompany) throws Exception {
        UseSystemApi api = UseSystemApi.create(model, false);
        int person = 0;
        for (int company = 1; company <= companies; company++) {
            String companyId = "scale_company_" + company;
            api.createObject("Company", companyId);
            api.setAttributeValue(companyId, "name", "'Company_" + company + "'");
            for (int employee = 1; employee <= employeesPerCompany; employee++) {
                person++;
                String personId = "scale_person_" + person;
                api.createObject("Person", personId);
                api.setAttributeValue(personId, "age", String.valueOf(18 + person % 45));
                api.setAttributeValue(personId, "firstName", "'P" + person + "'");
                api.setAttributeValue(personId, "salary", String.valueOf(2_000 + (person * 137) % 7_000));
                api.createLink("CompanyEmployee", companyId, personId);
                if (employee == 1) api.createLink("CompanyManager", companyId, personId);
            }
        }
        return api;
    }

    private List<CanonicalBatchInsertExecutor.StageTiming> bulkLoad(
            String modelName, int companies, int employeesPerCompany, int batchSize) {
        String modelKey = CanonicalGraphEncoding.modelKey(modelName);
        String objectKeyPrefix = CanonicalGraphEncoding.objectKeyPrefix(modelName);
        String companyClassKey = CanonicalGraphEncoding.classKey(modelName, "Company");
        String personClassKey = CanonicalGraphEncoding.classKey(modelName, "Person");
        CanonicalBatchInsertExecutor batches = new CanonicalBatchInsertExecutor(batchSize);
        List<CanonicalBatchInsertExecutor.StageTiming> timings =
                CanonicalBatchInsertExecutor.mutableTimingList();
        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            timings.add(batches.ensureCanonicalIndexes(session));
            timings.add(batches.executeRange(session, "companies", 1, companies, COMPANY_LOAD,
                    Map.of("modelKey", modelKey, "objectKeyPrefix", objectKeyPrefix,
                            "classKey", companyClassKey,
                            "attributeKey", CanonicalGraphEncoding.attributeKey(modelName, "Company", "name"))));
            int persons = companies * employeesPerCompany;
            timings.add(batches.executeRange(session, "persons-and-attributes", 1, persons, PERSON_LOAD,
                    Map.of("modelKey", modelKey, "objectKeyPrefix", objectKeyPrefix,
                            "classKey", personClassKey,
                            "ageKey", CanonicalGraphEncoding.attributeKey(modelName, "Person", "age"),
                            "firstNameKey", CanonicalGraphEncoding.attributeKey(modelName, "Person", "firstName"),
                            "salaryKey", CanonicalGraphEncoding.attributeKey(modelName, "Person", "salary"))));
            timings.add(batches.executeRange(session, "employment-links", 1, persons, EMPLOYMENT_LOAD,
                    Map.of("employees", employeesPerCompany, "modelKey", modelKey,
                            "objectKeyPrefix", objectKeyPrefix,
                            "associationKey", CanonicalGraphEncoding.associationKey(modelName, "CompanyEmployee"))));
            timings.add(batches.executeOnce(session, "manager-links", companies, MANAGER_LOAD,
                    Map.of("companies", companies, "employees", employeesPerCompany,
                            "modelKey", modelKey, "objectKeyPrefix", objectKeyPrefix,
                            "associationKey", CanonicalGraphEncoding.associationKey(modelName, "CompanyManager"))));
        }
        for (CanonicalBatchInsertExecutor.StageTiming timing : timings) {
            System.out.println("SCALE_LOAD_STAGE=" + timing.stage()
                    + " rows=" + timing.rows() + " batches=" + timing.batches()
                    + " ms=" + millis(timing.elapsedNs()));
        }
        return List.copyOf(timings);
    }

    private void assertGraphCounts(String modelKey, int objects, int links) {
        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            long actualObjects = session.run("MATCH (o:Object {modelKey:$modelKey}) RETURN count(o) AS n",
                    Map.of("modelKey", modelKey)).single().get("n").asLong();
            long actualLinks = session.run("MATCH ()-[r:LinkAssociateWith {modelKey:$modelKey}]->() RETURN count(r) AS n",
                    Map.of("modelKey", modelKey)).single().get("n").asLong();
            assertEquals(objects, actualObjects);
            assertEquals(links, actualLinks);
        }
    }

    private GraphEvaluationMetrics collectScaleMetrics(
            String modelKey,
            int objects,
            int persons,
            int companies,
            long encodingNs,
            GraphEvaluationMetrics.LatencyCollector latency) {
        int samples = Integer.getInteger("neo4j.scale.samples", 7);
        Map<String, String> observations = Map.of(
                "object", "MATCH (o:Object {modelKey:$modelKey,use_id:'scale_person_1'}) RETURN o",
                "type", "MATCH (:Object {modelKey:$modelKey,use_id:'scale_person_1'})"
                        + "-[:ObjectInstanceOf]->(c) RETURN c.classKey",
                "attribute", "MATCH (:Object {modelKey:$modelKey,use_id:'scale_person_1'})"
                        + "-[:ObjectHasAttribute]->(v) RETURN v.value",
                "navigation", "MATCH (:Object {modelKey:$modelKey,use_id:'scale_company_1'})"
                        + "-[r:LinkAssociateWith]->(p:Object) RETURN p.use_id",
                "allInstances", "MATCH (o:Object {modelKey:$modelKey})"
                        + "-[:ObjectInstanceOf]->(c {classKey:$personClassKey}) RETURN o.use_id");
        Map<String, Object> parameters = Map.of(
                "modelKey", modelKey,
                "personClassKey", CanonicalGraphEncoding.classKey(modelKey, "Person"));
        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            observations.forEach((name, query) -> {
                session.run(query, parameters).list();
                for (int sample = 0; sample < samples; sample++) {
                    long start = System.nanoTime();
                    session.run(query, parameters).list();
                    latency.add(name, System.nanoTime() - start);
                }
            });
            long nodes = scalar(session,
                    "MATCH (n {modelKey:$modelKey}) RETURN count(n) AS value", parameters);
            long relationships = scalar(session,
                    "MATCH (a {modelKey:$modelKey})-[r]->(b {modelKey:$modelKey}) "
                            + "RETURN count(r) AS value", parameters);
            long nodeProperties = scalar(session,
                    "MATCH (n {modelKey:$modelKey}) "
                            + "RETURN coalesce(sum(size(keys(n))),0) AS value", parameters);
            long relationshipProperties = scalar(session,
                    "MATCH (a {modelKey:$modelKey})-[r]->(b {modelKey:$modelKey}) "
                            + "RETURN coalesce(sum(size(keys(r))),0) AS value", parameters);
            long nodePayloadChars = payloadChars(session,
                    "MATCH (n {modelKey:$modelKey}) RETURN properties(n) AS props", parameters);
            long relationshipPayloadChars = payloadChars(session,
                    "MATCH (a {modelKey:$modelKey})-[r]->(b {modelKey:$modelKey}) "
                            + "RETURN properties(r) AS props", parameters);
            long maxTypeDegree = scalar(session,
                    "MATCH (o:Object {modelKey:$modelKey})-[:ObjectInstanceOf]->(c) "
                            + "WITH c,count(o) AS degree RETURN coalesce(max(degree),0) AS value", parameters);
            long sourceAttributeSlots = companies + 3L * persons;
            long sourceLinks = persons + (long) companies;
            return new GraphEvaluationMetrics(8, objects, sourceAttributeSlots, sourceLinks,
                    nodes, relationships, nodeProperties + relationshipProperties,
                    nodePayloadChars + relationshipPayloadChars, maxTypeDegree,
                    encodingNs, latency.summarize());
        }
    }

    private long scalar(Session session, String query, Map<String, Object> parameters) {
        return session.run(query, parameters).single().get("value").asLong();
    }

    private long payloadChars(Session session, String query, Map<String, Object> parameters) {
        return session.run(query, parameters).list(record -> record.get("props").asMap().toString().length())
                .stream().mapToLong(Integer::longValue).sum();
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
        assertNotNull(model, diagnostics.toString());
        return model;
    }

    private Set<String> execute(String cypher, Map<String, Object> parameters) {
        Set<String> ids = new LinkedHashSet<>();
        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            var result = session.run(cypher, parameters);
            while (result.hasNext()) ids.add(result.next().get("useId").asString());
        }
        return Set.copyOf(ids);
    }

    private void cleanup(String modelKey) {
        Neo4jDriverManager manager = Neo4jDriverManager.getInstance();
        if (manager == null || !manager.isConnected()) return;
        try {
            ensureCleanupValueIndex();
            Map<String, Object> parameters = Map.of("modelKey", modelKey);
            deleteLabeledNodesInBatches("AttributeValue", "n.modelKey = $modelKey", parameters);
            deleteLabeledNodesInBatches("Object", "n.modelKey = $modelKey", parameters);
            deleteRelationshipsInBatches("n.modelKey = $modelKey", parameters);
            deleteNodesInBatches("n.modelKey = $modelKey", parameters);
            try (Session session = manager.openSession()) {
                session.run("MATCH (m:ManageModel {name:$modelName}) DETACH DELETE m",
                        Map.of("modelName", modelKey)).consume();
            }
        } finally {
            dropCleanupValueIndex();
        }
    }

    private void cleanupStaleScaleModels() {
        String predicate = "n.modelKey STARTS WITH 'ResearchScale_' "
                + "OR (n:ManageModel AND n.name STARTS WITH 'ResearchScale_')";
        try {
            ensureCleanupValueIndex();
            deleteLabeledNodesInBatches("AttributeValue",
                    "n.modelKey STARTS WITH 'ResearchScale_'", Map.of());
            deleteLabeledNodesInBatches("Object",
                    "n.modelKey STARTS WITH 'ResearchScale_'", Map.of());
            deleteRelationshipsInBatches(predicate, Map.of());
            deleteNodesInBatches(predicate, Map.of());
        } finally {
            dropCleanupValueIndex();
        }
    }

    private void ensureCleanupValueIndex() {
        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            session.run("CREATE INDEX research_cleanup_value_model IF NOT EXISTS "
                    + "FOR (n:AttributeValue) ON (n.modelKey)").consume();
            session.run("CALL db.awaitIndexes(300)").consume();
        }
    }

    private void dropCleanupValueIndex() {
        Neo4jDriverManager manager = Neo4jDriverManager.getInstance();
        if (manager == null || !manager.isConnected()) return;
        try (Session session = manager.openSession()) {
            session.run("DROP INDEX research_cleanup_value_model IF EXISTS").consume();
        }
    }

    private void deleteLabeledNodesInBatches(String label, String predicate,
                                             Map<String, Object> commonParameters) {
        if (!List.of("AttributeValue", "Object").contains(label)) {
            throw new IllegalArgumentException("Unsupported cleanup label: " + label);
        }
        deleteInBatches("MATCH (n:" + label + ") WHERE " + predicate
                + " WITH n LIMIT $batch DETACH DELETE n RETURN count(*) AS deleted", commonParameters);
    }

    private void deleteRelationshipsInBatches(String nodePredicate, Map<String, Object> commonParameters) {
        deleteInBatches("MATCH (n)-[r]-() WHERE " + nodePredicate
                + " WITH DISTINCT r LIMIT $batch DELETE r RETURN count(*) AS deleted", commonParameters);
    }

    private void deleteNodesInBatches(String nodePredicate, Map<String, Object> commonParameters) {
        deleteInBatches("MATCH (n) WHERE " + nodePredicate
                + " WITH n LIMIT $batch DELETE n RETURN count(*) AS deleted", commonParameters);
    }

    private void deleteInBatches(String cypher, Map<String, Object> commonParameters) {
        while (true) {
            Map<String, Object> parameters = new java.util.HashMap<>(commonParameters);
            parameters.put("batch", CLEANUP_BATCH);
            long deleted;
            try (Session session = Neo4jDriverManager.getInstance().openSession()) {
                deleted = session.run(cypher, parameters).single().get("deleted").asLong();
            }
            if (deleted == 0) return;
        }
    }

    private static String millis(long nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0);
    }

    private static final String MODEL = """
            model %s
            class Company
            attributes
                name : String
            end
            class Person
            attributes
                age : Integer
                firstName : String
                salary : Integer
            end
            association CompanyEmployee between
                Company[*] role employer
                Person[*] role employee
            end
            association CompanyManager between
                Company[*] role managedCompany
                Person[1] role manager
            end
            """;

    private static final String OCL_SUITE = """
            context Company inv HasEmployees: self.employee->notEmpty()
            context Company inv WorkingAge: self.employee->forAll(p | p.age <= 65)
            context Company inv HasSenior: self.employee->exists(p | p.age > 50)
            context Company inv SeniorSelect: self.employee->select(p | p.age > 50)->notEmpty()
            context Company inv EmployeeCount: self.employee->size() >= 1
            context Company inv CompanyNameNonEmpty: self.name <> ''
            context Person inv HasEmployer: self.employer->notEmpty()
            context Person inv AdultWorker: self.age >= 18 implies self.employer->notEmpty()
            context Company inv HighSalaryExists: self.employee->exists(e | e.salary > 5000)
            context Company inv AllSalariesNonNegative: self.employee->forAll(e | e.salary >= 0)
            """;

    private static final String COMPANY_LOAD = """
            MATCH (cls {classKey:$classKey}), (def:Attribute {attributeKey:$attributeKey})
            UNWIND range($start,$end) AS c
            CREATE (o:Object:Company {use_id:'scale_company_'+toString(c),
                objectKey:$objectKeyPrefix+'scale_company_'+toString(c), modelKey:$modelKey,
                runtimeClassKey:$classKey})-[:ObjectInstanceOf]->(cls)
            CREATE (v:AttributeValue {name:'scale_company_'+toString(c)+'_name', modelKey:$modelKey,
                attributeKey:$attributeKey, type:'String', value:"'Company_"+toString(c)+"'",
                isCollection:false, collectionType:'None', isNestedCollection:false})
            CREATE (o)-[:ObjectHasAttribute]->(v)-[:InstanceOf]->(def)
            """;

    private static final String PERSON_LOAD = """
            MATCH (cls {classKey:$classKey})
            MATCH (ageDef:Attribute {attributeKey:$ageKey})
            MATCH (nameDef:Attribute {attributeKey:$firstNameKey})
            MATCH (salaryDef:Attribute {attributeKey:$salaryKey})
            UNWIND range($start,$end) AS p
            CREATE (o:Object:Person {use_id:'scale_person_'+toString(p),
                objectKey:$objectKeyPrefix+'scale_person_'+toString(p), modelKey:$modelKey,
                runtimeClassKey:$classKey})-[:ObjectInstanceOf]->(cls)
            CREATE (age:AttributeValue {name:'scale_person_'+toString(p)+'_age', modelKey:$modelKey,
                attributeKey:$ageKey, type:'Integer', value:"'"+toString(18+(p%45))+"'",
                isCollection:false, collectionType:'None', isNestedCollection:false})
            CREATE (firstName:AttributeValue {name:'scale_person_'+toString(p)+'_firstName', modelKey:$modelKey,
                attributeKey:$firstNameKey, type:'String', value:"'P"+toString(p)+"'",
                isCollection:false, collectionType:'None', isNestedCollection:false})
            CREATE (salary:AttributeValue {name:'scale_person_'+toString(p)+'_salary', modelKey:$modelKey,
                attributeKey:$salaryKey, type:'Integer', value:"'"+toString(2000+((p*137)%7000))+"'",
                isCollection:false, collectionType:'None', isNestedCollection:false})
            CREATE (o)-[:ObjectHasAttribute]->(age)-[:InstanceOf]->(ageDef)
            CREATE (o)-[:ObjectHasAttribute]->(firstName)-[:InstanceOf]->(nameDef)
            CREATE (o)-[:ObjectHasAttribute]->(salary)-[:InstanceOf]->(salaryDef)
            """;

    private static final String EMPLOYMENT_LOAD = """
            UNWIND range($start,$end) AS p
            WITH p, toInteger((p-1)/$employees)+1 AS c
            MATCH (company:Object {objectKey:$objectKeyPrefix+'scale_company_'+toString(c)})
            MATCH (person:Object {objectKey:$objectKeyPrefix+'scale_person_'+toString(p)})
            CREATE (company)-[:LinkAssociateWith {name:'CompanyEmployee', associationKey:$associationKey,
                modelKey:$modelKey, sourceRole:'employer', targetRole:'employee', isTernary:false,
                sourceQualifiers:[], targetQualifiers:[]}]->(person)
            """;

    private static final String MANAGER_LOAD = """
            UNWIND range(1,$companies) AS c
            WITH c, ((c-1)*$employees)+1 AS p
            MATCH (company:Object {objectKey:$objectKeyPrefix+'scale_company_'+toString(c)})
            MATCH (person:Object {objectKey:$objectKeyPrefix+'scale_person_'+toString(p)})
            CREATE (company)-[:LinkAssociateWith {name:'CompanyManager', associationKey:$associationKey,
                modelKey:$modelKey, sourceRole:'managedCompany', targetRole:'manager', isTernary:false,
                sourceQualifiers:[], targetQualifiers:[]}]->(person)
            """;
}
