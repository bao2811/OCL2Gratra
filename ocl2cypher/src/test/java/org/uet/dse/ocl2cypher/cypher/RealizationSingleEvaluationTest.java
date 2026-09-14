package org.uet.dse.ocl2cypher.cypher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.uet.dse.ocl2cypher.core.CoreExpr;
import org.uet.dse.ocl2cypher.diagnostics.SourceSpan;
import org.uet.dse.ocl2cypher.graph.GraphBuilder;
import org.uet.dse.ocl2cypher.graph.GraphModel;
import org.uet.dse.ocl2cypher.qcyp.QNode;
import org.uet.dse.ocl2cypher.qcyp.QQuery;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.runtime.OclValue;
import org.uet.dse.ocl2cypher.source.model.SchemaModel;
import org.uet.dse.ocl2cypher.source.model.Snapshot;
import org.uet.dse.ocl2cypher.source.model.UmlAssociation;
import org.uet.dse.ocl2cypher.source.model.UmlClass;
import org.uet.dse.ocl2cypher.source.model.UmlQualifier;

/**
 * Structural gate for the R rules: a child expression has one serialized
 * definition. Repeated semantic observations must read a bound alias rather
 * than serialize the child tree again.
 */
class RealizationSingleEvaluationTest {
    private static final SourceSpan S = SourceSpan.UNKNOWN;

    @Test
    void typeTestAndTypeCastSerializeTheirSourceOnce() {
        GraphModel graph = graph();

        assertMarkerOnce(valueArtifact(
                new QNode.QExpr.TypeTest(S, CoreExpr.TypeTestKind.CONFORMS_TO,
                        complexObject("type-test-source"), "Person"), graph),
                "type-test-source");

        assertMarkerOnce(valueArtifact(
                new QNode.QExpr.TypeCast(S, complexObject("type-cast-source"), "Person"),
                graph), "type-cast-source");
    }

    @Test
    void collectionObserversSerializeEveryOperandOnce() {
        GraphModel graph = graph();

        QNode.QExpr membership = new QNode.QExpr.IncludesFamily(S, QNode.QKind.INCLUDES,
                complexSet("membership-source"), complexString("membership-element"),
                OclType.BOOLEAN);
        assertMarkersOnce(valueArtifact(membership, graph),
                "membership-source", "membership-element");

        QNode.QExpr size = new QNode.QExpr.CountFamily(S, QNode.QKind.SIZE,
                complexSet("count-source"), null, OclType.INTEGER);
        assertMarkerOnce(valueArtifact(size, graph), "count-source");

        QNode.QExpr union = new QNode.QExpr.SetAlgebra(S, CoreExpr.BinaryOp.SET_UNION,
                complexSet("set-left"), complexSet("set-right"),
                OclType.set(OclType.STRING));
        assertMarkersOnce(valueArtifact(union, graph), "set-left", "set-right");
    }

    @Test
    void qualifiedToOneAndToManyNavigationSerializeSourceAndQualifierOnce() {
        GraphModel graph = graph();

        QNode.QExpr one = new QNode.QExpr.NavigateOne(S,
                complexCompany("one-navigation-source"), "employmentOne", "employee",
                List.of(complexInteger(new BigInteger("991827364"))),
                OclType.clazz("Employee"));
        assertMarkersOnce(valueArtifact(one, graph),
                "one-navigation-source", "991827364");

        QNode.QPlan many = new QNode.QPlan.NavigateMany(S,
                complexCompany("many-navigation-source"), "employmentMany", "employees",
                List.of(complexInteger(new BigInteger("827364991"))),
                OclType.clazz("Employee"));
        assertMarkersOnce(planArtifact(many, graph),
                "many-navigation-source", "827364991");
    }

    private static QNode.QExpr complexObject(String marker) {
        return new QNode.QExpr.IfExpr(S, bool(true),
                new QNode.QExpr.Constant(S, OclType.clazz("Employee"), marker),
                new QNode.QExpr.Bottom(S, OclType.clazz("Employee")),
                OclType.clazz("Employee"));
    }

