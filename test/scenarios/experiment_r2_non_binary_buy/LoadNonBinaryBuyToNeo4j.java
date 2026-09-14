package scenarios.experiment_r2_non_binary_buy;

import org.tzi.use.api.UseSystemApi;
import org.tzi.use.uml.mm.MModel;
import scenarios.common.ScenarioSupport;

import java.nio.file.Path;

public final class LoadNonBinaryBuyToNeo4j {
    private static final Path SCENARIO_DIR = Path.of("test", "scenarios", "experiment_r2_non_binary_buy");

    private LoadNonBinaryBuyToNeo4j() {
    }

    public static MModel load() throws Exception {
        ScenarioSupport.ensureNeo4jConnected();
        ScenarioSupport.resetDatabase();

        MModel model = ScenarioSupport.compileModel(SCENARIO_DIR.resolve("model.use"));
        UseSystemApi api = ScenarioSupport.createSystemApi(model);

        api.createObject("Person", "buyer1");
        api.createObject("Company", "seller1");
        api.createObject("Animal", "pet1");

        api.setAttributeValue("buyer1", "age", "30");
        api.setAttributeValue("seller1", "name", "'Acme'");
        api.setAttributeValue("pet1", "kind", "'Dog'");

        api.createLink("Buy", "buyer1", "seller1", "pet1");

        ScenarioSupport.pushModelAndObjects(model, api);
        return model;
    }

    public static void main(String[] args) throws Exception {
        load();
        System.out.println("Non-binary buy metamodel and objects loaded into Neo4j.");
    }
}
