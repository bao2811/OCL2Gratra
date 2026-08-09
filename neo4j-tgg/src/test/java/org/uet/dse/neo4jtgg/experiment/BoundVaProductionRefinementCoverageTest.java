package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.uet.dse.neo4jtgg.ocl.OclSemanticBinder;
import org.uet.dse.neo4jtgg.ocl.ir.OclIr;
import org.uet.dse.neo4jtgg.service.impl.DefaultOclToCypherCompiler;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reflection and executable-witness guard for the production Bound-to-VA abstraction. */
class BoundVaProductionRefinementCoverageTest {
    private static final Path MATRIX = Path.of("verification/coverage/bound_va_refinement_matrix.csv");
    private static final String EVIDENCE_METHOD =
            "everyProductionBoundConstructorHasExactVaAbstractionEvidence";

    @Test
    void everyProductionBoundConstructorHasExactVaAbstractionEvidence() throws Exception {
        Path workspace = workspace();
        List<Row> rows = rows(workspace.resolve(MATRIX));
        Map<String, Row> byBound = new LinkedHashMap<>();
        rows.forEach(row -> byBound.put(row.boundConstructor(), row));

        Set<String> productionBound = new LinkedHashSet<>();
        for (Class<?> nested : OclSemanticBinder.class.getDeclaredClasses()) {
            if (nested.isRecord() && OclSemanticBinder.BoundExpression.class.isAssignableFrom(nested)) {
                productionBound.add(nested.getSimpleName());
                Row row = byBound.get(nested.getSimpleName());
                assertNotNull(row, nested.getSimpleName());
                assertEquals(Arrays.asList(row.boundPayloadFields().split(";", -1)),
                        Arrays.stream(nested.getRecordComponents()).map(component -> component.getName()).toList(),
                        nested.getSimpleName());
            }
        }
        assertEquals(productionBound, byBound.keySet());

        Set<String> productionVa = Arrays.stream(OclIr.SemanticExpression.class.getPermittedSubclasses())
                .map(Class::getSimpleName).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> matrixVa = new LinkedHashSet<>();
        rows.forEach(row -> matrixVa.addAll(List.of(row.vaConstructors().split("\\|", -1))));
        assertEquals(productionVa, matrixVa);

        String builder = Files.readString(workspace.resolve(
                "neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/ir/OclIrBuilder.java"));
        String verifier = Files.readString(workspace.resolve(
                "neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/PipelineRefinementVerifier.java"));
        String lean = Files.readString(workspace.resolve("verification/lean/Ocl2CypherProof.lean"));
        assertTrue(lean.contains("theorem bound_va_abstraction"));
        for (Row row : rows) {
            assertTrue(builder.contains("instanceof OclSemanticBinder." + row.boundConstructor()),
                    row.boundConstructor());
            assertTrue(builder.contains(row.builderMethod() + "("), row.boundConstructor());
            assertTrue(verifier.contains("instanceof OclSemanticBinder." + row.boundConstructor()),
                    row.boundConstructor());
            assertTrue(verifier.contains(row.javaRefinementMethod() + "("), row.boundConstructor());
            for (String constructor : row.leanConstructors().split("\\|", -1)) {
                assertTrue(lean.contains("| " + constructor + " ") || lean.contains("| " + constructor + "("),
                        row.boundConstructor() + " -> " + constructor);
            }
            assertEquals(getClass().getSimpleName(), row.evidenceTest());
            assertEquals(EVIDENCE_METHOD, row.evidenceMethod());
        }

        MModel model = model();
        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        Set<String> witnessedBound = new LinkedHashSet<>();
        Set<String> witnessedVa = new LinkedHashSet<>();
        for (OclValFragmentCoverageTest.CoverageCase coverage : OclValFragmentCoverageTest.admittedCases()) {
            InstrumentedCompilationResult compiled = compiler.compileInvariantInstrumented(coverage.ocl());
            PipelineRefinementVerifier.verify(compiled);
            collect(compiled.bound().expression(), compiled.validationAlgebra().predicate(),
                    witnessedBound, witnessedVa);
        }
        assertEquals(productionBound, witnessedBound);
        assertEquals(productionVa, witnessedVa);
    }

