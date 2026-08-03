package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.sys.MSystem;
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
    private static final Path ROOT =
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
        Map<String, Set<String>> expected = expectedIds(directory.resolve("expected-violations.csv"));
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
            assertEquals(expected.get(id), reference.violationIds(system, invariant), id);
        }
    }

    private MModel compileModel(Path source) throws Exception {
        StringWriter diagnostics = new StringWriter();
        try (var input = Files.newInputStream(source)) {
            MModel model = USECompiler.compileSpecification(input, source.toString(),
                    new PrintWriter(diagnostics, true), new ModelFactory());
            assertNotNull(model, diagnostics.toString());
            return model;
        }
    }

    private Map<String, Set<String>> expectedIds(Path source) throws Exception {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        var lines = Files.readAllLines(source);
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            String[] columns = line.split(",", -1);
            result.put(columns[0], columns[1].isBlank()
                    ? Set.of() : Set.of(columns[1].split(";")));
        }
        return result;
    }
}
