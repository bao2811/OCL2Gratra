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
    void rendersCanonicalBottomEqualityInsteadOfCypherNullEquality() {
        var bottom = new OclCypherPlan.LiteralPlan(
                null, org.uet.dse.neo4jtgg.ocl.OclTypeBinding.scalar("Void"));
        var value = new OclCypherPlan.LiteralPlan(
                "defined", org.uet.dse.neo4jtgg.ocl.OclTypeBinding.scalar("String"));
        var booleanType = org.uet.dse.neo4jtgg.ocl.OclTypeBinding.scalar("Boolean");

        var bottomEqualsBottom = new OclCypherRenderer().renderTopLevelExpression(
                new OclCypherPlan.BinaryPlan("=", bottom, bottom, booleanType));
        var valueNotEqualsBottom = new OclCypherRenderer().renderTopLevelExpression(
                new OclCypherPlan.BinaryPlan("<>", value, bottom, booleanType));

        assertTrue(bottomEqualsBottom.cypher().contains("null IS NULL AND null IS NULL"));
        assertTrue(bottomEqualsBottom.cypher().contains("THEN true"));
        assertTrue(valueNotEqualsBottom.cypher().contains("THEN false"));
        assertFalse(bottomEqualsBottom.cypher().contains("RETURN (null = null)"));
    }

    @Test
    void validationSemanticsTreatsOnlyTrueAsSatisfied() {
        assertEquals("coalesce(p, false)", OclValidationSemantics.validationTruth("p"));
        assertEquals("NOT coalesce(p, false)", OclValidationSemantics.violationPredicate("p"));
        assertEquals("NOT coalesce(null, false)", OclValidationSemantics.violationPredicate("null"));
        assertEquals("(NOT coalesce(p, false))", OclValidationSemantics.not("p"));
        assertEquals("(coalesce(a, false) AND coalesce(b, false))", OclValidationSemantics.and("a", "b"));
        assertEquals("(coalesce(a, false) OR coalesce(b, false))", OclValidationSemantics.or("a", "b"));
        assertEquals("((NOT coalesce(a, false)) OR coalesce(b, false))", OclValidationSemantics.implies("a", "b"));
    }

    @Test
    void rendersUndefinedInvariantPredicateAsValidationViolation() {
        OclCypherPlan.InvariantPlan plan = new OclCypherPlan.InvariantPlan(
                "Person",
                "UndefinedIsViolation",
                new OclCypherPlan.LiteralPlan(null, org.uet.dse.neo4jtgg.ocl.OclTypeBinding.scalar("Boolean")));

        OclCypherRenderer.RenderedInvariant rendered = new OclCypherRenderer().renderInvariant(plan);

        assertTrue(rendered.cypher().contains("WHERE NOT coalesce(null, false)"));
        assertTrue(rendered.cypher().contains("RETURN DISTINCT self.use_id AS useId"));
    }

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
        assertTrue(rendered.cypher().contains("AND coalesce("));
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
        assertTrue(rendered.cypher().contains("CASE WHEN coalesce("));
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
        assertTrue(rendered.cypher().contains("head([_let"));
        assertFalse(rendered.cypher().contains("reduce(_let"));
        assertTrue(rendered.parameters().containsValue(18L));
    }

    @Test
    void rendersOptimizedIteratorChainWithScopedTargetAlias() {
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
                        "context Family inv ScopedIteratorAlias: self.children->select(a | a.age >= 18)->exists(b | b.name = 'Lisa')")))).oclFile());

        OclSemanticBinder binder = new OclSemanticBinder(new OclMetamodelIndex(model));
        OclSemanticBinder.BoundContextInvariant bound = binder.bindContext(firstContext(ast));
        OclIr.InvariantQuery invariantQuery = new OclIrOptimizer().optimizeInvariant(new OclIrBuilder().buildInvariant(bound));
        OclCypherPlan.InvariantPlan plan = new OclCypherPlanner().planInvariant(invariantQuery);

        OclCypherRenderer.RenderedInvariant rendered = new OclCypherRenderer().renderInvariant(plan);
        assertTrue(rendered.cypher().contains("EXISTS { MATCH (self)-[r]->(b)"), rendered.cypher());
        assertTrue(rendered.cypher().contains("WITH b.objectKey AS attrOwner"), rendered.cypher());
        assertTrue(rendered.cypher().contains("(attrOwner"), rendered.cypher());
        assertFalse(rendered.cypher().contains("WITH a.objectKey AS attrOwner"), rendered.cypher());
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
