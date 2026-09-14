package org.uet.dse.ocl2cypher;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.uet.dse.ocl2cypher.core.CoreDeclaration;
import org.uet.dse.ocl2cypher.core.CoreLowering;
import org.uet.dse.ocl2cypher.cypher.CypherArtifacts;
import org.uet.dse.ocl2cypher.cypher.CypherAst;
import org.uet.dse.ocl2cypher.cypher.CypherAstWellFormednessValidator;
import org.uet.dse.ocl2cypher.cypher.Realization;
import org.uet.dse.ocl2cypher.cypher.Serializer;
import org.uet.dse.ocl2cypher.diagnostics.SourceSpan;
import org.uet.dse.ocl2cypher.diagnostics.Stage;
import org.uet.dse.ocl2cypher.graph.GraphBuilder;
import org.uet.dse.ocl2cypher.qcyp.QCypTranslator;
import org.uet.dse.ocl2cypher.qcyp.QQuery;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.runtime.OclValue;
import org.uet.dse.ocl2cypher.source.model.SchemaModel;
import org.uet.dse.ocl2cypher.source.model.Snapshot;
import org.uet.dse.ocl2cypher.source.model.UmlAttribute;
import org.uet.dse.ocl2cypher.source.model.UmlClass;
import org.uet.dse.ocl2cypher.source.omg.OmgAs;

/**
 * Slice A end-to-end to Cypher text: {@code E_SM → N_SM → T_G → R → S}.
 *
 * <p>
 * The assertions do not check the raw string byte-for-byte (that is the
 * serializer's own determinism concern); they check the semantics-bearing shape
 * the specification requires: a violation query scans the context via
 * {@code ObjectInstanceOf}, keeps rows whose tagged body is bottom or false,
 * and returns a DISTINCT stable id — and that a native {@code null} appears
 * only as the guarded payload of a bottom tag, never as a returned value.
 */
class CypherSerializationTest {

    private static SchemaModel personSchema() {
        return SchemaModel.builder("m")
                .clazz(UmlClass.of("Person"))
                .attribute(UmlAttribute.of("Person", "age", OclType.INTEGER))
                .attribute(UmlAttribute.of("Person", "active", OclType.BOOLEAN))
                .build();
    }

    private static Snapshot personSnapshot() {
        return Snapshot.builder()
                .object("alice", "Person")
                .attribute("alice", "age", new OclValue.IntegerValue(BigInteger.valueOf(20)))
                .attribute("alice", "active", new OclValue.BooleanValue(
                        OclType.BOOLEAN, OclValue.BooleanValue.Bool3.TRUE))
                .build();
    }

    private static CypherAst.GeneratedArtifact compileWithSchema(String ocl, SchemaModel sm,
            Snapshot sn) {
        var fe = org.uet.dse.ocl2cypher.api.FrontendCompiler.compile(ocl, sm);
        assertTrue(fe.isSuccess(), () -> "frontend: " + fe.diagnostics());
        OmgAs.OmgDocument doc = fe.value().get(0);
        var low = CoreLowering.lower(sm, doc, doc.constraints.get(0));
        assertTrue(low.isSuccess(), () -> "lowering: " + low.diagnostics());
        var g = GraphBuilder.build(sm, sn);
        assertTrue(g.isSuccess(), () -> "graph: " + g.diagnostics());
        var q = QCypTranslator.translate(low.value());
        assertTrue(q.isSuccess(), () -> "translate: " + q.diagnostics());
        var r = Realization.realize(q.value(), g.value().graph(), CypherAst.Dialect.CYPHER_5);
        assertTrue(r.isSuccess(), () -> "realize: " + r.diagnostics());
        return r.value();
    }

    private static void expectNumericRejection(String ocl) {
        var schema = personSchema();
        var fe = org.uet.dse.ocl2cypher.api.FrontendCompiler.compile(ocl, schema);
        assertTrue(fe.isSuccess(), () -> fe.diagnostics().toString());
        var doc = fe.value().get(0);
        var low = CoreLowering.lower(schema, doc, doc.constraints.get(0));
        assertTrue(low.isSuccess(), () -> low.diagnostics().toString());
        var g = GraphBuilder.build(schema, personSnapshot());
        assertTrue(g.isSuccess(), () -> g.diagnostics().toString());
        var q = QCypTranslator.translate(low.value());
        assertTrue(q.isSuccess(), () -> q.diagnostics().toString());
        var r = Realization.realize(q.value(), g.value().graph(), CypherAst.Dialect.CYPHER_5);
        assertFalse(r.isSuccess());
        assertEquals("R_NUMERIC_CAPABILITY", r.primaryDiagnostic().code());
        assertEquals(Stage.R, r.primaryDiagnostic().stage());
    }

    @Test
    void integerAttributeRealizesAndSerializes() {
        var artifact = compileWithSchema("context Person inv Adult: self.age >= 18",
                personSchema(), personSnapshot());
        Serializer.Serialized s = Serializer.serialize(artifact);
        assertNotNull(s.cypherText());
        assertTrue(s.cypherText().contains("toInteger"), s.cypherText());
    }

