package org.uet.dse.neo4jtgg.ocl.ir;

import org.junit.jupiter.api.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.uet.dse.neo4j.OCLLexer;
import org.uet.dse.neo4j.OCLParser;
import org.uet.dse.neo4j.oclite.ast.ASTContext;
import org.uet.dse.neo4j.oclite.ast.ASTFile;
import org.uet.dse.neo4j.oclite.ast.ASTNode;
import org.uet.dse.neo4j.oclite.ast.ASTVisitor;
import org.uet.dse.neo4jtgg.model.CypherCompilationResult;
import org.uet.dse.neo4jtgg.ocl.OclMetamodelIndex;
import org.uet.dse.neo4jtgg.ocl.OclSemanticBinder;
import org.uet.dse.neo4jtgg.service.impl.DefaultOclToCypherCompiler;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OclRewritePreservationTest {
    @Test
    void notEmptyRewriteMatchesExistentialLemma() {
        OclIr.Expression optimized = optimizeFamilyPredicate(
                "context Family inv HasChildren: self.children->notEmpty()");

        assertTrue(optimized instanceof OclIr.NavigationPredicateCheck);
        OclIr.NavigationPredicateCheck exists = (OclIr.NavigationPredicateCheck) optimized;
        assertEquals(OclIr.NavigationPredicateKind.EXISTS, exists.kind());
        assertEquals("children", exists.navigation().navigation().roleName());
    }

    @Test
    void isEmptyRewriteMatchesNegatedExistentialLemma() {
        OclIr.Expression optimized = optimizeFamilyPredicate(
                "context Family inv NoChildren: self.children->isEmpty()");

        assertTrue(optimized instanceof OclIr.NavigationPredicateCheck);
        OclIr.NavigationPredicateCheck notExists = (OclIr.NavigationPredicateCheck) optimized;
        assertEquals(OclIr.NavigationPredicateKind.NOT_EXISTS, notExists.kind());
        assertEquals("children", notExists.navigation().navigation().roleName());
    }

    @Test
    void navigationSizeComparisonKeepsCountObligation() {
        OclIr.Expression optimized = optimizeFamilyPredicate(
                "context Family inv ManyChildren: self.children->size() >= 2");

        assertTrue(optimized instanceof OclIr.NavigationCountComparison);
        OclIr.NavigationCountComparison count = (OclIr.NavigationCountComparison) optimized;
        assertEquals(">=", count.operator());
        assertEquals(2L, count.literal());
        assertEquals("children", count.navigation().navigation().roleName());
    }

    @Test
    void forAllRewriteUsesNegatedExistentialCounterexample() {
        CypherCompilationResult result = compiler(familyModel()).compile(
                "context Family inv AdultChildren: self.children->forAll(c | c.age >= 18)");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains("NOT EXISTS {")
                && result.getCypher().contains(")-[r]->(c:Object {modelKey:"), result.getCypher());
        assertTrue(result.getCypher().contains("NOT coalesce("), result.getCypher());
        assertTrue(result.getCypher().contains("AND (NOT coalesce("), result.getCypher());
    }

    @Test
    void impliesRewriteUsesValidationOrSemantics() {
        OclIr.Expression optimized = optimizeFamilyPredicate(
                "context Family inv NamedHasChildren: self.name = 'Simpson' implies self.children->notEmpty()");

        assertTrue(optimized instanceof OclIr.Binary);
        OclIr.Binary or = (OclIr.Binary) optimized;
        assertEquals("or", or.operator());
        assertTrue(or.left() instanceof OclIr.Not);
        assertTrue(or.right() instanceof OclIr.NavigationPredicateCheck);
    }

    @Test
    void xorRewriteUsesTwoExclusiveBooleanWitnesses() {
        OclIr.Expression optimized = optimizeFamilyPredicate(
                "context Family inv Exclusive: (self.name = 'A') xor self.children->notEmpty()");

        assertTrue(optimized instanceof OclIr.Binary);
        OclIr.Binary or = (OclIr.Binary) optimized;
        assertEquals("or", or.operator());
        assertTrue(or.left() instanceof OclIr.Binary);
        assertTrue(or.right() instanceof OclIr.Binary);
        assertEquals("and", ((OclIr.Binary) or.left()).operator());
        assertEquals("and", ((OclIr.Binary) or.right()).operator());
    }

    @Test
    void formalNormalizerAndGraphOptimizerAreSemanticNotSyntacticCounterparts() {
        OclIr.Expression generalSize = optimizeFamilyPredicate(
                "context Family inv ConstantSize: Set{1, 2}->size() = 2");
        assertTrue(generalSize instanceof OclIr.Binary);
        OclIr.Binary sizeEquality = (OclIr.Binary) generalSize;
        assertTrue(sizeEquality.left() instanceof OclIr.CollectionOperation);
        assertEquals("size", ((OclIr.CollectionOperation) sizeEquality.left()).operationName());

        OclIr.Expression navigationSize = optimizeFamilyPredicate(
                "context Family inv HasChildrenByCount: self.children->size() > 0");
        assertTrue(navigationSize instanceof OclIr.NavigationCountComparison);
        OclIr.NavigationCountComparison count = (OclIr.NavigationCountComparison) navigationSize;
        assertEquals(">", count.operator());
        assertEquals(0L, count.literal());

        OclIr.Expression navigationForAll = optimizeFamilyPredicate(
                "context Family inv AdultChildrenByForAll: self.children->forAll(c | c.age >= 18)");
        assertTrue(navigationForAll instanceof OclIr.NavigationPredicateCheck);
        assertEquals(OclIr.NavigationPredicateKind.FORALL,
                ((OclIr.NavigationPredicateCheck) navigationForAll).kind());

        OclIr.Expression membership = optimizeFamilyPredicate(
                "context Family inv SetMembership: Set{1, 2}->includes(1) and Set{1, 2}->excludes(3)");
        assertTrue(membership instanceof OclIr.Binary);
        OclIr.Binary conjunction = (OclIr.Binary) membership;
        assertTrue(conjunction.left() instanceof OclIr.CollectionOperation);
        assertTrue(conjunction.right() instanceof OclIr.CollectionOperation);
        assertEquals("includes", ((OclIr.CollectionOperation) conjunction.left()).operationName());
        assertEquals("excludes", ((OclIr.CollectionOperation) conjunction.right()).operationName());
    }

    @Test
    void fusionFallsBackWhenIteratorRenameWouldCaptureNestedBinder() {
        OclIr.Expression optimized = optimizeFamilyPredicate(
                "context Family inv CaptureSafe: "
                        + "self.children->select(a | self.children->exists(c | a.age < c.age))"
                        + "->exists(c | c.name = 'Lisa')");

        assertTrue(optimized instanceof OclIr.IteratorOperation);
        OclIr.IteratorOperation outerExists = (OclIr.IteratorOperation) optimized;
        assertEquals("exists", outerExists.operationName());
        assertEquals("c", outerExists.iteratorName());
        assertTrue(outerExists.source() instanceof OclIr.IteratorOperation);

        OclIr.IteratorOperation select = (OclIr.IteratorOperation) outerExists.source();
        assertEquals("select", select.operationName());
        assertEquals("a", select.iteratorName());
        assertTrue(select.body() instanceof OclIr.NavigationPredicateCheck);

        OclIr.NavigationPredicateCheck nestedExists = (OclIr.NavigationPredicateCheck) select.body();
        assertEquals("c", nestedExists.iteratorName());
        assertTrue(nestedExists.predicate() instanceof OclIr.Binary);
        OclIr.Binary comparison = (OclIr.Binary) nestedExists.predicate();
        assertTrue(comparison.left() instanceof OclIr.AttributeAccess);
        OclIr.AttributeAccess outerAge = (OclIr.AttributeAccess) comparison.left();
        assertTrue(outerAge.source() instanceof OclIr.Variable);
        assertEquals("a", ((OclIr.Variable) outerAge.source()).name());
    }

    @Test
    void fusionMayProceedAcrossTargetBinderWhenSourceIteratorIsUnused() {
        OclIr.Expression optimized = optimizeFamilyPredicate(
                "context Family inv UnusedIteratorCaptureSafe: "
                        + "self.children->select(a | self.children->exists(c | c.age >= 18))"
                        + "->exists(c | c.name = 'Lisa')");

        assertTrue(optimized instanceof OclIr.NavigationPredicateCheck);
        OclIr.NavigationPredicateCheck outerExists = (OclIr.NavigationPredicateCheck) optimized;
        assertEquals(OclIr.NavigationPredicateKind.EXISTS, outerExists.kind());
        assertEquals("c", outerExists.iteratorName());
        assertTrue(outerExists.predicate() instanceof OclIr.Binary);
        OclIr.Binary conjunction = (OclIr.Binary) outerExists.predicate();
        assertEquals("and", conjunction.operator());
        assertTrue(conjunction.left() instanceof OclIr.NavigationPredicateCheck);
        OclIr.NavigationPredicateCheck nestedExists =
                (OclIr.NavigationPredicateCheck) conjunction.left();
        assertEquals("c", nestedExists.iteratorName());
    }

    @Test
    void iteratorPredicateIsCoercedToValidationTruthInCypher() {
        CypherCompilationResult result = compiler(familyModel()).compile(
                "context Family inv HasAdultBart: self.children->exists(c | c.age >= 18 and c.name = 'Bart')");

        assertTrue(result.isSupported(), result.getReason());
        assertTrue(result.getCypher().contains("EXISTS {")
                && result.getCypher().contains(")-[r]->(c:Object {modelKey:"), result.getCypher());
        assertTrue(result.getCypher().contains("AND coalesce("), result.getCypher());
    }

    private OclIr.Expression optimizeFamilyPredicate(String ocl) {
        OclIr.InvariantQuery invariantQuery = buildInvariant(familyModel(), ocl);
        return new OclIrOptimizer().optimizeExpression(invariantQuery.predicate());
    }

    private OclIr.InvariantQuery buildInvariant(MModel model, String ocl) {
        ASTNode ast = new ASTVisitor().visit(new OCLParser(new org.antlr.v4.runtime.CommonTokenStream(
                new OCLLexer(org.antlr.v4.runtime.CharStreams.fromString(ocl)))).oclFile());
        OclSemanticBinder binder = new OclSemanticBinder(new OclMetamodelIndex(model));
        return new OclIrBuilder().buildInvariant(binder.bindContext(firstContext(ast)));
    }

    private ASTContext firstContext(ASTNode ast) {
        assertTrue(ast instanceof ASTFile);
        ASTFile file = (ASTFile) ast;
        assertEquals(1, file.invariants().size());
        return file.invariants().get(0);
    }

    private DefaultOclToCypherCompiler compiler(MModel model) {
        return new DefaultOclToCypherCompiler(model);
    }

    private MModel familyModel() {
        String spec = """
                model Demo
                class Family
                attributes
                    name : String
                end
                class Person
                attributes
                    name : String
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
        return model;
    }
}
