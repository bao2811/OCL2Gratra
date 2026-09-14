package org.uet.dse.ocl2cypher.cypher;

import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.uet.dse.ocl2cypher.core.CoreExpr;
import org.uet.dse.ocl2cypher.diagnostics.Result;
import org.uet.dse.ocl2cypher.diagnostics.SourceSpan;
import org.uet.dse.ocl2cypher.graph.GraphModel;
import org.uet.dse.ocl2cypher.qcyp.QNode;
import org.uet.dse.ocl2cypher.qcyp.QQuery;
import org.uet.dse.ocl2cypher.qcyp.QValidator;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.runtime.OclValue;

import static org.junit.jupiter.api.Assertions.*;

/** Executable finite-catalogue refinement for F-8 carrier dispatch. */
class F8CarrierDispatchTest {
    private static final SourceSpan S = SourceSpan.UNKNOWN;
    private static final GraphModel G = new GraphModel("f8-carrier");

    @Test
    void everyExecutableTypeKindHasOneCanonicalTypeExpansion() {
        assertEquals(Set.of(OclType.Kind.BOOLEAN, OclType.Kind.INTEGER,
                        OclType.Kind.REAL, OclType.Kind.STRING, OclType.Kind.CLASS,
                        OclType.Kind.SET, OclType.Kind.BAG),
                Set.of(OclType.Kind.values()));

        List<OclType> representatives = List.of(
                OclType.BOOLEAN, OclType.INTEGER, OclType.REAL, OclType.STRING,
                OclType.clazz("Person"), OclType.set(OclType.STRING),
                OclType.bag(OclType.STRING));
        List<String> tags = representatives.stream().map(CypherArtifacts::typeTag).toList();
        assertEquals(List.of("Boolean3", "Integer", "Real", "String",
                "Class:Person", "Set<String>", "Bag<String>"), tags);
        assertEquals(tags.size(), new LinkedHashSet<>(tags).size(),
                "different carrier types must not share a canonical tag");
    }

    @Test
    void runtimeValueHierarchyHasExactlyTheEightExecutableLeafCarriers() {
        Set<String> actual = new LinkedHashSet<>();
        collectConcreteSealedLeaves(OclValue.class, actual);
        assertEquals(Set.of("BooleanValue", "IntegerValue", "RealValue", "StringValue",
                "ObjectValue", "SetValue", "BagValue", "BottomValue"), actual);
    }

    @Test
    void everyQCarrierFamilyExpandsThroughProductionRealization() {
        OclType person = OclType.clazz("Person");
        List<QNode.QExpr> values = new ArrayList<>(List.of(
                constant(OclType.BOOLEAN, true),
                constant(OclType.INTEGER, BigInteger.valueOf(Long.MAX_VALUE)),
                constant(OclType.REAL, new BigDecimal("0.5")),
                constant(OclType.STRING, "text"),
                constant(person, "person-1"),
                collection(CoreExpr.CollectionKind.SET, OclType.set(OclType.STRING),
                        List.of(constant(OclType.STRING, "a"),
                                constant(OclType.STRING, "a"))),
                collection(CoreExpr.CollectionKind.BAG, OclType.bag(OclType.STRING),
                        List.of(constant(OclType.STRING, "a"),
                                constant(OclType.STRING, "a")))));

        List<OclType> allTypes = List.of(OclType.BOOLEAN, OclType.INTEGER, OclType.REAL,
                OclType.STRING, person, OclType.set(OclType.STRING),
                OclType.bag(OclType.STRING));
        for (OclType type : allTypes) {
            values.add(new QNode.QExpr.Bottom(S, type));
            values.add(new QNode.QExpr.Parameter(S,
                    new QNode.QParameter("p" + type.kind().name(), type)));
        }

        int successes = 0;
        for (QNode.QExpr expression : values) {
            QQuery query = valueQuery(expression);
            assertTrue(QValidator.validate(query).isEmpty(),
                    () -> expression.getClass().getSimpleName() + ": "
                            + QValidator.validate(query));
            Result<CypherAst.GeneratedArtifact> result = Realization.realize(
                    query, G, CypherAst.Dialect.CYPHER_5);
            assertTrue(result.isSuccess(),
                    () -> expression.getClass().getSimpleName() + ": " + result.diagnostics());
            assertEquals(CypherArtifacts.typeTag(expression.type),
                    result.value().contract().elementTypeTag());
            assertDoesNotThrow(() -> CypherAstWellFormednessValidator.validate(result.value()));
            successes++;
        }
        assertEquals(21, successes);
    }

    @Test
    void knownNumericShortfallsAreTypedFailuresNotUncoveredDispatch() {
        List<QNode.QExpr> unsupported = List.of(
                constant(OclType.INTEGER,
                        BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)),
                constant(OclType.REAL, new BigDecimal("0.1")));
        List<String> expected = List.of("R_NUMERIC_CAPABILITY", "R-REAL-EXACT-UNSUPPORTED");

        for (int i = 0; i < unsupported.size(); i++) {
            Result<CypherAst.GeneratedArtifact> result = Realization.realize(
                    valueQuery(unsupported.get(i)), G, CypherAst.Dialect.CYPHER_5);
            assertTrue(result.isFailure());
            assertEquals(expected.get(i), result.primaryDiagnostic().code());
            assertNotEquals("R_UNCOVERED_CONSTRUCTOR", result.primaryDiagnostic().code());
        }
    }

    private static QNode.QExpr constant(OclType type, Object value) {
        return new QNode.QExpr.Constant(S, type, value);
    }

    private static QNode.QExpr collection(CoreExpr.CollectionKind kind, OclType type,
                                          List<QNode.QExpr> elements) {
        return new QNode.QExpr.CollectionLiteral(S, kind, elements, type);
    }

    private static QQuery valueQuery(QNode.QExpr expression) {
        QQuery.QResultShape shape = switch (expression.type.kind()) {
            case SET -> QQuery.QResultShape.SET;
            case BAG -> QQuery.QResultShape.BAG;
            default -> QQuery.QResultShape.SCALAR;
        };
        return new QQuery(expression, null, shape, QQuery.QueryMode.VALUE,
                expression.type, null, null, false);
    }

    private static void collectConcreteSealedLeaves(Class<?> root, Set<String> leaves) {
        if (!root.isSealed()) {
            if (!Modifier.isAbstract(root.getModifiers())) {
                leaves.add(root.getSimpleName());
            }
            return;
        }
        Arrays.stream(root.getPermittedSubclasses())
                .forEach(child -> collectConcreteSealedLeaves(child, leaves));
    }
}
