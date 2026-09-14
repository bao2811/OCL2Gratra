package org.uet.dse.ocl2cypher.cypher;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import org.uet.dse.ocl2cypher.core.CoreExpr;
import org.uet.dse.ocl2cypher.diagnostics.SourceSpan;
import org.uet.dse.ocl2cypher.graph.GraphModel;
import org.uet.dse.ocl2cypher.qcyp.*;
import org.uet.dse.ocl2cypher.runtime.OclType;
import static org.junit.jupiter.api.Assertions.*;

class IntegerDivModCertificateTest {
    private static QNode.QExpr lit(long n) {
        return new QNode.QExpr.Constant(SourceSpan.UNKNOWN, OclType.INTEGER, BigInteger.valueOf(n));
    }
    private static QNode.QExpr expr(CoreExpr.BinaryOp op, QNode.QExpr a, QNode.QExpr b) {
        return new QNode.QExpr.Binary(SourceSpan.UNKNOWN, op, a, b, OclType.INTEGER);
    }
    private static org.uet.dse.ocl2cypher.diagnostics.Result<CypherAst.GeneratedArtifact> realize(QNode.QExpr e) {
        return Realization.realize(new QQuery(e, null, QQuery.QResultShape.SCALAR,
                QQuery.QueryMode.VALUE, e.type, null, null, false), new GraphModel("divmod"), CypherAst.Dialect.CYPHER_5);
    }
    @Test void exactSignedDivisionAndRemainderReachParserWithoutNativeDivision() {
        long[] values = {Long.MIN_VALUE, Long.MAX_VALUE, -7, -3, -1, 0, 1, 2, 3, 7};
        for (long a : values) for (long b : values) {
            if (b == 0 || (a == Long.MIN_VALUE && b == -1)) continue;
            for (var op : new CoreExpr.BinaryOp[]{CoreExpr.BinaryOp.INTEGER_DIVIDE, CoreExpr.BinaryOp.INTEGER_MOD}) {
                var expression = expr(op, lit(a), lit(b));
                var certificate = new IntegerRangeCertificates().certify(expression).orElseThrow();
                // Independent Java integer operators, excluding their overflow case.
                BigInteger expected = BigInteger.valueOf(op == CoreExpr.BinaryOp.INTEGER_DIVIDE ? a / b : a % b);
                assertEquals(expected, certificate.lower());
                assertEquals(expected, certificate.upper());
                assertEquals(4, certificate.children().size());
                var result = realize(expression);
                assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
                String text = Serializer.cypherText(result.value());
                assertFalse(text.contains(" / "));
                assertFalse(text.contains(" % "));
                Neo4jCypherParserGate.assertParses(text);
            }
        }
    }
    @Test void invalidAndUncertifiedInputsStayRejected() {
        for (var op : new CoreExpr.BinaryOp[]{CoreExpr.BinaryOp.INTEGER_DIVIDE, CoreExpr.BinaryOp.INTEGER_MOD}) {
            for (var e : new QNode.QExpr[]{expr(op, lit(1), lit(0)), expr(op, lit(Long.MIN_VALUE), lit(-1)),
                    expr(op, expr(CoreExpr.BinaryOp.NUMERIC_ADD, lit(Long.MAX_VALUE), lit(1)), lit(2))}) {
                assertTrue(new IntegerRangeCertificates().certify(e).isEmpty());
                var result = realize(e);
                assertTrue(result.isFailure());
                assertEquals("R_NUMERIC_CAPABILITY", result.primaryDiagnostic().code());
            }
        }
    }
    @Test void certificatesComposeThroughDivision() {
        var e = expr(CoreExpr.BinaryOp.NUMERIC_ADD,
                expr(CoreExpr.BinaryOp.INTEGER_DIVIDE, lit(-7), lit(3)), lit(5));
        assertEquals(BigInteger.valueOf(3), new IntegerRangeCertificates().certify(e).orElseThrow().lower());
        assertTrue(realize(e).isSuccess());
    }
}