    private static QNode.QExpr complexCompany(String marker) {
        return new QNode.QExpr.IfExpr(S, bool(true),
                new QNode.QExpr.Constant(S, OclType.clazz("Company"), marker),
                new QNode.QExpr.Bottom(S, OclType.clazz("Company")),
                OclType.clazz("Company"));
    }

    private static QNode.QExpr complexString(String marker) {
        return new QNode.QExpr.IfExpr(S, bool(true),
                new QNode.QExpr.Constant(S, OclType.STRING, marker),
                new QNode.QExpr.Bottom(S, OclType.STRING), OclType.STRING);
    }

    private static QNode.QExpr complexInteger(BigInteger marker) {
        return new QNode.QExpr.Unary(S, CoreExpr.UnaryOp.NUMERIC_NEGATE,
                new QNode.QExpr.Constant(S, OclType.INTEGER, marker), OclType.INTEGER);
    }

    private static QNode.QExpr complexSet(String marker) {
        OclType type = OclType.set(OclType.STRING);
        QNode.QExpr literal = new QNode.QExpr.CollectionLiteral(S,
                CoreExpr.CollectionKind.SET,
                List.of(new QNode.QExpr.Constant(S, OclType.STRING, marker)), type);
        return new QNode.QExpr.IfExpr(S, bool(true), literal,
                new QNode.QExpr.Bottom(S, type), type);
    }

    private static QNode.QExpr bool(boolean value) {
        return new QNode.QExpr.Constant(S, OclType.BOOLEAN, value);
    }

    private static CypherAst.GeneratedArtifact valueArtifact(QNode.QExpr expression,
                                                              GraphModel graph) {
        QQuery.QResultShape shape = expression.type.isCollection()
                ? QQuery.QResultShape.SET : QQuery.QResultShape.SCALAR;
        QQuery query = new QQuery(expression, null, shape, QQuery.QueryMode.VALUE,
                expression.type, null, null, false);
        var result = Realization.realize(query, graph, CypherAst.Dialect.CYPHER_5);
        assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
        return result.value();
    }

    private static CypherAst.GeneratedArtifact planArtifact(QNode.QPlan plan,
                                                             GraphModel graph) {
        QQuery query = new QQuery(null, plan, QQuery.QResultShape.SET,
                QQuery.QueryMode.VALUE, plan.type, null, null, false);
        var result = Realization.realize(query, graph, CypherAst.Dialect.CYPHER_5);
        assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
        return result.value();
    }

    private static void assertMarkerOnce(CypherAst.GeneratedArtifact artifact, String marker) {
        String text = Serializer.cypherText(artifact);
        assertEquals(1, occurrences(text, marker), () -> marker + " in:\n" + text);
        Neo4jCypherParserGate.assertParses(text);
    }

    private static void assertMarkersOnce(CypherAst.GeneratedArtifact artifact,
                                           String... markers) {
        for (String marker : markers) {
            assertMarkerOnce(artifact, marker);
        }
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        for (int at = 0; (at = text.indexOf(needle, at)) >= 0; at += needle.length()) {
            count++;
        }
        return count;
    }

    private static GraphModel graph() {
        UmlQualifier slot = new UmlQualifier("slot", OclType.INTEGER,
                List.of(new OclValue.IntegerValue(new BigInteger("991827364")),
                        new OclValue.IntegerValue(new BigInteger("827364991"))));
        SchemaModel schema = SchemaModel.builder("single-evaluation")
                .clazz(UmlClass.of("Person"))
                .clazz(UmlClass.of("Employee", "Person"))
                .clazz(UmlClass.of("Company"))
                .association(new UmlAssociation("employment-one", "employmentOne",
                        "Company", "employer", 0, 1,
                        "Employee", "employee", 0, 1,
                        List.of(slot), false, true))
                .association(new UmlAssociation("employment-many", "employmentMany",
                        "Company", "employerMany", 0, 1,
                        "Employee", "employees", 0, -1,
                        List.of(slot), false, true))
                .build();
        Snapshot snapshot = Snapshot.builder()
                .object("company", "Company")
                .object("employee", "Employee")
                .build();
        var built = GraphBuilder.build(schema, snapshot);
        assertTrue(built.isSuccess(), () -> built.diagnostics().toString());
        return built.value().graph();
    }
}
