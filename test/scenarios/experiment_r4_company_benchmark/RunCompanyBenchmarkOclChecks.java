package scenarios.experiment_r4_company_benchmark;

import org.tzi.use.uml.mm.MModel;
import org.uet.dse.neo4jtgg.model.OclFileValidationResult;
import scenarios.common.ScenarioSupport;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class RunCompanyBenchmarkOclChecks {
    private static final Path SCENARIO_DIR = Path.of("test", "scenarios", "experiment_r4_company_benchmark");

    private RunCompanyBenchmarkOclChecks() {
    }

    public static void main(String[] args) throws Exception {
        List<Scale> scales = args.length == 0
                ? List.of(new Scale(50, 20), new Scale(500, 20), new Scale(5000, 20))
                : parseScales(args);

        StringBuilder report = new StringBuilder();
        report.append("\n=== Company Benchmark OCL Validation ===\n");
        report.append(String.format("%-12s %-12s %-12s %-12s %-14s %-14s %-14s %-8s %-8s %-10s %-10s %-14s %-14s\n",
                "Companies", "Persons", "Objects", "Load(ms)", "Response(ms)", "Compile(ms)", "Execute(ms)",
                "Pass", "Fail", "Fallback", "Mismatch", "HeapDelta(MB)", "HeapUsed(MB)"));

        for (Scale scale : scales) {
            long heapBeforeMb = usedHeapMb();
            long loadStart = System.nanoTime();
            MModel model = LoadCompanyBenchmarkToNeo4j.loadFast(scale.companyCount(), scale.employeesPerCompany());
            long loadMs = nanosToMillis(System.nanoTime() - loadStart);

            OclFileValidationResult result = ScenarioSupport.validateQueries(model, SCENARIO_DIR.resolve("queries.ocl"));
            int personCount = scale.companyCount() * scale.employeesPerCompany();
            int objectCount = scale.companyCount() + personCount;
            long heapAfterMb = usedHeapMb();
            long heapDeltaMb = heapAfterMb - heapBeforeMb;

            report.append(String.format("%-12d %-12d %-12d %-12d %-14d %-14d %-14d %-8d %-8d %-10d %-10d %-14d %-14d\n",
                    scale.companyCount(),
                    personCount,
                    objectCount,
                    loadMs,
                    result.getResponseTimeMs(),
                    result.getCompileTimeMs(),
                    result.getExecutionTimeMs(),
                    result.getPassCount(),
                    result.getFailCount(),
                    result.getFallbackCount(),
                    result.getDualCheckMismatchCount(),
                    heapDeltaMb,
                    heapAfterMb));

            if (!result.isSuccess()) {
                report.append("\nValidation failed for scale ")
                        .append(scale.label())
                        .append(":\n")
                        .append(result.toDisplayText())
                        .append('\n');
            }
        }

        System.out.println(report);
    }

    private static List<Scale> parseScales(String[] args) {
        List<Scale> scales = new ArrayList<>(args.length);
        for (String arg : args) {
            String[] parts = arg.toLowerCase().split("x");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid scale `" + arg + "`. Expected format <companies>x<employeesPerCompany>.");
            }
            int companies = Integer.parseInt(parts[0].trim());
            int employeesPerCompany = Integer.parseInt(parts[1].trim());
            scales.add(new Scale(companies, employeesPerCompany));
        }
        return scales;
    }

    private static long nanosToMillis(long nanos) {
        return nanos / 1_000_000L;
    }

    private static long usedHeapMb() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L);
    }

    private record Scale(int companyCount, int employeesPerCompany) {
        private String label() {
            return companyCount + "x" + employeesPerCompany;
        }
    }
}
