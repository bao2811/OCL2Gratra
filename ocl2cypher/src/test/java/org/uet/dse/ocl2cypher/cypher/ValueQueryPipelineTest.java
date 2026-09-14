package org.uet.dse.ocl2cypher.cypher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.uet.dse.ocl2cypher.api.FrontendCompiler;
import org.uet.dse.ocl2cypher.api.ValueQueryRequest;
import org.uet.dse.ocl2cypher.core.CoreInterpreter;
import org.uet.dse.ocl2cypher.core.CoreInvariant;
import org.uet.dse.ocl2cypher.core.CoreLowering;
import org.uet.dse.ocl2cypher.core.CoreQuery;
import org.uet.dse.ocl2cypher.core.CoreUnit;
import org.uet.dse.ocl2cypher.graph.GraphBuilder;
import org.uet.dse.ocl2cypher.qcyp.QCypTranslator;
import org.uet.dse.ocl2cypher.qcyp.QInterpreter;
import org.uet.dse.ocl2cypher.qcyp.QQuery;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.runtime.OclValue;
import org.uet.dse.ocl2cypher.source.model.SchemaModel;
import org.uet.dse.ocl2cypher.source.model.Admission;
import org.uet.dse.ocl2cypher.source.model.CapabilityMatrix;
import org.uet.dse.ocl2cypher.source.model.Snapshot;
import org.uet.dse.ocl2cypher.source.model.UmlAttribute;
import org.uet.dse.ocl2cypher.source.model.UmlClass;
import org.uet.dse.ocl2cypher.source.omg.OmgAs;

/**
 * Independent ExpressionInOcl-like VALUE route; no invariant carrier.
 */
class ValueQueryPipelineTest {

    @Test
    void contextualExpressionUsesValueQueryCarrierAndPreservesBottom() {
        SchemaModel schema = SchemaModel.builder("m")
                .clazz(UmlClass.of("Person"))
                .attribute(UmlAttribute.of("Person", "age", OclType.INTEGER))
                .build();
        Snapshot snapshot = Snapshot.builder().object("carol", "Person").build();

        var frontend = FrontendCompiler.compileValueQuery(
                ValueQueryRequest.contextual("self.age", "Person"), schema);
        assertTrue(frontend.isSuccess(), () -> frontend.diagnostics().toString());
        OmgAs.ExpressionInOcl valueQuery = frontend.value();
        assertNotNull(valueQuery.contextVariable);
        assertEquals(OclType.INTEGER, valueQuery.bodyExpression.type);

        var core = CoreLowering.lowerValueQuery(schema, valueQuery);
        assertTrue(core.isSuccess(), () -> core.diagnostics().toString());
        assertEquals(CoreUnit.Mode.QUERY_VALUE, core.value().mode());

        var graph = GraphBuilder.build(schema, snapshot);
        assertTrue(graph.isSuccess(), () -> graph.diagnostics().toString());
        var q = QCypTranslator.translate(core.value());
        assertTrue(q.isSuccess(), () -> q.diagnostics().toString());

        CoreInterpreter.Env coreEnvironment = contextEnvironment(core.value(), "carol");
        CoreInterpreter.Env qEnvironment = contextEnvironment(core.value(), "carol");
        OclValue coreValue = CoreInterpreter.evalUnit(schema, snapshot, core.value(),
                coreEnvironment);
        OclValue qValue = QInterpreter.evalExpr(schema, graph.value().graph(), qEnvironment,
                q.value().expressionBody());
        assertEquals(new OclValue.BottomValue(OclType.INTEGER), coreValue);
        assertEquals(coreValue, qValue);

        var realized = Realization.realize(q.value(), graph.value().graph(),
                CypherAst.Dialect.CYPHER_5);
        assertTrue(realized.isSuccess(), () -> "certified attribute must be accepted: "
                + realized.diagnostics());
        return;
    }

    @Test
    void contextlessCollectionExpressionHasNoSyntheticSelfOrMatch() {
        SchemaModel schema = SchemaModel.builder("m").clazz(UmlClass.of("Person")).build();
        Snapshot snapshot = Snapshot.builder().build();

        var frontend = FrontendCompiler.compileValueQuery(
                ValueQueryRequest.contextless("Bag{1, 1}"), schema);
        assertTrue(frontend.isSuccess(), () -> frontend.diagnostics().toString());
        assertNull(frontend.value().contextVariable);

        var core = CoreLowering.lowerValueQuery(schema, frontend.value());
        assertTrue(core.isSuccess(), () -> core.diagnostics().toString());
        assertNull(core.value().contextClassKey());
        assertNull(core.value().selfVariable());
        assertEquals(new OclValue.BagValue(OclType.bag(OclType.INTEGER),
                java.util.List.of(new OclValue.IntegerValue(BigInteger.ONE),
                        new OclValue.IntegerValue(BigInteger.ONE))),
                CoreInterpreter.evalUnit(schema, snapshot, core.value(),
                        new CoreInterpreter.Env()));

        var q = QCypTranslator.translate(core.value());
        assertTrue(q.isSuccess(), () -> q.diagnostics().toString());
        var graph = GraphBuilder.build(schema, snapshot);
        assertTrue(graph.isSuccess(), () -> graph.diagnostics().toString());
        var realized = Realization.realize(q.value(), graph.value().graph(),
                CypherAst.Dialect.CYPHER_5);
        assertTrue(realized.isSuccess(), () -> realized.diagnostics().toString());
        assertEquals(CypherAst.ResultShape.BAG, realized.value().contract().shape());
        String text = Serializer.cypherText(realized.value());
        assertFalse(text.contains("MATCH"), text);
        assertTrue(realized.value().parameters().isEmpty());
        Neo4jCypherParserGate.parse(text);
    }

