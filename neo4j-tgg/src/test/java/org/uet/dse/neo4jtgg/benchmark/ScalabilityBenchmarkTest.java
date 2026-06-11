package org.uet.dse.neo4jtgg.benchmark;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Session;
import org.neo4j.driver.TransactionConfig;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.uet.dse.neo4j.model.FullObjectSnapshot;
import org.uet.dse.neo4j.model.LinkState;
import org.uet.dse.neo4j.model.ObjectState;
import org.uet.dse.neo4jtgg.model.CypherCompilationResult;
import org.uet.dse.neo4jtgg.model.ModelDelta;
import org.uet.dse.neo4jtgg.model.WorkspaceSide;
import org.uet.dse.neo4jtgg.service.impl.DefaultOclToCypherCompiler;
import org.uet.dse.neo4jtgg.service.impl.SnapshotDeltaComputer;

/**
 * Scalability benchmark: measures OCL compile time, IR evaluation time, and
 * snapshot delta computation time at increasing model sizes.
 */
class ScalabilityBenchmarkTest {

    private static final String ENV_NEO4J_URI = "NEO4J_URI";
    private static final String ENV_NEO4J_USER = "NEO4J_USER";
    private static final String ENV_NEO4J_PASSWORD = "NEO4J_PASSWORD";
    private static final String ENV_NEO4J_DB = "NEO4J_DB";
    private static final String ENV_NEO4J_CREATE = "NEO4J_CREATE_DB";
    private static final String ENV_NEO4J_DELETE = "NEO4J_DELETE_ON_EXIT";

