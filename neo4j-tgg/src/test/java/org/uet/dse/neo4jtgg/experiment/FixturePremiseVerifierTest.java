package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Test;
import org.uet.dse.neo4jtgg.ocl.OclBottomToken;
import org.uet.dse.neo4jtgg.ocl.OclExecutionPremiseChecker;
import org.uet.dse.neo4jtgg.service.impl.DefaultOclToCypherCompiler;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FixturePremiseVerifierTest {
    @Test
    void acceptsObservedNonZeroDivisorsAndRejectsReachableOrLiteralZeroDivisors() throws Exception {
        var model = Ocl2CypherCaseStudyTest.compileModel(
                Ocl2CypherCaseStudyTest.CASE_STUDY.resolve("company.use"));
        var system = Ocl2CypherCaseStudyTest.loadSoil(model,
                Ocl2CypherCaseStudyTest.CASE_STUDY.resolve("company.soil"));
        var compiler = new DefaultOclToCypherCompiler(model);

        var safe = compiler.compileInvariantInstrumented(
                "context Person inv SafeDivisor: self.age / 1 >= 0.0");
        assertDoesNotThrow(() -> FixturePremiseVerifier.verify(system, safe));

        var dynamicNonZero = compiler.compileInvariantInstrumented(
                "context Person inv DynamicNonZero: self.age / self.age >= 0.0");
        assertDoesNotThrow(() -> FixturePremiseVerifier.verify(system, dynamicNonZero));

        var dynamicZero = compiler.compileInvariantInstrumented(
                "context Person inv DynamicZero: self.age / (self.age - 17) >= 0.0");
        assertThrows(IllegalStateException.class, () -> FixturePremiseVerifier.verify(system, dynamicZero));

        var zero = compiler.compileInvariantInstrumented(
                "context Person inv ZeroDivisor: self.age / 0 >= 0.0");
        assertThrows(IllegalStateException.class, () -> FixturePremiseVerifier.verify(system, zero));
    }

    @Test
    void generatedBottomGateRejectsDuplicateAndNestedTokens() {
        assertThrows(IllegalStateException.class,
                () -> OclExecutionPremiseChecker.requireGeneratedBottomSeparated(Map.of(
                        "bottom1", OclBottomToken.value(),
                        "bottom2", OclBottomToken.value())));
        assertThrows(IllegalArgumentException.class,
                () -> OclExecutionPremiseChecker.requireGeneratedBottomSeparated(Map.of(
                        "nested", Map.of("value", OclBottomToken.value()))));
    }
}
