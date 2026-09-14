package org.uet.dse.ocl2cypher.execution;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.uet.dse.ocl2cypher.core.CoreExpr;
import org.uet.dse.ocl2cypher.core.CoreInterpreter;
import org.uet.dse.ocl2cypher.cypher.CypherAst;
import org.uet.dse.ocl2cypher.cypher.Neo4jCypherParserGate;
import org.uet.dse.ocl2cypher.cypher.Realization;
import org.uet.dse.ocl2cypher.cypher.Serializer;
import org.uet.dse.ocl2cypher.diagnostics.SourceSpan;
import org.uet.dse.ocl2cypher.graph.GraphKey;
import org.uet.dse.ocl2cypher.graph.GraphModel;
import org.uet.dse.ocl2cypher.qcyp.NormQ;
import org.uet.dse.ocl2cypher.qcyp.QInterpreter;
import org.uet.dse.ocl2cypher.qcyp.QNode;
import org.uet.dse.ocl2cypher.qcyp.QQuery;
import org.uet.dse.ocl2cypher.qcyp.QValidator;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.runtime.OclValue;

class R1ParameterContractTest {
    private static final SourceSpan S = SourceSpan.UNKNOWN;

    @Test
    void integerParameterHasOnePublicDeclarationAndExactQValue() {
        var declaration = new QNode.QParameter("limit", OclType.INTEGER);
        var expression = new QNode.QExpr.Parameter(S, declaration);
        var query = valueQuery(expression);

        assertTrue(QValidator.validate(query).isEmpty());
        assertSame(expression, NormQ.normalizeExpr(expression));

        var realized = Realization.realize(query, new GraphModel("m"),
                CypherAst.Dialect.CYPHER_5);
        assertTrue(realized.isSuccess(), () -> realized.diagnostics().toString());
        var parameter = realized.value().parameters().stream()
                .filter(p -> p.name().equals("limit")).findFirst().orElseThrow();
        assertEquals("Integer", parameter.logicalTypeTag());
        assertEquals(CypherAst.QueryParameter.Origin.PUBLIC, parameter.origin());
        assertNull(parameter.canonicalValue());
        String text = Serializer.serialize(realized.value()).cypherText();
        assertTrue(text.contains("$limit"), text);
        assertTrue(text.contains("toInteger"), text);
        Neo4jCypherParserGate.assertParses(text);

        Map<String, Object> wire = scalar(false, "Integer", "42");
        Map<String, Object> physical = Neo4jExecutionAdapter.buildParamMap(
                realized.value(), Map.of("limit", wire));
        assertSame(wire, physical.get("limit"));

        CoreInterpreter.Env env = new CoreInterpreter.Env();
        env.bindParameter("limit", new OclValue.IntegerValue(BigInteger.valueOf(42)));
        assertEquals(new OclValue.IntegerValue(BigInteger.valueOf(42)),
                QInterpreter.evalExpr(null, null, env, expression));
    }

    @Test
    void malformedRawUnknownAndUncertifiedNumericInputsAreRejected() {
        var integerArtifact = artifactWithPublic("p", "Integer");
        assertThrows(IllegalArgumentException.class,
                () -> Neo4jExecutionAdapter.buildParamMap(integerArtifact, Map.of("p", 1L)));
        assertThrows(IllegalArgumentException.class,
                () -> Neo4jExecutionAdapter.buildParamMap(integerArtifact,
                        Map.of("p", scalar(false, "Integer", "01"))));
        assertThrows(IllegalArgumentException.class,
                () -> Neo4jExecutionAdapter.buildParamMap(integerArtifact,
                        Map.of("p", scalar(false, "Integer", "9223372036854775808"))));

        var unknown = artifactWithPublic("p", "Mystery");
        assertThrows(IllegalArgumentException.class,
                () -> Neo4jExecutionAdapter.buildParamMap(unknown,
                        Map.of("p", scalar(false, "Mystery", "x"))));

        var unknownGenerated = new CypherAst.GeneratedArtifact(
                unknown.dialect(), unknown.query(), unknown.contract(),
                List.of(new CypherAst.QueryParameter("__oclMystery", "Mystery",
                        CypherAst.QueryParameter.Origin.GENERATED, "x")));
        assertThrows(IllegalArgumentException.class,
                () -> Neo4jExecutionAdapter.buildParamMap(unknownGenerated, Map.of()));

        var realArtifact = artifactWithPublic("p", "Real");
        assertThrows(IllegalArgumentException.class,
                () -> Neo4jExecutionAdapter.buildParamMap(realArtifact,
                        Map.of("p", scalar(false, "Real", "0.1"))));
        assertDoesNotThrow(() -> Neo4jExecutionAdapter.buildParamMap(realArtifact,
                Map.of("p", scalar(false, "Real", "0.5"))));
    }

