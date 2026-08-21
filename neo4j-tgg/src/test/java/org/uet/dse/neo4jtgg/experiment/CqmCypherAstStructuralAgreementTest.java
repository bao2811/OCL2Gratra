package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Test;
import org.tzi.use.uml.mm.MModel;
import org.uet.dse.neo4jtgg.service.impl.DefaultOclToCypherCompiler;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compares CQM lowering with Neo4j's independently parsed Cypher AST and keeps
 * a regression bound on textual/AST expansion for a formerly explosive plan.
 */
class CqmCypherAstStructuralAgreementTest {
    @Test
    void setImageCollectHasTheRequiredAstShapeWithoutRecursiveTextExplosion() {
        MModel model = OclCypherPlanFormalTreeAgreementTest.model();
        var result = new DefaultOclToCypherCompiler(model).compileInvariantInstrumented(
                "context Company inv CollectUsesSetImage: "
                        + "self.employee->collect(e | e.age)->size() = 1");
        var plan = result.queryPlan();
        String cypher = result.cypher();

        ExpectedFormalCypherTreeVerifier.verifyInvariant(plan, model.name());
        Neo4jCypherAstBridge.Observation ast = Neo4jCypherAstBridge.parse(cypher);

        assertTrue(ast.hasAnyType("CollectExpression"), ast.productNames().toString());
        assertTrue(ast.hasAnyType("CaseExpression"), ast.productNames().toString());
        assertTrue(ast.hasAnyType("ReduceExpression"), ast.productNames().toString());
        assertTrue(ast.hasAnyType("ListComprehension"), ast.productNames().toString());

        assertTrue(cypher.length() < 15_000, "CQM lowering expanded to " + cypher.length() + " characters");
        assertTrue(occurrences(cypher, "ObjectHasAttribute") <= 3, cypher);
        assertTrue(occurrences(cypher, "COLLECT {") <= 20, cypher);
        assertTrue(occurrences(cypher, "reduce(") <= 20, cypher);
        assertTrue(occurrences(cypher, "CASE WHEN") <= 25, cypher);
        assertTrue(ast.count("CollectExpression") <= 20, ast.productCounts().toString());
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }
}
