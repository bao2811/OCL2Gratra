package scenarios.experiment_r4_company_benchmark;

import org.tzi.use.api.UseSystemApi;
import org.tzi.use.uml.mm.MModel;
import scenarios.common.ScenarioSupport;

import java.nio.file.Path;

public final class LoadCompanyBenchmarkToNeo4j {
    private static final Path SCENARIO_DIR = Path.of("test", "scenarios", "experiment_r4_company_benchmark");

    private LoadCompanyBenchmarkToNeo4j() {
    }

    public static MModel load(int companyCount, int employeesPerCompany) throws Exception {
        ScenarioSupport.ensureNeo4jConnected();
        ScenarioSupport.resetDatabase();

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

    public static void main(String[] args) throws Exception {
        int companyCount = args.length > 0 ? Integer.parseInt(args[0]) : 10;
        int employeesPerCompany = args.length > 1 ? Integer.parseInt(args[1]) : 20;
        load(companyCount, employeesPerCompany);
        System.out.println("Company benchmark dataset loaded into Neo4j: companies="
                + companyCount + ", employeesPerCompany=" + employeesPerCompany);
    }
}
