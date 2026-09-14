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
import org.uet.dse.neo4jtgg.ocl.OclTypeBinding;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OclIrOptimizerTest {
    private static final org.uet.dse.neo4jtgg.ocl.OclTypeBinding INTEGER =
            org.uet.dse.neo4jtgg.ocl.OclTypeBinding.scalar("Integer");
    private static final org.uet.dse.neo4jtgg.ocl.OclTypeBinding REAL =
            org.uet.dse.neo4jtgg.ocl.OclTypeBinding.scalar("Real");
    private static final org.uet.dse.neo4jtgg.ocl.OclTypeBinding BOOLEAN =
            org.uet.dse.neo4jtgg.ocl.OclTypeBinding.scalar("Boolean");

    @Test
    void foldsPureInt64WithoutPassingThroughDouble() {
        OclIr.Expression difference = new OclIr.Binary(
                "-", new OclIr.Literal(9_007_199_254_740_993L, INTEGER),
                new OclIr.Literal(9_007_199_254_740_992L, INTEGER), INTEGER);
        OclIr.Expression equality = new OclIr.Binary(
                "=", new OclIr.Literal(9_007_199_254_740_993L, INTEGER),
                new OclIr.Literal(9_007_199_254_740_992L, INTEGER), BOOLEAN);

        OclIr.Expression optimizedDifference = new OclIrOptimizer().optimizeExpression(difference);
        OclIr.Expression optimizedEquality = new OclIrOptimizer().optimizeExpression(equality);

        assertTrue(optimizedDifference instanceof OclIr.Literal);
        assertEquals(1L, ((OclIr.Literal) optimizedDifference).value());
        assertTrue(optimizedEquality instanceof OclIr.Literal);
        assertEquals(Boolean.FALSE, ((OclIr.Literal) optimizedEquality).value());
    }

    @Test
    void leavesOverflowAndInexactMixedNumericConstantsForScalarClosed() {
        OclIr.Expression overflow = new OclIr.Binary(
                "+", new OclIr.Literal(Long.MAX_VALUE, INTEGER),
                new OclIr.Literal(1L, INTEGER), INTEGER);
        OclIr.Expression inexactMixedEquality = new OclIr.Binary(
                "=", new OclIr.Literal(9_007_199_254_740_993L, INTEGER),
                new OclIr.Literal(9_007_199_254_740_992.0d, REAL), BOOLEAN);

        assertTrue(new OclIrOptimizer().optimizeExpression(overflow) instanceof OclIr.Binary);
        assertTrue(new OclIrOptimizer().optimizeExpression(inexactMixedEquality) instanceof OclIr.Binary);
    }

    @Test
    void identifiesAndCanonicalizesSignedZeroDuringNestedRealFolding() {
        OclIr.Expression negativeOne = new OclIr.Binary(
                "-", new OclIr.Literal(0L, INTEGER),
                new OclIr.Literal(1L, INTEGER), INTEGER);
        OclIr.Expression negativeZero = new OclIr.Binary(
                "/", new OclIr.Literal(0.0d, REAL), negativeOne, REAL);
        OclIr.Expression equality = new OclIr.Binary(
                "=", negativeZero, new OclIr.Literal(0.0d, REAL), BOOLEAN);

        OclIr.Expression optimizedZero = new OclIrOptimizer().optimizeExpression(negativeZero);
        OclIr.Expression optimizedEquality = new OclIrOptimizer().optimizeExpression(equality);

        assertTrue(optimizedZero instanceof OclIr.Literal);
        assertEquals(Double.doubleToRawLongBits(0.0d),
                Double.doubleToRawLongBits((Double) ((OclIr.Literal) optimizedZero).value()));
        assertTrue(optimizedEquality instanceof OclIr.Literal);
        assertEquals(Boolean.TRUE, ((OclIr.Literal) optimizedEquality).value());
    }

    @Test
    void retainedLetRemovesShadowedOuterInliningBinding() {
        OclIr.Expression innerBody = new OclIr.Binary("and",
                new OclIr.Binary(">", new OclIr.Variable("x", INTEGER),
                        new OclIr.Literal(0L, INTEGER), BOOLEAN),
                new OclIr.Binary("<", new OclIr.Variable("x", INTEGER),
                        new OclIr.Literal(10L, INTEGER), BOOLEAN), BOOLEAN);
        OclIr.Expression inner = new OclIr.Let("x",
                new OclIr.Binary("+", new OclIr.Variable("n", INTEGER),
                        new OclIr.Literal(1L, INTEGER), INTEGER), INTEGER, innerBody, BOOLEAN);
        OclIr.Expression expression = new OclIr.Let("x", new OclIr.Literal(1L, INTEGER),
                INTEGER,
                new OclIr.Binary("and",
                        new OclIr.Binary("=", new OclIr.Variable("x", INTEGER),
                                new OclIr.Literal(1L, INTEGER), BOOLEAN),
                        inner, BOOLEAN), BOOLEAN);

        OclIr.Expression optimized = new OclIrOptimizer().optimizeExpression(expression);

        assertTrue(optimized instanceof OclIr.Binary);
        OclIr.Expression right = ((OclIr.Binary) optimized).right();
        assertTrue(right instanceof OclIr.Let);
        assertTrue(((OclIr.Let) right).body() instanceof OclIr.Binary);
        assertTrue(((OclIr.Binary) ((OclIr.Let) right).body()).left() instanceof OclIr.Binary);
    }

    @Test
    void doesNotInlineVariableAcrossSameNamedIteratorBinder() {
        OclIr.Expression iterator = new OclIr.IteratorOperation(
                new OclIr.SetLiteral(java.util.List.of(new OclIr.Literal(1L, INTEGER)),
                        org.uet.dse.neo4jtgg.ocl.OclTypeBinding.scalarCollection(
                                "Integer", org.uet.dse.neo4jtgg.ocl.OclTypeBinding.CollectionKind.SET)),
                org.uet.dse.neo4jtgg.ocl.OclTypeBinding.scalarCollection(
                        "Integer", org.uet.dse.neo4jtgg.ocl.OclTypeBinding.CollectionKind.SET),
                "exists", "y",
                INTEGER,
                new OclIr.Binary("=", new OclIr.Variable("x", INTEGER),
                        new OclIr.Variable("y", INTEGER), BOOLEAN), BOOLEAN);
        OclIr.Expression expression = new OclIr.Let("x", new OclIr.Variable("y", INTEGER),
                INTEGER, iterator, BOOLEAN);

        assertTrue(new OclIrOptimizer().optimizeExpression(expression) instanceof OclIr.Let);
    }

    @Test
    void foldsLiteralIfConditionToChosenBranch() {
        OclIrOptimizer optimizer = new OclIrOptimizer();
        OclIr.Expression expression = new OclIr.If(
                new OclIr.Literal(Boolean.TRUE, org.uet.dse.neo4jtgg.ocl.OclTypeBinding.scalar("Boolean")),
                new OclIr.Literal(1L, org.uet.dse.neo4jtgg.ocl.OclTypeBinding.scalar("Integer")),
                new OclIr.Literal(2L, org.uet.dse.neo4jtgg.ocl.OclTypeBinding.scalar("Integer")),
                org.uet.dse.neo4jtgg.ocl.OclTypeBinding.scalar("Integer"));

        OclIr.Expression optimized = optimizer.optimizeExpression(expression);

        assertTrue(optimized instanceof OclIr.Literal);
        assertEquals(1L, ((OclIr.Literal) optimized).value());
    }

    @Test
    void preservesContextualTypeWhenFoldingNullIfBranch() {
        OclIrOptimizer optimizer = new OclIrOptimizer();
        OclIr.Expression expression = new OclIr.If(
                new OclIr.Literal(Boolean.TRUE, BOOLEAN),
                new OclIr.Literal(null, OclTypeBinding.scalar("Void")),
                new OclIr.Literal(1L, INTEGER),
                INTEGER);

        OclIr.Expression optimized = optimizer.optimizeExpression(expression);

        assertTrue(optimized instanceof OclIr.Literal);
        assertNull(((OclIr.Literal) optimized).value());
        assertEquals(INTEGER, optimized.type());
    }

    @Test
    void doesNotFoldEqualBranchesWhenConditionMayBeBottom() {
        OclIrOptimizer optimizer = new OclIrOptimizer();
        OclTypeBinding voidType = OclTypeBinding.scalar("Void");
        OclIr.Expression expression = new OclIr.If(
                new OclIr.Literal(null, voidType),
                new OclIr.Literal(1L, INTEGER),
                new OclIr.Literal(1L, INTEGER),
                INTEGER);

        OclIr.Expression optimized = optimizer.optimizeExpression(expression);

        assertTrue(optimized instanceof OclIr.If);
    }

    @Test
    void foldsLiteralBooleanAndArithmeticExpressions() {
        OclIrOptimizer optimizer = new OclIrOptimizer();
        OclIr.Expression expression = new OclIr.Binary(
                "and",
                new OclIr.Not(
                        new OclIr.Literal(Boolean.FALSE, org.uet.dse.neo4jtgg.ocl.OclTypeBinding.scalar("Boolean")),
                        org.uet.dse.neo4jtgg.ocl.OclTypeBinding.scalar("Boolean")),
                new OclIr.Binary(
                        ">=",
                        new OclIr.Binary(
                                "+",
                                new OclIr.Literal(18L, org.uet.dse.neo4jtgg.ocl.OclTypeBinding.scalar("Integer")),
                                new OclIr.Literal(1L, org.uet.dse.neo4jtgg.ocl.OclTypeBinding.scalar("Integer")),
                                org.uet.dse.neo4jtgg.ocl.OclTypeBinding.scalar("Integer")),
                        new OclIr.Literal(19L, org.uet.dse.neo4jtgg.ocl.OclTypeBinding.scalar("Integer")),
                        org.uet.dse.neo4jtgg.ocl.OclTypeBinding.scalar("Boolean")),
                org.uet.dse.neo4jtgg.ocl.OclTypeBinding.scalar("Boolean"));

        OclIr.Expression optimized = optimizer.optimizeExpression(expression);

        assertTrue(optimized instanceof OclIr.Literal);
        assertEquals(Boolean.TRUE, ((OclIr.Literal) optimized).value());
    }

    @Test
    void inlinesLetBindingsIntoBody() {
        String spec = """
                model Demo
                class Person
                attributes
                    age : Integer
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        ASTNode ast = new ASTVisitor().visit(new OCLParser(new org.antlr.v4.runtime.CommonTokenStream(
                new OCLLexer(org.antlr.v4.runtime.CharStreams.fromString(
                        "context Person inv AdultByLet: let threshold = 18 in self.age >= threshold")))).oclFile());

        OclSemanticBinder binder = new OclSemanticBinder(new OclMetamodelIndex(model));
        OclSemanticBinder.BoundContextInvariant bound = binder.bindContext(firstContext(ast));
        OclIr.InvariantQuery invariantQuery = new OclIrBuilder().buildInvariant(bound);

        OclIr.Expression optimized = new OclIrOptimizer().optimizeExpression(invariantQuery.predicate());

        assertTrue(optimized instanceof OclIr.Binary);
        OclIr.Binary comparison = (OclIr.Binary) optimized;
        assertFalse(comparison.right() instanceof OclIr.Variable);
        assertTrue(comparison.right() instanceof OclIr.Literal);
        assertEquals(18L, ((OclIr.Literal) comparison.right()).value());
    }

    @Test
    void inlinesNestedLetsUsingOuterBindingInInnerValue() {
        String spec = """
                model Demo
                class Person
                attributes
                    age : Integer
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        ASTNode ast = new ASTVisitor().visit(new OCLParser(new org.antlr.v4.runtime.CommonTokenStream(
                new OCLLexer(org.antlr.v4.runtime.CharStreams.fromString(
                        "context Person inv NestedLet: let threshold = 18 in let bonus = threshold + 1 in self.age >= bonus")))).oclFile());

        OclSemanticBinder binder = new OclSemanticBinder(new OclMetamodelIndex(model));
        OclSemanticBinder.BoundContextInvariant bound = binder.bindContext(firstContext(ast));
        OclIr.InvariantQuery invariantQuery = new OclIrBuilder().buildInvariant(bound);

        OclIr.Expression optimized = new OclIrOptimizer().optimizeExpression(invariantQuery.predicate());

        assertTrue(optimized instanceof OclIr.Binary);
        OclIr.Binary comparison = (OclIr.Binary) optimized;
        assertTrue(comparison.right() instanceof OclIr.Literal);
        assertEquals(19L, ((OclIr.Literal) comparison.right()).value());
    }

    @Test
    void preservesLetWhenNonCheapValueIsReusedMultipleTimes() {
        String spec = """
                model Demo
                class Person
                attributes
                    age : Integer
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        ASTNode ast = new ASTVisitor().visit(new OCLParser(new org.antlr.v4.runtime.CommonTokenStream(
                new OCLLexer(org.antlr.v4.runtime.CharStreams.fromString(
                        "context Person inv ReusedLet: let threshold = self.age + 1 in threshold >= 18 and threshold <= 65")))).oclFile());

        OclSemanticBinder binder = new OclSemanticBinder(new OclMetamodelIndex(model));
        OclSemanticBinder.BoundContextInvariant bound = binder.bindContext(firstContext(ast));
        OclIr.InvariantQuery invariantQuery = new OclIrBuilder().buildInvariant(bound);

        OclIr.Expression optimized = new OclIrOptimizer().optimizeExpression(invariantQuery.predicate());

        assertTrue(optimized instanceof OclIr.Let);
        OclIr.Let letExpression = (OclIr.Let) optimized;
        assertEquals("threshold", letExpression.variableName());
    }

    @Test
    void rewritesNavigationNotEmptyIntoExistsPredicateCheck() {
        OclIr.InvariantQuery invariantQuery = buildFamilyInvariant(
                "context Family inv HasChildren: self.children->notEmpty()");
        assertTrue(OclSemanticIr.isSemantic(invariantQuery.predicate()));

        OclIr.Expression optimized = new OclIrOptimizer().optimizeExpression(invariantQuery.predicate());

        assertTrue(optimized instanceof OclIr.NavigationPredicateCheck);
        assertTrue(OclOptimizedIr.isOptimized(optimized));
        assertFalse(OclSemanticIr.isSemantic(optimized));
        OclIr.NavigationPredicateCheck predicateCheck = (OclIr.NavigationPredicateCheck) optimized;
        assertEquals(OclIr.NavigationPredicateKind.EXISTS, predicateCheck.kind());
        assertEquals("nav", predicateCheck.iteratorName());
        assertNull(predicateCheck.predicate());
    }

    @Test
    void rewritesNavigationIsEmptyIntoNotExistsPredicateCheck() {
        OclIr.Expression optimized = optimizeFamilyChildrenPredicate(
                "context Family inv NoChildren: self.children->isEmpty()");

        assertTrue(optimized instanceof OclIr.NavigationPredicateCheck);
        OclIr.NavigationPredicateCheck predicateCheck = (OclIr.NavigationPredicateCheck) optimized;
        assertEquals(OclIr.NavigationPredicateKind.NOT_EXISTS, predicateCheck.kind());
        assertEquals("nav", predicateCheck.iteratorName());
        assertNull(predicateCheck.predicate());
    }

    @Test
    void rewritesNavigationSizeComparisonsIntoCountComparisonIr() {
        OclIr.Expression greaterThanZero = optimizeFamilyChildrenPredicate(
                "context Family inv SomeChildren: self.children->size() > 0");
        OclIr.Expression equalsZero = optimizeFamilyChildrenPredicate(
                "context Family inv NoChildren: self.children->size() = 0");

        assertTrue(greaterThanZero instanceof OclIr.NavigationCountComparison);
        OclIr.NavigationCountComparison gt = (OclIr.NavigationCountComparison) greaterThanZero;
        assertEquals(">", gt.operator());
        assertEquals(0L, gt.literal());
        assertEquals("nav", gt.iteratorName());

        assertTrue(equalsZero instanceof OclIr.NavigationCountComparison);
        OclIr.NavigationCountComparison eq = (OclIr.NavigationCountComparison) equalsZero;
        assertEquals("=", eq.operator());
        assertEquals(0L, eq.literal());
        assertEquals("nav", eq.iteratorName());
    }

    @Test
    void rewritesImpliesIntoOrNotForm() {
        String spec = """
                model Demo
                class Person
                attributes
                    age : Integer
                end
                """;

        OclIr.Expression optimized = optimizePredicate(spec,
                "context Person inv AdultImpliesSenior: self.age >= 18 implies self.age >= 65");

        assertTrue(optimized instanceof OclIr.Binary);
        OclIr.Binary orExpression = (OclIr.Binary) optimized;
        assertEquals("or", orExpression.operator());
        assertTrue(orExpression.left() instanceof OclIr.Not);
        assertTrue(orExpression.right() instanceof OclIr.Binary);
    }

    @Test
    void foldsNestedLetArithmeticToSingleLiteralThreshold() {
        String spec = """
                model Demo
                class Person
                attributes
                    age : Integer
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        ASTNode ast = new ASTVisitor().visit(new OCLParser(new org.antlr.v4.runtime.CommonTokenStream(
                new OCLLexer(org.antlr.v4.runtime.CharStreams.fromString(
                        "context Person inv NestedLet: let threshold = 18 in let bonus = threshold + 1 in self.age >= bonus")))).oclFile());

        OclSemanticBinder binder = new OclSemanticBinder(new OclMetamodelIndex(model));
        OclSemanticBinder.BoundContextInvariant bound = binder.bindContext(firstContext(ast));
        OclIr.InvariantQuery invariantQuery = new OclIrBuilder().buildInvariant(bound);

        OclIr.Expression optimized = new OclIrOptimizer().optimizeExpression(invariantQuery.predicate());

        assertTrue(optimized instanceof OclIr.Binary);
        OclIr.Binary comparison = (OclIr.Binary) optimized;
        assertTrue(comparison.right() instanceof OclIr.Literal);
        assertEquals(19L, ((OclIr.Literal) comparison.right()).value());
    }

    @Test
    void preservesIteratorShadowingWhenInliningOuterLet() {
        String spec = """
                model Demo
                class Family
                attributes
                    name : String
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
                        "context Family inv Shadowed: let c = self in self.children->exists(c | c.isDefined())")))).oclFile());

        OclSemanticBinder binder = new OclSemanticBinder(new OclMetamodelIndex(model));
        OclSemanticBinder.BoundContextInvariant bound = binder.bindContext(firstContext(ast));
        OclIr.InvariantQuery invariantQuery = new OclIrBuilder().buildInvariant(bound);

        OclIr.Expression optimized = new OclIrOptimizer().optimizeExpression(invariantQuery.predicate());

        assertTrue(optimized instanceof OclIr.NavigationPredicateCheck);
        OclIr.NavigationPredicateCheck iterator = (OclIr.NavigationPredicateCheck) optimized;
        assertEquals("c", iterator.iteratorName());
        assertTrue(iterator.predicate() instanceof OclIr.MethodCall);
        OclIr.MethodCall body = (OclIr.MethodCall) iterator.predicate();
        assertTrue(body.source() instanceof OclIr.Variable);
        assertEquals("c", ((OclIr.Variable) body.source()).name());
    }

    @Test
    void rewritesNavigationOneIntoCountComparison() {
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
                        "context Family inv ExactlyOneAdult: self.children->one(c | c.age >= 18)")))).oclFile());

        OclSemanticBinder binder = new OclSemanticBinder(new OclMetamodelIndex(model));
        OclSemanticBinder.BoundContextInvariant bound = binder.bindContext(firstContext(ast));
        OclIr.InvariantQuery invariantQuery = new OclIrBuilder().buildInvariant(bound);

        OclIr.Expression optimized = new OclIrOptimizer().optimizeExpression(invariantQuery.predicate());

        assertTrue(optimized instanceof OclIr.NavigationCountComparison);
        OclIr.NavigationCountComparison count = (OclIr.NavigationCountComparison) optimized;
        assertEquals("=", count.operator());
        assertEquals(1L, count.literal());
        assertEquals("c", count.iteratorName());
    }

    @Test
    void rewritesSelectedNavigationExistsIntoCombinedPredicateCheck() {
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
                        "context Family inv AdultBart: self.children->select(c | c.age >= 18)->exists(c | c.name = 'Bart')")))).oclFile());

        OclSemanticBinder binder = new OclSemanticBinder(new OclMetamodelIndex(model));
        OclSemanticBinder.BoundContextInvariant bound = binder.bindContext(firstContext(ast));
        OclIr.InvariantQuery invariantQuery = new OclIrBuilder().buildInvariant(bound);

        OclIr.Expression optimized = new OclIrOptimizer().optimizeExpression(invariantQuery.predicate());

        assertTrue(optimized instanceof OclIr.NavigationPredicateCheck);
        OclIr.NavigationPredicateCheck predicateCheck = (OclIr.NavigationPredicateCheck) optimized;
        assertEquals(OclIr.NavigationPredicateKind.EXISTS, predicateCheck.kind());
        assertEquals("c", predicateCheck.iteratorName());
        assertTrue(predicateCheck.predicate() instanceof OclIr.Binary);
        assertEquals("and", ((OclIr.Binary) predicateCheck.predicate()).operator());
    }

    @Test
    void rewritesRejectedNavigationOperationsIntoNavigationPredicates() {
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
                        "context Family inv RejectChecks: self.children->reject(c | c.age >= 18)->isEmpty() and self.children->reject(c | c.age >= 18)->exists(c | c.name = 'Lisa')")))).oclFile());

        OclSemanticBinder binder = new OclSemanticBinder(new OclMetamodelIndex(model));
        OclSemanticBinder.BoundContextInvariant bound = binder.bindContext(firstContext(ast));
        OclIr.InvariantQuery invariantQuery = new OclIrBuilder().buildInvariant(bound);

        OclIr.Expression optimized = new OclIrOptimizer().optimizeExpression(invariantQuery.predicate());

        assertTrue(optimized instanceof OclIr.Binary);
        OclIr.Binary binary = (OclIr.Binary) optimized;
        assertTrue(binary.left() instanceof OclIr.NavigationPredicateCheck);
        assertTrue(binary.right() instanceof OclIr.NavigationPredicateCheck);
        OclIr.NavigationPredicateCheck emptyCheck = (OclIr.NavigationPredicateCheck) binary.left();
        OclIr.NavigationPredicateCheck existsCheck = (OclIr.NavigationPredicateCheck) binary.right();
        assertEquals(OclIr.NavigationPredicateKind.NOT_EXISTS, emptyCheck.kind());
        assertEquals(OclIr.NavigationPredicateKind.EXISTS, existsCheck.kind());
        assertTrue(emptyCheck.predicate() instanceof OclIr.Not);
        assertTrue(existsCheck.predicate() instanceof OclIr.Binary);
    }

    @Test
    void rewritesMultiLevelSelectRejectChainsIntoSingleNavigationPredicate() {
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
        OclIr.InvariantQuery invariantQuery = new OclIrBuilder().buildInvariant(bound);

        OclIr.Expression optimized = new OclIrOptimizer().optimizeExpression(invariantQuery.predicate());

        assertTrue(optimized instanceof OclIr.NavigationPredicateCheck);
        OclIr.NavigationPredicateCheck predicateCheck = (OclIr.NavigationPredicateCheck) optimized;
        assertEquals(OclIr.NavigationPredicateKind.EXISTS, predicateCheck.kind());
        assertTrue(predicateCheck.predicate() instanceof OclIr.Binary);
        OclIr.Binary outer = (OclIr.Binary) predicateCheck.predicate();
        assertEquals("and", outer.operator());
    }

    @Test
    void rewritesMultiLevelSelectRejectChainsWithDifferentIteratorNames() {
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
        OclIr.InvariantQuery invariantQuery = new OclIrBuilder().buildInvariant(bound);

        OclIr.Expression optimized = new OclIrOptimizer().optimizeExpression(invariantQuery.predicate());

        assertTrue(optimized instanceof OclIr.NavigationPredicateCheck);
        OclIr.NavigationPredicateCheck predicateCheck = (OclIr.NavigationPredicateCheck) optimized;
        assertEquals("c", predicateCheck.iteratorName());
        assertTrue(predicateCheck.predicate() instanceof OclIr.Binary);
    }

    @Test
    void rewritesFlattenNotEmptyIntoNestedNavigationExistenceCheck() {
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
        OclIr.InvariantQuery invariantQuery = new OclIrBuilder().buildInvariant(bound);

        OclIr.Expression optimized = new OclIrOptimizer().optimizeExpression(invariantQuery.predicate());

        assertTrue(optimized instanceof OclIr.NavigationPredicateCheck);
        OclIr.NavigationPredicateCheck predicateCheck = (OclIr.NavigationPredicateCheck) optimized;
        assertEquals(OclIr.NavigationPredicateKind.EXISTS, predicateCheck.kind());
        assertTrue(predicateCheck.predicate() instanceof OclIr.CollectionOperation);
        OclIr.CollectionOperation nested = (OclIr.CollectionOperation) predicateCheck.predicate();
        assertEquals("notEmpty", nested.operationName());
    }

    @Test
    void rewritesFlattenIsEmptyIntoNestedNavigationNonExistenceCheck() {
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
        OclIr.InvariantQuery invariantQuery = new OclIrBuilder().buildInvariant(bound);

        OclIr.Expression optimized = new OclIrOptimizer().optimizeExpression(invariantQuery.predicate());

        assertTrue(optimized instanceof OclIr.NavigationPredicateCheck);
        OclIr.NavigationPredicateCheck predicateCheck = (OclIr.NavigationPredicateCheck) optimized;
        assertEquals(OclIr.NavigationPredicateKind.NOT_EXISTS, predicateCheck.kind());
        assertTrue(predicateCheck.predicate() instanceof OclIr.CollectionOperation);
        OclIr.CollectionOperation nested = (OclIr.CollectionOperation) predicateCheck.predicate();
        assertEquals("notEmpty", nested.operationName());
    }

    @Test
    void rewritesNavigationCollectSumIntoNavigationAggregation() {
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
                        "context Family inv TotalAgePositive: self.children->collect(c | c.age)->sum() >= 0")))).oclFile());

        OclSemanticBinder binder = new OclSemanticBinder(new OclMetamodelIndex(model));
        OclSemanticBinder.BoundContextInvariant bound = binder.bindContext(firstContext(ast));
        OclIr.InvariantQuery invariantQuery = new OclIrBuilder().buildInvariant(bound);

        OclIr.Expression optimized = new OclIrOptimizer().optimizeExpression(invariantQuery.predicate());

        assertTrue(optimized instanceof OclIr.Binary);
        OclIr.Binary comparison = (OclIr.Binary) optimized;
        assertTrue(comparison.left() instanceof OclIr.NavigationAggregation);
        OclIr.NavigationAggregation aggregation = (OclIr.NavigationAggregation) comparison.left();
        assertEquals("sum", aggregation.operationName());
        assertEquals("c", aggregation.iteratorName());
        assertTrue(aggregation.projection() instanceof OclIr.AttributeAccess);
    }

    @Test
    void rewritesSelectedNavigationCollectMaxIntoNavigationAggregation() {
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
                        "context Family inv AdultScoreMax: self.children->select(c | c.age >= 18)->collect(c | c.score)->max() >= 0.0")))).oclFile());

        OclSemanticBinder binder = new OclSemanticBinder(new OclMetamodelIndex(model));
        OclSemanticBinder.BoundContextInvariant bound = binder.bindContext(firstContext(ast));
        OclIr.InvariantQuery invariantQuery = new OclIrBuilder().buildInvariant(bound);

        OclIr.Expression optimized = new OclIrOptimizer().optimizeExpression(invariantQuery.predicate());

        assertTrue(optimized instanceof OclIr.Binary);
        OclIr.Binary comparison = (OclIr.Binary) optimized;
        assertTrue(comparison.left() instanceof OclIr.NavigationAggregation);
        OclIr.NavigationAggregation aggregation = (OclIr.NavigationAggregation) comparison.left();
        assertEquals("max", aggregation.operationName());
        assertEquals("c", aggregation.iteratorName());
        assertTrue(aggregation.predicate() instanceof OclIr.Binary);
        assertTrue(aggregation.projection() instanceof OclIr.AttributeAccess);
    }

    @Test
    void rewritesSelectedNavigationCollectWithDifferentIteratorNamesIntoNavigationAggregation() {
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
                        "context Family inv AdultScoreSum: self.children->select(a | a.age >= 18)->collect(b | b.score)->sum() >= 0.0")))).oclFile());

        OclSemanticBinder binder = new OclSemanticBinder(new OclMetamodelIndex(model));
        OclSemanticBinder.BoundContextInvariant bound = binder.bindContext(firstContext(ast));
        OclIr.InvariantQuery invariantQuery = new OclIrBuilder().buildInvariant(bound);

        OclIr.Expression optimized = new OclIrOptimizer().optimizeExpression(invariantQuery.predicate());

        assertTrue(optimized instanceof OclIr.Binary);
        OclIr.Binary comparison = (OclIr.Binary) optimized;
        assertTrue(comparison.left() instanceof OclIr.NavigationAggregation);
        OclIr.NavigationAggregation aggregation = (OclIr.NavigationAggregation) comparison.left();
        assertEquals("b", aggregation.iteratorName());
        assertTrue(aggregation.predicate() instanceof OclIr.Binary);
        assertTrue(aggregation.projection() instanceof OclIr.AttributeAccess);
    }

    @Test
    void rewritesNavigationIsUniqueIntoNavigationUniquenessCheck() {
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
        OclIr.InvariantQuery invariantQuery = new OclIrBuilder().buildInvariant(bound);

        OclIr.Expression optimized = new OclIrOptimizer().optimizeExpression(invariantQuery.predicate());

        assertTrue(optimized instanceof OclIr.NavigationUniquenessCheck);
        OclIr.NavigationUniquenessCheck uniquenessCheck = (OclIr.NavigationUniquenessCheck) optimized;
        assertEquals("c", uniquenessCheck.iteratorName());
        assertTrue(uniquenessCheck.predicate() instanceof OclIr.Binary);
        assertTrue(uniquenessCheck.projection() instanceof OclIr.AttributeAccess);
    }

    private ASTContext firstContext(ASTNode ast) {
        assertTrue(ast instanceof ASTFile);
        ASTFile file = (ASTFile) ast;
        assertEquals(1, file.invariants().size());
        return file.invariants().get(0);
    }

    private OclIr.Expression optimizeFamilyChildrenPredicate(String ocl) {
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
        return optimizePredicate(spec, ocl);
    }

    private OclIr.InvariantQuery buildFamilyInvariant(String ocl) {
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
        return buildInvariant(spec, ocl);
    }

    private OclIr.Expression optimizePredicate(String spec, String ocl) {
        OclIr.InvariantQuery invariantQuery = buildInvariant(spec, ocl);
        return new OclIrOptimizer().optimizeExpression(invariantQuery.predicate());
    }

    private OclIr.InvariantQuery buildInvariant(String spec, String ocl) {
        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        ASTNode ast = new ASTVisitor().visit(new OCLParser(new org.antlr.v4.runtime.CommonTokenStream(
                new OCLLexer(org.antlr.v4.runtime.CharStreams.fromString(ocl)))).oclFile());

        OclSemanticBinder binder = new OclSemanticBinder(new OclMetamodelIndex(model));
        OclSemanticBinder.BoundContextInvariant bound = binder.bindContext(firstContext(ast));
        return new OclIrBuilder().buildInvariant(bound);
    }
}
