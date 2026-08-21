package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Test;
import org.uet.dse.neo4jtgg.ocl.ir.OclCypherPlan;
import org.uet.dse.neo4jtgg.ocl.ir.CqmAstLoweringContract;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Structural gate for the CQM-to-Raw-Cypher-AST lowering contract catalog. */
class CqmAstLoweringContractTest {
    @Test
    void catalogCoversEverySealedPlanConstructorWithADeclaredAstContract() throws Exception {
        Path root = workspace();
        List<String> constructors = Arrays.stream(OclCypherPlan.ExpressionPlan.class.getPermittedSubclasses())
                .map(Class::getSimpleName).toList();
        CqmAstLoweringContract contract = CqmAstLoweringContract.load(
                root.resolve("verification/coverage/cqm_ast_lowering_rules.csv"));
        var rules = contract.requireComplete(new LinkedHashSet<>(constructors));
        assertEquals(constructors.size(), rules.size());
        for (var rule : rules.values()) {
            assertTrue(Set.of("IMPLEMENTED_UNCERTIFIED", "CERTIFIED", "EXCLUDED")
                    .contains(rule.proofStatus()), rule.sourceConstructor());
        }
    }

    private static Path workspace() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("verification/contract/proof-contract-registry.json"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate workspace root");
    }
}
