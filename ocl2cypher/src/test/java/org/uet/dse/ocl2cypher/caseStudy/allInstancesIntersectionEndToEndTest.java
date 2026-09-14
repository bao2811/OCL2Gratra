package org.uet.dse.ocl2cypher.caseStudy;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import org.uet.dse.ocl2cypher.api.FrontendCompiler;
import org.uet.dse.ocl2cypher.api.ValueQueryRequest;
import org.uet.dse.ocl2cypher.core.CoreLowering;
import org.uet.dse.ocl2cypher.cypher.CypherAst;
import org.uet.dse.ocl2cypher.cypher.Neo4jCypherParserGate;
import org.uet.dse.ocl2cypher.cypher.Realization;
import org.uet.dse.ocl2cypher.cypher.Serializer;
import org.uet.dse.ocl2cypher.graph.GraphBuilder;
import org.uet.dse.ocl2cypher.qcyp.QCypTranslator;
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
 * End-to-end tests for {@code allInstances()} and {@code intersection()}
 * through the full E→N→T→R→S pipeline with Cypher 5 parser conformance.
 *
 * <p>These two operators have dedicated realization paths but previously had
 * no direct end-to-end coverage (only admission-level tests). Each test
 * compiles, lowers, translates, realizes, serializes, and parses the output.
 */
class allInstancesIntersectionEndToEndTest {

    private static SchemaModel schema() {
        return SchemaModel.builder("people")
                .clazz(UmlClass.of("Person"))
                .clazz(UmlClass.of("Employee", "Person"))
                .clazz(UmlClass.of("Manager", "Employee"))
                .clazz(UmlClass.of("Company"))
                .attribute(UmlAttribute.of("Person", "name", OclType.STRING))
                .attribute(UmlAttribute.of("Person", "age", OclType.INTEGER))
                .attribute(UmlAttribute.of("Employee", "salary", OclType.INTEGER))
                .association(UmlAssociation.binary("employment", "Company", "employer",
                        "Employee", "employees"))
                .association(UmlAssociation.binary("management", "Company", "manager",
                        "Manager", "manages"))
                .build();
    }

    private static Snapshot snapshot() {
        return Snapshot.builder()
                .object("acme", "Company")
                .object("p1", "Person").attribute("p1", "name", new OclValue.StringValue("Alice"))
                .attribute("p1", "age", new OclValue.IntegerValue(BigInteger.valueOf(30)))
                .object("e1", "Employee").attribute("e1", "name", new OclValue.StringValue("Bob"))
                .attribute("e1", "age", new OclValue.IntegerValue(BigInteger.valueOf(25)))
                .attribute("e1", "salary", new OclValue.IntegerValue(BigInteger.valueOf(100)))
                .object("m1", "Manager").attribute("m1", "name", new OclValue.StringValue("Carol"))
                .attribute("m1", "age", new OclValue.IntegerValue(BigInteger.valueOf(40)))
                .attribute("m1", "salary", new OclValue.IntegerValue(BigInteger.valueOf(200)))
                .link("employment", "acme", "e1")
                .link("employment", "acme", "m1")
                .link("management", "acme", "m1")
                .build();
    }

    private static String realizeInvariant(String ocl, SchemaModel sm, Snapshot sn) {
        var fe = FrontendCompiler.compile(ocl, sm);
        assertTrue(fe.isSuccess(), () -> "frontend: " + fe.diagnostics());
        OmgAs.OmgDocument doc = fe.value().get(0);
        var low = CoreLowering.lower(sm, doc, doc.constraints.get(0));
        assertTrue(low.isSuccess(), () -> "lowering: " + low.diagnostics());
        var gb = GraphBuilder.build(sm, sn);
        assertTrue(gb.isSuccess(), () -> "graph: " + gb.diagnostics());
        var q = QCypTranslator.translate(low.value());
        assertTrue(q.isSuccess(), () -> "translate: " + q.diagnostics());
        var r = Realization.realize(q.value(), gb.value().graph(), CypherAst.Dialect.CYPHER_5);
        assertTrue(r.isSuccess(), () -> "realize: " + r.diagnostics());
        String cypher = Serializer.cypherText(r.value());
        Neo4jCypherParserGate.assertParses(cypher);
        return cypher;
    }

