package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Test;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.uml.mm.MModel;
import org.uet.dse.neo4jtgg.service.impl.DefaultOclToCypherCompiler;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Object-side non-vacuity contract fixed independently before real Cypher execution. */
class OclVal47NonVacuityContractTest {
    @Test
    void everyCountermodelCapableCaseIsMixedAndEveryAllPassCaseHasAProfileProofReason()
            throws Exception {
        MModel model = OclVal47NonVacuityFixture.compileModel("OclVal47NonVacuity");
        UseSystemApi api = OclVal47NonVacuityFixture.seed(model);
        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        UseObjectSideReferenceEvaluator reference = new UseObjectSideReferenceEvaluator();
        Map<String, OclVal47NonVacuityFixture.ExpectedCase> expected =
                OclVal47NonVacuityFixture.expectations();
        Map<BenchmarkVacuityStatus, Integer> vacuity = new EnumMap<>(BenchmarkVacuityStatus.class);
        Map<OclVal47NonVacuityFixture.Obligation, Integer> obligations =
                new EnumMap<>(OclVal47NonVacuityFixture.Obligation.class);
        Map<String, OclValFragmentCoverageTest.CoverageCase> actualCases = new LinkedHashMap<>();

        for (OclValFragmentCoverageTest.CoverageCase testCase
                : OclValFragmentCoverageTest.admittedCases()) {
            var invariant = compiler.parseContextInvariants(testCase.ocl()).get(0);
            String id = invariant.className + "::" + invariant.invName;
            OclVal47NonVacuityFixture.ExpectedCase row = expected.get(id);
            assertNotNull(row, "Missing independently reviewed expectation for " + id);
            assertEquals(testCase.feature(), row.feature(), id);
            assertEquals(invariant.className, row.context(), id);
            assertTrue(actualCases.put(id, testCase) == null, "Duplicate admitted case " + id);

            Set<String> context = OclVal47NonVacuityFixture.contextIds(api, invariant.className);
            Set<String> violations = reference.violationIds(api.getSystem(), invariant,
                    OclVal47NonVacuityFixture.expressionSource(testCase.ocl()));
            assertEquals(row.expectedViolations(), violations, id + " independent USE oracle");
            BenchmarkVacuityStatus status = ViolationSetOracle.compare(
                    id, context, violations, violations).vacuityStatus();
            assertEquals(row.expectedClass(), status, id);

            if (row.obligation() == OclVal47NonVacuityFixture.Obligation.MIXED_REQUIRED) {
                assertEquals(BenchmarkVacuityStatus.NON_VACUOUS_MIXED, status, id);
                assertFalse(violations.isEmpty(), id + " needs a violating witness");
                assertFalse(violations.equals(context), id + " needs a satisfying witness");
            } else {
                assertEquals(BenchmarkVacuityStatus.ALL_PASS, status, id);
                assertTrue(violations.isEmpty(), id + " profile tautology must have no fixture violation");
            }
            vacuity.merge(status, 1, Integer::sum);
            obligations.merge(row.obligation(), 1, Integer::sum);
        }

        assertEquals(expected.keySet(), actualCases.keySet());
        assertEquals(47, actualCases.size());
        assertEquals(28, obligations.getOrDefault(
                OclVal47NonVacuityFixture.Obligation.MIXED_REQUIRED, 0));
        assertEquals(19, obligations.getOrDefault(
                OclVal47NonVacuityFixture.Obligation.PROFILE_TAUTOLOGY, 0));
        assertEquals(28, vacuity.getOrDefault(BenchmarkVacuityStatus.NON_VACUOUS_MIXED, 0));
        assertEquals(19, vacuity.getOrDefault(BenchmarkVacuityStatus.ALL_PASS, 0));
        assertEquals(0, vacuity.getOrDefault(BenchmarkVacuityStatus.ALL_VIOLATE, 0));
        assertEquals(0, vacuity.getOrDefault(BenchmarkVacuityStatus.EMPTY_CONTEXT, 0));
    }
}
