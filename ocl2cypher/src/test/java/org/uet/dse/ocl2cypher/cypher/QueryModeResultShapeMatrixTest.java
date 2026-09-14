package org.uet.dse.ocl2cypher.cypher;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import org.uet.dse.ocl2cypher.api.FrontendCompiler;
import org.uet.dse.ocl2cypher.api.ValueQueryRequest;
import org.uet.dse.ocl2cypher.core.CoreLowering;
import org.uet.dse.ocl2cypher.graph.GraphBuilder;
import org.uet.dse.ocl2cypher.qcyp.QCypTranslator;
import org.uet.dse.ocl2cypher.qcyp.QQuery;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.runtime.OclValue;
import org.uet.dse.ocl2cypher.source.omg.OmgAs;
import org.uet.dse.ocl2cypher.source.model.SchemaModel;
import org.uet.dse.ocl2cypher.source.model.Snapshot;
import org.uet.dse.ocl2cypher.source.model.UmlAssociation;
import org.uet.dse.ocl2cypher.source.model.UmlAttribute;
import org.uet.dse.ocl2cypher.source.model.UmlClass;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Matrix test verifying each (QueryMode × ResultShape) combination produces
 * a well-formed, parseable Cypher artifact.
 *
 * <p>The matrix rows are {VIOLATIONS, VALUE} × {IDS, SCALAR, SET, BAG} plus
 * additional rows for let, if/else, nested collect, and empty-collection edge
 * cases.  Each cell runs the full pipeline E→N→T→R→S and parses the output
 * through Neo4j's Cypher 5 parser.
 *
 * <p><b>Note:</b> Only String/Boolean attributes are used because Integer
 * attribute reads are uncertified at the R stage ({@code R_NUMERIC_CAPABILITY}).
 * The matrix targets the (QueryMode × ResultShape) space, not the numeric gate.
 */
class QueryModeResultShapeMatrixTest {

    // ── Shared schema (String + Boolean attributes only) ────────────────
    private static SchemaModel schema() {
        return SchemaModel.builder("matrix")
                .clazz(UmlClass.of("Person"))
                .clazz(UmlClass.of("Company"))
                .clazz(UmlClass.of("Employee"))
                .attribute(UmlAttribute.of("Person", "name", OclType.STRING))
                .attribute(UmlAttribute.of("Person", "active", OclType.BOOLEAN))
                .attribute(UmlAttribute.of("Employee", "role", OclType.STRING))
                .attribute(UmlAttribute.of("Employee", "remote", OclType.BOOLEAN))
                .association(UmlAssociation.binary("employment", "Company", "employer",
                        "Employee", "employees"))
                .build();
    }

    private static Snapshot snapshot() {
        return Snapshot.builder()
                .object("acme", "Company")
                .object("alice", "Person")
                .attribute("alice", "name", new OclValue.StringValue("Alice"))
                .attribute("alice", "active",
                        new OclValue.BooleanValue(OclType.BOOLEAN, OclValue.BooleanValue.Bool3.TRUE))
                .object("bob", "Employee")
                .attribute("bob", "role", new OclValue.StringValue("Engineer"))
                .attribute("bob", "remote",
                        new OclValue.BooleanValue(OclType.BOOLEAN, OclValue.BooleanValue.Bool3.FALSE))
                .link("employment", "acme", "bob")
                .build();
    }

    // ── Violation pipeline (VIOLATIONS mode → IDS shape) ────────────────
    private static void assertViolationRow(String ocl, String description,
                                           SchemaModel sm, Snapshot sn) {
        var fe = FrontendCompiler.compile(ocl, sm);
        assertTrue(fe.isSuccess(), () -> "frontend " + description + ": " + fe.diagnostics());
        OmgAs.OmgDocument doc = fe.value().get(0);
        var low = CoreLowering.lower(sm, doc, doc.constraints.get(0));
        assertTrue(low.isSuccess(), () -> "lowering " + description + ": " + low.diagnostics());
        var gb = GraphBuilder.build(sm, sn);
        assertTrue(gb.isSuccess(), () -> "graph " + description + ": " + gb.diagnostics());
        var q = QCypTranslator.translate(low.value());
        assertTrue(q.isSuccess(), () -> "translate " + description + ": " + q.diagnostics());
        assertEquals(QQuery.QueryMode.VIOLATIONS, q.value().mode(),
                description + " must be VIOLATIONS mode");
        assertEquals(QQuery.QResultShape.IDS, q.value().resultShape(),
                description + " must have IDS result shape");
        var r = Realization.realize(q.value(), gb.value().graph(), CypherAst.Dialect.CYPHER_5);
        assertTrue(r.isSuccess(), () -> "realize " + description + ": " + r.diagnostics());
        String cypher = Serializer.cypherText(r.value());
        Neo4jCypherParserGate.assertParses(cypher);
    }