    @Test
    void malformedOrUnboundIndependentExpressionFailsAtFrontend() {
        SchemaModel schema = SchemaModel.builder("m").clazz(UmlClass.of("Person")).build();

        var unbound = FrontendCompiler.compileValueQuery(
                ValueQueryRequest.contextless("self"), schema);
        assertTrue(unbound.isFailure());
        assertEquals("E_RESOLUTION", unbound.primaryDiagnostic().code());

        var invariantText = FrontendCompiler.compileValueQuery(
                ValueQueryRequest.contextless("context Person inv X: true"), schema);
        assertTrue(invariantText.isFailure());
        assertNotNull(invariantText.primaryDiagnostic());
    }

    @Test
    void declaredValueResultTypeIsCheckedAtEsm() {
        SchemaModel schema = SchemaModel.builder("m").clazz(UmlClass.of("Person")).build();

        var matching = FrontendCompiler.compileValueQuery(
                ValueQueryRequest.contextless("1").expecting(OclType.INTEGER), schema);
        assertTrue(matching.isSuccess(), () -> matching.diagnostics().toString());
        assertEquals(OclType.INTEGER, matching.value().bodyExpression.type);

        var mismatch = FrontendCompiler.compileValueQuery(
                ValueQueryRequest.contextless("1").expecting(OclType.STRING), schema);
        assertTrue(mismatch.isFailure());
        assertEquals("E_VALUE_RESULT_TYPE", mismatch.primaryDiagnostic().code());
        assertEquals(org.uet.dse.ocl2cypher.diagnostics.Stage.E_SM,
                mismatch.primaryDiagnostic().stage());
    }

    @Test
    void valueMatrixKeepsEmptyKindsElementBottomWholeBottomObjectAndBooleanBottom() {
        SchemaModel schema = SchemaModel.builder("m")
                .clazz(UmlClass.of("Person"))
                .attribute(UmlAttribute.of("Person", "age", OclType.INTEGER))
                .build();
        Snapshot snapshot = Snapshot.builder().object("p", "Person").build();
        var graph = GraphBuilder.build(schema, snapshot);
        assertTrue(graph.isSuccess(), () -> graph.diagnostics().toString());

        assertValue(schema, snapshot, graph.value().graph(),
                ValueQueryRequest.contextless("Set{1}->select(x | false)"),
                new OclValue.SetValue(OclType.set(OclType.INTEGER), List.of()));
        assertValue(schema, snapshot, graph.value().graph(),
                ValueQueryRequest.contextless("Bag{1}->select(x | false)"),
                new OclValue.BagValue(OclType.bag(OclType.INTEGER), List.of()));
        assertValue(schema, snapshot, graph.value().graph(),
                ValueQueryRequest.contextless("Bag{1, 1}"),
                new OclValue.BagValue(OclType.bag(OclType.INTEGER), List.of(
                        new OclValue.IntegerValue(BigInteger.ONE),
                        new OclValue.IntegerValue(BigInteger.ONE))));
        assertValue(schema, snapshot, graph.value().graph(),
                ValueQueryRequest.contextual("Set{self.age}", "Person"),
                new OclValue.SetValue(OclType.set(OclType.INTEGER), List.of(
                        new OclValue.BottomValue(OclType.INTEGER))));
        assertValue(schema, snapshot, graph.value().graph(),
                ValueQueryRequest.contextual(
                        "Set{1}->select(x | self.age > 0)", "Person"),
                new OclValue.BottomValue(OclType.set(OclType.INTEGER)));
        assertValue(schema, snapshot, graph.value().graph(),
                ValueQueryRequest.contextual("self", "Person"),
                new OclValue.ObjectValue(OclType.clazz("Person"), "p"));
        assertValue(schema, snapshot, graph.value().graph(),
                ValueQueryRequest.contextual("self.age > 0", "Person"),
                new OclValue.BottomValue(OclType.BOOLEAN));
    }

