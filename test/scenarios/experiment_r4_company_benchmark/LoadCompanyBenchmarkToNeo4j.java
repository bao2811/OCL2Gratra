package scenarios.experiment_r4_company_benchmark;

import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.uml.mm.MModel;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.uet.dse.neo4j.sync.model.CoreModelPushService;
import scenarios.common.ScenarioSupport;

import java.nio.file.Path;

public final class LoadCompanyBenchmarkToNeo4j {
    private static final Path SCENARIO_DIR = Path.of("test", "scenarios", "experiment_r4_company_benchmark");
    private static final int BULK_BATCH_SIZE = 5_000;

    private LoadCompanyBenchmarkToNeo4j() {
    }

    public static MModel load(int companyCount, int employeesPerCompany) throws Exception {
        ScenarioSupport.ensureNeo4jConnected();
        ScenarioSupport.resetDatabase();
        ScenarioSupport.ensureBenchmarkIndexes();

        MModel model = ScenarioSupport.compileModel(SCENARIO_DIR.resolve("model.use"));
        UseSystemApi api = ScenarioSupport.createSystemApi(model);

        int personIndex = 0;
        for (int c = 1; c <= companyCount; c++) {
            String companyId = "company" + c;
            api.createObject("Company", companyId);
            api.setAttributeValue(companyId, "name", "'Company_" + c + "'");

            String managerId = null;
            for (int e = 1; e <= employeesPerCompany; e++) {
                personIndex++;
                String personId = "person" + personIndex;
                api.createObject("Person", personId);
                api.setAttributeValue(personId, "age", String.valueOf(18 + (personIndex % 45)));
                api.setAttributeValue(personId, "firstName", "'P" + personIndex + "'");
                api.setAttributeValue(personId, "salary", String.valueOf(2000 + ((personIndex * 137) % 7000)));
                api.createLink("CompanyEmployee", companyId, personId);

                if (e == 1) {
                    managerId = personId;
                }
            }

            if (managerId != null) {
                api.createLink("CompanyManager", companyId, managerId);
            }
        }

        ScenarioSupport.pushModelAndObjects(model, api);
        return model;
    }

