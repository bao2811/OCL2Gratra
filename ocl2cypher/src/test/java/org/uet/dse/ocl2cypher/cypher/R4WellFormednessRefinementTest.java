package org.uet.dse.ocl2cypher.cypher;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.uet.dse.ocl2cypher.api.FrontendCompiler;
import org.uet.dse.ocl2cypher.api.ValueQueryRequest;
import org.uet.dse.ocl2cypher.core.CoreLowering;
import org.uet.dse.ocl2cypher.graph.GraphBuilder;
import org.uet.dse.ocl2cypher.graph.GraphModel;
import org.uet.dse.ocl2cypher.qcyp.QCypTranslator;
import org.uet.dse.ocl2cypher.qcyp.QQuery;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.runtime.OclValue;
import org.uet.dse.ocl2cypher.source.model.CapabilityMatrix;
import org.uet.dse.ocl2cypher.source.model.SchemaModel;
import org.uet.dse.ocl2cypher.source.model.Snapshot;
import org.uet.dse.ocl2cypher.source.model.UmlAttribute;
import org.uet.dse.ocl2cypher.source.model.UmlClass;

import static org.junit.jupiter.api.Assertions.*;
import static org.uet.dse.ocl2cypher.cypher.CypherAst.*;

/** Production refinement witnesses for R-4, evaluated before the public validator. */
class R4WellFormednessRefinementTest {

