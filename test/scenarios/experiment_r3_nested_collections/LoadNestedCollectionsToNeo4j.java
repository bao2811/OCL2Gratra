package scenarios.experiment_r3_nested_collections;

import org.tzi.use.api.UseSystemApi;
import org.tzi.use.uml.mm.MModel;
import scenarios.common.ScenarioSupport;

import java.nio.file.Path;

public final class LoadNestedCollectionsToNeo4j {
    private static final Path SCENARIO_DIR = Path.of("test", "scenarios", "experiment_r3_nested_collections");

    private LoadNestedCollectionsToNeo4j() {
    }

    public static MModel load() throws Exception {
        ScenarioSupport.ensureNeo4jConnected();
        ScenarioSupport.resetDatabase();

        MModel model = ScenarioSupport.compileModel(SCENARIO_DIR.resolve("model.use"));
        UseSystemApi api = ScenarioSupport.createSystemApi(model);

        api.createObject("Person", "person1");
        api.setAttributeValue(
                "person1",
                "aliases2d",
                "Sequence{Sequence{'Bart','B'}, Sequence{'Lisa','Bart'}}");

        ScenarioSupport.pushModelAndObjects(model, api);
        return model;
    }

    public static void main(String[] args) throws Exception {
        load();
        System.out.println("Nested collection metamodel and objects loaded into Neo4j.");
    }
}
