package org.uet.dse.ocl2cypher.qcyp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.uet.dse.ocl2cypher.core.CoreDeclaration;
import org.uet.dse.ocl2cypher.core.CoreExpr;
import org.uet.dse.ocl2cypher.core.CoreInvariant;
import org.uet.dse.ocl2cypher.core.CoreQuery;
import org.uet.dse.ocl2cypher.diagnostics.SourceSpan;
import org.uet.dse.ocl2cypher.runtime.OclType;

class T8BatchTest {
    private static final SourceSpan S = SourceSpan.UNKNOWN;

    @Test
    void successfulBatchUsesSourceOrderRatherThanModeMapOrder() {
        var units = List.of(
                new QBatchTranslator.BatchUnit("inv-a", invariant("A", bool(true))),
                new QBatchTranslator.BatchUnit("query-b", query(integer(7))),
                new QBatchTranslator.BatchUnit("inv-c", invariant("C", bool(false))));
        Map<String, QQuery.QueryMode> modes = new LinkedHashMap<>();
        modes.put("inv-c", QQuery.QueryMode.VIOLATIONS);
        modes.put("inv-a", QQuery.QueryMode.VIOLATIONS);
        modes.put("query-b", QQuery.QueryMode.VALUE);

        var result = QBatchTranslator.translate(units, modes);

        assertTrue(result.isSuccess(), () -> "batch: " + result.diagnostics());
        assertEquals(List.of("inv-a", "query-b", "inv-c"),
                result.value().stream().map(QBatchTranslator.BatchEntry::unitId).toList());
        assertEquals(List.of(
                        QQuery.QueryMode.VIOLATIONS,
                        QQuery.QueryMode.VALUE,
                        QQuery.QueryMode.VIOLATIONS),
                result.value().stream().map(entry -> entry.query().mode()).toList());
    }

    @Test
    void emptyBatchIsARealSuccess() {
        var result = QBatchTranslator.translate(List.of(), Map.of());
        assertTrue(result.isSuccess());
        assertTrue(result.value().isEmpty());
    }

    @Test
    void exactModeDomainAndUniqueUnitIdsAreValidatedBeforeTranslation() {
        var unit = new QBatchTranslator.BatchUnit("a", invariant("A", bool(true)));
        assertFailure(QBatchTranslator.translate(
                java.util.Arrays.asList((QBatchTranslator.BatchUnit) null), Map.of()),
                "T_BATCH_UNIT");
        assertFailure(QBatchTranslator.translate(List.of(unit), Map.of()), "T_MODE_MAP");
        assertFailure(QBatchTranslator.translate(
                List.of(unit), Map.of("a", QQuery.QueryMode.VIOLATIONS,
                        "extra", QQuery.QueryMode.VALUE)), "T_MODE_MAP");

        Map<String, QQuery.QueryMode> nullMode = new LinkedHashMap<>();
        nullMode.put("a", null);
        assertFailure(QBatchTranslator.translate(List.of(unit), nullMode), "T_MODE_MAP");
        assertFailure(QBatchTranslator.translate(List.of(unit, unit),
                Map.of("a", QQuery.QueryMode.VIOLATIONS)), "T_DUPLICATE_UNIT_ID");
    }

    @Test
    void incompatibleModeFailsWithoutPublishingSuccessfulPrefix() {
        var units = List.of(
                new QBatchTranslator.BatchUnit("first", invariant("A", bool(true))),
                new QBatchTranslator.BatchUnit("middle", query(integer(1))),
                new QBatchTranslator.BatchUnit("last", invariant("C", bool(true))));
        var result = QBatchTranslator.translate(units, Map.of(
                "first", QQuery.QueryMode.VIOLATIONS,
                "middle", QQuery.QueryMode.VIOLATIONS,
                "last", QQuery.QueryMode.VIOLATIONS));

        assertFailure(result, "T_MODE_INCOMPATIBLE");
        assertFalse(result.hasValue());
        assertEquals("middle", result.primaryDiagnostic().sourceId().orElseThrow());
    }

    @Test
    void firstMiddleAndLastTranslationFailuresCarryTheFailingUnitId() {
        for (int failureIndex = 0; failureIndex < 3; failureIndex++) {
            var units = new java.util.ArrayList<QBatchTranslator.BatchUnit>();
            var modes = new LinkedHashMap<String, QQuery.QueryMode>();
            for (int index = 0; index < 3; index++) {
                String id = "u" + index;
                CoreInvariant invariant = index == failureIndex
                        ? invariant("Bad", integer(1)) : invariant("Good", bool(true));
                units.add(new QBatchTranslator.BatchUnit(id, invariant));
                modes.put(id, QQuery.QueryMode.VIOLATIONS);
            }
            var result = QBatchTranslator.translate(units, modes);
            assertFailure(result, "T_G_SYNTAX");
            assertFalse(result.hasValue());
            assertEquals("u" + failureIndex,
                    result.primaryDiagnostic().sourceId().orElseThrow());
        }
    }

    private static void assertFailure(
            org.uet.dse.ocl2cypher.diagnostics.Result<?> result, String code) {
        assertTrue(result.isFailure());
        assertEquals(code, result.primaryDiagnostic().code());
    }

    private static CoreInvariant invariant(String name, CoreExpr body) {
        CoreDeclaration self = new CoreDeclaration(
                name.hashCode(), "self", CoreDeclaration.Kind.SELF, OclType.clazz("C"));
        return new CoreInvariant(name, "C", self, body);
    }

    private static CoreQuery query(CoreExpr body) {
        return new CoreQuery(null, null, body);
    }

    private static CoreExpr bool(boolean value) {
        return new CoreExpr.LiteralBoolean(S, value);
    }

    private static CoreExpr integer(int value) {
        return new CoreExpr.LiteralInteger(S, BigInteger.valueOf(value));
    }
}
