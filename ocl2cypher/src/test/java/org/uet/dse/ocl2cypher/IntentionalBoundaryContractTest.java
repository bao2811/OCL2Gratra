package org.uet.dse.ocl2cypher;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.uet.dse.ocl2cypher.api.FrontendCompiler;
import org.uet.dse.ocl2cypher.api.ValueQueryRequest;
import org.uet.dse.ocl2cypher.core.CoreLowering;
import org.uet.dse.ocl2cypher.diagnostics.Stage;
import org.uet.dse.ocl2cypher.source.model.SchemaModel;
import org.uet.dse.ocl2cypher.source.model.UmlClass;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Negative contract for intentional OCL_val surface boundaries.
 *
 * <p>A missing feature is safe only when the public entry point returns a
 * diagnostic and does not construct a partial downstream artifact.  These
 * tests deliberately do not claim support for the rejected constructs.</p>
 */
class IntentionalBoundaryContractTest {

    private static final SchemaModel SCHEMA = SchemaModel.builder("boundary")
            .clazz(UmlClass.of("Person"))
            .build();

    private record RejectedSurface(String name, String expression) {}

    @TestFactory
    Stream<DynamicTest> unsupportedOperationsAndIteratorsFailAtFrontend() {
        List<RejectedSurface> cases = List.of(
                new RejectedSurface("string-concat", "'a'.concat('b')"),
                new RejectedSurface("string-upper", "'a'.toUpper()"),
                new RejectedSurface("string-lower", "'A'.toLower()"),
                new RejectedSurface("string-substring", "'abc'.substring(1, 2)"),
                new RejectedSurface("string-indexOf", "'abc'.indexOf('b')"),
                new RejectedSurface("string-matches", "'abc'.matches('a.*')"),
                new RejectedSurface("string-size", "'abc'.size()"),
                new RejectedSurface("collection-at", "Set{1}->at(1)"),
                new RejectedSurface("collection-first", "Set{1}->first()"),
                new RejectedSurface("collection-last", "Set{1}->last()"),
                new RejectedSurface("collection-append", "Set{1}->append(2)"),
                new RejectedSurface("collection-prepend", "Set{1}->prepend(0)"),
                new RejectedSurface("collection-insertAt", "Set{1}->insertAt(1, 2)"),
                new RejectedSurface("collection-subSequence", "Set{1}->subSequence(1, 1)"),
                new RejectedSurface("collection-flatten", "Set{1}->flatten()"),
                new RejectedSurface("iterator-one", "Set{1}->one(x | x = 1)"),
                new RejectedSurface("iterator-isUnique", "Set{1}->isUnique(x | x)"),
                new RejectedSurface("iterator-sortedBy", "Set{1}->sortedBy(x | x)"),
                new RejectedSurface("iterator-closure", "Set{1}->closure(x | Set{x})"),
                new RejectedSurface("iterator-collectNested", "Set{1}->collectNested(x | Set{x})"),
                new RejectedSurface("type-oclIsInvalid", "1.oclIsInvalid()"),
                new RejectedSurface("type-oclIsNew", "1.oclIsNew()"),
                new RejectedSurface("sequence-literal", "Sequence{1}"),
                new RejectedSurface("ordered-set-literal", "OrderedSet{1}"),
                new RejectedSurface("tuple-literal", "Tuple{x = 1}"),
                new RejectedSurface("enum-literal", "Color::red")
        );
        return cases.stream().map(c -> DynamicTest.dynamicTest(c.name(), () -> {
            var result = FrontendCompiler.compileValueQuery(
                    ValueQueryRequest.contextless(c.expression()), SCHEMA);
            assertTrue(result.isFailure(), "unsupported surface must not compile: " + c.expression());
            assertEquals(Stage.E_SM, result.primaryDiagnostic().stage());
            assertTrue(result.primaryDiagnostic().code().startsWith("E_"),
                    () -> "frontend rejection needs an E_* code: " + result.diagnostics());
        }));
    }

