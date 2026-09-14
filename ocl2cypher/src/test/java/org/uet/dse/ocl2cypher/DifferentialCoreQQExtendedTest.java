package org.uet.dse.ocl2cypher;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.uet.dse.ocl2cypher.api.FrontendCompiler;
import org.uet.dse.ocl2cypher.core.CoreInterpreter;
import org.uet.dse.ocl2cypher.core.CoreInvariant;
import org.uet.dse.ocl2cypher.core.CoreLowering;
import org.uet.dse.ocl2cypher.graph.GraphBuilder;
import org.uet.dse.ocl2cypher.graph.GraphModel;
import org.uet.dse.ocl2cypher.qcyp.QCypTranslator;
import org.uet.dse.ocl2cypher.qcyp.QInterpreter;
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
 * Differential oracle {@code Core ↔ Q-over-G} on the same source program —
 * extended constructor space (Slices C–G).
 *
 * <p>Each test asserts {@code violationsCore(SM,SN) == violationsQ(T_G(i),G)}
 * and, where meaningful, per-object agreement of the evaluated body value.
 * The extended slices cover the constructor families that Slice A/B/B2 did
 * not: {@code collect}, {@code reject}+{@code exists}, type tests under
 * inheritance, {@code allInstances}, and Bag occurrence semantics.
 */
class DifferentialCoreQQExtendedTest {

    private record Pipeline(SchemaModel sm, Snapshot sn, GraphModel g,
                           CoreInvariant unit, QQuery query) {
    }

    private static Pipeline build(String ocl, SchemaModel sm, Snapshot sn) {
        var fe = FrontendCompiler.compile(ocl, sm);
        assertTrue(fe.isSuccess(), () -> "frontend: " + fe.diagnostics());
        OmgAs.OmgDocument doc = fe.value().get(0);
        var low = CoreLowering.lower(sm, doc, doc.constraints.get(0));
        assertTrue(low.isSuccess(), () -> "lowering: " + low.diagnostics());
        CoreInvariant unit = low.value();
        var gb = GraphBuilder.build(sm, sn);
        assertTrue(gb.isSuccess(), () -> "graph: " + gb.diagnostics());
        var q = QCypTranslator.translate(unit);
        assertTrue(q.isSuccess(), () -> "translate: " + q.diagnostics());
        return new Pipeline(sm, sn, gb.value().graph(), unit, q.value());
    }

    private static OclValue i(long v) {
        return new OclValue.IntegerValue(BigInteger.valueOf(v));
    }

    private static OclValue s(String v) {
        return new OclValue.StringValue(v);
    }

    /** Per-object differential: Core body value and Q body value must agree. */
    private static void assertPerObjectAgreement(Pipeline p) {
        for (var sid : p.sn.objectsOfClass(p.sm, p.unit.contextClassKey())) {
            CoreInterpreter.Env cenv = new CoreInterpreter.Env();
            cenv.bind(p.unit.selfVariable(),
                    new OclValue.ObjectValue(OclType.clazz(p.unit.contextClassKey()), sid));
            OclValue cv = CoreInterpreter.evalUnit(p.sm, p.sn, p.unit, cenv);

            CoreInterpreter.Env qenv = new CoreInterpreter.Env();
            qenv.bind(p.unit.selfVariable(),
                    new OclValue.ObjectValue(OclType.clazz(p.unit.contextClassKey()), sid));
            OclValue qv = QInterpreter.evalExpr(p.sm, p.g, qenv, p.query.expressionBody());

            assertEquals(cv, qv, "core " + cv + " vs q " + qv + " on " + sid);
        }
    }

    @Test
    void sliceC_collect_agreement() {
        SchemaModel sm = SchemaModel.builder("m")
                .clazz(UmlClass.of("Company"))
                .clazz(UmlClass.of("Employee"))
                .attribute(UmlAttribute.of("Employee", "name", OclType.STRING))
                .association(UmlAssociation.binary("employment", "Company", "employer",
                        "Employee", "employees"))
                .build();
        Snapshot sn = Snapshot.builder()
                .object("acme", "Company")
                .object("e1", "Employee").attribute("e1", "name", s("Alice"))
                .object("e2", "Employee").attribute("e2", "name", s("Bob"))
                .object("e3", "Employee").attribute("e3", "name", s("Carol"))
                .link("employment", "acme", "e1")
                .link("employment", "acme", "e2")
                .link("employment", "acme", "e3")
                .object("empty-co", "Company")
                .build();
        // collect(e | e.name)->includes('Bob'): acme true, empty-co false (violates).
        Pipeline p = build(
                "context Company inv HasBob:\n"
                        + "  self.employees->collect(e | e.name)->includes('Bob')",
                sm, sn);

        List<String> qViol = QInterpreter.violations(sm, p.g, p.unit, p.query);
        assertEquals(Set.of("empty-co"), Set.copyOf(qViol),
                "acme has Bob; empty-co has no employees so includes is false");
        assertPerObjectAgreement(p);
    }

