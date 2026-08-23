package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;
import org.uet.dse.neo4jtgg.service.impl.DefaultOclToCypherCompiler;
import org.uet.dse.neo4jtgg.service.impl.SoilFileLoader;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclCodedUnsupportedOperationException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Static and independent-object-oracle checks for the two requested case studies. */
class MedicalAndCarRentalCaseStudyTest {
    private static final Path MEDICAL = Path.of("..", "examples", "medical-system-yte");
    private static final Path CAR_RENTAL = Path.of("..", "examples", "carrental");

    @Test
    void reconstructedMedicalModelMatchesTheDiagramBoundary() throws Exception {
        MModel model = compileModel(MEDICAL.resolve("medical-system.use"));

        assertEquals("MedicalSystemYte", model.name());
        assertEquals(Set.of("Gender", "HealthStatus", "Brand", "Administration",
                        "Specialty", "BloodType"),
                model.enumTypes().stream().map(type -> type.name())
                        .collect(java.util.stream.Collectors.toSet()));
        for (String className : Set.of("Person", "Nurse", "Doctor", "Patient", "Hospital",
                "Department", "Disease", "Medication", "Dosage", "DosagePlan",
                "MedicalRecord", "RecordEntry", "Prescription", "Hospitalization")) {
            assertNotNull(model.getClass(className), className);
        }
        assertEquals(Set.of("Appointment", "Consultation", "Prescription", "Hospitalization",
                        "HospitalStructure", "PatientRecord", "RecordEntries"),
                model.associations().stream().map(association -> association.name())
                        .collect(java.util.stream.Collectors.toSet()));
        assertEquals(Set.of("Person"), model.getClass("Doctor").parents().stream()
                .map(parent -> parent.name()).collect(java.util.stream.Collectors.toSet()));
        assertEquals(Set.of("Person"), model.getClass("Nurse").parents().stream()
                .map(parent -> parent.name()).collect(java.util.stream.Collectors.toSet()));
        assertEquals(Set.of("Person"), model.getClass("Patient").parents().stream()
                .map(parent -> parent.name()).collect(java.util.stream.Collectors.toSet()));
        assertEquals("Sequence(Set(Integer))",
                model.getClass("Doctor").attribute("shiftSchedule", false).type().toString());
        assertEquals("Set(Sequence(String))",
                model.getClass("Patient").attribute("treatmentHistory", false).type().toString());
        assertEquals("Sequence(Sequence(Medication))",
                model.getClass("Patient").attribute("prescriptionHistory", false).type().toString());
        assertFalse(model.getClass("Person").attributes().stream()
                .anyMatch(attribute -> Set.of("kk", "nono").contains(attribute.name())));
        assertFalse(model.getClass("Doctor").attributes().stream()
                .anyMatch(attribute -> Set.of("favNurse", "hotNurse", "gd").contains(attribute.name())));
    }

    @Test
    void medicalCypherIsStructurallyCertifiedAndMatchesUseViolationIds() throws Exception {
        verifyCase(MEDICAL, "medical-system.use", "medical-system.soil", 13);
    }

    @Test
    void medicalNestedDiscriminatorsCompileAndMatchUseViolationIds() throws Exception {
        verifyInvariantFile(MEDICAL, "medical-system.use", "medical-system.soil",
                "nested-discriminators.ocl", "expected-nested-discriminator-violations.csv",
                "expected-nested-certification-boundaries.txt",
                "expected-nested-production-gaps.txt",
                "expected-nested-production-gap-reasons.csv", 14);
    }

    @Test
    void nestedDiscriminatorMatrixCoversTheEntireExecutableCorpus() throws Exception {
        Path matrix = Path.of("..", "verification", "coverage",
                "medical_nested_discriminator_matrix.csv");
        var rows = Files.readAllLines(matrix).stream().skip(1)
                .filter(line -> !line.isBlank()).map(line -> line.split(",", -1)).toList();
        assertEquals(14, rows.size());
        assertEquals(java.util.stream.IntStream.rangeClosed(1, 14)
                        .mapToObj(index -> "ND-%02d".formatted(index)).toList(),
                rows.stream().map(row -> row[0]).toList());
        assertTrue(rows.stream().allMatch(row -> row.length == 7
                && !row[1].isBlank() && !row[2].isBlank()
                && !row[4].isBlank() && !row[5].isBlank() && !row[6].isBlank()));
    }

    @Test
    void carRentalCypherIsStructurallyCertifiedAndMatchesUseViolationIds() throws Exception {
        verifyCase(CAR_RENTAL, "carrentalmodel.use", "carrental.soil", 10);
    }

    private void verifyCase(Path directory, String modelFile, String soilFile,
                            int expectedInvariantCount) throws Exception {
        verifyInvariantFile(directory, modelFile, soilFile, "invariants.ocl",
                "expected-violations.csv", "expected-certification-boundaries.txt",
                "expected-production-gaps.txt", "expected-production-gap-reasons.csv",
                expectedInvariantCount);
    }

