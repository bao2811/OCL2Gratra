package org.uet.dse.ocl2cypher;

import org.junit.jupiter.api.Test;
import org.uet.dse.ocl2cypher.core.CoreLowering;
import org.uet.dse.ocl2cypher.cypher.CypherAst;
import org.uet.dse.ocl2cypher.cypher.Realization;
import org.uet.dse.ocl2cypher.cypher.Serializer;
import org.uet.dse.ocl2cypher.graph.GraphBuilder;
import org.uet.dse.ocl2cypher.qcyp.QCypTranslator;
import org.uet.dse.ocl2cypher.qcyp.QQuery;
import org.uet.dse.ocl2cypher.qcyp.QInterpreter;
import org.uet.dse.ocl2cypher.api.CoreOracle;
import org.uet.dse.ocl2cypher.diagnostics.Result;
import org.uet.dse.ocl2cypher.diagnostics.Stage;
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
 * Slice B/C end-to-end (to Cypher text): navigation + filter + exists/forAll +
 * materialized size + bottom-aware violations through R and S.
 *
 * <p>Note the carrier guarantee: a predicate-bottom on any occurrence makes
 * the filtered collection a typed bottom, not an empty set; hence collecting
 * a short list of filtered item IDs there would violate the carrier, so the
 * counter is used as a participation measure instead. Storage there is more
 * than a tag layout: a single invariant whose body is a predicate-bottom must
 * remain indistinguishable from a missing source, and the short-circuit for a
 * Boolean connective has to know where on the row its alias became whole
 * bottom, not merely that the row had a missing property somewhere.
 */
class SliceBCToCypherTest {

    private CypherAst.GeneratedArtifact realize(String ocl, SchemaModel sm, Snapshot sn) {
        var r = realizeResult(ocl, sm, sn);
        assertTrue(r.isSuccess(), () -> "realize: " + r.diagnostics() + " ocl=`" + ocl + "`");
        return r.value();
    }

    private void expectNumericRejection(String ocl, SchemaModel sm, Snapshot sn) {
        var r = realizeResult(ocl, sm, sn);
        assertTrue(r.isFailure(), "expected numeric rejection: " + ocl);
        assertEquals(Stage.R, r.primaryDiagnostic().stage());
        assertEquals("R_NUMERIC_CAPABILITY", r.primaryDiagnostic().code());
    }

    private Result<CypherAst.GeneratedArtifact> realizeResult(String ocl, SchemaModel sm, Snapshot sn) {
        var fe = org.uet.dse.ocl2cypher.api.FrontendCompiler.compile(ocl, sm);
        assertTrue(fe.isSuccess(), () -> "frontend: " + fe.diagnostics());
        OmgAs.OmgDocument doc = fe.value().get(0);
        var low = CoreLowering.lower(sm, doc, doc.constraints.get(0));
        assertTrue(low.isSuccess(), () -> "lowering: " + low.diagnostics());
        var g = GraphBuilder.build(sm, sn);
        assertTrue(g.isSuccess(), () -> "graph: " + g.diagnostics());
        var q = QCypTranslator.translate(low.value());
        assertTrue(q.isSuccess(), () -> "translate: " + q.diagnostics());
        assertEquals(new java.util.TreeSet<>(CoreOracle.violationsOclEq(ocl, sm, sn)),
                new java.util.TreeSet<>(QInterpreter.violations(sm, g.value().graph(), low.value(), q.value())),
                "Core/Q semantics must agree even when R rejects: " + ocl);
        return Realization.realize(q.value(), g.value().graph(), CypherAst.Dialect.CYPHER_5);
    }