    @Test
    void sliceD_exists_reject_agreement() {
        SchemaModel sm = SchemaModel.builder("m")
                .clazz(UmlClass.of("Company"))
                .clazz(UmlClass.of("Employee"))
                .attribute(UmlAttribute.of("Employee", "name", OclType.STRING))
                .attribute(UmlAttribute.of("Employee", "age", OclType.INTEGER))
                .association(UmlAssociation.binary("employment", "Company", "employer",
                        "Employee", "employees"))
                .build();
        Snapshot sn = Snapshot.builder()
                .object("acme", "Company")
                .object("e1", "Employee").attribute("e1", "name", s("Alice"))
                .attribute("e1", "age", i(30))
                .object("e2", "Employee").attribute("e2", "name", s("Bob"))
                .attribute("e2", "age", i(15))
                .object("e3", "Employee").attribute("e3", "name", s("Carol"))
                .attribute("e3", "age", i(10))
                .link("employment", "acme", "e1")
                .link("employment", "acme", "e2")
                .link("employment", "acme", "e3")
                .object("empty-co", "Company")
                .build();
        // reject(age > 18) keeps {Bob, Carol}; exists(name = 'Alice') is false
        // on the remainder => acme violates. empty-co: reject on empty is empty,
        // exists on empty is false => also violates.
        Pipeline p = build(
                "context Company inv NoMinorAlice:\n"
                        + "  self.employees->reject(e | e.age > 18)"
                        + "->exists(e | e.name = 'Alice')",
                sm, sn);

        List<String> qViol = QInterpreter.violations(sm, p.g, p.unit, p.query);
        assertEquals(Set.of("acme", "empty-co"), Set.copyOf(qViol),
                "reject drops Alice (adult), so exists(Alice) is false on the remainder; "
                        + "empty-co has no employees so exists is false too");
        assertPerObjectAgreement(p);
    }

    @Test
    void sliceE_typeTest_inheritance_agreement() {
        SchemaModel sm = SchemaModel.builder("m")
                .clazz(UmlClass.of("Company"))
                .clazz(UmlClass.of("Employee"))
                .clazz(UmlClass.of("Manager", "Employee"))
                .attribute(UmlAttribute.of("Employee", "name", OclType.STRING))
                .association(UmlAssociation.binary("employment", "Company", "employer",
                        "Employee", "employees"))
                .build();
        Snapshot sn = Snapshot.builder()
                .object("acme", "Company")
                .object("e1", "Employee").attribute("e1", "name", s("Alice"))
                .object("m1", "Manager").attribute("m1", "name", s("Mgr"))
                .link("employment", "acme", "e1")
                .link("employment", "acme", "m1")
                .object("empty-co", "Company")
                .build();
        // forAll(e | e.oclIsKindOf(Employee)): both e1 and m1 conform => acme passes.
        // empty-co: forAll on empty set is vacuously TRUE => no violation.
        Pipeline p = build(
                "context Company inv AllEmployees:\n"
                        + "  self.employees->forAll(e | e.oclIsKindOf(Employee))",
                sm, sn);

        List<String> qViol = QInterpreter.violations(sm, p.g, p.unit, p.query);
        assertEquals(Set.of(), Set.copyOf(qViol),
                "acme: all employees are Employee (Manager conforms); "
                        + "empty-co: forAll on empty set is vacuously TRUE");
        assertPerObjectAgreement(p);
    }

    @Test
    void sliceF_allInstances_agreement() {
        SchemaModel sm = SchemaModel.builder("m")
                .clazz(UmlClass.of("Employee"))
                .clazz(UmlClass.of("Manager", "Employee"))
                .attribute(UmlAttribute.of("Employee", "name", OclType.STRING))
                .build();
        Snapshot sn = Snapshot.builder()
                .object("e1", "Employee").attribute("e1", "name", s("Alice"))
                .object("e2", "Employee").attribute("e2", "name", s("Bob"))
                .object("m1", "Manager").attribute("m1", "name", s("Mgr"))
                .build();
        // Employee.allInstances()->notEmpty(): 3 instances (including subtype) => passes.
        Pipeline p = build(
                "context Employee inv InAllInstances:\n"
                        + "  Employee.allInstances()->notEmpty()",
                sm, sn);

        List<String> qViol = QInterpreter.violations(sm, p.g, p.unit, p.query);
        assertEquals(Set.of(), Set.copyOf(qViol),
                "allInstances(Employee) includes the Manager subtype, so notEmpty is true");
        assertPerObjectAgreement(p);
    }

    @Test
    void sliceG_bag_semantics_agreement() {
        SchemaModel sm = SchemaModel.builder("m")
                .clazz(UmlClass.of("Company"))
                .clazz(UmlClass.of("Employee"))
                .attribute(UmlAttribute.of("Employee", "name", OclType.STRING))
                .association(UmlAssociation.binary("employment", "Company", "employer",
                        "Employee", "employees"))
                .build();
        Snapshot sn = Snapshot.builder()
                .object("acme", "Company")
                .object("e1", "Employee").attribute("e1", "name", s("Alice"))
                .object("e2", "Employee").attribute("e2", "name", s("Bob"))
                .object("e3", "Employee").attribute("e3", "name", s("Carol"))
                .link("employment", "acme", "e1")
                .link("employment", "acme", "e2")
                .link("employment", "acme", "e3")
                .object("startup", "Company")
                .object("solo", "Employee").attribute("solo", "name", s("Solo"))
                .link("employment", "startup", "solo")
                .build();
        // self.employees->size() > 2: acme has 3 employees (3 > 2 is true → passes);
        // startup has 1 employee (1 > 2 is false → violates).
        Pipeline p = build(
                "context Company inv HasManyEmployees: self.employees->size() > 2",
                sm, sn);

        List<String> qViol = QInterpreter.violations(sm, p.g, p.unit, p.query);
        assertEquals(Set.of("startup"), Set.copyOf(qViol),
                "acme has 3 employees (3 > 2 passes); startup has 1 (1 > 2 violates)");
        assertPerObjectAgreement(p);
    }
}