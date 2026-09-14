package org.uet.dse.ocl2cypher.cypher;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.uet.dse.ocl2cypher.cypher.CypherAst.*;

class CypherScopeValidationTest {
    private static final CypherExpr TRUE = new BooleanLiteral(true);
    private static ReturnClause ret(CypherExpr e) {
        return new ReturnClause(false, List.of(new ProjectionItem(e, "result")));
    }
    private static void validate(CypherClause... clauses) {
        CypherAstWellFormednessValidator.validate(new GeneratedArtifact(Dialect.CYPHER_5,
                new CypherQuery(List.of(clauses), true),
                new ResultContract(ResultShape.SCALAR, "result", "Boolean3", false, null), List.of()));
    }
    private static void rejects(CypherClause... clauses) {
        var error = assertThrows(IllegalArgumentException.class, () -> validate(clauses));
        assertTrue(error.getMessage().startsWith("R_TARGET_WF:"));
    }
    @Test void visitsMapAndCaseBranches() {
        rejects(ret(new MapExpr(List.of(new MapEntry("x", new VariableExpr("ghost"))))));
        rejects(ret(new CaseExpr(List.of(new WhenThen(TRUE, new VariableExpr("ghost"))), TRUE)));
        validate(ret(new CaseExpr(List.of(new WhenThen(TRUE, TRUE)), TRUE)));
    }
    @Test void withValidatesOldScopeAndDropsUnprojectedNames() {
        rejects(new WithClause(false, List.of(new ProjectionItem(new VariableExpr("ghost"), "x")), null), ret(TRUE));
        var bind = new UnwindClause(new ListExpr(List.of(TRUE)), "x");
        validate(bind, new WithClause(false, List.of(new ProjectionItem(new VariableExpr("x"), null)), null), ret(new VariableExpr("x")));
        rejects(bind, new WithClause(false, List.of(new ProjectionItem(TRUE, "y")), null), ret(new VariableExpr("x")));
        rejects(new WithClause(false, List.of(new ProjectionItem(TRUE, "x"),
                new ProjectionItem(new VariableExpr("x"), "y")), null), ret(TRUE));
    }
    @Test void checksPredicatesAndUnwindInput() {
        rejects(new UnwindClause(new VariableExpr("x"), "x"), ret(TRUE));
        rejects(new WithClause(false, List.of(new ProjectionItem(TRUE, "x")), new VariableExpr("ghost")), ret(TRUE));
        var pattern = new Pattern(List.of(new PathPattern(List.of(new NodePattern("n", List.of(), List.of())), List.of())));
        rejects(new MatchClause(pattern, new VariableExpr("ghost")), ret(TRUE));
    }
    @Test void comprehensionAndReduceBindOnlyTheirBody() {
        validate(ret(new ListComprehension("x", new ListExpr(List.of(TRUE)), null, new VariableExpr("x"))));
        rejects(ret(new ListComprehension("x", new VariableExpr("x"), null, TRUE)));
        validate(ret(new ReduceExpr("acc", TRUE, "x", new ListExpr(List.of(TRUE)), new VariableExpr("acc"))));
        rejects(ret(new ReduceExpr("acc", new VariableExpr("acc"), "x", new ListExpr(List.of()), TRUE)));
        rejects(ret(new ListExpr(List.of(new ListComprehension("x", new ListExpr(List.of()), null, TRUE), new VariableExpr("x")))));
    }
    @Test void correlatedSubquerySeesOuterButDoesNotExportLocals() {
        var bind = new UnwindClause(new ListExpr(List.of(TRUE)), "outer");
        validate(bind, ret(new CollectSubquery(new CypherQuery(List.of(ret(new VariableExpr("outer"))), true))));
        rejects(ret(new CollectSubquery(new CypherQuery(List.of(ret(new VariableExpr("ghost"))), true))));
        var nested = new CollectSubquery(new CypherQuery(List.of(
                new UnwindClause(new ListExpr(List.of(TRUE)), "local"), ret(new VariableExpr("local"))), true));
        rejects(ret(new ListExpr(List.of(nested, new VariableExpr("local")))));
    }
    @Test void collectChecksCardinalityAndFunctionChecksExactArity() {
        rejects(ret(new CollectSubquery(new CypherQuery(List.of(new ReturnClause(false,
                List.of(new ProjectionItem(TRUE, "a"), new ProjectionItem(TRUE, "b")))), true))));
        rejects(ret(new FunctionCall("size", false, List.of(TRUE, TRUE))));
        rejects(ret(new FunctionCall("size", true, List.of(TRUE))));
        validate(ret(new FunctionCall("collect", true, List.of(TRUE))));
    }
}