    @Test
    void generatedModelKeyIsBoundFromItsCanonicalValueAndCannotBeOverridden() {
        var query = new CypherAst.CypherQuery(List.of(new CypherAst.ReturnClause(false,
                List.of(new CypherAst.ProjectionItem(
                        new CypherAst.ParameterExpr("__oclModelKey"), "result")))), true);
        var modelKey = new CypherAst.QueryParameter("__oclModelKey", "Physical:ModelKey",
                CypherAst.QueryParameter.Origin.GENERATED, "experiment-model");
        var artifact = new CypherAst.GeneratedArtifact(CypherAst.Dialect.CYPHER_5, query,
                new CypherAst.ResultContract(CypherAst.ResultShape.SCALAR,
                        "result", "String", false, null), List.of(modelKey));
        assertEquals(CypherAst.QueryParameter.Origin.GENERATED, modelKey.origin());
        assertEquals("experiment-model", modelKey.canonicalValue());
        assertEquals("experiment-model", Neo4jExecutionAdapter.buildParamMap(
                artifact, Map.of()).get(modelKey.name()));
        assertThrows(IllegalArgumentException.class,
                () -> Neo4jExecutionAdapter.buildParamMap(artifact,
                        Map.of(modelKey.name(), "caller-model")));
    }

    @Test
    void collectionDecodeSeparatesEmptyElementBottomWholeBottomAndMultiplicity() {
        OclType setType = OclType.set(OclType.INTEGER);
        OclType bagType = OclType.bag(OclType.INTEGER);
        Map<String, Object> one = scalar(false, "Integer", "1");
        Map<String, Object> integerBottom = scalar(true, "Integer", null);

        OclValue empty = Neo4jExecutionAdapter.decodePublicParameter(
                collection(false, "SET", "Integer", List.of()), setType, null);
        OclValue elementBottom = Neo4jExecutionAdapter.decodePublicParameter(
                collection(false, "SET", "Integer", List.of(integerBottom)), setType, null);
        OclValue wholeBottom = Neo4jExecutionAdapter.decodePublicParameter(
                collection(true, "SET", "Set<Integer>", List.of()), setType, null);
        OclValue bag = Neo4jExecutionAdapter.decodePublicParameter(
                collection(false, "BAG", "Integer", List.of(one, one)), bagType, null);

        assertEquals(0, ((OclValue.SetValue) empty).size());
        assertTrue(((OclValue.SetValue) elementBottom).members().get(0).isBottom());
        assertTrue(wholeBottom.isBottom());
        assertEquals(2, ((OclValue.BagValue) bag).size());
        assertThrows(IllegalArgumentException.class,
                () -> Neo4jExecutionAdapter.decodePublicParameter(
                        collection(false, "SET", "Integer", List.of(one, one)), setType, null));

        var parameter = new QNode.QExpr.Parameter(S,
                new QNode.QParameter("numbers", setType));
        var result = Realization.realize(valueQuery(parameter), new GraphModel("m"),
                CypherAst.Dialect.CYPHER_5);
        assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
        String cypher = Serializer.serialize(result.value()).cypherText();
        assertTrue(cypher.contains("parameterItem"), cypher);
        assertTrue(cypher.contains("toInteger"), cypher);
        Neo4jCypherParserGate.assertParses(cypher);
    }

