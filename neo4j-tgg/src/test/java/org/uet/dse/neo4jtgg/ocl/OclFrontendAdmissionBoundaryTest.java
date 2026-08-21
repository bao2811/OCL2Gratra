package org.uet.dse.neo4jtgg.ocl;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.uet.dse.neo4j.oclite.OclAstParser;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclCodedUnsupportedOperationException;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclDiagnosticCode;

import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Proves by executable examples that parser coverage cannot widen OCL_val. */
class OclFrontendAdmissionBoundaryTest {
    @TestFactory
    Stream<DynamicTest> broadSyntaxIsParsedThenRejectedByClosedAdmission() {
        Map<String, String> cases = Map.of(
                "non-Set collection", "Sequence{1, 2}->notEmpty()",
                "collection range", "Set{1..3}->notEmpty()",
                "invalid literal", "invalid = invalid",
                "unary minus", "-self.age < 0",
                "multiple iterators", "Set{1, 2}->forAll(x, y | x = y)",
                "at-pre call", "self.age@pre >= 0",
                "iterate accumulator", "Set{1, 2}->iterate(x; acc : Integer = 0 | acc + x) > 0");

        return cases.entrySet().stream().map(entry -> DynamicTest.dynamicTest(entry.getKey(), () -> {
            var invariant = OclAstParser.parseInvariant(
                    "context Person inv Outside: " + entry.getValue());
            OclCodedUnsupportedOperationException error = assertThrows(
                    OclCodedUnsupportedOperationException.class,
                    () -> OclValAdmissionPolicy.verify(invariant));
            assertEquals(OclDiagnosticCode.OCL_VAL_EXCLUDED_CONSTRUCT, error.code());
        }));
    }
}
