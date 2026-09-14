package scenarios.experiment_r2_non_binary_buy;

import org.tzi.use.uml.mm.MModel;
import org.uet.dse.neo4jtgg.model.OclFileValidationResult;
import scenarios.common.ScenarioSupport;

import java.nio.file.Path;

public final class RunNonBinaryBuyOclChecks {
    private static final Path SCENARIO_DIR = Path.of("test", "scenarios", "experiment_r2_non_binary_buy");

    private RunNonBinaryBuyOclChecks() {
    }

    public static void main(String[] args) throws Exception {
        MModel model = LoadNonBinaryBuyToNeo4j.load();
        OclFileValidationResult result = ScenarioSupport.validateQueries(model, SCENARIO_DIR.resolve("queries.ocl"));
        ScenarioSupport.assertValidationPassed(result);
        System.out.println(result.toDisplayText());
    }
}
