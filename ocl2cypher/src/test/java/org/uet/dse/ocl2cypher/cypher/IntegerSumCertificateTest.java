package org.uet.dse.ocl2cypher.cypher;

import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.uet.dse.ocl2cypher.core.CoreExpr;
import org.uet.dse.ocl2cypher.diagnostics.SourceSpan;
import org.uet.dse.ocl2cypher.graph.GraphModel;
import org.uet.dse.ocl2cypher.qcyp.*;
import org.uet.dse.ocl2cypher.runtime.OclType;
import static org.junit.jupiter.api.Assertions.*;

class IntegerSumCertificateTest {
    private static QNode.QExpr lit(long n) {
        return new QNode.QExpr.Constant(SourceSpan.UNKNOWN, OclType.INTEGER, BigInteger.valueOf(n));
    }
    private static QNode.QExpr sum(boolean set, boolean canonical, QNode.QExpr... elements) {
        var source = new QNode.QExpr.CollectionLiteral(SourceSpan.UNKNOWN,
                set ? CoreExpr.CollectionKind.SET : CoreExpr.CollectionKind.BAG,
                List.of(elements), set ? OclType.set(OclType.INTEGER) : OclType.bag(OclType.INTEGER));
        return canonical ? new QNode.QExpr.CountFamily(SourceSpan.UNKNOWN, QNode.QKind.SUM,
                source, null, OclType.INTEGER) : new QNode.QExpr.Unary(SourceSpan.UNKNOWN,
                CoreExpr.UnaryOp.COLLECTION_SUM, source, OclType.INTEGER);
    }
    private static org.uet.dse.ocl2cypher.diagnostics.Result<CypherAst.GeneratedArtifact> realize(QNode.QExpr e) {
        return Realization.realize(new QQuery(e, null, QQuery.QResultShape.SCALAR,
                QQuery.QueryMode.VALUE, e.type, null, null, false), new GraphModel("sum"), CypherAst.Dialect.CYPHER_5);
    }
    private static void accepted(QNode.QExpr e, BigInteger expected) {
        var c = new IntegerRangeCertificates().certify(e).orElseThrow();
        assertEquals(expected, c.lower());
        assertEquals(expected, c.upper());
        var r = realize(e);
        assertTrue(r.isSuccess(), () -> r.diagnostics().toString());
        String text = Serializer.cypherText(r.value());
        assertTrue(text.contains("reduce("));
        Neo4jCypherParserGate.assertParses(text);
    }
    @Test void emptyDuplicatesSignsAndBothQForms() {
        for (boolean canonical : List.of(false, true)) {
            accepted(sum(false, canonical), BigInteger.ZERO);
            accepted(sum(true, canonical), BigInteger.ZERO);
            accepted(sum(false, canonical, lit(2), lit(2), lit(-1)), BigInteger.valueOf(3));
            accepted(sum(true, canonical, lit(2), lit(2), lit(-1)), BigInteger.ONE);
            accepted(sum(true, canonical, lit(Long.MAX_VALUE), lit(Long.MAX_VALUE)), BigInteger.valueOf(Long.MAX_VALUE));
            accepted(sum(false, canonical, lit(Long.MIN_VALUE), lit(1)), BigInteger.valueOf(Long.MIN_VALUE).add(BigInteger.ONE));
            var two = new QNode.QExpr.Binary(SourceSpan.UNKNOWN, CoreExpr.BinaryOp.NUMERIC_ADD,
                    lit(1), lit(1), OclType.INTEGER);
            accepted(sum(true, canonical, lit(2), two), BigInteger.TWO);
        }
    }
    @Test void overflowCannotBeHiddenByCancellationOrBottom() {
        for (var e : List.of(
                sum(false, true, lit(Long.MAX_VALUE), lit(1), lit(-1)),
                sum(false, true, lit(Long.MIN_VALUE), lit(-1), lit(1)),
                sum(false, true, lit(Long.MAX_VALUE), lit(Long.MAX_VALUE)),
                sum(false, true, new QNode.QExpr.Bottom(SourceSpan.UNKNOWN, OclType.INTEGER)),
                new QNode.QExpr.Unary(SourceSpan.UNKNOWN, CoreExpr.UnaryOp.COLLECTION_SUM,
                        new QNode.QExpr.Bottom(SourceSpan.UNKNOWN, OclType.bag(OclType.INTEGER)), OclType.INTEGER))) {
            assertTrue(new IntegerRangeCertificates().certify(e).isEmpty());
            var r = realize(e);
            assertTrue(r.isFailure());
            assertEquals("R_NUMERIC_CAPABILITY", r.primaryDiagnostic().code());
        }
    }
}