    // ── Value pipeline (VALUE mode → SCALAR/SET/BAG) ────────────────────
    private static void assertValueRow(String expr, String contextClass,
                                        QQuery.QResultShape expectedShape,
                                        String description,
                                        SchemaModel sm, Snapshot sn) {
        var fe = FrontendCompiler.compileValueQuery(
                ValueQueryRequest.contextual(expr, contextClass), sm);
        assertTrue(fe.isSuccess(), () -> "VALUE frontend " + description + ": " + fe.diagnostics());
        var low = CoreLowering.lowerValueQuery(sm, fe.value());
        assertTrue(low.isSuccess(), () -> "VALUE lowering " + description + ": " + low.diagnostics());
        var gb = GraphBuilder.build(sm, sn);
        assertTrue(gb.isSuccess(), () -> "VALUE graph " + description + ": " + gb.diagnostics());
        var q = QCypTranslator.translate(low.value());
        assertTrue(q.isSuccess(), () -> "VALUE translate " + description + ": " + q.diagnostics());
        assertEquals(QQuery.QueryMode.VALUE, q.value().mode(),
                description + " must be VALUE mode");
        assertEquals(expectedShape, q.value().resultShape(),
                description + " must have " + expectedShape + " result shape");
        var r = Realization.realize(q.value(), gb.value().graph(), CypherAst.Dialect.CYPHER_5);
        assertTrue(r.isSuccess(), () -> "VALUE realize " + description + ": " + r.diagnostics());
        String cypher = Serializer.cypherText(r.value());
        Neo4jCypherParserGate.assertParses(cypher);
    }

    // ── Matrix cells ────────────────────────────────────────────────────

    @Test
    void violationsIds_simpleInvariant() {
        assertViolationRow(
                "context Person inv IsNamed: self.name <> ''",
                "VIOLATIONS×IDS simple", schema(), snapshot());
    }

    @Test
    void violationsIds_letExpression() {
        assertViolationRow(
                "context Person inv LetName: let n = self.name in n <> ''",
                "VIOLATIONS×IDS let", schema(), snapshot());
    }

    @Test
    void violationsIds_ifElse() {
        assertViolationRow(
                "context Person inv IfActive: if self.active then true else false endif",
                "VIOLATIONS×IDS if/else", schema(), snapshot());
    }

    @Test
    void valueScalar_contextualExpression() {
        assertValueRow(
                "self.name", "Person",
                QQuery.QResultShape.SCALAR,
                "VALUE×SCALAR name", schema(), snapshot());
    }

    @Test
    void valueScalar_ifElse() {
        assertValueRow(
                "if self.active then 'active' else 'inactive' endif", "Person",
                QQuery.QResultShape.SCALAR,
                "VALUE×SCALAR if/else", schema(), snapshot());
    }

    @Test
    void valueSet_filteredSelect() {
        assertValueRow(
                "self.employees->select(e | e.remote = false)", "Company",
                QQuery.QResultShape.SET,
                "VALUE×SET select", schema(), snapshot());
    }

    @Test
    void valueBag_collectRoles() {
        assertValueRow(
                "self.employees->collect(e | e.role)", "Company",
                QQuery.QResultShape.BAG,
                "VALUE×BAG collect", schema(), snapshot());
    }

    @Test
    void valueScalar_booleanExpression() {
        assertValueRow(
                "self.employees->select(e | false)->notEmpty()", "Company",
                QQuery.QResultShape.SCALAR,
                "VALUE×SCALAR boolean of empty select", schema(), snapshot());
    }
}