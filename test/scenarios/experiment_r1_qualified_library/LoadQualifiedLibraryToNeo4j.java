package scenarios.experiment_r1_qualified_library;

import org.tzi.use.api.UseSystemApi;
import org.tzi.use.uml.mm.MModel;
import scenarios.common.ScenarioSupport;

import java.nio.file.Path;

public final class LoadQualifiedLibraryToNeo4j {
    private static final Path SCENARIO_DIR = Path.of("test", "scenarios", "experiment_r1_qualified_library");

    private LoadQualifiedLibraryToNeo4j() {
    }

    public static MModel load() throws Exception {
        ScenarioSupport.ensureNeo4jConnected();
        ScenarioSupport.resetDatabase();

        MModel model = ScenarioSupport.compileModel(SCENARIO_DIR.resolve("model.use"));
        UseSystemApi api = ScenarioSupport.createSystemApi(model);

        api.createObject("Library", "library1");
        api.createObject("Book", "bookA1");
        api.createObject("Book", "bookB2");

        api.setAttributeValue("library1", "defaultShelf", "'A1'");
        api.setAttributeValue("bookA1", "title", "'Graph Transformations'");
        api.setAttributeValue("bookA1", "pages", "120");
        api.setAttributeValue("bookB2", "title", "'Meta Modeling'");
        api.setAttributeValue("bookB2", "pages", "80");

        api.createLink("Catalog",
                new String[]{"library1", "bookA1"},
                new String[][]{new String[0], new String[]{"'A1'"}});
        api.createLink("Catalog",
                new String[]{"library1", "bookB2"},
                new String[][]{new String[0], new String[]{"'B2'"}});

        ScenarioSupport.pushModelAndObjects(model, api);
        return model;
    }

    public static void main(String[] args) throws Exception {
        load();
        System.out.println("Qualified library metamodel and objects loaded into Neo4j.");
    }
}
