package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.tzi.use.parser.Symtable;
import org.tzi.use.parser.ocl.OCLCompiler;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uml.ocl.type.Type;
import org.uet.dse.neo4jtgg.ocl.OclSemanticBinder;
import org.uet.dse.neo4jtgg.ocl.OclTypeBinding;
import org.uet.dse.neo4jtgg.ocl.ir.OclIr;
import org.uet.dse.neo4jtgg.service.impl.DefaultOclToCypherCompiler;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Closed-universe coverage for every constructor/operator admitted by the
 * frozen OCL_val theorem profile. This checks transformation coverage, not
 * object/graph semantic equivalence; the real-Neo4j experiments provide the
 * latter oracle.
 */
class OclValFragmentCoverageTest {
    private final MModel fixtureModel = model();
    private final DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(fixtureModel);

    @TestFactory
    Stream<DynamicTest> everyAdmittedFeatureTraversesTheCompletePipeline() {
        return admittedCases().stream().map(testCase -> DynamicTest.dynamicTest(testCase.feature(), () -> {
            InstrumentedCompilationResult first = assertDoesNotThrow(
                    () -> compiler.compileInvariantInstrumented(testCase.ocl()), testCase.feature());
            InstrumentedCompilationResult second = assertDoesNotThrow(
                    () -> compiler.compileInvariantInstrumented(testCase.ocl()), testCase.feature());

            assertNotNull(first.ast(), testCase.feature());
            assertNotNull(first.bound(), testCase.feature());
            assertNotNull(first.validationAlgebra(), testCase.feature());
            assertNotNull(first.normalizedValidationAlgebra(), testCase.feature());
            assertNotNull(first.queryPlan(), testCase.feature());
            assertFalse(first.cypher().isBlank(), testCase.feature());
            assertTrue(first.cypher().contains("RETURN DISTINCT self.use_id AS useId"), testCase.feature());
            PipelineRefinementVerifier.verify(first);
            GeneratedCypherContractVerifier.verify(first, first.cypher(), first.parameters());
            var rawSyntax = GeneratedCypherSyntaxTree.parse(first.cypher());
            assertEquals(rawSyntax, GeneratedCypherSyntaxTree.parse(
                    GeneratedCypherSyntaxTree.render(rawSyntax)), testCase.feature());

            assertEquals(ResearchArtifactFingerprint.of(first.bound()),
                    ResearchArtifactFingerprint.of(second.bound()), testCase.feature());
            assertEquals(ResearchArtifactFingerprint.of(first.validationAlgebra()),
                    ResearchArtifactFingerprint.of(second.validationAlgebra()), testCase.feature());
            assertEquals(ResearchArtifactFingerprint.of(first.normalizedValidationAlgebra()),
                    ResearchArtifactFingerprint.of(second.normalizedValidationAlgebra()), testCase.feature());
            assertEquals(ResearchArtifactFingerprint.of(first.queryPlan()),
                    ResearchArtifactFingerprint.of(second.queryPlan()), testCase.feature());
            assertEquals(first.cypher(), second.cypher(), testCase.feature());
            assertEquals(first.parameters(), second.parameters(), testCase.feature());
        }));
    }

    @TestFactory
    Stream<DynamicTest> everyCoverageExpressionIsBooleanAndWellTypedByUseAgainstTheSameModel() {
        return admittedCases().stream().map(testCase -> DynamicTest.dynamicTest(
                "USE model agreement: " + testCase.feature(), () -> {
                    var invariant = compiler.parseContextInvariants(testCase.ocl()).get(0);
                    MClass contextClass = fixtureModel.getClass(invariant.className);
                    assertNotNull(contextClass, "Unknown fixture context: " + invariant.className);
                    Symtable variables = new Symtable();
                    variables.add("self", contextClass, null);
                    StringWriter diagnostics = new StringWriter();
                    Expression expression = OCLCompiler.compileExpression(
                            fixtureModel, expressionSource(testCase.ocl()),
                            invariant.invName + ".ocl", new PrintWriter(diagnostics, true),
                            variables, contextClass, false);
                    assertNotNull(expression, testCase.feature() + ": " + diagnostics);
                    assertTrue(expression.type().isTypeOfBoolean(),
                            testCase.feature() + " is not Boolean in USE: " + expression.type());
                }));
    }

