package org.uet.dse.ocl2cypher.cypher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.uet.dse.ocl2cypher.core.CoreDeclaration;
import org.uet.dse.ocl2cypher.core.CoreExpr;
import org.uet.dse.ocl2cypher.diagnostics.SourceSpan;
import org.uet.dse.ocl2cypher.graph.GraphModel;
import org.uet.dse.ocl2cypher.qcyp.QNode;
import org.uet.dse.ocl2cypher.qcyp.QQuery;
import org.uet.dse.ocl2cypher.qcyp.QValidator;
import org.uet.dse.ocl2cypher.runtime.OclType;

/** Executable witnesses for L-R-SCOPE construction and inline substitution. */
class R2ScopeRefinementTest {
    private static final SourceSpan S = SourceSpan.UNKNOWN;
    private static final GraphModel G = new GraphModel("scope-test");

    @Test
    void freshSupplyIsUniqueReservedAwareAndDoesNotWrapAtLongBoundary() {
        FreshAliasSupply supply = new FreshAliasSupply(
                BigInteger.valueOf(Long.MAX_VALUE).subtract(BigInteger.ONE));
        supply.reserve("self");

        assertEquals("x_9223372036854775806", supply.fresh("x"));
        assertEquals("x_9223372036854775807", supply.fresh("x"));
        assertEquals("x_9223372036854775808", supply.fresh("x"));
        assertEquals("self_9223372036854775809", supply.fresh("self"));
        assertThrows(IllegalArgumentException.class, () -> supply.reserve("self"));
        assertThrows(IllegalArgumentException.class, () -> supply.fresh("not-valid"));
    }

    @Test
    void expressionLetEqualsCaptureAvoidingStructuralSubstitution() {
        CoreDeclaration x = declaration(1, "x", OclType.STRING);
        QNode.QExpr value = string("value");
        QNode.QExpr body = new QNode.QExpr.Binary(S, CoreExpr.BinaryOp.VALUE_EQUAL,
                new QNode.QExpr.Variable(S, x), string("value"), OclType.BOOLEAN);
        QNode.QExpr let = new QNode.QExpr.Let(S, x, value, body);
        QNode.QExpr substituted = new QNode.QExpr.Binary(S, CoreExpr.BinaryOp.VALUE_EQUAL,
                value, string("value"), OclType.BOOLEAN);

        assertSameRealization(valueQuery(let), valueQuery(substituted));
    }

    @Test
    void repeatedLetReferenceSerializesInitializerOnce() {
        CoreDeclaration x = declaration(1, "x", OclType.STRING);
        QNode.QExpr let = new QNode.QExpr.Let(S, x, string("shared-value"),
                new QNode.QExpr.Binary(S, CoreExpr.BinaryOp.VALUE_EQUAL,
                        new QNode.QExpr.Variable(S, x),
                        new QNode.QExpr.Variable(S, x), OclType.BOOLEAN));

        var result = Realization.realize(valueQuery(let), G, CypherAst.Dialect.CYPHER_5);
        assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
        String text = Serializer.cypherText(result.value());
        assertEquals(1, occurrences(text, "'shared-value'"), text);
        assertTrue(text.contains("`letValue_"), text);
        Neo4jCypherParserGate.assertParses(text);
    }

    @Test
    void planLetEqualsCaptureAvoidingStructuralSubstitutionIncludingCollectionValue() {
        OclType setString = OclType.set(OclType.STRING);
        CoreDeclaration xs = declaration(1, "xs", setString);
        QNode.QExpr value = new QNode.QExpr.CollectionLiteral(S,
                CoreExpr.CollectionKind.SET, List.of(string("a"), string("b")), setString);
        QNode.QPlan let = new QNode.QPlan.PlanLet(S, xs, value,
                new QNode.QPlan.FromCollection(S, new QNode.QExpr.Variable(S, xs)));
        QNode.QPlan substituted = new QNode.QPlan.FromCollection(S, value);

        assertSameRealization(planQuery(let), planQuery(substituted));
    }

    @Test
    void declarationIdentityPreventsSameSurfaceNameCapture() {
        CoreDeclaration outer = declaration(1, "x", OclType.STRING);
        CoreDeclaration inner = declaration(2, "x", OclType.STRING);
        QNode.QExpr expression = new QNode.QExpr.Let(S, outer, string("outer"),
                new QNode.QExpr.Let(S, inner, string("inner"),
                        new QNode.QExpr.Binary(S, CoreExpr.BinaryOp.VALUE_EQUAL,
                                new QNode.QExpr.Variable(S, outer), string("outer"),
                                OclType.BOOLEAN)));
        QNode.QExpr substituted = new QNode.QExpr.Binary(S,
                CoreExpr.BinaryOp.VALUE_EQUAL, string("outer"), string("outer"),
                OclType.BOOLEAN);

        assertTrue(QValidator.validate(valueQuery(expression)).isEmpty());
        assertSameRealization(valueQuery(expression), valueQuery(substituted));
    }

    @Test
    void malformedReuseOfSameLiveDeclarationIsRejected() {
        CoreDeclaration x = declaration(1, "x", OclType.STRING);
        QNode.QExpr malformed = new QNode.QExpr.Let(S, x, string("outer"),
                new QNode.QExpr.Let(S, x, string("inner"),
                        new QNode.QExpr.Variable(S, x)));
        QQuery query = valueQuery(malformed);

        assertTrue(QValidator.validate(query).stream()
                .anyMatch(error -> error.message().contains("bound more than once")));
        var result = Realization.realize(query, G, CypherAst.Dialect.CYPHER_5);
        assertTrue(result.isFailure());
        assertEquals("R_SCOPE", result.primaryDiagnostic().code());
    }

    private static void assertSameRealization(QQuery left, QQuery right) {
        var leftResult = Realization.realize(left, G, CypherAst.Dialect.CYPHER_5);
        var rightResult = Realization.realize(right, G, CypherAst.Dialect.CYPHER_5);
        assertTrue(leftResult.isSuccess(), () -> leftResult.diagnostics().toString());
        assertTrue(rightResult.isSuccess(), () -> rightResult.diagnostics().toString());
        CypherAstWellFormednessValidator.validate(leftResult.value());
        CypherAstWellFormednessValidator.validate(rightResult.value());
        String leftText = Serializer.cypherText(leftResult.value());
        String rightText = Serializer.cypherText(rightResult.value());
        assertEquals(rightText, leftText);
        Neo4jCypherParserGate.assertParses(leftText);
    }

    private static QQuery valueQuery(QNode.QExpr expression) {
        return new QQuery(expression, null, QQuery.QResultShape.SCALAR,
                QQuery.QueryMode.VALUE, expression.type, null, null, false);
    }

    private static QQuery planQuery(QNode.QPlan plan) {
        return new QQuery(null, plan, QQuery.QResultShape.SET,
                QQuery.QueryMode.VALUE, plan.type, null, null, false);
    }

    private static CoreDeclaration declaration(int id, String name, OclType type) {
        return new CoreDeclaration(id, name, CoreDeclaration.Kind.LET, type);
    }

    private static QNode.QExpr string(String value) {
        return new QNode.QExpr.Constant(S, OclType.STRING, value);
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        for (int at = 0; (at = text.indexOf(needle, at)) >= 0; at += needle.length()) {
            count++;
        }
        return count;
    }
}