    public static MModel loadFast(int companyCount, int employeesPerCompany) throws Exception {
        ScenarioSupport.ensureNeo4jConnected();
        ScenarioSupport.resetDatabase();
        ScenarioSupport.ensureBenchmarkCoreIndexes();

        MModel model = ScenarioSupport.compileModel(SCENARIO_DIR.resolve("model.use"));
        new CoreModelPushService(new UseModelApi(model)).pushModelToNeo4j();

        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            for (int start = 1; start <= companyCount; start += BULK_BATCH_SIZE) {
                int end = Math.min(companyCount, start + BULK_BATCH_SIZE - 1);
                session.run("""
                    MATCH (cls {name: 'Company'}), (attrDef:Attribute {name: 'Company_name'})
                    UNWIND range($start, $end) AS c
                    CREATE (company:Object:Company {use_id: 'company' + toString(c)})
                    CREATE (company)-[:ObjectInstanceOf]->(cls)
                    CREATE (nameVal:AttributeValue {
                        name: 'company' + toString(c) + '_name',
                        type: 'String',
                        value: "'" + 'Company_' + toString(c) + "'",
                        isCollection: false,
                        collectionType: 'None',
                        isNestedCollection: false
                    })
                    CREATE (company)-[:ObjectHasAttribute]->(nameVal)
                    CREATE (nameVal)-[:InstanceOf]->(attrDef)
                    """, Values.parameters("start", start, "end", end)).consume();
            }

            int personCount = companyCount * employeesPerCompany;
            for (int start = 1; start <= personCount; start += BULK_BATCH_SIZE) {
                int end = Math.min(personCount, start + BULK_BATCH_SIZE - 1);
                session.run("""
                    MATCH (cls {name: 'Person'})
                    MATCH (ageDef:Attribute {name: 'Person_age'})
                    MATCH (firstNameDef:Attribute {name: 'Person_firstName'})
                    MATCH (salaryDef:Attribute {name: 'Person_salary'})
                    UNWIND range($start, $end) AS p
                    CREATE (person:Object:Person {use_id: 'person' + toString(p)})
                    CREATE (person)-[:ObjectInstanceOf]->(cls)
                    CREATE (ageVal:AttributeValue {
                        name: 'person' + toString(p) + '_age',
                        type: 'Integer',
                        value: "'" + toString(18 + (p % 45)) + "'",
                        isCollection: false,
                        collectionType: 'None',
                        isNestedCollection: false
                    })
                    CREATE (firstNameVal:AttributeValue {
                        name: 'person' + toString(p) + '_firstName',
                        type: 'String',
                        value: "'" + 'P' + toString(p) + "'",
                        isCollection: false,
                        collectionType: 'None',
                        isNestedCollection: false
                    })
                    CREATE (salaryVal:AttributeValue {
                        name: 'person' + toString(p) + '_salary',
                        type: 'Integer',
                        value: "'" + toString(2000 + ((p * 137) % 7000)) + "'",
                        isCollection: false,
                        collectionType: 'None',
                        isNestedCollection: false
                    })
                    CREATE (person)-[:ObjectHasAttribute]->(ageVal)
                    CREATE (person)-[:ObjectHasAttribute]->(firstNameVal)
                    CREATE (person)-[:ObjectHasAttribute]->(salaryVal)
                    CREATE (ageVal)-[:InstanceOf]->(ageDef)
                    CREATE (firstNameVal)-[:InstanceOf]->(firstNameDef)
                    CREATE (salaryVal)-[:InstanceOf]->(salaryDef)
                    """, Values.parameters("start", start, "end", end)).consume();
            }

            for (int start = 1; start <= personCount; start += BULK_BATCH_SIZE) {
                int end = Math.min(personCount, start + BULK_BATCH_SIZE - 1);
                session.run("""
                    UNWIND range($start, $end) AS p
                    WITH p, toInteger((p - 1) / $employeesPerCompany) + 1 AS c
                    MATCH (company:Object {use_id: 'company' + toString(c)})
                    MATCH (person:Object {use_id: 'person' + toString(p)})
                    CREATE (company)-[:LinkAssociateWith {
                        name: 'CompanyEmployee',
                        sourceRole: 'employer',
                        targetRole: 'employee',
                        isTernary: false,
                        sourceQualifiers: [],
                        targetQualifiers: []
                    }]->(person)
                    """, Values.parameters(
                        "start", start,
                        "end", end,
                        "employeesPerCompany", employeesPerCompany)).consume();
            }

            for (int start = 1; start <= companyCount; start += BULK_BATCH_SIZE) {
                int end = Math.min(companyCount, start + BULK_BATCH_SIZE - 1);
                session.run("""
                    UNWIND range($start, $end) AS c
                    WITH c, ((c - 1) * $employeesPerCompany) + 1 AS managerIndex
                    MATCH (company:Object {use_id: 'company' + toString(c)})
                    MATCH (manager:Object {use_id: 'person' + toString(managerIndex)})
                    CREATE (company)-[:LinkAssociateWith {
                        name: 'CompanyManager',
                        sourceRole: 'managedCompany',
                        targetRole: 'manager',
                        isTernary: false,
                        sourceQualifiers: [],
                        targetQualifiers: []
                    }]->(manager)
                    """, Values.parameters(
                        "start", start,
                        "end", end,
                        "employeesPerCompany", employeesPerCompany)).consume();
            }
        }
        ScenarioSupport.ensureBenchmarkValidationIndexes();
        return model;
    }

    public static void main(String[] args) throws Exception {
        int companyCount = args.length > 0 ? Integer.parseInt(args[0]) : 10;
        int employeesPerCompany = args.length > 1 ? Integer.parseInt(args[1]) : 20;
        load(companyCount, employeesPerCompany);
        System.out.println("Company benchmark dataset loaded into Neo4j: companies="
                + companyCount + ", employeesPerCompany=" + employeesPerCompany);
    }
}
