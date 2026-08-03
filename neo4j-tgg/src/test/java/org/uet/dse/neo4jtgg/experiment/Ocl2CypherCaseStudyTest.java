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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ocl2CypherCaseStudyTest {
    static final Path CASE_STUDY = Path.of("..", "examples", "ocl2cypher-correctness-case-study");

    @Test
    void fixtureCompilesEndToEndAndMatchesItsIndependentUseOracle() throws Exception {
        MModel model = compileModel(CASE_STUDY.resolve("company.use"));
        MSystem system = loadSoil(model, CASE_STUDY.resolve("company.soil"));
        String ocl = Files.readString(CASE_STUDY.resolve("invariants.ocl"));
        Map<String, Set<String>> expected = expectedIds(CASE_STUDY.resolve("expected-violations.csv"));
        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        UseObjectSideReferenceEvaluator reference = new UseObjectSideReferenceEvaluator();

        var invariants = compiler.parseContextInvariants(ocl);
        assertEquals(expected.size(), invariants.size());
        for (var invariant : invariants) {
            String id = invariant.className + "::" + invariant.invName;
            var compiled = compiler.compileInvariantInstrumented(invariant);
            GeneratedCypherContractVerifier.verify(compiled, compiled.cypher(), compiled.parameters());
            FixturePremiseVerifier.verify(system, compiled);
            assertNotNull(compiled.bound(), id);
            assertNotNull(compiled.validationAlgebra(), id);
            assertNotNull(compiled.normalizedValidationAlgebra(), id);
            assertNotNull(compiled.queryPlan(), id);
            assertEquals(expected.get(id), reference.violationIds(system, invariant), id);
        }
        assertEquals(Set.of("minor", "boundary", "adult_employee", "senior_employee", "lead_manager"),
                contextIds(system, "Person"));
        assertEquals(Set.of("acme", "empty_company"), contextIds(system, "Company"));
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

    private Map<String, Set<String>> expectedIds(Path source) throws Exception {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        var lines = Files.readAllLines(source);
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            String[] columns = line.split(",", -1);
            Set<String> ids = columns[1].isBlank()
                    ? Set.of() : Set.of(columns[1].split(";"));
            result.put(columns[0], ids);
        }
        return result;
    }

    static Set<String> contextIds(MSystem system, String className) {
        Set<String> ids = new LinkedHashSet<>();
        for (MObject object : system.state().objectsOfClassAndSubClasses(system.model().getClass(className))) {
            ids.add(object.name());
        }
        return Set.copyOf(ids);
    }
}
