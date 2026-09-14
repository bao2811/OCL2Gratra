package org.uet.dse.ocl2cypher;

import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.uet.dse.ocl2cypher.core.*;
import org.uet.dse.ocl2cypher.diagnostics.SourceSpan;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.source.model.*;
import org.uet.dse.ocl2cypher.source.omg.OmgAs;

class AdmissionLoweringRegressionTest {
    private static final SourceSpan S = SourceSpan.UNKNOWN;
    private final SchemaModel schema = SchemaModel.builder("regression")
            .clazz(UmlClass.of("Parent")).clazz(UmlClass.of("Child", "Parent")).build();
    private OmgAs.ExpressionInOcl query(OmgAs.OclExpression body) {
        return new OmgAs.ExpressionInOcl(S, body,
                new OmgAs.Variable(S, "self", OclType.clazz("Parent"), null));
    }
    private OmgAs.IntegerLiteralExp one() { return new OmgAs.IntegerLiteralExp(S, BigInteger.ONE); }
    private OmgAs.CollectionLiteralExp collection(OmgAs.OmgCollectionKind kind) {
        return new OmgAs.CollectionLiteralExp(S, kind, List.of(new OmgAs.CollectionItem(S, one())),
                kind == OmgAs.OmgCollectionKind.SET ? OclType.set(OclType.INTEGER) : OclType.bag(OclType.INTEGER));
    }
    @Test void rejectsAnyAtAdmissionIncludingNestedOccurrence() {
        var x = new OmgAs.Variable(S, "x", OclType.INTEGER, null);
        var any = new OmgAs.IteratorExp(S, "any", collection(OmgAs.OmgCollectionKind.SET),
                List.of(x), new OmgAs.BooleanLiteralExp(S, true), OclType.INTEGER);
        var let = new OmgAs.LetExp(S, new OmgAs.Variable(S, "v", OclType.INTEGER, any), one());
        for (var expression : List.of(any, let)) {
            assertEquals("N_UNSUPPORTED_ITERATOR", Admission.checkQueryExpression(expression).orElseThrow().code());
            assertEquals("N_UNSUPPORTED_ITERATOR", CoreLowering.lowerValueQuery(schema, query(expression)).primaryDiagnostic().code());
        }
    }
    @Test void insertsNumericAndCollectionLetCoercions() {
        for (var init : List.of(one(), collection(OmgAs.OmgCollectionKind.SET), collection(OmgAs.OmgCollectionKind.BAG))) {
            var target = init.type.isCollection()
                    ? (init.type.kind() == OclType.Kind.SET ? OclType.set(OclType.REAL) : OclType.bag(OclType.REAL)) : OclType.REAL;
            var v = new OmgAs.Variable(S, "v", target, init);
            var result = CoreLowering.lowerValueQuery(schema, query(new OmgAs.LetExp(S, v, new OmgAs.VariableExp(S, v))));
            assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
            var core = (CoreExpr.Let) result.value().body();
            assertEquals(core.binder.type(), core.value.type());
            assertTrue(core.value instanceof CoreExpr.Coerce);
            assertSame(core.binder, ((CoreExpr.Variable) core.inExpr).declaration);
        }
    }
    @Test void rejectsBagToSetAtLoweringWithoutBottom() {
        var v = new OmgAs.Variable(S, "v", OclType.set(OclType.INTEGER), collection(OmgAs.OmgCollectionKind.BAG));
        var result = CoreLowering.lowerValueQuery(schema, query(new OmgAs.LetExp(S, v, new OmgAs.VariableExp(S, v))));
        assertFalse(result.isSuccess());
        assertEquals("N_TYPE", result.primaryDiagnostic().code());
    }
    @Test void preservesOldEnvironmentAndShadowingIdentity() {
        var outer = new OmgAs.Variable(S, "x", OclType.INTEGER, one());
        var inner = new OmgAs.Variable(S, "x", OclType.REAL, new OmgAs.VariableExp(S, outer));
        var expression = new OmgAs.LetExp(S, outer,
                new OmgAs.LetExp(S, inner, new OmgAs.VariableExp(S, inner)));
        var result = CoreLowering.lowerValueQuery(schema, query(expression));
        assertTrue(result.isSuccess());
        var o = (CoreExpr.Let) result.value().body();
        assertTrue(o.value instanceof CoreExpr.LiteralInteger);
        var i = (CoreExpr.Let) o.inExpr;
        assertNotSame(o.binder, i.binder);
        var coercion = (CoreExpr.Coerce) i.value;
        assertSame(o.binder, ((CoreExpr.Variable) coercion.source).declaration);
        assertSame(i.binder, ((CoreExpr.Variable) i.inExpr).declaration);
    }

    @Test void aLetBinderCannotLeakIntoItsFollowingSibling() {
        var local = new OmgAs.Variable(S, "x", OclType.INTEGER, one());
        var scoped = new OmgAs.LetExp(S, local, new OmgAs.VariableExp(S, local));
        var malformed = new OmgAs.CollectionLiteralExp(S, OmgAs.OmgCollectionKind.SET,
                List.of(new OmgAs.CollectionItem(S, scoped),
                        new OmgAs.CollectionItem(S, new OmgAs.VariableExp(S, local))),
                OclType.set(OclType.INTEGER));

        var result = CoreLowering.lowerValueQuery(schema, query(malformed));
        assertTrue(result.isFailure());
        assertEquals("N_UNBOUND_VARIABLE", result.primaryDiagnostic().code());
    }

    @Test void oneOmgDeclarationObjectCannotBeUsedAsTwoBinders() {
        var reused = new OmgAs.Variable(S, "x", OclType.INTEGER, one());
        var first = new OmgAs.LetExp(S, reused, new OmgAs.VariableExp(S, reused));
        var second = new OmgAs.LetExp(S, reused, new OmgAs.VariableExp(S, reused));
        var malformed = new OmgAs.CollectionLiteralExp(S, OmgAs.OmgCollectionKind.BAG,
                List.of(new OmgAs.CollectionItem(S, first),
                        new OmgAs.CollectionItem(S, second)),
                OclType.bag(OclType.INTEGER));

        var result = CoreLowering.lowerValueQuery(schema, query(malformed));
        assertTrue(result.isFailure());
        assertEquals("N_DUPLICATE_DECLARATION", result.primaryDiagnostic().code());
    }
}
