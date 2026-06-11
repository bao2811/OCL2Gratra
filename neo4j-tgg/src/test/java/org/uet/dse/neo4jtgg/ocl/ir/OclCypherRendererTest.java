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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OclCypherRendererTest {
    @Test
    void rendersNavigationExistsUsingBoundDirectionInsteadOfLooseUndirectedMatch() {
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
                        "context Family inv HasAdultChild: self.children->exists(c | c.age >= 18)")))).oclFile());

        OclSemanticBinder binder = new OclSemanticBinder(new OclMetamodelIndex(model));
        OclSemanticBinder.BoundContextInvariant bound = binder.bindContext(firstContext(ast));
        OclIr.InvariantQuery invariantQuery = new OclIrBuilder().buildInvariant(bound);
        OclCypherPlan.InvariantPlan plan = new OclCypherPlanner().planInvariant(invariantQuery);

        OclCypherRenderer.RenderedInvariant rendered = new OclCypherRenderer().renderInvariant(plan);
        assertTrue(rendered.cypher().contains("EXISTS { MATCH (self)-[r]->(c)"));
        assertFalse(rendered.cypher().contains("MATCH (self)-[r]-(c)"));
    }

    @Test
    void rendersIfExpressionAsCaseWhen() {
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

        OclSemanticBinder binder = new OclSemanticBinder(new OclMetamodelIndex(model));
        OclSemanticBinder.BoundContextInvariant bound = binder.bindContext(firstContext(ast));
        OclIr.InvariantQuery invariantQuery = new OclIrBuilder().buildInvariant(bound);
        OclCypherPlan.InvariantPlan plan = new OclCypherPlanner().planInvariant(invariantQuery);

        OclCypherRenderer.RenderedInvariant rendered = new OclCypherRenderer().renderInvariant(plan);
        assertTrue(rendered.cypher().contains("CASE WHEN"));
        assertTrue(rendered.cypher().contains("THEN"));
        assertTrue(rendered.cypher().contains("ELSE"));
    }

    @Test
    void rendersLetExpressionWithoutLeakingSourceVariableName() {
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
        OclCypherPlan.InvariantPlan plan = new OclCypherPlanner().planInvariant(invariantQuery);

        OclCypherRenderer.RenderedInvariant rendered = new OclCypherRenderer().renderInvariant(plan);
        assertFalse(rendered.cypher().contains("threshold"));
        assertTrue(rendered.cypher().contains("reduce("));
        assertTrue(rendered.parameters().containsValue(18L));
    }

    @Test
    void rendersNestedNavigationAttributeAccessWithoutInliningNodeExpressionIntoPattern() {
        String spec = """
                model Demo
                class Family
                attributes
                    name : String
                end
                class Person
                attributes
                    name : String
                end
                association FamilyFather between
                    Family[*] role family
                    Person[0..1] role father
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        ASTNode ast = new ASTVisitor().visit(new OCLParser(new org.antlr.v4.runtime.CommonTokenStream(
                new OCLLexer(org.antlr.v4.runtime.CharStreams.fromString(
                        "context Family inv NamedFather: self.father.name <> ''")))).oclFile());

        OclSemanticBinder binder = new OclSemanticBinder(new OclMetamodelIndex(model));
        OclSemanticBinder.BoundContextInvariant bound = binder.bindContext(firstContext(ast));
        OclIr.InvariantQuery invariantQuery = new OclIrBuilder().buildInvariant(bound);
        OclCypherPlan.InvariantPlan plan = new OclCypherPlanner().planInvariant(invariantQuery);

        OclCypherRenderer.RenderedInvariant rendered = new OclCypherRenderer().renderInvariant(plan);
        assertFalse(rendered.cypher().contains("head([(head(["));
        assertTrue(rendered.cypher().contains("CASE WHEN head(["));
    }

    @Test
    void rendersNavigationSizeComparisonFromNestedCollectionAsListSize() {
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
                    Person[*] role sons
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        ASTNode ast = new ASTVisitor().visit(new OCLParser(new org.antlr.v4.runtime.CommonTokenStream(
                new OCLLexer(org.antlr.v4.runtime.CharStreams.fromString(
                        "context Family inv HasTwoSons: self.name = 'Flanders' implies self.sons->size() = 2")))).oclFile());

        OclSemanticBinder binder = new OclSemanticBinder(new OclMetamodelIndex(model));
        OclSemanticBinder.BoundContextInvariant bound = binder.bindContext(firstContext(ast));
        OclIr.InvariantQuery invariantQuery = new OclIrBuilder().buildInvariant(bound);
        OclCypherPlan.InvariantPlan plan = new OclCypherPlanner().planInvariant(invariantQuery);

        OclCypherRenderer.RenderedInvariant rendered = new OclCypherRenderer().renderInvariant(plan);
        assertTrue(rendered.cypher().contains("size("));
        assertFalse(rendered.cypher().contains("head([(head(["));
    }

    private ASTContext firstContext(ASTNode ast) {
        assertTrue(ast instanceof ASTFile);
        ASTFile file = (ASTFile) ast;
        assertEquals(1, file.invariants().size());
        return file.invariants().get(0);
    }
}
