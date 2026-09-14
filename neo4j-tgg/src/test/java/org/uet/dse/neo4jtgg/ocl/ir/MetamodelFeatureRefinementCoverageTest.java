package org.uet.dse.neo4jtgg.ocl.ir;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetamodelFeatureRefinementCoverageTest {
    @Test
    void everyDeclaredOvaAndCqmFeatureHasOneExplicitPolicy() throws Exception {
        Fixture fixture = fixture();
        var report = MetamodelFeatureRefinementCoverage.validate(fixture.models(), fixture.rows());
        assertTrue(report.valid(), report.errors()::toString);
        assertTrue(report.declaredCount() >= 140, "Unexpectedly small structural-feature inventory");
    }

    @Test
    void missingWrongKindWrongCardinalityAndIllegalErasureMutationsAreKilled() throws Exception {
        Fixture fixture = fixture();
        List<String> missing = new ArrayList<>(fixture.rows());
        missing.remove(missing.size() - 1);
        assertFalse(MetamodelFeatureRefinementCoverage.validate(fixture.models(), missing).valid());

        assertMutationKilled(fixture, "CQM;InvariantPlan;predicate;val;1;mapped",
                "CQM;InvariantPlan;predicate;ref;1;mapped");
        assertMutationKilled(fixture, "OVA;SetExpression;elements;val;*;mapped",
                "OVA;SetExpression;elements;val;1;mapped");
        assertMutationKilled(fixture, "OVA;Invariant;predicate;val;1;mapped",
                "OVA;Invariant;predicate;val;1;erased");
    }

    private void assertMutationKilled(Fixture fixture, String before, String after) {
        List<String> rows = fixture.rows().stream().map(row -> row.replace(before, after)).toList();
        assertFalse(MetamodelFeatureRefinementCoverage.validate(fixture.models(), rows).valid());
    }

    private Fixture fixture() throws Exception {
        Path root = workspaceRoot();
        return new Fixture(Map.of(
                "OVA", Files.readString(root.resolve("md/research/model/OCL-Validation-Algebra.emf")),
                "CQM", Files.readString(root.resolve("md/research/model/Cypher-Query-Model.emf"))),
                Files.readAllLines(root.resolve("verification/coverage/ova_cqm_total_feature_refinement.csv")));
    }

    private Path workspaceRoot() {
        Path working = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (Files.isDirectory(working.resolve("md/research/model"))) return working;
        if (working.getParent() != null && Files.isDirectory(working.getParent().resolve("md/research/model"))) {
            return working.getParent();
        }
        throw new IllegalStateException("Cannot locate workspace root from " + working);
    }

    private record Fixture(Map<String, String> models, List<String> rows) { }
}
