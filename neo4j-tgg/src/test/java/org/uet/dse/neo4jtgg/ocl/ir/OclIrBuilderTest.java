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

class OclIrBuilderTest {
    @Test
    void buildsInvariantIrFromBoundContext() {
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
                        "context Family inv AdultChildren: self.children->forall(c | c.age >= 18)")))).oclFile());
        assertTrue(ast instanceof ASTContext);
        ASTContext context = (ASTContext) ast;

        OclSemanticBinder binder = new OclSemanticBinder(new OclMetamodelIndex(model));
        OclSemanticBinder.BoundContextInvariant bound = binder.bindContext(context);
        OclIr.InvariantQuery invariantQuery = new OclIrBuilder().buildInvariant(bound);

        assertEquals("Family", invariantQuery.contextClassName());
        assertEquals("AdultChildren", invariantQuery.invariantName());
        assertTrue(invariantQuery.predicate() instanceof OclIr.IteratorOperation);
        OclIr.IteratorOperation iterator = (OclIr.IteratorOperation) invariantQuery.predicate();
        assertEquals("forall", iterator.operationName());
        assertEquals("c", iterator.iteratorName());
        assertTrue(iterator.source() instanceof OclIr.NavigationAccess);
        OclIr.NavigationAccess navigation = (OclIr.NavigationAccess) iterator.source();
        assertEquals("FamilyChildren", navigation.navigation().associationName());
        assertTrue(iterator.body() instanceof OclIr.Binary);
        OclIr.Binary body = (OclIr.Binary) iterator.body();
        assertEquals(">=", body.operator());
        assertTrue(body.right() instanceof OclIr.Literal);
    }

    @Test
    void buildsIfExpressionIntoIr() {
        String spec = """
                model Demo
                class Person
                attributes
                    age : Integer
                    name : String
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        ASTNode ast = new ASTVisitor().visit(new OCLParser(new org.antlr.v4.runtime.CommonTokenStream(
                new OCLLexer(org.antlr.v4.runtime.CharStreams.fromString(
                        "context Person inv AdultNamed: if self.age >= 18 then self.name else 'minor' endif <> ''")))).oclFile());
        assertTrue(ast instanceof ASTContext);

        OclSemanticBinder binder = new OclSemanticBinder(new OclMetamodelIndex(model));
        OclSemanticBinder.BoundContextInvariant bound = binder.bindContext((ASTContext) ast);
        OclIr.InvariantQuery invariantQuery = new OclIrBuilder().buildInvariant(bound);

        assertTrue(invariantQuery.predicate() instanceof OclIr.Binary);
        OclIr.Binary comparison = (OclIr.Binary) invariantQuery.predicate();
        assertEquals("<>", comparison.operator());
        assertTrue(comparison.left() instanceof OclIr.If);
    }

    @Test
    void buildsLetExpressionIntoIr() {
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

        assertTrue(invariantQuery.predicate() instanceof OclIr.Let);
        OclIr.Let letExpression = (OclIr.Let) invariantQuery.predicate();
        assertEquals("threshold", letExpression.variableName());
        assertTrue(letExpression.body() instanceof OclIr.Binary);
    }
}
