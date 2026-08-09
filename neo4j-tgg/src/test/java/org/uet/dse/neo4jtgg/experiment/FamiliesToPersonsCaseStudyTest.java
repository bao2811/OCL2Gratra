package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.uml.sys.MObject;
import org.uet.dse.neo4jtgg.service.impl.DefaultOclToCypherCompiler;
import org.uet.dse.neo4jtgg.service.impl.SoilFileLoader;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FamiliesToPersonsCaseStudyTest {
    static final Path ROOT =
            Path.of("..", "examples", "families-to-persons-correctness-case-study");

    @Test
    void familiesCaseMatchesItsOwnOracle() throws Exception {
        verify(ROOT.resolve("families"), "families.use", "families.soil", 10);
    }

    @Test
    void personsCaseMatchesItsOwnOracle() throws Exception {
        verify(ROOT.resolve("persons"), "persons.use", "persons.soil", 10);
    }

    private void verify(Path directory, String modelFile, String soilFile,
                        int expectedInvariantCount) throws Exception {
        MModel model = compileModel(directory.resolve(modelFile));
        MSystem system = new MSystem(model);
        new SoilFileLoader().load(system, Files.readString(directory.resolve(soilFile)),
                directory.resolve(soilFile).toString());
        String ocl = Files.readString(directory.resolve("invariants.ocl"));
        Map<String, ExpectedViolation> expected = expectedIds(directory.resolve("expected-violations.csv"));
        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        UseObjectSideReferenceEvaluator reference = new UseObjectSideReferenceEvaluator();

        var invariants = compiler.parseContextInvariants(ocl);
        assertEquals(expectedInvariantCount, invariants.size(), directory.toString());
        assertEquals(expected.size(), invariants.size(), directory.toString());
        for (var invariant : invariants) {
            String id = invariant.className + "::" + invariant.invName;
            var compiled = compiler.compileInvariantInstrumented(invariant);
            GeneratedCypherContractVerifier.verify(compiled, compiled.cypher(), compiled.parameters());
            assertNotNull(compiled.bound(), id);
            assertNotNull(compiled.validationAlgebra(), id);
            assertNotNull(compiled.normalizedValidationAlgebra(), id);
            assertNotNull(compiled.queryPlan(), id);
            Set<String> actual = reference.violationIds(system, invariant);
            assertEquals(expected.get(id).ids(), actual, id);
            BenchmarkVacuityStatus classification = ViolationSetOracle.compare(
                    id, contextIds(system, invariant.className), actual, actual).vacuityStatus();
            assertEquals(expected.get(id).classification(), classification, id);
            org.junit.jupiter.api.Assertions.assertFalse(
                    classification == BenchmarkVacuityStatus.ALL_VIOLATE, id);
        }
    }

    static MModel compileModel(Path source) throws Exception {
        StringWriter diagnostics = new StringWriter();
        try (var input = Files.newInputStream(source)) {
            MModel model = USECompiler.compileSpecification(input, source.toString(),
                    new PrintWriter(diagnostics, true), new ModelFactory());
            assertNotNull(model, diagnostics.toString());
            return model;
        }
    }

    static MSystem loadSoil(MModel model, Path source) throws Exception {
        MSystem system = new MSystem(model);
        new SoilFileLoader().load(system, Files.readString(source), source.toString());
        return system;
    }

    static Set<String> contextIds(MSystem system, String className) {
        Set<String> result = new java.util.LinkedHashSet<>();
        var contextClass = system.model().getClass(className);
        assertNotNull(contextClass, className);
        for (MObject object : system.state().objectsOfClassAndSubClasses(contextClass)) {
            result.add(object.name());
        }
        return Set.copyOf(result);
    }

    static Map<String, ExpectedViolation> expectedIds(Path source) throws Exception {
        Map<String, ExpectedViolation> result = new LinkedHashMap<>();
        var lines = Files.readAllLines(source);
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            String[] columns = line.split(",", -1);
            if (columns.length != 3) throw new IllegalArgumentException("Malformed oracle row: " + line);
            result.put(columns[0], new ExpectedViolation(
                    columns[1].isBlank() ? Set.of() : Set.of(columns[1].split(";")),
                    BenchmarkVacuityStatus.valueOf(columns[2])));
        }
        return Map.copyOf(result);
    }

    record ExpectedViolation(Set<String> ids, BenchmarkVacuityStatus classification) {
        ExpectedViolation {
            ids = Set.copyOf(ids);
        }
    }
}