    @Test
    void booleanInvariantRealizesReadOnlyViolationQuery() {
        var artifact = compileWithSchema("context Person inv Active: self.active",
                personSchema(), personSnapshot());
        Serializer.Serialized s = Serializer.serialize(artifact);
        String text = s.cypherText();

        assertTrue(text.startsWith("CYPHER 5"), "dialect header emitted once");
        assertTrue(text.contains("MATCH"), "context scan present");
        assertTrue(text.contains(":`" + "ObjectInstanceOf" + "`"),
                "typing goes through ObjectInstanceOf, not a guessed label: " + text);
        assertTrue(text.contains("RETURN DISTINCT"),
                "violation ids are DISTINCT stable ids");
        assertTrue(text.contains("`use_id`"), "projects the stable object id");
        assertTrue(text.contains(CypherArtifacts.OCL_BOTTOM),
                "body keeps the tagged bottom guard");
        // Read-only: no write clause ever emitted.
        for (String w : new String[]{"CREATE", "MERGE", "SET ", "DELETE", "REMOVE",
            "OPTIONAL MATCH"}) {
            assertFalse(text.contains(w), "read-only profile must not emit " + w + ": " + text);
        }
        assertEquals(CypherAst.ResultShape.IDS, artifact.contract().shape());
        assertTrue(artifact.contract().distinctRequired());
        assertTrue(artifact.query().requiresFinalReturn());
        var clauses = artifact.query().clauses();
        assertTrue(clauses.get(clauses.size() - 1) instanceof CypherAst.ReturnClause);
        var projection = (CypherAst.ReturnClause) clauses.get(clauses.size() - 1);
        assertTrue(projection.distinct());
        assertEquals(1, projection.items().size());
        assertEquals(artifact.contract().resultVariable(), projection.items().get(0).alias());
    }

    @Test
    void parametersAreDeclaredNotInlined() {
        var artifact = compileWithSchema("context Person inv Active: self.active",
                personSchema(), personSnapshot());
        // context class key and attribute key are compiler-generated params, not
        // spliced into the text as raw user data.
        assertTrue(artifact.parameters().stream()
                .anyMatch(p -> p.origin() == CypherAst.QueryParameter.Origin.GENERATED),
                "generated parameters present");
        String text = Serializer.cypherText(artifact);
        assertTrue(text.contains("$__oclContextClassKey"),
                "context class key is a $-parameter, not inlined: " + text);
        var attributeParameter = artifact.parameters().stream()
                .filter(p -> "Physical:AttributeKey".equals(p.logicalTypeTag()))
                .findFirst().orElseThrow();
        assertEquals(CypherAst.QueryParameter.Origin.GENERATED, attributeParameter.origin());
        assertEquals("Person::active", attributeParameter.canonicalValue());
        assertTrue(text.contains("$" + attributeParameter.name()), text);
        assertFalse(text.contains("Person::active"), "attribute key must remain out-of-band");
    }

    @Test
    void scanClassProjectionUsesTheFreshMatchedAlias() {
        var schema = SchemaModel.builder("m").clazz(UmlClass.of("Person")).build();
        var snapshot = Snapshot.builder().object("alice", "Person").build();
        var graph = GraphBuilder.build(schema, snapshot);
        assertTrue(graph.isSuccess(), () -> graph.diagnostics().toString());
        var decl = new CoreDeclaration(1, "p", CoreDeclaration.Kind.ITERATOR, OclType.clazz("Person"));
        var plan = new org.uet.dse.ocl2cypher.qcyp.QNode.QPlan.ScanClass(
                SourceSpan.UNKNOWN, "Person", decl);
        var q = new QQuery(null, plan, QQuery.QResultShape.SET, QQuery.QueryMode.VALUE,
                plan.type, null, null, false);
        var artifact = Realization.realize(q, graph.value().graph(), CypherAst.Dialect.CYPHER_5);
        assertTrue(artifact.isSuccess(), () -> artifact.diagnostics().toString());
        String text = Serializer.cypherText(artifact.value());
        assertTrue(text.contains("RETURN DISTINCT"), text);
        assertFalse(text.contains("`o`"), "must not reference unbound literal o: " + text);
    }

    @Test
    void realizedParametersHaveUniqueNamesAndValidOrigins() {
        var artifact = compileWithSchema("context Person inv Active: self.active",
                personSchema(), personSnapshot());
        var names = artifact.parameters().stream().map(CypherAst.QueryParameter::name).toList();
        assertEquals(names.size(), names.stream().distinct().count());
        for (var p : artifact.parameters()) {
            if (p.origin() == CypherAst.QueryParameter.Origin.GENERATED) {
                assertTrue(p.name().startsWith("__ocl"), p.name());
            } else {
                assertTrue(!p.name().startsWith("__ocl") || p.name().equals("__oclContextId"), p.name());
            }
        }
        CypherAstWellFormednessValidator.validate(artifact);
    }