    @Test
    void certifiedArtifactsKeepAttributeNavigationAndCollectionConstructorsDistinct() {
        InstrumentedCompilationResult attribute = compile("attribute");
        OclSemanticBinder.BoundBinary attributeRoot =
                as(OclSemanticBinder.BoundBinary.class, attribute.bound().expression());
        OclSemanticBinder.BoundProperty attributeAccess =
                as(OclSemanticBinder.BoundProperty.class, attributeRoot.left());
        assertTrue(attributeAccess.isAttribute());
        as(OclIr.AttributeAccess.class,
                as(OclIr.Binary.class, attribute.validationAlgebra().predicate()).left());

        InstrumentedCompilationResult navigation = compile("navigation forward");
        OclSemanticBinder.BoundCollectionOperation navOperation =
                as(OclSemanticBinder.BoundCollectionOperation.class, navigation.bound().expression());
        OclSemanticBinder.BoundProperty navAccess =
                as(OclSemanticBinder.BoundProperty.class, navOperation.source());
        assertFalse(navAccess.isAttribute());
        assertEquals(OclTypeBinding.CollectionKind.SET, navAccess.type().collectionKind());
        OclIr.CollectionOperation navVa =
                as(OclIr.CollectionOperation.class, navigation.validationAlgebra().predicate());
        as(OclIr.NavigationAccess.class, navVa.source());

        InstrumentedCompilationResult reverseNavigation = compile("navigation reverse");
        OclSemanticBinder.BoundProperty reverseAccess = as(
                OclSemanticBinder.BoundProperty.class,
                as(OclSemanticBinder.BoundCollectionOperation.class,
                        reverseNavigation.bound().expression()).source());
        assertFalse(reverseAccess.isAttribute());
        assertTrue(reverseAccess.navigation().targetSingleValued());
        assertFalse(reverseAccess.type().isCollection(),
                "Binding must retain USE's scalar navigation type");
        assertEquals("Company", reverseAccess.type().typeName());
        OclSemanticBinder.BoundCollectionOperation reverseNotEmpty = as(
                OclSemanticBinder.BoundCollectionOperation.class,
                reverseNavigation.bound().expression());
        assertEquals(OclTypeBinding.CollectionKind.SET,
                reverseNotEmpty.sourceCollectionType().collectionKind(),
                "The singleton/empty Set view belongs to the collection-operation boundary");

        InstrumentedCompilationResult collect = compile("collect scalar");
        OclSemanticBinder.BoundCollectionOperation includes = as(
                OclSemanticBinder.BoundCollectionOperation.class, collect.bound().expression());
        OclSemanticBinder.BoundIterator collectIterator =
                as(OclSemanticBinder.BoundIterator.class, includes.source());
        assertEquals("collect", collectIterator.ast().operation);
        assertEquals(OclTypeBinding.CollectionKind.SET, collectIterator.type().collectionKind());
        assertTrue(as(OclSemanticBinder.BoundProperty.class,
                collectIterator.body()).isAttribute());
    }

    @Test
    void typedLetAndTypedIteratorTraverseTheCertifiedPipeline() {
        List<String> cases = List.of(
                "context Person inv TypedLet: let threshold : Real = self.age in threshold >= 0.0",
                "context Person inv TypedSetLet: let xs : Set(Real) = Set{self.age, 17} in xs->includes(17.0)",
                "context Employee inv TypedClassLet: let p : Person = self in p.age >= 0",
                "context Person inv TypedIterator: Employee.allInstances()->forAll(e : Person | e.age >= 0)",
                "context Person inv TypedNumericIterator: Set{self.age, 17}->forAll(x : Real | x >= 0.0)");

        for (String source : cases) {
            InstrumentedCompilationResult compiled = assertDoesNotThrow(
                    () -> compiler.compileInvariantInstrumented(source), source);
            PipelineRefinementVerifier.verify(compiled);
            GeneratedCypherContractVerifier.verify(compiled, compiled.cypher(), compiled.parameters());
            if (source.contains("Real")) {
                assertTrue(compiled.cypher().contains("toFloat("), compiled.cypher());
            }
        }
    }

    @Test
    void nativeUseAndBoundTreeAgreeOnToOneTypeBeforeCollectionView() throws Exception {
        MClass person = fixtureModel.getClass("Person");
        Symtable variables = new Symtable();
        variables.add("self", person, null);
        StringWriter diagnostics = new StringWriter();
        Expression useNavigation = OCLCompiler.compileExpression(
                fixtureModel, "self.employer", "to-one-type.ocl",
                new PrintWriter(diagnostics, true), variables, person, false);
        assertNotNull(useNavigation, diagnostics.toString());
        assertFalse(useNavigation.type().isKindOfCollection(Type.VoidHandling.EXCLUDE_VOID));
        assertEquals("Company", useNavigation.type().shortName());

        InstrumentedCompilationResult compiled = compile("navigation reverse");
        OclSemanticBinder.BoundCollectionOperation notEmpty = as(
                OclSemanticBinder.BoundCollectionOperation.class,
                compiled.bound().expression());
        OclSemanticBinder.BoundProperty boundNavigation = as(
                OclSemanticBinder.BoundProperty.class, notEmpty.source());
        assertEquals("Company", boundNavigation.type().typeName());
        assertFalse(boundNavigation.type().isCollection());
        assertEquals(OclTypeBinding.CollectionKind.SET,
                notEmpty.sourceCollectionType().collectionKind());
    }

