package scenarios.experiment_r1_qualified_library;

import org.tzi.use.uml.mm.MModel;
import org.uet.dse.neo4jtgg.model.OclFileValidationResult;
import scenarios.common.ScenarioSupport;

import java.nio.file.Path;

public final class RunQualifiedLibraryOclChecks {
    private static final Path SCENARIO_DIR = Path.of("test", "scenarios", "experiment_r1_qualified_library");

    private RunQualifiedLibraryOclChecks() {
    }

    public static void main(String[] args) throws Exception {
        MModel model = LoadQualifiedLibraryToNeo4j.load();
        OclFileValidationResult result = ScenarioSupport.validateQueries(model, SCENARIO_DIR.resolve("queries.ocl"));
        ScenarioSupport.assertValidationPassed(result);
        System.out.println(result.toDisplayText());
    }
}
