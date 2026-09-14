package org.uet.dse.neo4jtgg.ocl.ir;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecPlanSimContractTest {
    @Test
    void everyReachableNvaConstructorHasExactlyOneCompleteCqmRule() throws Exception {
        Path root = workspaceRoot();
        var nva = DynamicEmfModelValidator.load(
                root.resolve("md/research/specification/metamodel/Normalized-Validation-Algebra.ecore"),
                root.resolve("verification/instances/nva-certified-v1.xmi"));
        var cqm = DynamicEmfModelValidator.load(
                root.resolve("md/research/model/Cypher-Query-Model.ecore"),
                root.resolve("verification/instances/cqm-certified-v1.xmi"));
        var report = SpecPlanSimContract.validate(nva.metamodel(), cqm.metamodel());
        assertTrue(report.valid(), report.errors()::toString);
        assertEquals(26, report.constructors().size());
        assertEquals(report.constructors().size(), SpecPlanSimContract.rules().size());
        assertEquals(SpecPlanSimContract.rules().size(),
                new HashSet<>(SpecPlanSimContract.rules().stream().map(SpecPlanSimContract.Rule::ruleId).toList()).size());
    }

    private Path workspaceRoot() {
        Path working = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (Files.isDirectory(working.resolve("md/research/model"))) return working;
        if (working.getParent() != null && Files.isDirectory(working.getParent().resolve("md/research/model"))) {
            return working.getParent();
        }
        throw new IllegalStateException("Cannot locate workspace root from " + working);
    }
}