    private void verifyInvariantFile(Path directory, String modelFile, String soilFile,
                                     String invariantFile, String expectedFile,
                                     String boundaryFile, String productionGapFile,
                                     String productionGapReasonFile,
                                     int expectedInvariantCount) throws Exception {
        MModel model = compileModel(directory.resolve(modelFile));
        MSystem system = loadSoil(model, directory.resolve(soilFile));
        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        String ocl = Files.readString(directory.resolve(invariantFile));
        var invariants = compiler.parseContextInvariants(ocl);
        var production = compiler.compileFile(ocl);
        Map<String, Set<String>> expected = expectedIds(directory.resolve(expectedFile));
        Set<String> expectedBoundaries = new LinkedHashSet<>(
                Files.readAllLines(directory.resolve(boundaryFile)));
        Set<String> expectedProductionGaps = new LinkedHashSet<>(
                Files.readAllLines(directory.resolve(productionGapFile)));
        Map<String, GapExpectation> expectedGapReasons =
                expectedGapReasons(directory.resolve(productionGapReasonFile));
        UseObjectSideReferenceEvaluator reference = new UseObjectSideReferenceEvaluator();
        Set<String> actualBoundaries = new LinkedHashSet<>();
        Set<String> actualProductionGaps = new LinkedHashSet<>();

        assertEquals(expectedInvariantCount, invariants.size());
        assertEquals(expectedInvariantCount, production.getRuleCount());
        assertTrue(production.getDocumentDiagnostics().isEmpty(), production::toDisplayText);
        assertEquals(expected.keySet(), invariants.stream()
                .map(invariant -> invariant.className + "::" + invariant.invName)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));

        for (int index = 0; index < invariants.size(); index++) {
            var invariant = invariants.get(index);
            var rule = production.getRuleResults().get(index);
            String id = invariant.className + "::" + invariant.invName;
            assertEquals(id, rule.getContextClassName() + "::" + rule.getInvariantName());
            assertEquals(expected.get(id), reference.violationIds(system, invariant), id);
            if (rule.isSupported()) {
                GeneratedCypherSyntaxTree.parse(rule.getCypher());
                assertTrue(rule.getCypher().startsWith("MATCH (self:Object"),
                        () -> id + "\n" + rule.getCypher());
                assertTrue(rule.getCypher().contains("RETURN DISTINCT self.use_id AS useId"),
                        () -> id + "\n" + rule.getCypher());
            } else {
                actualProductionGaps.add(id);
                var diagnostic = rule.getDiagnostics().get(0);
                GapExpectation expectedGap = expectedGapReasons.get(id);
                assertNotNull(expectedGap, () -> "Missing reason-coded production gap: " + id);
                assertEquals(expectedGap.phase(), diagnostic.phase().name(), id);
                assertEquals(expectedGap.code(), diagnostic.code().name(), id);
            }

            try {
                var certified = compiler.compileInvariantInstrumented(invariant);
                PipelineRefinementVerifier.verify(certified);
                GeneratedCypherContractVerifier.verify(
                        certified, certified.cypher(), certified.parameters());
                FixturePremiseVerifier.verify(system, certified);
                assertTrue(rule.isSupported(),
                        () -> id + " is certified but rejected by production: " + rule.getReason());
                assertEquals(rule.getCypher(), certified.cypher(), id);
                assertEquals(rule.getParameters(), certified.parameters(), id);
            } catch (OclCodedUnsupportedOperationException excluded) {
                actualBoundaries.add(id);
            }
        }
        assertEquals(expectedBoundaries, actualBoundaries,
                "The certified/general compiler boundary changed");
        assertEquals(expectedProductionGaps, actualProductionGaps,
                "The production compiler gap set changed");
        assertEquals(expectedProductionGaps, expectedGapReasons.keySet(),
                "The production gap ID and reason manifests diverged");
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
        for (MObject object : system.state().objectsOfClassAndSubClasses(modelClass(system, className))) {
            ids.add(object.name());
        }
        return Set.copyOf(ids);
    }

    private static org.tzi.use.uml.mm.MClass modelClass(MSystem system, String className) {
        return system.model().getClass(className);
    }

    static Map<String, Set<String>> expectedIds(Path source) throws Exception {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        var lines = Files.readAllLines(source);
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            String[] columns = line.split(",", -1);
            Set<String> ids = columns[1].isBlank() ? Set.of() : Set.of(columns[1].split(";"));
            result.put(columns[0], ids);
        }
        return Map.copyOf(result);
    }

    private static Map<String, GapExpectation> expectedGapReasons(Path source) throws Exception {
        Map<String, GapExpectation> result = new LinkedHashMap<>();
        var lines = Files.readAllLines(source);
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            String[] columns = line.split(",", -1);
            assertEquals(4, columns.length, () -> "Malformed production gap row: " + line);
            result.put(columns[0], new GapExpectation(columns[1], columns[2], columns[3]));
        }
        return Map.copyOf(result);
    }

    private record GapExpectation(String phase, String code, String capability) {
    }
}
