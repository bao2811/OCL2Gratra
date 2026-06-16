package org.uet.dse.neo4jtgg.ocl.ir;

import org.junit.jupiter.api.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.uet.dse.neo4j.OCLLexer;
import org.uet.dse.neo4j.OCLParser;
import org.uet.dse.neo4j.oclite.ast.ASTFile;
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

        OclSemanticBinder binder = new OclSemanticBinder(new OclMetamodelIndex(model));
        OclSemanticBinder.BoundContextInvariant bound = binder.bindContext(firstContext(ast));
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

        OclSemanticBinder binder = new OclSemanticBinder(new OclMetamodelIndex(model));
        OclSemanticBinder.BoundContextInvariant bound = binder.bindContext(firstContext(ast));
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

    @Test
    void plansOptimizedSelectExistsAndOneIntoDedicatedNavigationPlans() {
        String spec = """
                model Demo
                class Family
                end
                class Person
                attributes
                    age : Integer
                    name : String
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
                        "context Family inv MixedChecks: self.children->select(c | c.age >= 18)->exists(c | c.name = 'Bart') and self.children->one(c | c.age >= 18)")))).oclFile());

        OclSemanticBinder binder = new OclSemanticBinder(new OclMetamodelIndex(model));
        OclSemanticBinder.BoundContextInvariant bound = binder.bindContext(firstContext(ast));
        OclIr.InvariantQuery optimized = new OclIrOptimizer().optimizeInvariant(new OclIrBuilder().buildInvariant(bound));
        OclCypherPlan.InvariantPlan plan = new OclCypherPlanner().planInvariant(optimized);

        assertTrue(plan.predicate() instanceof OclCypherPlan.BinaryPlan);
        OclCypherPlan.BinaryPlan predicate = (OclCypherPlan.BinaryPlan) plan.predicate();
        assertTrue(predicate.left() instanceof OclCypherPlan.ExistsSubqueryPlan);
        assertTrue(predicate.right() instanceof OclCypherPlan.CountSubqueryComparisonPlan);
        OclCypherPlan.ExistsSubqueryPlan existsPlan = (OclCypherPlan.ExistsSubqueryPlan) predicate.left();
        OclCypherPlan.CountSubqueryComparisonPlan onePlan = (OclCypherPlan.CountSubqueryComparisonPlan) predicate.right();
        assertEquals("c", existsPlan.match().targetAlias());
        assertEquals("c", onePlan.match().targetAlias());
        assertTrue(existsPlan.match().predicate() instanceof OclCypherPlan.BinaryPlan);
        assertEquals("=", onePlan.operator());
        assertEquals(1L, onePlan.literal());
    }

    @Test
    void plansRejectedNavigationChecksAsDedicatedSubqueries() {
        String spec = """
                model Demo
                class Family
                end
                class Person
                attributes
                    age : Integer
                    name : String
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
                        "context Family inv RejectChecks: self.children->reject(c | c.age >= 18)->isEmpty() and self.children->reject(c | c.age >= 18)->one(c | c.name = 'Lisa')")))).oclFile());

        OclSemanticBinder binder = new OclSemanticBinder(new OclMetamodelIndex(model));
        OclSemanticBinder.BoundContextInvariant bound = binder.bindContext(firstContext(ast));
        OclIr.InvariantQuery optimized = new OclIrOptimizer().optimizeInvariant(new OclIrBuilder().buildInvariant(bound));
        OclCypherPlan.InvariantPlan plan = new OclCypherPlanner().planInvariant(optimized);

        assertTrue(plan.predicate() instanceof OclCypherPlan.BinaryPlan);
        OclCypherPlan.BinaryPlan predicate = (OclCypherPlan.BinaryPlan) plan.predicate();
        assertTrue(predicate.left() instanceof OclCypherPlan.NotExistsSubqueryPlan);
        assertTrue(predicate.right() instanceof OclCypherPlan.CountSubqueryComparisonPlan);
        OclCypherPlan.NotExistsSubqueryPlan emptyPlan = (OclCypherPlan.NotExistsSubqueryPlan) predicate.left();
        OclCypherPlan.CountSubqueryComparisonPlan onePlan = (OclCypherPlan.CountSubqueryComparisonPlan) predicate.right();
        assertEquals(OclCypherPlan.PredicateMode.NORMAL, emptyPlan.match().predicateMode());
        assertTrue(emptyPlan.match().predicate() instanceof OclCypherPlan.NotPlan);
        assertEquals("=", onePlan.operator());
        assertEquals(1L, onePlan.literal());
    }

    @Test
    void plansMultiLevelSelectRejectChainAsSingleExistsSubquery() {
        String spec = """
                model Demo
                class Family
                end
                class Person
                attributes
                    age : Integer
                    name : String
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
                        "context Family inv ChainedFilters: self.children->select(c | c.age >= 18)->reject(c | c.name = 'Bart')->exists(c | c.name = 'Lisa')")))).oclFile());

        OclSemanticBinder binder = new OclSemanticBinder(new OclMetamodelIndex(model));
        OclSemanticBinder.BoundContextInvariant bound = binder.bindContext(firstContext(ast));
        OclIr.InvariantQuery optimized = new OclIrOptimizer().optimizeInvariant(new OclIrBuilder().buildInvariant(bound));
        OclCypherPlan.InvariantPlan plan = new OclCypherPlanner().planInvariant(optimized);

        assertTrue(plan.predicate() instanceof OclCypherPlan.ExistsSubqueryPlan);
        OclCypherPlan.ExistsSubqueryPlan existsPlan = (OclCypherPlan.ExistsSubqueryPlan) plan.predicate();
        assertEquals("c", existsPlan.match().targetAlias());
        assertTrue(existsPlan.match().predicate() instanceof OclCypherPlan.BinaryPlan);
    }

    @Test
    void plansMultiLevelSelectRejectChainWithDifferentIteratorNamesAsSingleExistsSubquery() {
        String spec = """
                model Demo
                class Family
                end
                class Person
                attributes
                    age : Integer
                    name : String
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
                        "context Family inv ChainedFilters: self.children->select(a | a.age >= 18)->reject(b | b.name = 'Bart')->exists(c | c.name = 'Lisa')")))).oclFile());

        OclSemanticBinder binder = new OclSemanticBinder(new OclMetamodelIndex(model));
        OclSemanticBinder.BoundContextInvariant bound = binder.bindContext(firstContext(ast));
        OclIr.InvariantQuery optimized = new OclIrOptimizer().optimizeInvariant(new OclIrBuilder().buildInvariant(bound));
        OclCypherPlan.InvariantPlan plan = new OclCypherPlanner().planInvariant(optimized);

        assertTrue(plan.predicate() instanceof OclCypherPlan.ExistsSubqueryPlan);
        OclCypherPlan.ExistsSubqueryPlan existsPlan = (OclCypherPlan.ExistsSubqueryPlan) plan.predicate();
        assertEquals("c", existsPlan.match().targetAlias());
        assertTrue(existsPlan.match().predicate() instanceof OclCypherPlan.BinaryPlan);
    }

    @Test
    void plansFlattenNotEmptyAsOuterExistsWithNestedPredicate() {
        String spec = """
                model Demo
                class Family
                end
                class Person
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
                        "context Family inv FlattenedFamiliesExist: self.children->collect(c | c.family)->flatten()->notEmpty()")))).oclFile());

        OclSemanticBinder binder = new OclSemanticBinder(new OclMetamodelIndex(model));
        OclSemanticBinder.BoundContextInvariant bound = binder.bindContext(firstContext(ast));
        OclIr.InvariantQuery optimized = new OclIrOptimizer().optimizeInvariant(new OclIrBuilder().buildInvariant(bound));
        OclCypherPlan.InvariantPlan plan = new OclCypherPlanner().planInvariant(optimized);

        assertTrue(plan.predicate() instanceof OclCypherPlan.ExistsSubqueryPlan);
        OclCypherPlan.ExistsSubqueryPlan existsPlan = (OclCypherPlan.ExistsSubqueryPlan) plan.predicate();
        assertEquals("c", existsPlan.match().targetAlias());
        assertTrue(existsPlan.match().predicate() instanceof OclCypherPlan.CollectionOperationPlan);
        OclCypherPlan.CollectionOperationPlan nested = (OclCypherPlan.CollectionOperationPlan) existsPlan.match().predicate();
        assertEquals("notEmpty", nested.operationName());
    }

    @Test
    void plansFlattenIsEmptyAsOuterNotExistsWithNestedPredicate() {
        String spec = """
                model Demo
                class Family
                end
                class Person
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
                        "context Family inv FlattenedFamiliesEmpty: self.children->collect(c | c.family)->flatten()->isEmpty()")))).oclFile());

        OclSemanticBinder binder = new OclSemanticBinder(new OclMetamodelIndex(model));
        OclSemanticBinder.BoundContextInvariant bound = binder.bindContext(firstContext(ast));
        OclIr.InvariantQuery optimized = new OclIrOptimizer().optimizeInvariant(new OclIrBuilder().buildInvariant(bound));
        OclCypherPlan.InvariantPlan plan = new OclCypherPlanner().planInvariant(optimized);

        assertTrue(plan.predicate() instanceof OclCypherPlan.NotExistsSubqueryPlan);
        OclCypherPlan.NotExistsSubqueryPlan existsPlan = (OclCypherPlan.NotExistsSubqueryPlan) plan.predicate();
        assertEquals("c", existsPlan.match().targetAlias());
        assertTrue(existsPlan.match().predicate() instanceof OclCypherPlan.CollectionOperationPlan);
        OclCypherPlan.CollectionOperationPlan nested = (OclCypherPlan.CollectionOperationPlan) existsPlan.match().predicate();
        assertEquals("notEmpty", nested.operationName());
    }

    @Test
    void plansNavigationAggregatesIntoDedicatedAggregationPlan() {
        String spec = """
                model Demo
                class Family
                end
                class Person
                attributes
                    age : Integer
                    score : Real
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
                        "context Family inv AggregateChecks: self.children->collect(c | c.age)->sum() >= 0 and self.children->select(c | c.age >= 18)->collect(c | c.score)->max() >= 0.0")))).oclFile());

        OclSemanticBinder binder = new OclSemanticBinder(new OclMetamodelIndex(model));
        OclSemanticBinder.BoundContextInvariant bound = binder.bindContext(firstContext(ast));
        OclIr.InvariantQuery optimized = new OclIrOptimizer().optimizeInvariant(new OclIrBuilder().buildInvariant(bound));
        OclCypherPlan.InvariantPlan plan = new OclCypherPlanner().planInvariant(optimized);

        assertTrue(plan.predicate() instanceof OclCypherPlan.BinaryPlan);
        OclCypherPlan.BinaryPlan predicate = (OclCypherPlan.BinaryPlan) plan.predicate();
        assertTrue(predicate.left() instanceof OclCypherPlan.BinaryPlan);
        assertTrue(predicate.right() instanceof OclCypherPlan.BinaryPlan);

        OclCypherPlan.BinaryPlan leftComparison = (OclCypherPlan.BinaryPlan) predicate.left();
        OclCypherPlan.BinaryPlan rightComparison = (OclCypherPlan.BinaryPlan) predicate.right();
        assertTrue(leftComparison.left() instanceof OclCypherPlan.NavigationAggregationPlan);
        assertTrue(rightComparison.left() instanceof OclCypherPlan.NavigationAggregationPlan);

        OclCypherPlan.NavigationAggregationPlan sumPlan =
                (OclCypherPlan.NavigationAggregationPlan) leftComparison.left();
        OclCypherPlan.NavigationAggregationPlan maxPlan =
                (OclCypherPlan.NavigationAggregationPlan) rightComparison.left();

        assertEquals("sum", sumPlan.operationName());
        assertEquals("max", maxPlan.operationName());
        assertEquals("c", sumPlan.match().targetAlias());
        assertEquals("c", maxPlan.match().targetAlias());
        assertEquals(OclCypherPlan.PredicateMode.NONE, sumPlan.match().predicateMode());
        assertEquals(OclCypherPlan.PredicateMode.NORMAL, maxPlan.match().predicateMode());
    }

    @Test
    void plansNavigationIsUniqueIntoDedicatedUniquenessPlan() {
        String spec = """
                model Demo
                class Family
                end
                class Person
                attributes
                    age : Integer
                    name : String
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
                        "context Family inv UniqueAdultNames: self.children->select(c | c.age >= 18)->isUnique(c | c.name)")))).oclFile());

        OclSemanticBinder binder = new OclSemanticBinder(new OclMetamodelIndex(model));
        OclSemanticBinder.BoundContextInvariant bound = binder.bindContext(firstContext(ast));
        OclIr.InvariantQuery optimized = new OclIrOptimizer().optimizeInvariant(new OclIrBuilder().buildInvariant(bound));
        OclCypherPlan.InvariantPlan plan = new OclCypherPlanner().planInvariant(optimized);

        assertTrue(plan.predicate() instanceof OclCypherPlan.NavigationUniquenessPlan);
        OclCypherPlan.NavigationUniquenessPlan uniquenessPlan =
                (OclCypherPlan.NavigationUniquenessPlan) plan.predicate();
        assertEquals("c", uniquenessPlan.match().targetAlias());
        assertEquals(OclCypherPlan.PredicateMode.NORMAL, uniquenessPlan.match().predicateMode());
        assertTrue(uniquenessPlan.match().predicate() instanceof OclCypherPlan.BinaryPlan);
    }

    private ASTContext firstContext(ASTNode ast) {
        assertTrue(ast instanceof ASTFile);
        ASTFile file = (ASTFile) ast;
        assertEquals(1, file.invariants().size());
        return file.invariants().get(0);
    }
}