    @Test
    void toOneCollectionViewSurvivesBoundIrPlanAndDirectTextRendering() {
        List<String> cases = List.of(
                "context Person inv LiftAsSet: self.employer->asSet()->size() <= 1",
                "context Person inv LiftSelect: "
                        + "let selected = self.employer->asSet()->select(c | c.name <> '') "
                        + "in selected = selected",
                "context Person inv LiftSize: "
                        + "let n = self.employer->size() in n <= 1 and n >= 0");

        for (String source : cases) {
            InstrumentedCompilationResult compiled = compiler.compileInvariantInstrumented(source);
            assertTrue(compiled.cypher().contains("CASE WHEN head("), source + "\n" + compiled.cypher());
            assertTrue(compiled.cypher().contains("THEN [] ELSE [head("), source + "\n" + compiled.cypher());
            assertFalse(compiled.cypher().contains("size(head("), source + "\n" + compiled.cypher());
            assertFalse(compiled.cypher().contains(" IN head("), source + "\n" + compiled.cypher());
            PipelineRefinementVerifier.verify(compiled);
            GeneratedCypherContractVerifier.verify(compiled, compiled.cypher(), compiled.parameters());
        }
    }

    @Test
    void nullLiteralHasInternalVoidTypeButInvariantRemainsBooleanInUseAndBoundTrees() throws Exception {
        List<String> cases = List.of(
                "context Person inv NullEqualsNull: null = null",
                "context Person inv DefinedDiffersFromNull: 'defined' <> null");

        for (String source : cases) {
            InstrumentedCompilationResult compiled = compiler.compileInvariantInstrumented(source);
            OclSemanticBinder.BoundBinary root = as(
                    OclSemanticBinder.BoundBinary.class, compiled.bound().expression());
            OclSemanticBinder.BoundLiteral nullLiteral = root.left() instanceof OclSemanticBinder.BoundLiteral
                    && ((OclSemanticBinder.BoundLiteral) root.left()).value() == null
                    ? (OclSemanticBinder.BoundLiteral) root.left()
                    : as(OclSemanticBinder.BoundLiteral.class, root.right());

            assertEquals("Void", nullLiteral.type().typeName(), source);
            assertFalse(nullLiteral.type().isCollection(), source);
            assertEquals("Boolean", root.type().typeName(), source);

            var invariant = compiler.parseContextInvariants(source).get(0);
            MClass contextClass = fixtureModel.getClass(invariant.className);
            Symtable variables = new Symtable();
            variables.add("self", contextClass, null);
            StringWriter diagnostics = new StringWriter();
            Expression useExpression = OCLCompiler.compileExpression(
                    fixtureModel, expressionSource(source), invariant.invName + ".ocl",
                    new PrintWriter(diagnostics, true), variables, contextClass, false);
            assertNotNull(useExpression, diagnostics.toString());
            assertTrue(useExpression.type().isTypeOfBoolean(), source);

            PipelineRefinementVerifier.verify(compiled);
            GeneratedCypherContractVerifier.verify(compiled, compiled.cypher(), compiled.parameters());
        }
    }