    @Test
    void validatorRejectsVariableOutsideScope() {
        var query = new CypherAst.CypherQuery(List.of(new CypherAst.ReturnClause(false,
                List.of(new CypherAst.ProjectionItem(new CypherAst.VariableExpr("ghost"), "result")))), true);
        var artifact = new CypherAst.GeneratedArtifact(CypherAst.Dialect.CYPHER_5, query,
                new CypherAst.ResultContract(CypherAst.ResultShape.SCALAR, "result", "Integer", false, null), List.of());
        assertThrows(IllegalArgumentException.class, () -> CypherAstWellFormednessValidator.validate(artifact));
    }

    @Test
    void validatorRejectsNestedQueryMissingFinalReturn() {
        var nested = new CypherAst.CypherQuery(List.of(), true);
        var expr = new CypherAst.ExistsSubquery(nested);
        var query = new CypherAst.CypherQuery(List.of(new CypherAst.ReturnClause(false,
                List.of(new CypherAst.ProjectionItem(expr, "result")))), true);
        var artifact = new CypherAst.GeneratedArtifact(CypherAst.Dialect.CYPHER_5, query,
                new CypherAst.ResultContract(CypherAst.ResultShape.SCALAR, "result", "Boolean3", false, null), List.of());
        assertThrows(IllegalArgumentException.class, () -> CypherAstWellFormednessValidator.validate(artifact));
    }

    @Test
    void validatorRejectsDuplicateNodeLabelsAndProperties() {
        var node = new CypherAst.NodePattern("n", List.of("Object", "Object"),
                List.of(new CypherAst.PropertyMapEntry("k", new CypherAst.IntegerLiteral(BigInteger.ONE)),
                        new CypherAst.PropertyMapEntry("k", new CypherAst.IntegerLiteral(BigInteger.TWO))));
        var path = new CypherAst.PathPattern(List.of(node), List.of());
        var query = new CypherAst.CypherQuery(List.of(new CypherAst.MatchClause(
                new CypherAst.Pattern(List.of(path)), null), new CypherAst.ReturnClause(false,
                List.of(new CypherAst.ProjectionItem(new CypherAst.VariableExpr("n"), "result")))), true);
        var artifact = new CypherAst.GeneratedArtifact(CypherAst.Dialect.CYPHER_5, query,
                new CypherAst.ResultContract(CypherAst.ResultShape.SCALAR, "result", "Class:Object", false, null), List.of());
        assertThrows(IllegalArgumentException.class, () -> CypherAstWellFormednessValidator.validate(artifact));
    }

    @Test
    void quantifiedPredicatesUseCypherBinderSyntax() {
        CypherAst.CypherExpr any = new CypherAst.QuantifiedPredicateExpression(
                CypherAst.QuantifierKind.ANY, "x",
                new CypherAst.ListExpr(List.of(new CypherAst.IntegerLiteral(BigInteger.ONE))),
                new CypherAst.BinaryExpr(CypherAst.BinaryOp.GREATER_THAN,
                        new CypherAst.VariableExpr("x"),
                        new CypherAst.IntegerLiteral(BigInteger.ZERO)));
        CypherAst.CypherExpr all = new CypherAst.QuantifiedPredicateExpression(
                CypherAst.QuantifierKind.ALL, "x",
                new CypherAst.ListExpr(List.of(new CypherAst.IntegerLiteral(BigInteger.ONE))),
                new CypherAst.BinaryExpr(CypherAst.BinaryOp.GREATER_THAN,
                        new CypherAst.VariableExpr("x"),
                        new CypherAst.IntegerLiteral(BigInteger.ZERO)));
        CypherAst.CypherQuery query = new CypherAst.CypherQuery(List.of(
                new CypherAst.ReturnClause(false, List.of(
                        new CypherAst.ProjectionItem(any, "anyResult"),
                        new CypherAst.ProjectionItem(all, "allResult")))), true);
        CypherAst.GeneratedArtifact artifact = new CypherAst.GeneratedArtifact(
                CypherAst.Dialect.CYPHER_5, query,
                new CypherAst.ResultContract(CypherAst.ResultShape.SCALAR,
                        "anyResult", "Boolean3", false, null), List.of());

        String text = Serializer.cypherText(artifact);
        assertTrue(text.contains("any(`x` IN [1] WHERE (`x` > 0))"), text);
        assertTrue(text.contains("all(`x` IN [1] WHERE (`x` > 0))"), text);
        assertFalse(text.contains("any(["), text);
        assertFalse(text.contains("all(["), text);
    }

    @Test
    void cypher25DialectIsSerializedExplicitly() {
        CypherAst.CypherQuery query = new CypherAst.CypherQuery(List.of(
                new CypherAst.ReturnClause(false, List.of(
                        new CypherAst.ProjectionItem(
                                new CypherAst.IntegerLiteral(BigInteger.ONE), "result")))), true);
        CypherAst.GeneratedArtifact artifact = new CypherAst.GeneratedArtifact(
                CypherAst.Dialect.CYPHER_25, query,
                new CypherAst.ResultContract(CypherAst.ResultShape.SCALAR,
                        "result", "Integer", false, null), List.of());

        assertTrue(Serializer.cypherText(artifact).startsWith("CYPHER 25\n"));
    }
}
