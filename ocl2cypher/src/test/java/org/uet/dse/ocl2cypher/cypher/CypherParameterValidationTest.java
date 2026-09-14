package org.uet.dse.ocl2cypher.cypher;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.uet.dse.ocl2cypher.cypher.CypherAst.*;

class CypherParameterValidationTest {
    private static GeneratedArtifact artifact(CypherExpr expression, QueryParameter... parameters) {
        return new GeneratedArtifact(Dialect.CYPHER_5,
                new CypherQuery(List.of(new ReturnClause(false,
                        List.of(new ProjectionItem(expression, "result")))), true),
                new ResultContract(ResultShape.SCALAR, "result", "String", false, null), List.of(parameters));
    }
    private static void rejects(GeneratedArtifact artifact) {
        var error = assertThrows(IllegalArgumentException.class,
                () -> CypherAstWellFormednessValidator.validate(artifact));
        assertTrue(error.getMessage().startsWith("R_TARGET_WF:"));
    }
    @Test void declaredParameterIsVisibleInsideNestedQuery() {
        var nested = new CollectSubquery(new CypherQuery(List.of(new ReturnClause(false,
                List.of(new ProjectionItem(new ParameterExpr("value"), "x")))), true));
        rejects(artifact(nested));
        assertDoesNotThrow(() -> CypherAstWellFormednessValidator.validate(artifact(nested,
                new QueryParameter("value", "String", QueryParameter.Origin.PUBLIC, null))));
    }
    @Test void missingReferenceCannotHideInsideMapOrCase() {
        rejects(artifact(new MapExpr(List.of(new MapEntry("x", new ParameterExpr("missing"))))));
        rejects(artifact(new CaseExpr(List.of(new WhenThen(new BooleanLiteral(true),
                new ParameterExpr("missing"))), new StringLiteral("fallback"))));
    }
    @Test void generatedBindingAndPublicBindingAreExclusive() {
        var expression = new StringLiteral("x");
        rejects(artifact(expression, new QueryParameter("__oclKey", "String", QueryParameter.Origin.GENERATED, null)));
        rejects(artifact(expression, new QueryParameter("value", "String", QueryParameter.Origin.PUBLIC, "injected")));
        assertDoesNotThrow(() -> CypherAstWellFormednessValidator.validate(artifact(expression,
                new QueryParameter("__oclKey", "String", QueryParameter.Origin.GENERATED, ""))));
    }
    @Test void contextExceptionRequiresExactNameTypeAndOrigin() {
        var expression = new ParameterExpr("__oclContextId");
        assertDoesNotThrow(() -> CypherAstWellFormednessValidator.validate(artifact(expression,
                new QueryParameter("__oclContextId", "Physical:StableObjectId", QueryParameter.Origin.PUBLIC, null))));
        rejects(artifact(expression, new QueryParameter("__oclContextId", "Integer", QueryParameter.Origin.PUBLIC, null)));
        rejects(artifact(expression, new QueryParameter("__oclContextId", "Physical:StableObjectId", QueryParameter.Origin.GENERATED, "id")));
        rejects(artifact(expression, new QueryParameter("__oclOther", "Physical:StableObjectId", QueryParameter.Origin.PUBLIC, null)));
    }
    @Test void duplicateAndEmptyDeclarationsReject() {
        var p = new QueryParameter("v", "String", QueryParameter.Origin.PUBLIC, null);
        rejects(artifact(new StringLiteral("x"), p, p));
        rejects(artifact(new StringLiteral("x"), new QueryParameter("", "String", QueryParameter.Origin.PUBLIC, null)));
        rejects(artifact(new StringLiteral("x"), new QueryParameter("v", null, QueryParameter.Origin.PUBLIC, null)));
    }
}