    private static final String COMPANY_SPEC = """
            model Company
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

    private static final String[] OCL_EXPRESSIONS = {
        "context Company inv HasEmployees: self.employee->notEmpty()",
        "context Company inv WorkingAge: self.employee->forAll(p | p.age <= 65)",
        "context Company inv HasSenior: self.employee->exists(p | p.age > 50)",
        "context Company inv SeniorSelect: self.employee->select(p | p.age > 50)->notEmpty()",
        "context Company inv EmployeeCount: self.employee->size() >= 1",
        "context Company inv NamedManager: self.manager.firstName.isDefined()",
        "context Person inv HasEmployer: self.employer->notEmpty()",
        "context Person inv AdultWorker: self.age >= 18 implies self.employer->notEmpty()",
        "context Company inv HighSalaryExists: self.employee->exists(e | e.salary > 5000)",
        "context Company inv AllNamed: self.employee->forAll(e | e.firstName.isDefined())"
    };

    private static final String[] OCL_EXECUTION_EXPRESSIONS = {
        "context Company inv HasEmployees: self.employee->notEmpty()",
        "context Company inv WorkingAge: self.employee->forAll(p | p.age <= 65)",
        "context Company inv HasSenior: self.employee->exists(p | p.age > 50)",
        "context Company inv SeniorSelect: self.employee->select(p | p.age > 50)->notEmpty()",
        "context Company inv EmployeeCount: self.employee->size() >= 1",
        "context Person inv HasEmployer: self.employer->notEmpty()",
        "context Person inv AdultWorker: self.age >= 18 implies self.employer->notEmpty()",
        "context Company inv HighSalaryExists: self.employee->exists(e | e.salary > 5000)",
        "context Company inv AllNamed: self.employee->forAll(e | e.firstName.isDefined())"
    };

    @Test
    void benchmarkOclCompilationTime() {
        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(COMPANY_SPEC, "Company.use",
                new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);

        // Warm up
        for (String ocl : OCL_EXPRESSIONS) {
            compiler.compile(ocl);
        }

        StringBuilder report = new StringBuilder();
        report.append("\n=== OCL Compilation Benchmark ===\n");
        report.append(String.format("%-60s %10s %10s\n", "OCL Expression", "Compile(ms)", "Supported"));

        for (String ocl : OCL_EXPRESSIONS) {
            long start = System.nanoTime();
            CypherCompilationResult result = null;
            int iterations = 100;
            for (int i = 0; i < iterations; i++) {
                result = compiler.compile(ocl);
            }
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            double avg = (double) elapsed / iterations;

            String shortOcl = ocl.length() > 58 ? ocl.substring(0, 55) + "..." : ocl;
            report.append(String.format("%-60s %10.2f %10s\n", shortOcl, avg, result.isSupported()));
        }

        // Batch compilation benchmark
        report.append("\n=== Batch Compilation (all 10 expressions) ===\n");
        int[] batchSizes = {1, 10, 50, 100};
        for (int batchSize : batchSizes) {
            long start = System.nanoTime();
            for (int b = 0; b < batchSize; b++) {
                for (String ocl : OCL_EXPRESSIONS) {
                    compiler.compile(ocl);
                }
            }
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            report.append(String.format("  %d batches (%d compilations): %dms (avg %.2fms/compile)\n",
                    batchSize, batchSize * OCL_EXPRESSIONS.length, elapsed,
                    (double) elapsed / (batchSize * OCL_EXPRESSIONS.length)));
        }

        System.out.println(report);
    }

    @Test
    void benchmarkSnapshotDeltaAtScale() {
        int[] scales = {100, 500, 1000, 5000, 10000};

        StringBuilder report = new StringBuilder();
        report.append("\n=== Snapshot Delta Computation Benchmark ===\n");
        report.append(String.format("%-10s %-10s %-15s %-15s %-10s %-10s %-10s\n",
                "Objects", "Links", "DeltaTime(ms)", "InitTime(ms)", "Added", "Modified", "Deleted"));

        for (int scale : scales) {
            FullObjectSnapshot previous = generateSnapshot(scale, 0);
            // Current: 10% added, 10% modified, 10% deleted
            int addCount = scale / 10;
            int modCount = scale / 10;
            int delCount = scale / 10;
            FullObjectSnapshot current = generateModifiedSnapshot(previous, addCount, modCount, delCount);

            // Measure initial delta (null → current)
            long initStart = System.nanoTime();
            ModelDelta initDelta = SnapshotDeltaComputer.compute(WorkspaceSide.SOURCE, null, current);
            long initTime = (System.nanoTime() - initStart) / 1_000_000;

            // Measure incremental delta (previous → current)
            long deltaStart = System.nanoTime();
            ModelDelta delta = SnapshotDeltaComputer.compute(WorkspaceSide.SOURCE, previous, current);
            long deltaTime = (System.nanoTime() - deltaStart) / 1_000_000;

            int linkCount = previous.links.size();
            report.append(String.format("%-10d %-10d %-15d %-15d %-10d %-10d %-10d\n",
                    scale, linkCount, deltaTime, initTime,
                    delta.addedObjects().size(), delta.modifiedObjects().size(), delta.deletedObjects().size()));

            // Validate correctness
            assertEquals(addCount, delta.addedObjects().size());
            assertEquals(modCount, delta.modifiedObjects().size());
            assertEquals(delCount, delta.deletedObjects().size());
        }

        System.out.println(report);
    }

    @Test
    void benchmarkSnapshotDeltaWithLinks() {
        int[] scales = {100, 500, 1000, 5000, 10000};

        StringBuilder report = new StringBuilder();
        report.append("\n=== Snapshot Delta with Links Benchmark ===\n");
        report.append(String.format("%-10s %-10s %-15s %-12s %-12s\n",
                "Objects", "Links", "DeltaTime(ms)", "AddedLinks", "DeletedLinks"));

        for (int scale : scales) {
            int linkCount = scale * 3;
            FullObjectSnapshot previous = generateSnapshotWithLinks(scale, linkCount);
            FullObjectSnapshot current = generateModifiedSnapshotWithLinks(previous, scale / 10, linkCount / 10);

            long start = System.nanoTime();
            ModelDelta delta = SnapshotDeltaComputer.compute(WorkspaceSide.SOURCE, previous, current);
            long elapsed = (System.nanoTime() - start) / 1_000_000;

            report.append(String.format("%-10d %-10d %-15d %-12d %-12d\n",
                    scale, linkCount, elapsed,
                    delta.addedLinks().size(), delta.deletedLinks().size()));
        }

        System.out.println(report);
    }

    @Test
    void benchmarkCypherExecutionAtScale() {
        if (!ensureNeo4jConnected()) {
            System.out.println("\n=== Cypher Execution Benchmark ===\n"
                    + "Neo4j is not configured. Set NEO4J_URI, NEO4J_USER, NEO4J_PASSWORD (optional NEO4J_DB).\n");
            return;
        }

        int[] scales = {100, 1000, 10000};

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(COMPANY_SPEC, "Company.use",
                new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        List<CypherCompilationResult> compiled = new ArrayList<>();
        for (String ocl : OCL_EXECUTION_EXPRESSIONS) {
            compiled.add(compiler.compile(ocl));
        }

        StringBuilder report = new StringBuilder();
        report.append("\n=== Cypher Execution Benchmark ===\n");
        report.append(String.format("%-10s %-10s %-18s %-10s %-10s\n",
                "Objects", "Links", "CypherTime(ms)", "Queries", "Skipped"));

        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            for (int scale : scales) {
                int linkCount = scale * 3;
                resetNeo4j(session);
                seedNeo4jCompanyData(session, scale, linkCount);

                // Warm up
                runCypherBatch(session, compiled);

                long start = System.nanoTime();
                BatchRun run = runCypherBatch(session, compiled);
                long elapsed = (System.nanoTime() - start) / 1_000_000;

                report.append(String.format("%-10d %-10d %-18d %-10d %-10d\n",
                        scale, linkCount, elapsed, run.executed(), run.skipped()));
            }
        }

        System.out.println(report);
    }

    private FullObjectSnapshot generateSnapshot(int objectCount, int startIndex) {
        FullObjectSnapshot snapshot = new FullObjectSnapshot();
        for (int i = startIndex; i < startIndex + objectCount; i++) {
            ObjectState obj = new ObjectState();
            obj.name = "obj" + i;
            obj.className = (i % 5 == 0) ? "Company" : "Person";
            obj.primitiveValues = new LinkedHashMap<>(Map.of(
                    "name", "Entity_" + i,
                    "age", 20 + (i % 45),
                    "salary", 3000 + (i * 100 % 7000)
            ));
            obj.objectReferences = new LinkedHashMap<>();
            snapshot.objects.put(obj.name, obj);
        }
        return snapshot;
    }

    private FullObjectSnapshot generateModifiedSnapshot(FullObjectSnapshot previous,
            int addCount, int modCount, int delCount) {
        FullObjectSnapshot current = new FullObjectSnapshot();
        int idx = 0;
        int modified = 0;
        int deleted = 0;

        for (Map.Entry<String, ObjectState> entry : previous.objects.entrySet()) {
            if (deleted < delCount && idx % 10 == 9) {
                // skip (delete) every 10th object
                deleted++;
            } else if (modified < modCount && idx % 10 == 5) {
                // modify every 10th object (offset by 5)
                ObjectState copy = new ObjectState();
                copy.name = entry.getValue().name;
                copy.className = entry.getValue().className;
                copy.primitiveValues = new LinkedHashMap<>(entry.getValue().primitiveValues);
                copy.primitiveValues.put("name", "Modified_" + idx);
                copy.objectReferences = new LinkedHashMap<>(entry.getValue().objectReferences);
                current.objects.put(copy.name, copy);
                modified++;
            } else {
                current.objects.put(entry.getKey(), entry.getValue());
            }
            idx++;
        }

        // Add new objects
        int maxId = previous.objects.size() + 1000;
        for (int i = 0; i < addCount; i++) {
            ObjectState obj = new ObjectState();
            obj.name = "new_obj" + (maxId + i);
            obj.className = "Person";
            obj.primitiveValues = new LinkedHashMap<>(Map.of("name", "NewPerson_" + i, "age", 25));
            obj.objectReferences = new LinkedHashMap<>();
            current.objects.put(obj.name, obj);
        }

        return current;
    }

    private FullObjectSnapshot generateSnapshotWithLinks(int objectCount, int linkCount) {
        FullObjectSnapshot snapshot = generateSnapshot(objectCount, 0);
        String[] objectNames = snapshot.objects.keySet().toArray(new String[0]);

        for (int i = 0; i < linkCount && i < objectNames.length - 1; i++) {
            LinkState link = new LinkState();
            link.assocName = "CompanyEmployee";
            link.participants = List.of(objectNames[i % objectNames.length],
                    objectNames[(i + 1) % objectNames.length]);
            snapshot.links.put(link.getIdentity(), link);
        }
        return snapshot;
    }

    private FullObjectSnapshot generateModifiedSnapshotWithLinks(FullObjectSnapshot previous,
            int addedObjects, int deletedLinks) {
        FullObjectSnapshot current = new FullObjectSnapshot();
        current.objects.putAll(previous.objects);
        current.links.putAll(previous.links);

        // Add new objects
        int base = previous.objects.size() + 5000;
        for (int i = 0; i < addedObjects; i++) {
            ObjectState obj = new ObjectState();
            obj.name = "link_new_" + (base + i);
            obj.className = "Person";
            obj.primitiveValues = new LinkedHashMap<>(Map.of("name", "LinkNew_" + i));
            obj.objectReferences = new LinkedHashMap<>();
            current.objects.put(obj.name, obj);
        }

        // Delete some links
        int removed = 0;
        var iterator = current.links.entrySet().iterator();
        while (iterator.hasNext() && removed < deletedLinks) {
            iterator.next();
            iterator.remove();
            removed++;
        }

        // Add new links
        String[] names = current.objects.keySet().toArray(new String[0]);
        for (int i = 0; i < deletedLinks && names.length > 1; i++) {
            LinkState link = new LinkState();
            link.assocName = "CompanyManager";
            link.participants = List.of(names[i % names.length], names[(i + 3) % names.length]);
            current.links.put(link.getIdentity(), link);
        }

        return current;
    }

    private boolean ensureNeo4jConnected() {
        Neo4jDriverManager manager = Neo4jDriverManager.getInstance();
        if (manager != null && manager.isConnected()) {
            return true;
        }

        String uri = readEnvOrProperty(ENV_NEO4J_URI);
        String user = readEnvOrProperty(ENV_NEO4J_USER);
        String password = readEnvOrProperty(ENV_NEO4J_PASSWORD);
        String database = readEnvOrProperty(ENV_NEO4J_DB);
        if (uri == null || user == null || password == null) {
            return false;
        }

        boolean createDb = Boolean.parseBoolean(readEnvOrProperty(ENV_NEO4J_CREATE, "false"));
        boolean deleteOnExit = Boolean.parseBoolean(readEnvOrProperty(ENV_NEO4J_DELETE, "false"));
        try {
            Neo4jDriverManager.connect(uri, user, password, database, createDb, deleteOnExit);
            return Neo4jDriverManager.getInstance() != null && Neo4jDriverManager.getInstance().isConnected();
        } catch (Exception ex) {
            System.err.println("Neo4j connection failed: " + ex.getMessage());
            return false;
        }
    }

    private String readEnvOrProperty(String key) {
        return readEnvOrProperty(key, null);
    }

    private String readEnvOrProperty(String key, String defaultValue) {
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) {
            return env;
        }
        String prop = System.getProperty(key);
        return (prop != null && !prop.isBlank()) ? prop : defaultValue;
    }

    private void resetNeo4j(Session session) {
        session.run("MATCH (n) DETACH DELETE n").consume();
    }

    private BatchRun runCypherBatch(Session session, List<CypherCompilationResult> compiled) {
        int executed = 0;
        int skipped = 0;
        TransactionConfig config = TransactionConfig.builder()
                .withTimeout(Duration.ofSeconds(10))
                .build();
        for (CypherCompilationResult result : compiled) {
            if (!result.isSupported()) {
                skipped++;
                continue;
            }
            try {
                session.run(result.getCypher(), result.getParameters(), config).list();
                executed++;
            } catch (RuntimeException ex) {
                skipped++;
                System.err.println("Cypher execution skipped: " + ex.getMessage());
            }
        }
        return new BatchRun(executed, skipped);
    }

    private record BatchRun(int executed, int skipped) {

    }

    private void seedNeo4jCompanyData(Session session, int objectCount, int linkCount) {
        int companyCount = Math.max(1, objectCount / 5);
        int personCount = Math.max(1, objectCount - companyCount);

        session.run("UNWIND $classes AS cls MERGE (:Class {name: cls})",
                Map.of("classes", List.of("Company", "Person"))).consume();

        List<Map<String, Object>> objects = new ArrayList<>(objectCount);
        for (int i = 0; i < companyCount; i++) {
            objects.add(Map.of("id", "c" + i, "cls", "Company"));
        }
        for (int i = 0; i < personCount; i++) {
            objects.add(Map.of("id", "p" + i, "cls", "Person"));
        }

        session.run("UNWIND $rows AS row "
                + "MERGE (o {use_id: row.id}) "
                + "MERGE (cls {name: row.cls}) "
                + "MERGE (o)-[:ObjectInstanceOf]->(cls)",
                Map.of("rows", objects)).consume();

        List<Map<String, Object>> attrs = new ArrayList<>(objectCount * 3);
        for (int i = 0; i < companyCount; i++) {
            String id = "c" + i;
            attrs.add(attrRow(id, "name", "Company_" + i));
        }
        for (int i = 0; i < personCount; i++) {
            String id = "p" + i;
            attrs.add(attrRow(id, "age", String.valueOf(18 + (i % 50))));
            attrs.add(attrRow(id, "firstName", "P" + i));
            attrs.add(attrRow(id, "salary", String.valueOf(2000 + (i * 37 % 7000))));
        }

        session.run("UNWIND $rows AS row "
                + "MATCH (o {use_id: row.objId}) "
                + "MERGE (v:AttributeValue {name: row.valId}) "
                + "SET v.value = row.val "
                + "MERGE (o)-[:ObjectHasAttribute]->(v)",
                Map.of("rows", attrs)).consume();

        List<Map<String, Object>> employeeLinks = new ArrayList<>();
        List<Map<String, Object>> managerLinks = new ArrayList<>();
        int employeeLinksTarget = (int) Math.round(linkCount * 0.8);
        int managerLinksTarget = linkCount - employeeLinksTarget;

        for (int i = 0; i < employeeLinksTarget; i++) {
            String src = "c" + (i % companyCount);
            String tgt = "p" + (i % personCount);
            employeeLinks.add(linkRow(src, tgt));
        }
        for (int i = 0; i < managerLinksTarget; i++) {
            String src = "c" + (i % companyCount);
            String tgt = "p" + ((i * 7) % personCount);
            managerLinks.add(linkRow(src, tgt));
        }

        session.run("UNWIND $rows AS row "
                + "MATCH (a {use_id: row.src}), (b {use_id: row.tgt}) "
                + "MERGE (a)-[r:LinkCompanyEmployee {name: 'CompanyEmployee'}]->(b) "
                + "SET r.sourceRole = 'employer', r.targetRole = 'employee'",
                Map.of("rows", employeeLinks)).consume();

        session.run("UNWIND $rows AS row "
                + "MATCH (a {use_id: row.src}), (b {use_id: row.tgt}) "
                + "MERGE (a)-[r:LinkCompanyManager {name: 'CompanyManager'}]->(b) "
                + "SET r.sourceRole = 'managedCompany', r.targetRole = 'manager'",
                Map.of("rows", managerLinks)).consume();
    }

    private Map<String, Object> attrRow(String objId, String attr, String value) {
        Map<String, Object> row = new HashMap<>();
        row.put("objId", objId);
        row.put("valId", objId + "_" + attr);
        row.put("val", value);
        return row;
    }

    private Map<String, Object> linkRow(String src, String tgt) {
        Map<String, Object> row = new HashMap<>();
        row.put("src", src);
        row.put("tgt", tgt);
        return row;
    }
}
