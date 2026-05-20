package org.uet.dse.neo4jtgg.ocl.ir;

import org.junit.jupiter.api.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.uet.dse.neo4j.OCLLexer;
import org.uet.dse.neo4j.OCLParser;
import org.uet.dse.neo4j.oclite.ast.ASTContext;
import org.uet.dse.neo4j.oclite.ast.ASTNode;
import org.uet.dse.neo4j.oclite.ast.ASTVisitor;
import org.uet.dse.neo4jtgg.ocl.OclMetamodelIndex;
import org.uet.dse.neo4jtgg.ocl.OclSemanticBinder;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OclCypherPlannerTest {
    @Test
    void plansOptimizedNavigationNodesIntoDedicatedPlanTypes() {
        String spec = """
                model Demo
                class Family
                end
                class Person
                attributes
                    age : Integer
                end
                association FamilyChildren between
                    Family[*] role family
                    Person[*] role children
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        ASTNode ast = new ASTVisitor().visit(new OCLParser(new org.antlr.v4.runtime.CommonTokenStream(
                new OCLLexer(org.antlr.v4.runtime.CharStreams.fromString(
                        "context Family inv TwoAdults: self.children->select(c | c.age >= 18)->size() >= 2 and self.children->exists(c | c.age >= 18)")))).oclFile());
        assertTrue(ast instanceof ASTContext);

        OclSemanticBinder binder = new OclSemanticBinder(new OclMetamodelIndex(model));
        OclSemanticBinder.BoundContextInvariant bound = binder.bindContext((ASTContext) ast);
        OclIr.InvariantQuery optimized = new OclIrOptimizer().optimizeInvariant(new OclIrBuilder().buildInvariant(bound));
        OclCypherPlan.InvariantPlan plan = new OclCypherPlanner().planInvariant(optimized);

        assertTrue(plan.predicate() instanceof OclCypherPlan.BinaryPlan);
        OclCypherPlan.BinaryPlan predicate = (OclCypherPlan.BinaryPlan) plan.predicate();
        assertEquals("and", predicate.operator());
        assertTrue(predicate.left() instanceof OclCypherPlan.CountSubqueryComparisonPlan);
        assertTrue(predicate.right() instanceof OclCypherPlan.ExistsSubqueryPlan);
        OclCypherPlan.CountSubqueryComparisonPlan countPlan = (OclCypherPlan.CountSubqueryComparisonPlan) predicate.left();
        OclCypherPlan.ExistsSubqueryPlan predicatePlan = (OclCypherPlan.ExistsSubqueryPlan) predicate.right();
        assertEquals("c", countPlan.match().targetAlias());
        assertEquals("c", predicatePlan.match().targetAlias());
        assertTrue(countPlan.match().predicate() instanceof OclCypherPlan.BinaryPlan);
        assertTrue(predicatePlan.match().predicate() instanceof OclCypherPlan.BinaryPlan);
        assertEquals(OclCypherPlan.PredicateMode.NORMAL, countPlan.match().predicateMode());
        assertEquals(OclCypherPlan.PredicateMode.NORMAL, predicatePlan.match().predicateMode());
    }

    @Test
    void plansForAllAndEmptyChecksAsNotExistsSubqueries() {
        String spec = """
                model Demo
                class Family
                end
                class Person
                attributes
                    age : Integer
                end
                association FamilyChildren between
                    Family[*] role family
                    Person[*] role children
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        ASTNode ast = new ASTVisitor().visit(new OCLParser(new org.antlr.v4.runtime.CommonTokenStream(
                new OCLLexer(org.antlr.v4.runtime.CharStreams.fromString(
                        "context Family inv Checks: self.children->forAll(c | c.age >= 18) and self.children->isEmpty()")))).oclFile());
        assertTrue(ast instanceof ASTContext);

        OclSemanticBinder binder = new OclSemanticBinder(new OclMetamodelIndex(model));
        OclSemanticBinder.BoundContextInvariant bound = binder.bindContext((ASTContext) ast);
        OclIr.InvariantQuery optimized = new OclIrOptimizer().optimizeInvariant(new OclIrBuilder().buildInvariant(bound));
        OclCypherPlan.InvariantPlan plan = new OclCypherPlanner().planInvariant(optimized);

        assertTrue(plan.predicate() instanceof OclCypherPlan.BinaryPlan);
        OclCypherPlan.BinaryPlan predicate = (OclCypherPlan.BinaryPlan) plan.predicate();
        assertTrue(predicate.left() instanceof OclCypherPlan.NotExistsSubqueryPlan);
        assertTrue(predicate.right() instanceof OclCypherPlan.NotExistsSubqueryPlan);
        OclCypherPlan.NotExistsSubqueryPlan forAllPlan = (OclCypherPlan.NotExistsSubqueryPlan) predicate.left();
        OclCypherPlan.NotExistsSubqueryPlan emptyPlan = (OclCypherPlan.NotExistsSubqueryPlan) predicate.right();
        assertEquals(OclCypherPlan.PredicateMode.NEGATED, forAllPlan.match().predicateMode());
        assertEquals(OclCypherPlan.PredicateMode.NONE, emptyPlan.match().predicateMode());
    }
}
