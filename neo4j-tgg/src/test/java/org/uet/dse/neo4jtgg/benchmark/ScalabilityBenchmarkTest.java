package org.uet.dse.neo4jtgg.benchmark;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Session;
import org.neo4j.driver.TransactionConfig;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.uet.dse.neo4jtgg.experiment.InstrumentedCompilationResult;
import org.uet.dse.neo4jtgg.model.CypherCompilationResult;
import org.uet.dse.neo4jtgg.service.impl.DefaultOclToCypherCompiler;

/**
 * Legacy microbenchmark container. Certified compilation timing is retained,
 * but the old real-graph branch is disabled because its unscoped cleanup and
 * pre-canonical graph vocabulary are not research evidence. Use
 * {@code ResearchScaleRealNeo4jTest} for the key-scoped canonical campaign.
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
        "context Company inv CompanyNameNonEmpty: self.name <> ''",
        "context Person inv HasEmployer: self.employer->notEmpty()",
        "context Person inv AdultWorker: self.age >= 18 implies self.employer->notEmpty()",
        "context Company inv HighSalaryExists: self.employee->exists(e | e.salary > 5000)",
        "context Company inv AllSalariesNonNegative: self.employee->forAll(e | e.salary >= 0)"
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
        "context Company inv AllSalariesNonNegative: self.employee->forAll(e | e.salary >= 0)"
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
            compiler.compileInvariantInstrumented(ocl);
        }

        StringBuilder report = new StringBuilder();
        report.append("\n=== OCL Compilation Benchmark ===\n");
        report.append(String.format("%-60s %10s %10s\n", "OCL Expression", "Compile(ms)", "Supported"));

        for (String ocl : OCL_EXPRESSIONS) {
            long start = System.nanoTime();
            InstrumentedCompilationResult result = null;
            int iterations = 100;
            for (int i = 0; i < iterations; i++) {
                result = compiler.compileInvariantInstrumented(ocl);
            }
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            double avg = (double) elapsed / iterations;

            String shortOcl = ocl.length() > 58 ? ocl.substring(0, 55) + "..." : ocl;
            report.append(String.format("%-60s %10.2f %10s\n", shortOcl, avg, result != null));
        }

        // Batch compilation benchmark
        report.append("\n=== Batch Compilation (all 10 expressions) ===\n");
        int[] batchSizes = {1, 10, 50, 100};
        for (int batchSize : batchSizes) {
            long start = System.nanoTime();
            for (int b = 0; b < batchSize; b++) {
                for (String ocl : OCL_EXPRESSIONS) {
                    compiler.compileInvariantInstrumented(ocl);
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
    @Disabled("Legacy unscoped/non-canonical graph benchmark; use ResearchScaleRealNeo4jTest")
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

        session.run("UNWIND $classes AS cls MERGE (:UmlClass {classKey: cls})",
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
                + "MERGE (cls:UmlClass {classKey: row.cls}) "
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