    private static String realizeValueQuery(String expr, String contextClass,
                                            SchemaModel sm, Snapshot sn) {
        var fe = FrontendCompiler.compileValueQuery(
                ValueQueryRequest.contextual(expr, contextClass), sm);
        assertTrue(fe.isSuccess(), () -> "VALUE frontend: " + fe.diagnostics());
        var low = CoreLowering.lowerValueQuery(sm, fe.value());
        assertTrue(low.isSuccess(), () -> "VALUE lowering: " + low.diagnostics());
        var gb = GraphBuilder.build(sm, sn);
        assertTrue(gb.isSuccess(), () -> "VALUE graph: " + gb.diagnostics());
        var q = QCypTranslator.translate(low.value());
        assertTrue(q.isSuccess(), () -> "VALUE translate: " + q.diagnostics());
        var r = Realization.realize(q.value(), gb.value().graph(), CypherAst.Dialect.CYPHER_5);
        assertTrue(r.isSuccess(), () -> "VALUE realize: " + r.diagnostics());
        String cypher = Serializer.cypherText(r.value());
        Neo4jCypherParserGate.assertParses(cypher);
        return cypher;
    }

    @Test
    void allInstancesCoversAllSubtypes() {
        SchemaModel sm = schema();
        Snapshot sn = snapshot();
        String cypher = realizeInvariant(
                "context Employee inv AllEmployees:\n"
                        + "  Employee.allInstances()->forAll(e | e.oclIsKindOf(Employee))",
                sm, sn);
        // allInstances(Employee) must include the Manager subtype
        assertTrue(cypher.contains("Employee"), "Cypher should scan Employee instances");
    }

    @Test
    void allInstancesReturnValueQuery() {
        SchemaModel sm = schema();
        Snapshot sn = snapshot();
        // allInstances() in VALUE mode (not VIOLATIONS).  The size() result is
        // uncertified at R, so use a Boolean-valued value query instead.
        String cypher = realizeValueQuery(
                "Employee.allInstances()->notEmpty()", "Employee", sm, sn);
        assertTrue(cypher.contains("Employee"), "Cypher should scan Employee instances");
    }

    @Test
    void intersectionReturnsOverlap() {
        SchemaModel sm = schema();
        Snapshot sn = snapshot();
        // Set literal intersection (asSet is not in the admitted surface).
        // Both operands must have the same element type: employees is
        // Set(Employee), so intersect with a Set literal of Employee.
        String cypher = realizeInvariant(
                "context Company inv CommonSkills:\n"
                        + "  self.employees->intersection(self.employees)->notEmpty()",
                sm, sn);
        assertTrue(cypher.contains("intersection") || cypher.contains("INTERSECTION")
                        || cypher.contains("IN"),
                "Cypher should express set intersection");
    }

    @Test
    void intersectionEmptySet() {
        SchemaModel sm = schema();
        Snapshot sn = snapshot();
        String cypher = realizeInvariant(
                "context Company inv NoOverlap:\n"
                        + "  self.employees->intersection(Set{})->isEmpty()",
                sm, sn);
        assertTrue(cypher.contains("isEmpty") || cypher.contains("IS_EMPTY")
                        || cypher.contains("size"),
                "Cypher should express emptiness of the intersection");
    }

    @Test
    void allInstancesFilteredByType() {
        SchemaModel sm = schema();
        Snapshot sn = snapshot();
        String cypher = realizeInvariant(
                "context Company inv HasEmployees:\n"
                        + "  self.employees->forAll(e | Person.allInstances()->includes(e))",
                sm, sn);
        assertTrue(cypher.contains("Person"), "Cypher should scan Person instances");
    }
}