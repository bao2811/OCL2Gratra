package org.uet.dse.ocl2cypher.qcyp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.uet.dse.ocl2cypher.core.CoreDeclaration;
import org.uet.dse.ocl2cypher.core.CoreExpr;
import org.uet.dse.ocl2cypher.core.CoreInterpreter;
import org.uet.dse.ocl2cypher.diagnostics.SourceSpan;
import org.uet.dse.ocl2cypher.graph.GraphModel;
import org.uet.dse.ocl2cypher.runtime.OclEquality;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.runtime.OclValue;
import org.uet.dse.ocl2cypher.source.model.SchemaModel;
import org.uet.dse.ocl2cypher.source.model.Snapshot;
import org.uet.dse.ocl2cypher.source.model.UmlClass;

/** Executable refinement evidence for F-4/L-COERCE on the admitted carrier. */
class CoercionRefinementTest {
    private static final SourceSpan S = SourceSpan.UNKNOWN;
    private static final SchemaModel SCHEMA = SchemaModel.builder("coercion")
            .clazz(UmlClass.of("Parent"))
            .clazz(UmlClass.of("Child", "Parent"))
            .build();
    private static final Snapshot SNAPSHOT = Snapshot.builder().build();
    private static final GraphModel GRAPH = new GraphModel("coercion");

    /** Allows the proof witness to run even when Maven plugin resolution is unavailable. */
    public static void main(String[] args) {
        CoercionRefinementTest test = new CoercionRefinementTest();
        test.integerToRealAndTypedBottomCommuteFromCoreToQ();
        test.classUpcastPreservesStableIdentityAndRetagsBottom();
        test.collectionElementCoercionPreservesKindOccurrencesAndWholeBottom();
        System.out.println("PASS: F-4 Core/Q coercion refinement; 3 groups, 7 carrier cases");
    }

    @Test
    void integerToRealAndTypedBottomCommuteFromCoreToQ() {
        assertCoreQAgreement(new CoreExpr.Coerce(S,
                CoreExpr.CoercionKind.INTEGER_TO_REAL, OclType.INTEGER,
                new CoreExpr.LiteralInteger(S, BigInteger.valueOf(7)), OclType.REAL),
                new CoreInterpreter.Env());
        assertCoreQAgreement(new CoreExpr.Coerce(S,
                CoreExpr.CoercionKind.INTEGER_TO_REAL, OclType.INTEGER,
                new CoreExpr.Bottom(S, OclType.INTEGER), OclType.REAL),
                new CoreInterpreter.Env());
    }

    @Test
    void classUpcastPreservesStableIdentityAndRetagsBottom() {
        CoreDeclaration value = new CoreDeclaration(1, "value",
                CoreDeclaration.Kind.PARAMETER, OclType.clazz("Child"));
        CoreExpr.Coerce upcast = new CoreExpr.Coerce(S,
                CoreExpr.CoercionKind.CLASS_UPCAST, OclType.clazz("Child"),
                new CoreExpr.Variable(S, value), OclType.clazz("Parent"));

        CoreInterpreter.Env defined = new CoreInterpreter.Env();
        defined.bind(value, new OclValue.ObjectValue(OclType.clazz("Child"), "object-1"));
        OclValue result = assertCoreQAgreement(upcast, defined);
        assertEquals(OclType.clazz("Parent"), result.type());
        assertEquals("object-1", ((OclValue.ObjectValue) result).stableId());

        CoreInterpreter.Env bottom = new CoreInterpreter.Env();
        bottom.bind(value, new OclValue.BottomValue(OclType.clazz("Child")));
        OclValue bottomResult = assertCoreQAgreement(upcast, bottom);
        assertEquals(true, bottomResult.isBottom());
        assertEquals(OclType.clazz("Parent"), bottomResult.type());
    }

    @Test
    void collectionElementCoercionPreservesKindOccurrencesAndWholeBottom() {
        CoreExpr one = new CoreExpr.LiteralInteger(S, BigInteger.ONE);
        CoreExpr elementBottom = new CoreExpr.Bottom(S, OclType.INTEGER);

        CoreExpr set = new CoreExpr.CollectionLiteral(S, CoreExpr.CollectionKind.SET,
                List.of(one, elementBottom, one), OclType.set(OclType.INTEGER));
        OclValue setResult = assertCoreQAgreement(new CoreExpr.Coerce(S,
                CoreExpr.CoercionKind.COLLECTION_ELEMENT_COERCION,
                OclType.set(OclType.INTEGER), set, OclType.set(OclType.REAL)),
                new CoreInterpreter.Env());
        assertEquals(2, ((OclValue.SetValue) setResult).members().size());

        CoreExpr bag = new CoreExpr.CollectionLiteral(S, CoreExpr.CollectionKind.BAG,
                List.of(one, one, elementBottom), OclType.bag(OclType.INTEGER));
        OclValue bagResult = assertCoreQAgreement(new CoreExpr.Coerce(S,
                CoreExpr.CoercionKind.COLLECTION_ELEMENT_COERCION,
                OclType.bag(OclType.INTEGER), bag, OclType.bag(OclType.REAL)),
                new CoreInterpreter.Env());
        assertEquals(3, ((OclValue.BagValue) bagResult).occurrences().size());

        CoreExpr wholeBottom = new CoreExpr.Coerce(S,
                CoreExpr.CoercionKind.COLLECTION_ELEMENT_COERCION,
                OclType.bag(OclType.INTEGER),
                new CoreExpr.Bottom(S, OclType.bag(OclType.INTEGER)),
                OclType.bag(OclType.REAL));
        OclValue wholeBottomResult = assertCoreQAgreement(wholeBottom,
                new CoreInterpreter.Env());
        assertEquals(true, wholeBottomResult.isBottom());
        assertEquals(OclType.bag(OclType.REAL), wholeBottomResult.type());
    }

    private static OclValue assertCoreQAgreement(CoreExpr expression,
                                                  CoreInterpreter.Env environment) {
        OclValue core = CoreInterpreter.eval(SCHEMA, SNAPSHOT, environment, expression);
        QNode.QExpr qExpression = QCypTranslator.transE(expression);
        OclValue q = QInterpreter.evalExpr(SCHEMA, GRAPH, environment, qExpression);
        assertEquals(expression.type(), core.type());
        assertEquals(expression.type(), q.type());
        assertEquals(OclEquality.BoolKind.TRUE, OclEquality.equal(core, q));
        return q;
    }
}
