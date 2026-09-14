package org.uet.dse.ocl2cypher.cypher;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.uet.dse.ocl2cypher.core.CoreExpr;
import org.uet.dse.ocl2cypher.diagnostics.SourceSpan;
import org.uet.dse.ocl2cypher.graph.GraphModel;
import org.uet.dse.ocl2cypher.qcyp.QNode;
import org.uet.dse.ocl2cypher.qcyp.QQuery;
import org.uet.dse.ocl2cypher.runtime.OclType;

/**
 * Runs the actual R entry point, not a mathematical witness implementation.
 */
public class NumericCapabilityGateCheck {

    private static final SourceSpan S = SourceSpan.UNKNOWN;

    private static QNode.QExpr integer(BigInteger value) {
        return new QNode.QExpr.Constant(S, OclType.INTEGER, value);
    }

    private static void check(QNode.QExpr expression, boolean accepted) {
        var query = new QQuery(expression, null, QQuery.QResultShape.SCALAR,
                QQuery.QueryMode.VALUE, expression.type, null, null, false);
        var result = Realization.realize(query, new GraphModel("numeric-test"),
                CypherAst.Dialect.CYPHER_5);
        if (result.isSuccess() != accepted) {
            throw new AssertionError("Unexpected R result: " + result.diagnostics());
        }
        String expectedCode = expression.type.equals(OclType.REAL)
                ? org.uet.dse.ocl2cypher.diagnostics.RuleId.R_REAL_EXACT_UNSUPPORTED
                : "R_NUMERIC_CAPABILITY";
        if (!accepted && !expectedCode.equals(result.primaryDiagnostic().code())) {
            throw new AssertionError("Wrong rejection: " + result.diagnostics());
        }
    }

    private static void runCases() {
        check(integer(BigInteger.valueOf(Long.MIN_VALUE)), true);
        check(integer(BigInteger.valueOf(Long.MAX_VALUE)), true);
        check(integer(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)), false);
        check(integer(BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.ONE)), false);
        check(new QNode.QExpr.Unary(S, CoreExpr.UnaryOp.NUMERIC_NEGATE,
                integer(BigInteger.valueOf(Long.MIN_VALUE)), OclType.INTEGER), false);
        check(new QNode.QExpr.Unary(S, CoreExpr.UnaryOp.NUMERIC_NEGATE,
                integer(BigInteger.valueOf(Long.MAX_VALUE)), OclType.INTEGER), true);
        check(new QNode.QExpr.Unary(S, CoreExpr.UnaryOp.NUMERIC_NEGATE,
                integer(BigInteger.ZERO), OclType.INTEGER), true);
        var overflowingNegation = new QNode.QExpr.Unary(S, CoreExpr.UnaryOp.NUMERIC_NEGATE,
                integer(BigInteger.valueOf(Long.MIN_VALUE)), OclType.INTEGER);
        check(new QNode.QExpr.Unary(S, CoreExpr.UnaryOp.NUMERIC_NEGATE,
                overflowingNegation, OclType.INTEGER), false);
        check(new QNode.QExpr.Constant(S, OclType.REAL, new BigDecimal("0.5")), true);
        check(new QNode.QExpr.Constant(S, OclType.REAL, new BigDecimal("0.1")), false);
        check(new QNode.QExpr.Bottom(S, OclType.REAL), true);
        check(new QNode.QExpr.Constant(S, OclType.BOOLEAN, true), true);
        var add = new QNode.QExpr.Binary(S, CoreExpr.BinaryOp.NUMERIC_ADD,
                integer(BigInteger.ONE), integer(BigInteger.ONE), OclType.INTEGER);
        check(add, true);
        // Nested numeric operation cannot bypass the gate via a Boolean root.
        check(new QNode.QExpr.Binary(S, CoreExpr.BinaryOp.VALUE_EQUAL,
                add, integer(BigInteger.TWO), OclType.BOOLEAN), true);
        var overflow = new QNode.QExpr.Binary(S, CoreExpr.BinaryOp.NUMERIC_ADD,
                integer(BigInteger.valueOf(Long.MAX_VALUE)), integer(BigInteger.ONE), OclType.INTEGER);
        check(overflow, false);
        // Final result fits, but the child overflow must not be hidden.
        check(new QNode.QExpr.Binary(S, CoreExpr.BinaryOp.NUMERIC_SUBTRACT,
                overflow, integer(BigInteger.ONE), OclType.INTEGER), false);
        check(new QNode.QExpr.Binary(S, CoreExpr.BinaryOp.NUMERIC_MULTIPLY,
                integer(BigInteger.valueOf(Long.MIN_VALUE)), integer(BigInteger.valueOf(-1)), OclType.INTEGER), false);
        check(new QNode.QExpr.Binary(S, CoreExpr.BinaryOp.NUMERIC_MULTIPLY,
                integer(BigInteger.valueOf(-7)), integer(BigInteger.valueOf(3)), OclType.INTEGER), true);
        check(new QNode.QExpr.Binary(S, CoreExpr.BinaryOp.NUMERIC_SUBTRACT,
                integer(BigInteger.valueOf(-7)), integer(BigInteger.valueOf(3)), OclType.INTEGER), true);
    }

    /**
     * Fallback when Maven dependencies are unavailable; same cases as JUnit.
     */
    public static void main(String[] args) {
        runCases();
        int rangeCases = 0;
        for (int x = -12; x <= 12; x++) {
            for (int y = -12; y <= 12; y++) {
                for (var op : new CoreExpr.BinaryOp[]{CoreExpr.BinaryOp.NUMERIC_ADD,
                    CoreExpr.BinaryOp.NUMERIC_SUBTRACT, CoreExpr.BinaryOp.NUMERIC_MULTIPLY}) {
                    var expression = new QNode.QExpr.Binary(S, op, integer(BigInteger.valueOf(x)),
                            integer(BigInteger.valueOf(y)), OclType.INTEGER);
                    var cert = new IntegerRangeCertificates().certify(expression).orElseThrow();
                    long expected = switch (op) {
                        case NUMERIC_ADD ->
                            (long) x + y;
                        case NUMERIC_SUBTRACT ->
                            (long) x - y;
                        default ->
                            (long) x * y;
                    };
                    if (!cert.lower().equals(BigInteger.valueOf(expected))
                            || !cert.upper().equals(cert.lower()) || cert.children().size() != 2) {
                        throw new AssertionError("Invalid derived certificate");
                    }
                    rangeCases++;
                }
            }
        }
        System.out.println("PASS: actual Realization gate including negation boundaries; " + rangeCases + " derived range certificates");
    }
}