    @Test
    void everySuccessfullyConstructedCapabilityArtifactIsWellFormedBeforePostValidation() {
        SchemaModel schema = SchemaModel.builder("r4")
                .clazz(UmlClass.of("Person"))
                .attribute(UmlAttribute.of("Person", "age", OclType.INTEGER))
                .build();
        Snapshot snapshot = Snapshot.builder().object("p", "Person")
                .attribute("p", "age", new OclValue.IntegerValue(BigInteger.valueOf(20)))
                .build();
        GraphModel graph = GraphBuilder.build(schema, snapshot).value().graph();
        AtomicInteger successfulArtifacts = new AtomicInteger();

        CapabilityMatrix.entries().entrySet().stream()
                .filter(entry -> entry.getValue().admitted())
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String witness = entry.getValue().surfaceWitness();
                    QQuery violation = violationQuery("context Person inv R4_"
                            + entry.getKey().name() + ": " + witness, schema);
                    QQuery value = valueQuery(witness, schema);
                    verifyConstruction(violation, graph, entry.getKey() + " invariant",
                            successfulArtifacts);
                    verifyConstruction(value, graph, entry.getKey() + " value",
                            successfulArtifacts);
                });

        assertTrue(successfulArtifacts.get() >= 60,
                "constructor corpus unexpectedly small: " + successfulArtifacts.get());
    }

    @Test
    void resultContractsAreShapeExactAndViolationIdsHaveNoBottomChannel() {
        SchemaModel schema = SchemaModel.builder("r4")
                .clazz(UmlClass.of("Person")).build();
        GraphModel graph = GraphBuilder.build(schema,
                Snapshot.builder().object("p", "Person").build()).value().graph();
        QQuery query = violationQuery("context Person inv Always: true", schema);
        var artifact = Realization.constructWithoutValidation(query, graph,
                Dialect.CYPHER_5);
        assertTrue(artifact.isSuccess(), () -> artifact.diagnostics().toString());
        assertEquals(ResultShape.IDS, artifact.value().contract().shape());
        assertEquals("StableObjectId", artifact.value().contract().elementTypeTag());
        assertNull(artifact.value().contract().wholeBottomTag());
        assertDoesNotThrow(() -> CypherAstWellFormednessValidator.validate(artifact.value()));

        assertRejected(contractArtifact(
                new ResultContract(ResultShape.IDS, "result", "StableObjectId",
                        true, CypherArtifacts.OCL_BOTTOM)));
        assertRejected(contractArtifact(
                new ResultContract(ResultShape.SCALAR, "result", "Boolean",
                        false, null)));
        assertRejected(contractArtifact(
                new ResultContract(ResultShape.SET, "result", "Set<Integer>",
                        true, null)));
        assertRejected(contractArtifact(
                new ResultContract(ResultShape.BAG, "result", "Set<Integer>",
                        false, CypherArtifacts.OCL_BOTTOM)));
    }

    @Test
    void validatorRejectsMalformedBinderLiteralComprehensionAndPatternProducts() {
        assertRejected(scalarArtifact(new ListComprehension("x", new ListExpr(List.of()),
                null, null)));
        assertRejected(scalarArtifact(new CaseExpr(List.of(), new BooleanLiteral(true))));
        assertRejected(scalarArtifact(new IntegerLiteral(null)));

        Pattern empty = new Pattern(List.of());
        CypherQuery query = new CypherQuery(List.of(new MatchClause(empty, null),
                new ReturnClause(false, List.of(new ProjectionItem(
                        new BooleanLiteral(true), "result")))), true);
        assertRejected(new GeneratedArtifact(Dialect.CYPHER_5, query,
                new ResultContract(ResultShape.SCALAR, "result", "Boolean3", false, null),
                List.of()));

        assertRejected(new GeneratedArtifact(Dialect.CYPHER_5,
                new CypherQuery(List.of(new UnwindClause(new ListExpr(List.of()), ""),
                        new ReturnClause(false, List.of(new ProjectionItem(
                                new BooleanLiteral(true), "result")))), true),
                new ResultContract(ResultShape.SCALAR, "result", "Boolean3", false, null),
                List.of()));
    }

    private static void verifyConstruction(QQuery query, GraphModel graph, String label,
                                           AtomicInteger successCount) {
        var unchecked = Realization.constructWithoutValidation(query, graph, Dialect.CYPHER_5);
        var checked = Realization.realize(query, graph, Dialect.CYPHER_5);
        assertEquals(unchecked.isSuccess(), checked.isSuccess(), label);
        if (unchecked.isFailure()) {
            assertEquals(unchecked.primaryDiagnostic().code(),
                    checked.primaryDiagnostic().code(), label);
            return;
        }
        successCount.incrementAndGet();
        assertDoesNotThrow(() -> CypherAstWellFormednessValidator.validate(unchecked.value()),
                label);
        assertEquals(unchecked.value(), checked.value(), label);
    }

    private static QQuery violationQuery(String source, SchemaModel schema) {
        var frontend = FrontendCompiler.compile(source, schema);
        assertTrue(frontend.isSuccess(), () -> frontend.diagnostics().toString());
        var document = frontend.value().get(0);
        var core = CoreLowering.lower(schema, document, document.constraints.get(0));
        assertTrue(core.isSuccess(), () -> core.diagnostics().toString());
        var query = QCypTranslator.translate(core.value());
        assertTrue(query.isSuccess(), () -> query.diagnostics().toString());
        return query.value();
    }

    private static QQuery valueQuery(String source, SchemaModel schema) {
        var frontend = FrontendCompiler.compileValueQuery(
                ValueQueryRequest.contextual(source, "Person"), schema);
        assertTrue(frontend.isSuccess(), () -> frontend.diagnostics().toString());
        var core = CoreLowering.lowerValueQuery(schema, frontend.value());
        assertTrue(core.isSuccess(), () -> core.diagnostics().toString());
        var query = QCypTranslator.translate(core.value());
        assertTrue(query.isSuccess(), () -> query.diagnostics().toString());
        return query.value();
    }

    private static GeneratedArtifact scalarArtifact(CypherExpr expression) {
        CypherQuery query = new CypherQuery(List.of(new ReturnClause(false,
                List.of(new ProjectionItem(expression, "result")))), true);
        return new GeneratedArtifact(Dialect.CYPHER_5, query,
                new ResultContract(ResultShape.SCALAR, "result", "Boolean3", false, null),
                List.of());
    }

    private static GeneratedArtifact contractArtifact(ResultContract contract) {
        CypherQuery query = new CypherQuery(List.of(new ReturnClause(false,
                List.of(new ProjectionItem(new BooleanLiteral(true), "result")))), true);
        return new GeneratedArtifact(Dialect.CYPHER_5, query, contract, List.of());
    }

    private static void assertRejected(GeneratedArtifact artifact) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> CypherAstWellFormednessValidator.validate(artifact));
        assertTrue(error.getMessage().startsWith("R_TARGET_WF:"), error.getMessage());
    }
}
