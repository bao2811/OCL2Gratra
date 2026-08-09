package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.uet.dse.neo4jtgg.service.impl.DefaultOclToCypherCompiler;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclCodedUnsupportedOperationException;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclDiagnosticCode;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclDiagnosticPhase;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Negative admission matrix for malformed or ill-typed theorem inputs. */
class OclValNegativeAdmissionCoverageTest {
    private final DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model());

    @TestFactory
    Stream<DynamicTest> invalidInputsAreRejectedBeforeQueryExecution() {
        return rejectedCases().stream().map(testCase -> DynamicTest.dynamicTest(testCase.name(), () -> {
            if (testCase.instrumented()) {
                RuntimeException thrown = assertThrows(RuntimeException.class,
                        () -> compiler.compileInvariantInstrumented(testCase.ocl()), testCase.name());
                if (testCase.expectedCode() != null) {
                    assertTrue(thrown instanceof OclCodedUnsupportedOperationException,
                            testCase.name() + ": expected coded admission failure but got " + thrown);
                    assertTrue(((OclCodedUnsupportedOperationException) thrown).code()
                                    == testCase.expectedCode(),
                            testCase.name() + ": expected code " + testCase.expectedCode()
                                    + " but got " + ((OclCodedUnsupportedOperationException) thrown).code());
                }
            } else {
                var result = compiler.compile(testCase.ocl());
                assertFalse(result.isSupported(), testCase.name() + ": " + result.getCypher());
                assertNotNull(result.getDiagnostic(), testCase.name());
                assertTrue(result.getDiagnostics().stream().anyMatch(diagnostic ->
                                diagnostic.phase() == testCase.expectedPhase()),
                        testCase.name() + ": expected phase " + testCase.expectedPhase()
                                + " but got " + result.getDiagnostics());
                if (testCase.expectedCode() != null) {
                    assertTrue(result.getDiagnostics().stream().anyMatch(diagnostic ->
                                    diagnostic.code() == testCase.expectedCode()),
                            testCase.name() + ": expected code " + testCase.expectedCode()
                                    + " but got " + result.getDiagnostics());
                }
            }
        }));
    }

    private List<RejectedCase> rejectedCases() {
        return List.of(
                c("malformed syntax", "context Person inv Broken: self.age >=", false, OclDiagnosticPhase.PARSE),
                c("unknown context class", "context Missing inv Bad: true", false, OclDiagnosticPhase.SEMANTIC),
                c("unknown property", "context Person inv Bad: self.missing = 1", false, OclDiagnosticPhase.SEMANTIC),
                c("non-Boolean if condition", "context Person inv Bad: if self.age then true else false endif", false, OclDiagnosticPhase.SEMANTIC),
                c("incompatible if branches", "context Person inv Bad: (if self.age >= 18 then self.name else self.age endif) = self.name", false, OclDiagnosticPhase.SEMANTIC),
                c("non-Boolean select body", "context Company inv Bad: self.employee->select(p | p.name)->notEmpty()", false, OclDiagnosticPhase.SEMANTIC),
                c("wrong qualifier type", "context Library inv Bad: self.book[1]->notEmpty()", false, OclDiagnosticPhase.SEMANTIC),
                certified("scalar attribute is not a collection source",
                        "context Person inv Bad: self.age->isEmpty()"),
                certified("native-scalar to-one navigation is not a direct iterator source",
                        "context Person inv Bad: self.employer->select(c | c = c)->notEmpty()"),
                c("non-invariant root", "Person.allInstances()->size() >= 0", true, null),
                c("empty Set literal", "context Person inv Bad: Set{}->isEmpty()", false, OclDiagnosticPhase.SEMANTIC),
                c("Void-only Set literal has no contextual element type",
                        "context Person inv Bad: Set{null}->notEmpty()", false,
                        OclDiagnosticPhase.SEMANTIC, OclDiagnosticCode.INVALID_COLLECTION_ARGUMENT),
                c("nested Set literal", "context Person inv Bad: Set{Set{1}}->notEmpty()", false, OclDiagnosticPhase.SEMANTIC),
                c("heterogeneous Set literal", "context Person inv Bad: Set{1, 'one'}->notEmpty()", false, OclDiagnosticPhase.SEMANTIC),
                c("incompatible set union", "context Person inv Bad: Set{1}->union(Set{'one'})->notEmpty()", false, OclDiagnosticPhase.SEMANTIC),
                c("wrong union arity", "context Person inv Bad: Set{1}->union(Set{2}, Set{3})->notEmpty()", false, OclDiagnosticPhase.SEMANTIC),
                c("isUnique collection body", "context Person inv Bad: Set{1}->isUnique(x | Set{x})", false, OclDiagnosticPhase.SEMANTIC),
                c("xor non-Boolean operand", "context Person inv Bad: self.age xor true", false, OclDiagnosticPhase.SEMANTIC),
                c("oclIsTypeOf outside certified fragment",
                        "context Person inv Bad: self.oclIsTypeOf(Person)", false,
                        OclDiagnosticPhase.SEMANTIC,
                        OclDiagnosticCode.OCL_IS_TYPE_OF_OUTSIDE_CERTIFIED_FRAGMENT),
                c("oclIsTypeOf rejected by certified policy",
                        "context Person inv Bad: self.oclIsTypeOf(Person)", true,
                        null, OclDiagnosticCode.OCL_IS_TYPE_OF_OUTSIDE_CERTIFIED_FRAGMENT),
                certified("any outside certified fragment",
                        "context Company inv Bad: self.employee->any(p | p.age >= 18) = self.employee->any(p | p.age >= 18)"),
                certified("one outside certified fragment",
                        "context Company inv Bad: self.employee->one(p | p.age >= 18)"),
                certified("sortedBy outside certified fragment",
                        "context Company inv Bad: self.employee->sortedBy(p | p.age)->notEmpty()"),
                certified("count element outside certified fragment",
                        "context Person inv Bad: Person.allInstances()->count(self) >= 0"),
                certified("Bag conversion outside certified fragment",
                        "context Person inv Bad: Set{1}->asBag()->size() = 1"),
                certified("OrderedSet conversion outside certified fragment",
                        "context Person inv Bad: Set{1}->asOrderedSet()->size() = 1"),
                certified("ordered positional access outside certified fragment",
                        "context Person inv Bad: Set{1}->asOrderedSet()->first() = 1"),
                certified("fallback string method outside certified fragment",
                        "context Person inv Bad: self.name.split(',')->notEmpty()")
        );
    }

    @Test
    void rejectsNavigationOverNonBinaryAssociation() {
        MModel nary = compileModel("""
                model NonBinary
                class Person end
                class Company end
                class Animal end
                association Buy between
                    Person[0..1] role buyer
                    Company[0..1] role seller
                    Animal[*] role pet
                end
                """, "non-binary.use");
        assertNotNull(nary);
        var result = new DefaultOclToCypherCompiler(nary)
                .compile("context Person inv Bad: self.pet->notEmpty()");
        assertFalse(result.isSupported());
        assertTrue(result.getDiagnostics().stream().anyMatch(diagnostic ->
                diagnostic.phase() == OclDiagnosticPhase.SEMANTIC
                        && diagnostic.code() == OclDiagnosticCode.NON_BINARY_ASSOCIATION_UNSUPPORTED));
    }

    @Test
    void rejectsAmbiguousNavigationAtMetamodelAdmission() {
        MModel ambiguous = compileModel("""
                model AmbiguousNavigation
                class Person end
                class Company end
                class Agency end
                association Employment between
                    Person[*] role employee
                    Company[0..1] role employer
                end
                association Contract between
                    Person[*] role contractor
                    Agency[0..1] role employer
                end
                """, "ambiguous-navigation.use");
        assertNotNull(ambiguous);
        var result = new DefaultOclToCypherCompiler(ambiguous)
                .compile("context Person inv Bad: self.employer->notEmpty()");
        assertFalse(result.isSupported());
        assertTrue(result.getDiagnostics().stream().anyMatch(diagnostic ->
                diagnostic.phase() == OclDiagnosticPhase.SEMANTIC
                        && diagnostic.code() == OclDiagnosticCode.AMBIGUOUS_NAVIGATION));
    }

    @Test
    void experimentalGeneralCompilerSupportDoesNotImplyCertifiedAdmission() {
        String expression = "context Company inv Experimental: self.employee->one(p | p.age >= 18)";

        var generalResult = compiler.compile(expression);
        assertTrue(generalResult.isSupported(), generalResult.getReason());
        OclCodedUnsupportedOperationException certifiedFailure = assertThrows(
                OclCodedUnsupportedOperationException.class,
                () -> compiler.compileInvariantInstrumented(expression));
        assertTrue(certifiedFailure.code() == OclDiagnosticCode.OCL_VAL_EXCLUDED_CONSTRUCT);
    }

    @Test
    void toOneNavigationKeepsUseScalarTypeAndIsNotSilentlyCertifiedAsASet() {
        MModel model = compileModel("""
                model ToOneNavigation
                class Company end
                class Person
                attributes
                    name : String
                end
                association CompanyManager between
                    Company[*] role managedCompany
                    Person[1] role manager
                end
                """, "to-one-navigation.use");
        assertNotNull(model);
        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);

        String scalarAccess = "context Company inv ManagerNamed: self.manager.name <> ''";
        assertTrue(compiler.compile(scalarAccess).isSupported(),
                "The experimental path must preserve USE's scalar Person navigation");

        OclCodedUnsupportedOperationException failure = assertThrows(
                OclCodedUnsupportedOperationException.class,
                () -> compiler.compileInvariantInstrumented(scalarAccess));
        assertTrue(failure.code() == OclDiagnosticCode.OCL_VAL_EXCLUDED_CONSTRUCT);
        assertTrue(failure.getMessage().contains("association navigation must bind to Set(Entity)"));
    }

    @Test
    void collectionAndReferenceValuedAttributesRemainOutsideScalarPa5Certificate() {
        MModel model = compileModel("""
                model AttributeKinds
                class Person
                attributes
                    aliases : Set(String)
                    manager : Person
                end
                """, "attribute-kinds.use");
        assertNotNull(model);
        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);

        String collectionAttribute = "context Person inv ExperimentalCollection: self.aliases->isEmpty()";
        assertTrue(compiler.compile(collectionAttribute).isSupported());
        OclCodedUnsupportedOperationException collectionFailure = assertThrows(
                OclCodedUnsupportedOperationException.class,
                () -> compiler.compileInvariantInstrumented(collectionAttribute));
        assertTrue(collectionFailure.code() == OclDiagnosticCode.OCL_VAL_EXCLUDED_CONSTRUCT);
        assertTrue(collectionFailure.getMessage().contains("PA5 currently certifies scalar slots only"));

        String referenceAttribute = "context Person inv ExperimentalReference: self.manager = self";
        assertTrue(compiler.compile(referenceAttribute).isSupported());
        OclCodedUnsupportedOperationException referenceFailure = assertThrows(
                OclCodedUnsupportedOperationException.class,
                () -> compiler.compileInvariantInstrumented(referenceAttribute));
        assertTrue(referenceFailure.code() == OclDiagnosticCode.OCL_VAL_EXCLUDED_CONSTRUCT);
        assertTrue(referenceFailure.getMessage().contains("PA5 currently certifies scalar slots only"));
    }

    @Test
    void enumerationRemainsOutsideTheCertifiedScalarDomain() {
        MModel model = compileModel("""
                model EnumAttribute
                enum Status { active, inactive }
                class Person
                attributes
                    status : Status
                end
                """, "enum-attribute.use");
        assertNotNull(model);
        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        String expression = "context Person inv ExperimentalEnum: self.status = Status::active";

        assertTrue(compiler.compile(expression).isSupported());
        OclCodedUnsupportedOperationException failure = assertThrows(
                OclCodedUnsupportedOperationException.class,
                () -> compiler.compileInvariantInstrumented(expression));
        assertTrue(failure.code() == OclDiagnosticCode.OCL_VAL_EXCLUDED_CONSTRUCT);
        assertTrue(failure.getMessage().contains("certified scalar types"));
    }

    private RejectedCase c(String name, String ocl, boolean instrumented,
                           OclDiagnosticPhase expectedPhase) {
        return c(name, ocl, instrumented, expectedPhase, null);
    }

    private RejectedCase c(String name, String ocl, boolean instrumented,
                           OclDiagnosticPhase expectedPhase, OclDiagnosticCode expectedCode) {
        return new RejectedCase(name, ocl, instrumented, expectedPhase, expectedCode);
    }

    private RejectedCase certified(String name, String ocl) {
        return c(name, ocl, true, null, OclDiagnosticCode.OCL_VAL_EXCLUDED_CONSTRUCT);
    }

    private MModel model() {
        String specification = """
                model OclValNegative
                class Company
                end
                class Person
                attributes
                    name : String
                    age : Integer
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
        MModel result = USECompiler.compileSpecification(specification, "oclval-negative.use",
                new PrintWriter(diagnostics, true), new ModelFactory());
        assertNotNull(result, diagnostics.toString());
        return result;
    }

    private MModel compileModel(String specification, String fileName) {
        return USECompiler.compileSpecification(specification, fileName,
                new PrintWriter(new StringWriter(), true), new ModelFactory());
    }

    private record RejectedCase(String name, String ocl, boolean instrumented,
                                OclDiagnosticPhase expectedPhase,
                                OclDiagnosticCode expectedCode) {
    }
}