    @Test
    void omgValueCarrierPreservesShadowedDeclarationIdentity() {
        SchemaModel schema = SchemaModel.builder("m").clazz(UmlClass.of("Person")).build();
        var frontend = FrontendCompiler.compileValueQuery(
                ValueQueryRequest.contextless("let x = 1 in let x = 2 in x"), schema);
        assertTrue(frontend.isSuccess(), () -> frontend.diagnostics().toString());

        OmgAs.LetExp outer = (OmgAs.LetExp) frontend.value().bodyExpression;
        OmgAs.LetExp inner = (OmgAs.LetExp) outer.in;
        OmgAs.VariableExp reference = (OmgAs.VariableExp) inner.in;
        assertTrue(reference.referredVariable == inner.variable);
        assertFalse(reference.referredVariable == outer.variable);

        var core = CoreLowering.lowerValueQuery(schema, frontend.value());
        assertTrue(core.isSuccess(), () -> core.diagnostics().toString());
        assertEquals(new OclValue.IntegerValue(BigInteger.valueOf(2)),
                CoreInterpreter.evalUnit(schema, Snapshot.builder().build(), core.value(),
                        new CoreInterpreter.Env()));
    }

    @Test
    void omgAdmissionRejectsSurfaceNullAtNsmRatherThanTurningItIntoBottom() {
        SchemaModel schema = SchemaModel.builder("m").clazz(UmlClass.of("Person")).build();
        var frontend = FrontendCompiler.compileValueQuery(
                ValueQueryRequest.contextless("null"), schema);
        assertTrue(frontend.isSuccess(), () -> frontend.diagnostics().toString());
        var core = CoreLowering.lowerValueQuery(schema, frontend.value());
        assertTrue(core.isFailure());
        assertEquals("N_UNSUPPORTED_NULL_LITERAL", core.primaryDiagnostic().code());
    }

    @Test
    void publicFrontendAndLoweringSignaturesDoNotExposeLegacyAsNode() throws Exception {
        for (Class<?> api : List.of(FrontendCompiler.class, CoreLowering.class,
                Admission.class, CapabilityMatrix.class)) {
            for (var method : api.getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers())) {
                    continue;
                }
                assertFalse(method.getGenericReturnType().getTypeName().contains("AsNode"),
                        method.toGenericString());
                for (var parameter : method.getGenericParameterTypes()) {
                    assertFalse(parameter.getTypeName().contains("AsNode"),
                            method.toGenericString());
                }
            }
        }
        assertFalse(Modifier.isPublic(Class.forName(
                "org.uet.dse.ocl2cypher.frontend.OclFrontend").getModifiers()));
        assertThrows(ClassNotFoundException.class, () -> Class.forName(
                "org.uet.dse.ocl2cypher.frontend.OmgAsBuilder"),
                "Phase 5 removes the post-frontend legacy-to-OMG builder");
        assertThrows(ClassNotFoundException.class, () -> Class.forName(
                "org.uet.dse.ocl2cypher.source.ast.AsNode"),
                "Phase 5 removes the legacy parser AST carrier");
        Set<Class<?>> translatorInputs = java.util.Arrays.stream(
                QCypTranslator.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> method.getName().equals("translate"))
                .map(method -> method.getParameterTypes()[0])
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of(CoreInvariant.class, CoreQuery.class), translatorInputs);
    }

    private static void assertValue(SchemaModel schema, Snapshot snapshot,
            org.uet.dse.ocl2cypher.graph.GraphModel graph,
            ValueQueryRequest request, OclValue expected) {
        var frontend = FrontendCompiler.compileValueQuery(request, schema);
        assertTrue(frontend.isSuccess(), () -> request.expression() + ": "
                + frontend.diagnostics());
        var core = CoreLowering.lowerValueQuery(schema, frontend.value());
        assertTrue(core.isSuccess(), () -> request.expression() + ": " + core.diagnostics());
        var query = QCypTranslator.translate(core.value());
        assertTrue(query.isSuccess(), () -> request.expression() + ": "
                + query.diagnostics());

        CoreInterpreter.Env coreEnvironment = request.contextClassKey() == null
                ? new CoreInterpreter.Env() : contextEnvironment(core.value(), "p");
        CoreInterpreter.Env qEnvironment = request.contextClassKey() == null
                ? new CoreInterpreter.Env() : contextEnvironment(core.value(), "p");
        OclValue coreValue = CoreInterpreter.evalUnit(schema, snapshot, core.value(),
                coreEnvironment);
        OclValue qValue = QInterpreter.value(schema, graph, core.value(), query.value(),
                qEnvironment);
        assertEquals(expected, coreValue, request.expression() + " Core");
        assertEquals(coreValue, qValue, request.expression() + " Core/Q");

        var artifact = Realization.realize(query.value(), graph, CypherAst.Dialect.CYPHER_5);
        assertTrue(artifact.isSuccess(), () -> request.expression() + ": "
                + artifact.diagnostics());
        Neo4jCypherParserGate.parse(Serializer.cypherText(artifact.value()));
    }

    private static CoreInterpreter.Env contextEnvironment(CoreQuery unit, String stableId) {
        CoreInterpreter.Env environment = new CoreInterpreter.Env();
        environment.bind(unit.selfVariable(), new OclValue.ObjectValue(
                OclType.clazz(unit.contextClassKey()), stableId));
        return environment;
    }
}
