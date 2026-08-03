package org.uet.dse.neo4jtgg.ocl;

import org.junit.jupiter.api.Test;
import org.uet.dse.neo4jtgg.ocl.ir.OclIr;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OclScalarClosureCheckerTest {
    private static final OclTypeBinding INTEGER = OclTypeBinding.scalar("Integer");
    private static final OclTypeBinding REAL = OclTypeBinding.scalar("Real");

    @Test
    void admitsExactlyTheSupportedFiniteScalarBoundaries() {
        assertEquals(OclScalarClosureChecker.Status.PASS,
                OclScalarClosureChecker.checkValues(List.of(
                        Long.MIN_VALUE, Long.MAX_VALUE, -0.0d, Double.MAX_VALUE,
                        true, false, "", "\uD83D\uDE00"), "boundary").status());
        assertEquals(OclScalarClosureChecker.Status.FAIL,
                OclScalarClosureChecker.checkValues(List.of(Double.NaN), "boundary").status());
        assertEquals(OclScalarClosureChecker.Status.FAIL,
                OclScalarClosureChecker.checkValues(List.of(Double.POSITIVE_INFINITY), "boundary").status());
        assertEquals(OclScalarClosureChecker.Status.FAIL,
                OclScalarClosureChecker.checkValues(List.of(
                        BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)), "boundary").status());
        assertEquals(OclScalarClosureChecker.Status.FAIL,
                OclScalarClosureChecker.checkValues(List.of("\uD800"), "boundary").status());
    }

    @Test
    void detectsIntegerOverflowAndZeroDivisorBeforeExecution() {
        assertEquals(OclScalarClosureChecker.Status.FAIL,
                check(arithmetic("+", literal(Long.MAX_VALUE, INTEGER), literal(1L, INTEGER), INTEGER)).status());
        assertEquals(OclScalarClosureChecker.Status.FAIL,
                check(arithmetic("/", literal(1L, INTEGER), literal(0L, INTEGER), REAL)).status());
    }

    @Test
    void rejectsInexactIntegerToRealCoercion() {
        OclIr.Expression mixed = arithmetic("+",
                literal(9_007_199_254_740_993L, INTEGER), literal(0.5d, REAL), REAL);
        assertEquals(OclScalarClosureChecker.Status.FAIL, check(mixed).status());
    }

    @Test
    void reportsUnobservedDynamicArithmeticAsOutOfScope() {
        OclIr.Expression dynamic = arithmetic("*",
                new OclIr.Variable("x", INTEGER), literal(2L, INTEGER), INTEGER);
        assertEquals(OclScalarClosureChecker.Status.OUT_OF_SCOPE, check(dynamic).status());
    }

    private OclScalarClosureChecker.Result check(OclIr.Expression expression) {
        return OclScalarClosureChecker.check(new OclIr.InvariantQuery("C", "I", expression), null);
    }

    private OclIr.Literal literal(Object value, OclTypeBinding type) {
        return new OclIr.Literal(value, type);
    }

    private OclIr.Binary arithmetic(String operator, OclIr.Expression left,
                                    OclIr.Expression right, OclTypeBinding type) {
        return new OclIr.Binary(operator, left, right, type);
    }
}
