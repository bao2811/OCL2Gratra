package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.tzi.use.parser.Symtable;
import org.tzi.use.parser.ocl.OCLCompiler;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.expr.Expression;
import org.uet.dse.neo4jtgg.ocl.OclSemanticBinder;
import org.uet.dse.neo4jtgg.ocl.OclTypeBinding;
import org.uet.dse.neo4jtgg.service.impl.DefaultOclToCypherCompiler;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Closed contextual boundary for the internal Void/null type of OCL_val. */
class OclVoidContextMatrixTest {
    private static final Path MATRIX = Path.of("verification/coverage/void_context_matrix.csv");
    private final MModel model = model();
    private final DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);

    @TestFactory
    Stream<DynamicTest> everyVoidContextAgreesWithTheDeclaredUseAndCertifiedBoundary() throws Exception {
        return rows().stream().map(row -> DynamicTest.dynamicTest(row.id(), () -> verify(row)));
    }

    private void verify(Row row) throws Exception {
        var invariant = compiler.parseContextInvariants(row.ocl()).get(0);
        MClass contextClass = model.getClass(invariant.className);
        assertNotNull(contextClass, row.id());
        Symtable variables = new Symtable();
        variables.add("self", contextClass, null);
        StringWriter diagnostics = new StringWriter();
        Expression useExpression = OCLCompiler.compileExpression(
                model, expressionSource(row.ocl()), row.id() + ".ocl",
                new PrintWriter(diagnostics, true), variables, contextClass, false);

        switch (row.useResult()) {
            case "BOOLEAN" -> {
                assertNotNull(useExpression, row.id() + ": " + diagnostics);
                assertTrue(useExpression.type().isTypeOfBoolean(), row.id() + ": " + useExpression.type());
            }
            case "NON_BOOLEAN" -> {
                assertNotNull(useExpression, row.id() + ": " + diagnostics);
                assertFalse(useExpression.type().isTypeOfBoolean(), row.id());
            }
            case "REJECT" -> assertNull(useExpression, row.id() + ": " + useExpression);
            default -> throw new IllegalStateException("Unknown USE result " + row.useResult());
        }

        if ("REJECT".equals(row.profileDecision())) {
            assertThrows(RuntimeException.class,
                    () -> compiler.compileInvariantInstrumented(row.ocl()), row.id());
            return;
        }

        InstrumentedCompilationResult compiled = compiler.compileInvariantInstrumented(row.ocl());
        assertEquals("Boolean", compiled.bound().expression().type().typeName(), row.id());
        OclSemanticBinder.BoundExpression focused = focus(compiled.bound().expression(), row.focus());
        assertEquals(row.expectedBoundType(), describe(focused.type()), row.id());
        PipelineRefinementVerifier.verify(compiled);
        GeneratedCypherContractVerifier.verify(compiled, compiled.cypher(), compiled.parameters());
    }

    private static OclSemanticBinder.BoundExpression focus(
            OclSemanticBinder.BoundExpression root, String focus) {
        return switch (focus) {
            case "ROOT" -> root;
            case "LEFT" -> ((OclSemanticBinder.BoundBinary) root).left();
            case "RIGHT" -> ((OclSemanticBinder.BoundBinary) root).right();
            case "SOURCE" -> ((OclSemanticBinder.BoundCollectionOperation) root).source();
            case "VALUE" -> ((OclSemanticBinder.BoundLet) root).value();
            case "CONDITION" -> ((OclSemanticBinder.BoundIf) root).condition();
            default -> throw new IllegalStateException("Unknown focus " + focus);
        };
    }

    private static String describe(OclTypeBinding type) {
        if (!type.isCollection()) return type.typeName();
        return "Set(" + describe(type.elementType()) + ")";
    }

    private static List<Row> rows() throws Exception {
        Path path = workspace().resolve(MATRIX);
        return Files.readAllLines(path, StandardCharsets.UTF_8).stream()
                .skip(1).filter(line -> !line.isBlank()).map(line -> {
                    String[] fields = line.split(";", 6);
                    if (fields.length != 6) throw new IllegalStateException("Malformed Void row: " + line);
                    return new Row(fields[0], fields[1], fields[2], fields[3], fields[4], fields[5]);
                }).toList();
    }

    private static Path workspace() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.isRegularFile(current.resolve(MATRIX))) {
            current = current.getParent();
        }
        if (current == null) throw new IllegalStateException("Cannot locate " + MATRIX);
        return current;
    }

    private static String expressionSource(String invariantSource) {
        int separator = invariantSource.indexOf(':');
        return invariantSource.substring(separator + 1).trim();
    }

    private static MModel model() {
        String specification = """
                model OclVoidContexts
                class Person
                attributes
                    name : String
                    age : Integer
                end
                """;
        StringWriter diagnostics = new StringWriter();
        MModel result = USECompiler.compileSpecification(specification, "ocl-void-contexts.use",
                new PrintWriter(diagnostics, true), new ModelFactory());
        assertNotNull(result, diagnostics.toString());
        return result;
    }

    private record Row(String id, String profileDecision, String useResult,
                       String focus, String expectedBoundType, String ocl) {
    }
}