    @Test
    void objectParameterMustResolveAndConformInsideTheGraphScope() {
        GraphModel graph = graphWithPerson("m", "p1");
        OclType person = OclType.clazz("Person");
        Map<String, Object> value = scalar(false, "Class:Person", "p1");
        assertEquals(new OclValue.ObjectValue(person, "p1"),
                Neo4jExecutionAdapter.decodePublicParameter(value, person, graph));
        assertThrows(IllegalArgumentException.class,
                () -> Neo4jExecutionAdapter.decodePublicParameter(value, person, null));
        assertThrows(IllegalArgumentException.class,
                () -> Neo4jExecutionAdapter.decodePublicParameter(
                        scalar(false, "Class:Person", "missing"), person, graph));
    }

    @Test
    void conflictingDeclarationsCannotShareOnePublicName() {
        var left = new QNode.QExpr.Parameter(S,
                new QNode.QParameter("same", OclType.INTEGER));
        var right = new QNode.QExpr.Parameter(S,
                new QNode.QParameter("same", OclType.STRING));
        var equality = new QNode.QExpr.Binary(S, CoreExpr.BinaryOp.VALUE_EQUAL,
                left, right, OclType.BOOLEAN);
        assertFalse(QValidator.validate(valueQuery(equality)).isEmpty());
        var realized = Realization.realize(valueQuery(equality), new GraphModel("m"),
                CypherAst.Dialect.CYPHER_5);
        assertTrue(realized.isFailure());
        assertEquals("R_PARAMETER_COLLISION", realized.primaryDiagnostic().code());
    }

    private static QQuery valueQuery(QNode.QExpr expression) {
        QQuery.QResultShape shape = expression.type.kind() == OclType.Kind.SET
                ? QQuery.QResultShape.SET
                : expression.type.kind() == OclType.Kind.BAG
                        ? QQuery.QResultShape.BAG : QQuery.QResultShape.SCALAR;
        return new QQuery(expression, null, shape, QQuery.QueryMode.VALUE,
                expression.type, null, null, false);
    }

    private static CypherAst.GeneratedArtifact artifactWithPublic(String name, String tag) {
        var query = new CypherAst.CypherQuery(List.of(new CypherAst.ReturnClause(false,
                List.of(new CypherAst.ProjectionItem(new CypherAst.ParameterExpr(name), "result")))),
                true);
        return new CypherAst.GeneratedArtifact(CypherAst.Dialect.CYPHER_5, query,
                new CypherAst.ResultContract(CypherAst.ResultShape.SCALAR,
                        "result", "String", false, null),
                List.of(new CypherAst.QueryParameter(name, tag,
                        CypherAst.QueryParameter.Origin.PUBLIC, null)));
    }

    private static Map<String, Object> scalar(boolean bottom, String type, Object payload) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("__oclBottom", bottom);
        map.put("__oclType", type);
        map.put("__oclValue", payload);
        return map;
    }

    private static Map<String, Object> collection(boolean bottom, String kind, String type,
                                                   List<?> items) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("__oclBottom", bottom);
        map.put("__oclKind", kind);
        map.put("__oclType", type);
        map.put("__oclItems", items);
        return map;
    }

    private static GraphModel graphWithPerson(String model, String objectId) {
        GraphModel graph = new GraphModel(model);
        String classNode = GraphKey.clazz(model, "Person");
        String objectNode = GraphKey.object(model, objectId);
        graph.addNode(new GraphModel.Node(classNode, model, GraphModel.Projection.SCHEMA,
                "UmlClass", List.of("UmlClass"),
                Map.of("modelKey", model, "classKey", "Person")));
        graph.addNode(new GraphModel.Node(objectNode, model, GraphModel.Projection.INSTANCE,
                "Object", List.of("Object"),
                Map.of("modelKey", model, "objectKey", objectId)));
        graph.addRelationship(new GraphModel.Relationship(
                GraphKey.of(model, GraphKey.Kind.OBJECT_TYPING, objectId, "Person"),
                model, GraphModel.Projection.TYPING, GraphModel.OBJECT_INSTANCE_OF,
                objectNode, classNode, Map.of("modelKey", model)));
        return graph;
    }
}