    private static void collect(OclSemanticBinder.BoundExpression bound, OclIr.Expression va,
                                Set<String> boundNames, Set<String> vaNames) {
        boundNames.add(bound.getClass().getSimpleName());
        vaNames.add(va.getClass().getSimpleName());
        if (bound instanceof OclSemanticBinder.BoundSetLiteral b) {
            collectLists(b.elements(), ((OclIr.SetLiteral) va).elements(), boundNames, vaNames);
        } else if (bound instanceof OclSemanticBinder.BoundNot b) {
            collect(b.expression(), ((OclIr.Not) va).expression(), boundNames, vaNames);
        } else if (bound instanceof OclSemanticBinder.BoundIf b) {
            OclIr.If v = (OclIr.If) va;
            collect(b.condition(), v.condition(), boundNames, vaNames);
            collect(b.thenBranch(), v.thenBranch(), boundNames, vaNames);
            collect(b.elseBranch(), v.elseBranch(), boundNames, vaNames);
        } else if (bound instanceof OclSemanticBinder.BoundLet b) {
            OclIr.Let v = (OclIr.Let) va;
            collect(b.value(), v.value(), boundNames, vaNames);
            collect(b.body(), v.body(), boundNames, vaNames);
        } else if (bound instanceof OclSemanticBinder.BoundBinary b) {
            OclIr.Binary v = (OclIr.Binary) va;
            collect(b.left(), v.left(), boundNames, vaNames);
            collect(b.right(), v.right(), boundNames, vaNames);
        } else if (bound instanceof OclSemanticBinder.BoundProperty b) {
            OclIr.Expression source = b.isAttribute()
                    ? ((OclIr.AttributeAccess) va).source() : ((OclIr.NavigationAccess) va).source();
            collect(b.source(), source, boundNames, vaNames);
            if (!b.isAttribute()) {
                collectLists(b.qualifiers(), ((OclIr.NavigationAccess) va).qualifiers(), boundNames, vaNames);
            }
        } else if (bound instanceof OclSemanticBinder.BoundMethodCall b) {
            OclIr.MethodCall v = (OclIr.MethodCall) va;
            collect(b.source(), v.source(), boundNames, vaNames);
            collectLists(b.arguments(), v.arguments(), boundNames, vaNames);
        } else if (bound instanceof OclSemanticBinder.BoundCollectionOperation b) {
            OclIr.CollectionOperation v = (OclIr.CollectionOperation) va;
            collect(b.source(), v.source(), boundNames, vaNames);
            collectLists(b.arguments(), v.arguments(), boundNames, vaNames);
        } else if (bound instanceof OclSemanticBinder.BoundIterator b) {
            OclIr.IteratorOperation v = (OclIr.IteratorOperation) va;
            collect(b.source(), v.source(), boundNames, vaNames);
            collect(b.body(), v.body(), boundNames, vaNames);
        }
    }

    private static void collectLists(List<? extends OclSemanticBinder.BoundExpression> bound,
                                     List<? extends OclIr.Expression> va,
                                     Set<String> boundNames, Set<String> vaNames) {
        assertEquals(bound.size(), va.size());
        for (int index = 0; index < bound.size(); index++) {
            collect(bound.get(index), va.get(index), boundNames, vaNames);
        }
    }

    private static List<Row> rows(Path path) throws Exception {
        List<Row> result = new ArrayList<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8).stream().skip(1).toList()) {
            if (line.isBlank()) continue;
            String[] fields = line.split(",", -1);
            if (fields.length != 8) throw new IllegalStateException("Malformed Bound/VA row: " + line);
            result.add(new Row(fields[0], fields[1], fields[2], fields[3], fields[4], fields[5], fields[6], fields[7]));
        }
        return List.copyOf(result);
    }

    private static Path workspace() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.isRegularFile(current.resolve(MATRIX))) current = current.getParent();
        if (current == null) throw new IllegalStateException("Cannot locate " + MATRIX);
        return current;
    }

    private static MModel model() {
        String specification = """
                model BoundVaCoverage
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
        MModel result = USECompiler.compileSpecification(specification, "bound-va-coverage.use",
                new PrintWriter(diagnostics, true), new ModelFactory());
        assertNotNull(result, diagnostics.toString());
        return result;
    }

    private record Row(String boundConstructor, String boundPayloadFields, String vaConstructors,
                       String leanConstructors, String builderMethod, String javaRefinementMethod,
                       String evidenceTest, String evidenceMethod) {
    }
}
