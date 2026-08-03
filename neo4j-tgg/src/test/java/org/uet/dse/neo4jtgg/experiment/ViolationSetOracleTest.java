package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViolationSetOracleTest {
    private static final Set<String> CONTEXT = Set.of("p1", "p2", "p3");

    @Test
    void classifiesAllCompletedSetRelations() {
        assertStatus(ViolationOracleStatus.EQUIVALENT, Set.of("p1"), Set.of("p1"));
        assertStatus(ViolationOracleStatus.MISSING_ONLY, Set.of("p1", "p2"), Set.of("p1"));
        assertStatus(ViolationOracleStatus.SPURIOUS_ONLY, Set.of("p1"), Set.of("p1", "p2"));
        assertStatus(ViolationOracleStatus.INCOMPARABLE, Set.of("p1"), Set.of("p2"));
    }

    @Test
    void exposesMissingAndSpuriousWitnessesWithTheoremSixOrientation() {
        ViolationOracleResult result = ViolationSetOracle.compare(
                "Person::Adult", CONTEXT, Set.of("p1", "p2"), Set.of("p2", "p3"));

        assertEquals(Set.of("p1"), result.missingIds());
        assertEquals(Set.of("p3"), result.spuriousIds());
        assertEquals(ViolationOracleStatus.INCOMPARABLE, result.status());
        assertTrue(result.completed());
        assertFalse(result.equivalent());
        assertTrue(result.render().contains("missing=[p1]"));
    }

    @Test
    void keepsOutOfContextCandidateIdsVisibleToMetrics() {
        ViolationOracleResult result = ViolationSetOracle.compare(
                "Person::Adult", Set.of("p1"), Set.of(), Set.of("company"));

        assertEquals(ViolationOracleStatus.SPURIOUS_ONLY, result.status());
        assertEquals(Set.of("p1", "company"), result.metricUniverseIds());
    }

    @Test
    void classifiesReferenceVacuityWithoutLookingAtCandidateResults() {
        assertEquals(BenchmarkVacuityStatus.EMPTY_CONTEXT,
                ViolationSetOracle.compare("empty", Set.of(), Set.of(), Set.of()).vacuityStatus());
        assertEquals(BenchmarkVacuityStatus.ALL_PASS,
                ViolationSetOracle.compare("pass", CONTEXT, Set.of(), Set.of("p1")).vacuityStatus());
        assertEquals(BenchmarkVacuityStatus.ALL_VIOLATE,
                ViolationSetOracle.compare("fail", CONTEXT, CONTEXT, Set.of()).vacuityStatus());
        assertEquals(BenchmarkVacuityStatus.NON_VACUOUS_MIXED,
                ViolationSetOracle.compare("mixed", CONTEXT, Set.of("p1"), Set.of()).vacuityStatus());
    }

    @Test
    void reportsReferenceAndCypherFailuresSeparately() {
        ViolationSetOracle referenceFailure = new ViolationSetOracle(
                (system, invariant) -> { throw new IllegalArgumentException("bad OCL"); },
                (cypher, parameters) -> Set.of("p1"));
        ViolationOracleResult first = referenceFailure.evaluate(
                "reference-error", CONTEXT, null, null, "RETURN 1", Map.of());
        assertEquals(ViolationOracleStatus.REFERENCE_ERROR, first.status());
        assertTrue(first.referenceError().contains("bad OCL"));

        ViolationSetOracle cypherFailure = new ViolationSetOracle(
                (system, invariant) -> Set.of("p1"),
                (cypher, parameters) -> { throw new IllegalStateException("database unavailable"); });
        ViolationOracleResult second = cypherFailure.evaluate(
                "cypher-error", CONTEXT, null, null, "RETURN 1", Map.of());
        assertEquals(ViolationOracleStatus.CYPHER_ERROR, second.status());
        assertTrue(second.cypherError().contains("database unavailable"));
    }

    private void assertStatus(ViolationOracleStatus expected,
                              Set<String> referenceIds,
                              Set<String> cypherIds) {
        ViolationOracleResult result = ViolationSetOracle.compare(
                "case-" + expected, CONTEXT, referenceIds, cypherIds);
        assertEquals(expected, result.status());
    }
}
