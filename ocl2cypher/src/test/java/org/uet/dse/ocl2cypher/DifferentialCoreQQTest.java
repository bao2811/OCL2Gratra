package org.uet.dse.ocl2cypher;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.uet.dse.ocl2cypher.core.CoreInterpreter;
import org.uet.dse.ocl2cypher.core.CoreInvariant;
import org.uet.dse.ocl2cypher.core.CoreLowering;
import org.uet.dse.ocl2cypher.core.CoreUnit;
import org.uet.dse.ocl2cypher.diagnostics.Result;
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
 * Differential oracle {@code Core ↔ Q-over-G} on the same source program.
 *
 * <p>For each invariant: {@code violationsCore(SM,SN) == violationsQ(T_G(i),G)},
 * where {@code G} is built from the SAME snapshot by {@code F_G} and every Q
 * observation goes through PGMM observers — so the comparison transitively
 * tests {@code Theorem T}, {@code F_G} fidelity and the observer contract in
 * one shot. A wrong graph projection, lost multiplicity or collapsed bottom
 * would make the two sets disagree.
 */
class DifferentialCoreQQTest {

    private record Pipeline(SchemaModel sm, Snapshot sn, GraphModel g,
                           CoreInvariant unit, QQuery query) {
    }

    private static Pipeline build(String ocl, SchemaModel sm, Snapshot sn) {
        var fe = org.uet.dse.ocl2cypher.api.FrontendCompiler.compile(ocl, sm);
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

    @Test
    void sliceA_scalarAttribute_agreement() {
        SchemaModel sm = SchemaModel.builder("m")
                .clazz(UmlClass.of("Person"))
                .attribute(UmlAttribute.of("Person", "age", OclType.INTEGER))
                .build();
        Snapshot sn = Snapshot.builder()
                .object("alice", "Person")
                .attribute("alice", "age", new OclValue.IntegerValue(BigInteger.valueOf(20)))
                .object("bob", "Person")
                .attribute("bob", "age", new OclValue.IntegerValue(BigInteger.valueOf(15)))
                .object("carol", "Person") // missing age -> bottom -> violation
                .build();
        Pipeline p = build("context Person inv Adult: self.age >= 18", sm, sn);

        CoreInterpreter.Env env = new CoreInterpreter.Env();
        List<String> coreViol = new java.util.ArrayList<>();
        for (var o : sn.objectsOfClass(sm, "Person")) {
            env = new CoreInterpreter.Env();
            env.bind(p.unit.selfVariable(),
                    new OclValue.ObjectValue(OclType.clazz("Person"), o));
            OclValue v = CoreInterpreter.evalUnit(sm, sn, p.unit, env);
            if (!(v instanceof OclValue.BooleanValue bv
                    && bv.bool() == OclValue.BooleanValue.Bool3.TRUE)) {
                coreViol.add(o);
            }
        }
        List<String> qViol = QInterpreter.violations(sm, p.g, p.unit, p.query);

        assertEquals(Set.copyOf(coreViol), Set.copyOf(qViol),
                "Core and Q-over-G must agree, bottom included: " + coreViol + " vs " + qViol);
        assertEquals(Set.of("bob", "carol"), Set.copyOf(qViol));
    }

    @Test
    void sliceB_navigation_filter_forall_agreement() {
        SchemaModel sm = SchemaModel.builder("m")
                .clazz(UmlClass.of("Company"))
                .clazz(UmlClass.of("Employee"))
                .attribute(UmlAttribute.of("Employee", "age", OclType.INTEGER))
                .attribute(UmlAttribute.of("Employee", "salary", OclType.INTEGER))
                .association(UmlAssociation.binary("employment", "Company", "employer",
                        "Employee", "employees"))
                .build();
        Snapshot sn = Snapshot.builder()
                .object("acme", "Company")
                .object("e1", "Employee")
                .attribute("e1", "age", new OclValue.IntegerValue(BigInteger.valueOf(20)))
                .attribute("e1", "salary", new OclValue.IntegerValue(BigInteger.valueOf(100)))
                .object("e2", "Employee")
                .attribute("e2", "age", new OclValue.IntegerValue(BigInteger.valueOf(25)))
                .attribute("e2", "salary", new OclValue.IntegerValue(BigInteger.valueOf(50)))
                .object("e3", "Employee")
                .attribute("e3", "age", new OclValue.IntegerValue(BigInteger.valueOf(10)))
                .attribute("e3", "salary", new OclValue.IntegerValue(BigInteger.valueOf(999)))
                .link("employment", "acme", "e1")
                .link("employment", "acme", "e2")
                .link("employment", "acme", "e3")
                .object("empty-co", "Company")
                .build();
        // after select(age>=18) -> forAll(salary>60):
        // e1 (adult, 100 ok), e2 (adult, 50 NOT ok) => acme violates; empty company passes.
        Pipeline p = build(
                "context Company inv ValidAdultEmployees:\n"
                        + "  self.employees->select(e | e.age >= 18)->forAll(e | e.salary > 60)",
                sm, sn);

        List<String> qViol = QInterpreter.violations(sm, p.g, p.unit, p.query);
        assertEquals(List.of("acme"), qViol,
                "acme has an adult with salary 50; empty-co vacuously passes");

        // Core agrees on every context object — the full differential:
        for (var sid : sn.objectsOfClass(sm, "Company")) {
            CoreInterpreter.Env cenv = new CoreInterpreter.Env();
            cenv.bind(p.unit.selfVariable(), new OclValue.ObjectValue(OclType.clazz("Company"), sid));
            OclValue cv = CoreInterpreter.evalUnit(sm, sn, p.unit, cenv);

            CoreInterpreter.Env qenv = new CoreInterpreter.Env();
            qenv.bind(p.unit.selfVariable(), new OclValue.ObjectValue(OclType.clazz("Company"), sid));
            OclValue qv = QInterpreter.evalExpr(sm, p.g, qenv, p.query.expressionBody());

            boolean coreOk = cv instanceof OclValue.BooleanValue b1
                    && b1.bool() == OclValue.BooleanValue.Bool3.TRUE;
            boolean qOk = qv instanceof OclValue.BooleanValue b2
                    && b2.bool() == OclValue.BooleanValue.Bool3.TRUE;
            assertEquals(coreOk, qOk, "core " + cv + " vs q " + qv + " on " + sid);
        }
    }

    @Test
    void sliceB2_materialization_agreement() {
        SchemaModel sm = SchemaModel.builder("m")
                .clazz(UmlClass.of("Company"))
                .clazz(UmlClass.of("Employee"))
                .association(UmlAssociation.binary("employment", "Company", "employer",
                        "Employee", "employees"))
                .build();
        Snapshot sn = Snapshot.builder()
                .object("acme", "Company")
                .object("e1", "Employee")
                .object("e2", "Employee")
                .link("employment", "acme", "e1")
                .link("employment", "acme", "e2")
                .object("startup", "Company")
                .build();
        // self.employees->size() > 3 — exercises QMaterializePlan(QNavigate)
        Pipeline p = build(
                "context Company inv HasManyEmployees: self.employees->size() > 3",
                sm, sn);

        List<String> qViol = QInterpreter.violations(sm, p.g, p.unit, p.query);
        assertEquals(Set.of("acme", "startup"), Set.copyOf(qViol),
                "2 employees !> 3 for acme; size(empty)=0 for startup; both violate");

        // differential on the materialized size, not only the violation set:
        CoreInterpreter.Env cenv = new CoreInterpreter.Env();
        cenv.bind(p.unit.selfVariable(), new OclValue.ObjectValue(OclType.clazz("Company"), "acme"));
        OclValue cv = CoreInterpreter.evalUnit(sm, sn, p.unit, cenv);
        CoreInterpreter.Env qenv = new CoreInterpreter.Env();
        qenv.bind(p.unit.selfVariable(), new OclValue.ObjectValue(OclType.clazz("Company"), "acme"));
        OclValue qv = QInterpreter.evalExpr(sm, p.g, qenv, p.query.expressionBody());
        assertEquals(cv, qv, "size comparison must agree: " + cv + " vs " + qv);
    }
}
