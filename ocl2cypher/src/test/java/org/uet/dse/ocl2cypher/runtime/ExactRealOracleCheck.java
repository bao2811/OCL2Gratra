package org.uet.dse.ocl2cypher.runtime;

import java.math.BigDecimal;
import java.math.BigInteger;
import org.uet.dse.ocl2cypher.core.CoreExpr;
import org.uet.dse.ocl2cypher.core.CoreInterpreter;
import org.uet.dse.ocl2cypher.diagnostics.SourceSpan;
import org.uet.dse.ocl2cypher.qcyp.QNode;
import org.uet.dse.ocl2cypher.qcyp.QInterpreter;
import org.uet.dse.ocl2cypher.graph.GraphModel;
import org.uet.dse.ocl2cypher.graph.GraphValueCodec;
import org.uet.dse.ocl2cypher.source.model.SchemaModel;
import org.uet.dse.ocl2cypher.source.model.Snapshot;

/** Dependency-free execution of actual Core/Q numeric paths. */
public final class ExactRealOracleCheck {
    private static void require(boolean ok) { if (!ok) throw new AssertionError(); }
    public static void main(String[] args) {
        var s = SourceSpan.UNKNOWN;
        var schema = SchemaModel.builder("exact").build();
        var snapshot = Snapshot.builder().build();
        var env = new CoreInterpreter.Env();
        int cases = 0;
        for (int n = -12; n <= 12; n++) for (int d = -9; d <= 9; d++) {
            var core = new CoreExpr.Binary(s, CoreExpr.BinaryOp.REAL_DIVIDE,
                    new CoreExpr.LiteralReal(s, BigDecimal.valueOf(n)),
                    new CoreExpr.LiteralReal(s, BigDecimal.valueOf(d)), OclType.REAL);
            var query = new QNode.QExpr.Binary(s, CoreExpr.BinaryOp.REAL_DIVIDE,
                    new QNode.QExpr.Constant(s, OclType.REAL, BigDecimal.valueOf(n)),
                    new QNode.QExpr.Constant(s, OclType.REAL, BigDecimal.valueOf(d)), OclType.REAL);
            var a = CoreInterpreter.eval(schema, snapshot, env, core);
            var b = QInterpreter.evalExpr(schema, new GraphModel("exact"), env, query);
            require(a.equals(b));
            if (d == 0) require(a.isBottom());
            else {
                var x = ((OclValue.RealValue)a).exactValue();
                require(x.numerator().multiply(BigInteger.valueOf(d)).equals(
                        BigInteger.valueOf(n).multiply(x.denominator())));
            }
            cases++;
        }
        var third = new OclValue.RealValue(new ExactReal(BigInteger.ONE, BigInteger.valueOf(3)));
        require(OclOps.numeric(third, new OclValue.RealValue(new BigDecimal("3")), OclType.REAL, "*")
                .equals(new OclValue.RealValue(BigDecimal.ONE)));
        require(third.equals(new OclValue.RealValue(new ExactReal(BigInteger.TWO, BigInteger.valueOf(6)))));
        require(third.hashCode() == new OclValue.RealValue(new ExactReal(BigInteger.TWO, BigInteger.valueOf(6))).hashCode());
        require(OclOps.floor(new OclValue.RealValue(new BigDecimal("-1.5"))).equals(new OclValue.IntegerValue(BigInteger.valueOf(-2))));
        require(OclOps.round(new OclValue.RealValue(new BigDecimal("-1.5"))).equals(new OclValue.IntegerValue(BigInteger.valueOf(-1))));
        boolean rejected = false;
        try { GraphValueCodec.encode(OclType.REAL, third); }
        catch (GraphValueCodec.CodecException e) { rejected = "G_CODEC_REPRESENTABILITY".equals(e.code()); }
        require(rejected);
        System.out.println("PASS: " + cases + " actual Core/Q divisions; exact reconstruction, normalization, rounding, codec rejection");
    }
}
