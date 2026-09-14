package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;
import org.uet.dse.neo4jtgg.service.impl.DefaultOclToCypherCompiler;
import org.uet.dse.neo4jtgg.service.impl.SoilFileLoader;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComprehensiveUmlOclCaseStudyTest {
    static final Path CASE_STUDY = Path.of("..", "examples", "uml-ocl-comprehensive-case-study");

    @Test
    void coversBoundaryTopologyAndCompositionCasesAgainstTheObjectOracle() throws Exception {
        MModel model = compileModel(CASE_STUDY.resolve("comprehensive.use"));
        MSystem system = loadSoil(model, CASE_STUDY.resolve("comprehensive.soil"));
        String ocl = Files.readString(CASE_STUDY.resolve("invariants.ocl"));
        Map<String, ExpectedViolation> expected = expected(CASE_STUDY.resolve("expected-violations.csv"));
        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        UseObjectSideReferenceEvaluator reference = new UseObjectSideReferenceEvaluator();
        Map<BenchmarkVacuityStatus, Integer> vacuity = new EnumMap<>(BenchmarkVacuityStatus.class);

        var invariants = compiler.parseContextInvariants(ocl);
        assertEquals(30, invariants.size());
        assertEquals(expected.size(), invariants.size());
        for (var invariant : invariants) {
            String id = invariant.className + "::" + invariant.invName;
            var compiled = compiler.compileInvariantInstrumented(invariant);
            PipelineRefinementVerifier.verify(compiled);
            GeneratedCypherContractVerifier.verify(compiled, compiled.cypher(), compiled.parameters());
            FixturePremiseVerifier.verify(system, compiled);
            if ("Department::HasMinimumWitness".equals(id)) {
                assertTrue(compiled.cypher().contains("any("), compiled.cypher());
                assertFalse(compiled.cypher().contains(
                        "WHERE NOT coalesce(EXISTS { UNWIND COLLECT {"), compiled.cypher());
            }
            assertNotNull(compiled.bound(), id);
            assertNotNull(compiled.validationAlgebra(), id);
            assertNotNull(compiled.normalizedValidationAlgebra(), id);
            assertNotNull(compiled.queryPlan(), id);
            Set<String> actual = reference.violationIds(system, invariant);
            assertEquals(expected.get(id).ids(), actual, id);
            BenchmarkVacuityStatus status = ViolationSetOracle.compare(
                    id, contextIds(system, invariant.className), actual, actual).vacuityStatus();
            assertEquals(expected.get(id).classification(), status, id);
            vacuity.merge(status, 1, Integer::sum);
        }
        assertEquals(26, vacuity.getOrDefault(BenchmarkVacuityStatus.NON_VACUOUS_MIXED, 0));
        assertEquals(4, vacuity.getOrDefault(BenchmarkVacuityStatus.ALL_PASS, 0));
        assertEquals(0, vacuity.getOrDefault(BenchmarkVacuityStatus.EMPTY_CONTEXT, 0));
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
        Set<String> ids = new LinkedHashSet<>();
        for (MObject object : system.state().objectsOfClassAndSubClasses(system.model().getClass(className))) {
            ids.add(object.name());
        }
        return Set.copyOf(ids);
    }

    private Map<String, ExpectedViolation> expected(Path source) throws Exception {
        Map<String, ExpectedViolation> result = new LinkedHashMap<>();
        var lines = Files.readAllLines(source);
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            String[] columns = line.split(",", -1);
            Set<String> ids = columns[1].isBlank() ? Set.of() : Set.of(columns[1].split(";"));
            result.put(columns[0], new ExpectedViolation(ids,
                    BenchmarkVacuityStatus.valueOf(columns[2])));
        }
        return Map.copyOf(result);
    }

    private record ExpectedViolation(Set<String> ids, BenchmarkVacuityStatus classification) { }
}