    @TestFactory
    Stream<DynamicTest> unsupportedConstraintKindsFailAtFrontend() {
        List<RejectedSurface> cases = List.of(
                new RejectedSurface("precondition", "context Person pre P: true"),
                new RejectedSurface("postcondition", "context Person post P: true"),
                new RejectedSurface("definition", "context Person def: answer : Integer = 1"),
                new RejectedSurface("attribute-init", "context Person::x : Integer init: 1"),
                new RejectedSurface("attribute-derive", "context Person::x : Integer derive: 1")
        );
        return cases.stream().map(c -> DynamicTest.dynamicTest(c.name(), () -> {
            var result = FrontendCompiler.compile(c.expression(), SCHEMA);
            assertTrue(result.isFailure(), "unsupported constraint must not compile: " + c.expression());
            assertEquals(Stage.E_SM, result.primaryDiagnostic().stage());
            assertTrue(result.primaryDiagnostic().code().startsWith("E_"),
                    () -> "frontend rejection needs an E_* code: " + result.diagnostics());
        }));
    }

    @Test
    void nullAndInvalidReachNAdmissionButNeverBecomeBottom() {
        assertNRejection("null", "N_UNSUPPORTED_NULL_LITERAL");
        assertNRejection("invalid", "N_UNSUPPORTED_INVALID_LITERAL");
    }

    @Test
    void wellTypedCollectionRangeIsPreservedInAsThenRejectedByNAdmission() {
        var frontend = FrontendCompiler.compileValueQuery(
                ValueQueryRequest.contextless("Set{1..5}"), SCHEMA);
        assertTrue(frontend.isSuccess(), () -> frontend.diagnostics().toString());
        assertTrue(frontend.value().bodyExpression
                instanceof org.uet.dse.ocl2cypher.source.omg.OmgAs.CollectionLiteralExp);
        var literal = (org.uet.dse.ocl2cypher.source.omg.OmgAs.CollectionLiteralExp)
                frontend.value().bodyExpression;
        assertEquals(org.uet.dse.ocl2cypher.runtime.OclType.set(
                org.uet.dse.ocl2cypher.runtime.OclType.INTEGER), literal.type());
        assertEquals(1, literal.part.size());
        assertTrue(literal.part.get(0)
                instanceof org.uet.dse.ocl2cypher.source.omg.OmgAs.CollectionRange);

        var core = CoreLowering.lowerValueQuery(SCHEMA, frontend.value());
        assertTrue(core.isFailure(), "range outside OCL_val must not produce Core");
        assertEquals(Stage.N_SM, core.primaryDiagnostic().stage());
        assertEquals("N_UNSUPPORTED_COLLECTION_RANGE", core.primaryDiagnostic().code());
    }

    @Test
    void collectionRangeTypeErrorsRemainAtFrontend() {
        var result = FrontendCompiler.compileValueQuery(
                ValueQueryRequest.contextless("Set{'a'..'z'}"), SCHEMA);
        assertTrue(result.isFailure());
        assertEquals(Stage.E_SM, result.primaryDiagnostic().stage());
        assertEquals("E_TYPE", result.primaryDiagnostic().code());
    }

    @Test
    void malformedCollectionRangeRemainsAParseError() {
        var result = FrontendCompiler.compileValueQuery(
                ValueQueryRequest.contextless("Set{1..}"), SCHEMA);
        assertTrue(result.isFailure());
        assertEquals(Stage.E_SM, result.primaryDiagnostic().stage());
        assertEquals("E_PARSE", result.primaryDiagnostic().code());
    }

    private static void assertNRejection(String expression, String expectedCode) {
        var frontend = FrontendCompiler.compileValueQuery(
                ValueQueryRequest.contextless(expression), SCHEMA);
        assertTrue(frontend.isSuccess(), () -> frontend.diagnostics().toString());
        var core = CoreLowering.lowerValueQuery(SCHEMA, frontend.value());
        assertTrue(core.isFailure(), "unsupported literal must not produce Core");
        assertEquals(Stage.N_SM, core.primaryDiagnostic().stage());
        assertEquals(expectedCode, core.primaryDiagnostic().code());
    }
}