    @Test
    void selectForAllRealizesToTaggedViolationQuery() {
        SchemaModel sm = SchemaModel.builder("m")
                .clazz(UmlClass.of("Company"))
                .clazz(UmlClass.of("Employee"))
                .attribute(UmlAttribute.of("Employee", "age", OclType.INTEGER))
                .attribute(UmlAttribute.of("Employee", "salary", OclType.INTEGER))
                .attribute(UmlAttribute.of("Employee", "active", OclType.BOOLEAN))
                .association(UmlAssociation.binary("employment", "Company", "employer",
                        "Employee", "employees"))
                .build();
        Snapshot sn = Snapshot.builder()
                .object("acme", "Company")
                .object("e1", "Employee")
                .attribute("e1", "age", new OclValue.IntegerValue(java.math.BigInteger.valueOf(20)))
                .attribute("e1", "salary", new OclValue.IntegerValue(java.math.BigInteger.valueOf(100)))
                .link("employment", "acme", "e1")
                .build();

        var adultArtifact = realize(
                "context Company inv ValidAdultEmployees:\n"
                        + "  self.employees->select(e | e.age >= 18)->forAll(e | e.salary > 0)",
                sm, sn);
        assertNotNull(Serializer.cypherText(adultArtifact));
        var artifact = realize("context Company inv ActiveEmployees: "
                + "self.employees->select(e | e.active)->forAll(e | e.active)", sm, sn);
        String text = Serializer.cypherText(artifact);
        // The Q plan for the select uses a list comprehension filtered on the
        // tagged Boolean3 predicate; forAll folds with exists-over-negated.
        assertTrue(text.contains("any("), "exists/forAll materialized as quantified predicates");
        assertTrue(text.contains(" IN ") && text.contains(" WHERE "),
                "quantified predicates keep their binder and predicate: " + text);
        assertFalse(text.contains("any(["),
                "any must not receive a list comprehension as a function argument: " + text);
        assertTrue(text.contains("__oclBottom"), "whole-bottom flag survives into the filter");
        assertTrue(text.contains("MATCH"), "scan + navigation emit MATCH patterns");
        assertEquals(CypherAst.ResultShape.IDS, artifact.contract().shape());
        assertTrue(artifact.contract().distinctRequired());
    }

    @Test
    void certifiedSizeAndNotEmptyPreserveMaterializedStructure() {
        SchemaModel sm = SchemaModel.builder("m")
                .clazz(UmlClass.of("Company"))
                .clazz(UmlClass.of("Employee"))
                .association(UmlAssociation.binary("employment", "Company", "employer",
                        "Employee", "employees"))
                .build();
        Snapshot sn = Snapshot.builder()
                .object("acme", "Company")
                .object("e1", "Employee")
                .link("employment", "acme", "e1")
                .build();

        var sizeArtifact = realize(
                "context Company inv HasManyEmployees: self.employees->size() > 3",
                sm, sn);
        String sizeText = Serializer.cypherText(sizeArtifact);
        assertTrue(sizeText.contains("size("), "certified cardinality uses native size");
        var artifact = realize("context Company inv Staff: self.employees->notEmpty()", sm, sn);
        String text = Serializer.cypherText(artifact);
        assertTrue(text.contains("__oclItems"), "notEmpty reads the materialized tagged items list");
        assertTrue(text.contains("COLLECT"), "materialize uses COLLECT { MATCH ... RETURN }");
        assertTrue(text.contains("size("), "Boolean notEmpty uses physical size over the list");
        assertEquals(CypherAst.ResultShape.IDS, artifact.contract().shape());
    }

    @Test
    void missingNumericAndBooleanBottomRealizes() {
        SchemaModel sm = SchemaModel.builder("m")
                .clazz(UmlClass.of("Employee"))
                .attribute(UmlAttribute.of("Employee", "salary", OclType.INTEGER))
                .attribute(UmlAttribute.of("Employee", "paid", OclType.BOOLEAN))
                .build();
        Snapshot sn = Snapshot.builder()
                .object("e1", "Employee") // no salary — slot absent
                .build();

        var artifactInt = realize("context Employee inv Paid: self.salary > 0", sm, sn);
        String textInt = Serializer.cypherText(artifactInt);
        assertTrue(textInt.contains("__oclBottom"));
        assertEquals(java.util.Set.of("e1"), new java.util.HashSet<>(
                CoreOracle.violationsOclEq("context Employee inv Paid: self.paid", sm, sn)));
        var artifact = realize("context Employee inv Paid: self.paid", sm, sn);
        String text = Serializer.cypherText(artifact);
        // The violation predicate must keep the bottom-of-receiver guard from
        // R-E-ATTRIBUTE second out of shadow remains unchanged: a missing
        // attribute makes the per-object body bottom, and the violation oracle
        // therefore includes the object.
        assertTrue(text.contains("__oclBottom"),
                "attribute read keeps the typed bottom guard into the body");
    }
}
