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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OclIrOptimizerTest {
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
        assertTrue(ast instanceof ASTContext);

        OclSemanticBinder binder = new OclSemanticBinder(new OclMetamodelIndex(model));
        OclSemanticBinder.BoundContextInvariant bound = binder.bindContext((ASTContext) ast);
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
        assertTrue(ast instanceof ASTContext);

        OclSemanticBinder binder = new OclSemanticBinder(new OclMetamodelIndex(model));
        OclSemanticBinder.BoundContextInvariant bound = binder.bindContext((ASTContext) ast);
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
        assertTrue(ast instanceof ASTContext);

        OclSemanticBinder binder = new OclSemanticBinder(new OclMetamodelIndex(model));
        OclSemanticBinder.BoundContextInvariant bound = binder.bindContext((ASTContext) ast);
        OclIr.InvariantQuery invariantQuery = new OclIrBuilder().buildInvariant(bound);

        OclIr.Expression optimized = new OclIrOptimizer().optimizeExpression(invariantQuery.predicate());

        assertTrue(optimized instanceof OclIr.Let);
        OclIr.Let letExpression = (OclIr.Let) optimized;
        assertEquals("threshold", letExpression.variableName());
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
        assertTrue(ast instanceof ASTContext);

        OclSemanticBinder binder = new OclSemanticBinder(new OclMetamodelIndex(model));
        OclSemanticBinder.BoundContextInvariant bound = binder.bindContext((ASTContext) ast);
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
        assertTrue(ast instanceof ASTContext);

        OclSemanticBinder binder = new OclSemanticBinder(new OclMetamodelIndex(model));
        OclSemanticBinder.BoundContextInvariant bound = binder.bindContext((ASTContext) ast);
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
}
