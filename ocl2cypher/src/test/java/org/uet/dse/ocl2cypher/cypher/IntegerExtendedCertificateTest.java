package org.uet.dse.ocl2cypher.cypher;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import org.uet.dse.ocl2cypher.core.CoreExpr;
import org.uet.dse.ocl2cypher.diagnostics.SourceSpan;
import org.uet.dse.ocl2cypher.graph.GraphModel;
import org.uet.dse.ocl2cypher.qcyp.*;
import org.uet.dse.ocl2cypher.runtime.OclType;
import static org.junit.jupiter.api.Assertions.*;

class IntegerExtendedCertificateTest {
    private static QNode.QExpr literal(long n) {
        return new QNode.QExpr.Constant(SourceSpan.UNKNOWN, OclType.INTEGER, BigInteger.valueOf(n));
    }
    @Test void absMinMaxCertificatesReachRealization() {
        for (long a : new long[]{Long.MIN_VALUE, -7, 0, 9, Long.MAX_VALUE}) {
            var abs = new QNode.QExpr.Unary(SourceSpan.UNKNOWN, CoreExpr.UnaryOp.NUMERIC_ABS, literal(a), OclType.INTEGER);
            if (a == Long.MIN_VALUE) {
                assertTrue(new IntegerRangeCertificates().certify(abs).isEmpty());
                assertTrue(realize(abs).isFailure());
            } else verify(abs, BigInteger.valueOf(a).abs());
            for (long b : new long[]{Long.MIN_VALUE, -1, 0, Long.MAX_VALUE}) {
                verify(new QNode.QExpr.Binary(SourceSpan.UNKNOWN, CoreExpr.BinaryOp.NUMERIC_MIN,
                        literal(a), literal(b), OclType.INTEGER), BigInteger.valueOf(a).min(BigInteger.valueOf(b)));
                verify(new QNode.QExpr.Binary(SourceSpan.UNKNOWN, CoreExpr.BinaryOp.NUMERIC_MAX,
                        literal(a), literal(b), OclType.INTEGER), BigInteger.valueOf(a).max(BigInteger.valueOf(b)));
            }
        }
    }
    @Test void extremumCannotHideOverflowingChild() {
        var overflow = new QNode.QExpr.Binary(SourceSpan.UNKNOWN, CoreExpr.BinaryOp.NUMERIC_ADD,
                literal(Long.MAX_VALUE), literal(1), OclType.INTEGER);
        var min = new QNode.QExpr.Binary(SourceSpan.UNKNOWN, CoreExpr.BinaryOp.NUMERIC_MIN,
                overflow, literal(0), OclType.INTEGER);
        assertTrue(new IntegerRangeCertificates().certify(min).isEmpty());
        var result = realize(min);
        assertTrue(result.isFailure());
        assertEquals("R_NUMERIC_CAPABILITY", result.primaryDiagnostic().code());
    }
    private static void verify(QNode.QExpr e, BigInteger expected) {
        var c = new IntegerRangeCertificates().certify(e).orElseThrow();
        assertEquals(expected, c.lower());
        assertEquals(expected, c.upper());
        var result = realize(e);
        assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
        Neo4jCypherParserGate.assertParses(Serializer.cypherText(result.value()));
    }
    private static org.uet.dse.ocl2cypher.diagnostics.Result<CypherAst.GeneratedArtifact> realize(QNode.QExpr e) {
        return Realization.realize(new QQuery(e, null, QQuery.QResultShape.SCALAR,
                QQuery.QueryMode.VALUE, e.type, null, null, false), new GraphModel("cert-test"), CypherAst.Dialect.CYPHER_5);
    }
}