    static List<CoverageCase> admittedCases() {
        return List.of(
                c("self", "context Person inv C01: self = self"),
                c("lexical variable", "context Person inv C02: let p = self in p = self"),
                c("Boolean literal", "context Person inv C03: true"),
                c("Integer literal", "context Person inv C04: self.age >= 18"),
                c("Real literal", "context Person inv C05: self.age / 1 >= 18.0"),
                c("String literal", "context Person inv C06: self.name <> ''"),
                c("attribute", "context Person inv C07: self.age >= 0"),
                c("navigation forward", "context Company inv C08: self.employee->notEmpty()"),
                c("navigation reverse", "context Person inv C09: self.employer->notEmpty()"),
                c("qualified navigation", "context Library inv C10: self.book['A1']->notEmpty()"),
                c("allInstances", "context Person inv C11: Person.allInstances()->includes(self)"),
                c("not", "context Person inv C12: not (self.age < 0)"),
                c("and", "context Person inv C13: self.age >= 0 and self.name <> ''"),
                c("or", "context Person inv C14: self.age >= 0 or self.name = ''"),
                c("implies", "context Person inv C15: self.age >= 18 implies self.age >= 0"),
                c("equals", "context Person inv C16: self.age = self.age"),
                c("not equals", "context Person inv C17: self.name <> ''"),
                c("less than", "context Person inv C18: self.age < 200"),
                c("less or equal", "context Person inv C19: self.age <= 200"),
                c("greater than", "context Person inv C20: self.age > 0"),
                c("greater or equal", "context Person inv C21: self.age >= 0"),
                c("addition", "context Person inv C22: self.age + 1 > self.age"),
                c("subtraction", "context Person inv C23: self.age - 1 < self.age"),
                c("multiplication", "context Person inv C24: self.age * 1 = self.age"),
                c("division", "context Person inv C25: self.age / 1 >= 0.0"),
                c("if", "context Person inv C26: if self.age >= 18 then true else self.age < 18 endif"),
                c("let", "context Person inv C27: let threshold = 18 in self.age >= threshold"),
                c("exists", "context Company inv C28: self.employee->exists(p | p.age >= 18)"),
                c("forAll", "context Company inv C29: self.employee->forAll(p | p.age >= 0)"),
                c("select", "context Company inv C30: self.employee->select(p | p.age >= 18)->size() >= 0"),
                c("reject", "context Company inv C31: self.employee->reject(p | p.age < 18)->size() >= 0"),
                c("collect scalar", "context Company inv C32: self.employee->collect(p | p.age)->includes(18)"),
                c("includes", "context Person inv C33: Person.allInstances()->includes(self)"),
                c("excludes", "context Person inv C34: Person.allInstances()->excludes(self) = false"),
                c("includesAll", "context Company inv C35: self.employee->includesAll(self.employee)"),
                c("excludesAll", "context Company inv C36: self.employee->select(p | p.age < 0)->excludesAll(self.employee)"),
                c("size", "context Company inv C37: self.employee->size() >= 0"),
                c("isEmpty", "context Company inv C38: self.employee->select(p | p.age < 0)->isEmpty()"),
                c("notEmpty", "context Company inv C39: self.employee->notEmpty()"),
                c("oclIsKindOf", "context Person inv C40: self.oclIsKindOf(Person)"),
                c("oclAsType", "context Person inv C41: self.oclAsType(Person).age = self.age"),
                c("xor", "context Person inv C42: (self.age >= 18) xor (self.age >= 65)"),
                c("Set literal", "context Person inv C43: Set{self.age, 17}->size() = 1"),
                c("union", "context Person inv C44: Set{self.age}->union(Set{17})->size() = 1"),
                c("intersection", "context Person inv C45: Set{self.age}->intersection(Set{17})->notEmpty()"),
                c("asSet", "context Person inv C46: Set{self.age, 17}->asSet()->size() = 1"),
                c("isUnique", "context Person inv C47: Set{self.age, 17}->isUnique(x | self.age >= 18)")
        );
    }

    private static CoverageCase c(String feature, String ocl) {
        return new CoverageCase(feature, ocl);
    }

    private InstrumentedCompilationResult compile(String feature) {
        CoverageCase testCase = admittedCases().stream()
                .filter(candidate -> candidate.feature().equals(feature)).findFirst().orElseThrow();
        return compiler.compileInvariantInstrumented(testCase.ocl());
    }

    private static String expressionSource(String invariantSource) {
        int separator = invariantSource.indexOf(':');
        if (separator < 0 || separator == invariantSource.length() - 1) {
            throw new IllegalArgumentException("Invariant has no predicate: " + invariantSource);
        }
        return invariantSource.substring(separator + 1).trim();
    }

    private static <T> T as(Class<T> expected, Object value) {
        assertTrue(expected.isInstance(value),
                () -> "Expected " + expected.getSimpleName() + " but got "
                        + (value == null ? "null" : value.getClass().getSimpleName()));
        return expected.cast(value);
    }

    private MModel model() {
        String specification = """
                model OclValCoverage
                class Company
                attributes
                    name : String
                end
                class Person
                attributes
                    name : String
                    age : Integer
                end
                class Employee < Person
                end
                class Library
                end
                class Book
                end
                association Employment between
                    Company[1] role employer
                    Person[*] role employee
                end
                association Catalog between
                    Library[1] role library qualifier (shelf : String)
                    Book[*] role book
                end
                """;
        StringWriter diagnostics = new StringWriter();
        MModel result = USECompiler.compileSpecification(specification, "oclval-coverage.use",
                new PrintWriter(diagnostics, true), new ModelFactory());
        assertNotNull(result, diagnostics.toString());
        return result;
    }

    record CoverageCase(String feature, String ocl) {
    }
}
