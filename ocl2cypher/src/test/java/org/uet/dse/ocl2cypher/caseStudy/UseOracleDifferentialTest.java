package org.uet.dse.ocl2cypher.caseStudy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.uet.dse.ocl2cypher.api.CoreOracle;
import org.uet.dse.ocl2cypher.oracle.UseOracleAdapter;

/**
 * Differential source-oracle checks against the independent USE evaluator.
 * These tests do not use the generated graph or Cypher execution as an oracle.
 */
class UseOracleDifferentialTest {

    private static final Path MEDICAL = Path.of("..", "examples", "medical");
    private static final Path CAR_RENTAL = Path.of("..", "examples", "carrental", "umlmm");
    private static final Set<String> CAR_RENTAL_FRONTEND_BOUNDARY = Set.of(
            "CarGroup::ExactlyOneLowest",
            "CarGroup::ExactlyOneHighest",
            "Car::NotRentedDuringMaintenance",
            "Rental::AssignedRequestedGroup",
            "Rental::AssignedCarInProviderFleet",
            "Branch::UniqueEmployeeFirstNames");
    /*
     * OCL_val deliberately propagates an undefined iterator predicate to a
     * whole-collection bottom. USE's select/reject evaluator instead completes
     * these two formulas to true on ward_b. Keep the difference executable and
     * explicit; it must not be counted as external-oracle agreement.
     */
    private static final Map<String, Set<String>> MEDICAL_TYPED_BOTTOM_REFINEMENTS = Map.of(
            "Ward::PatientPartition", Set.of(),
            "Ward::AdultFilterIsSound", Set.of());

    @Test
    void useAgreesWithMedicalOutsideDeclaredTypedBottomRefinements() throws Exception {
        var use = UseOracleAdapter.evaluate(
                MEDICAL.resolve("medical.use"),
                MEDICAL.resolve("invariants.ocl"),
                MEDICAL.resolve("medical.soil"));
        Map<String, Set<String>> expected = expected(
                MEDICAL.resolve("expected-violations.csv"));
        Map<String, Set<String>> expectedUse = new LinkedHashMap<>(expected);
        MEDICAL_TYPED_BOTTOM_REFINEMENTS.forEach(expectedUse::put);
        assertEquals(expectedUse, use.violations(),
                "V_USE vs Medical expectations with declared OCL_val refinements");

        var fixture = CarRentalFixture.read(
                MEDICAL.resolve("medical.use"), MEDICAL.resolve("medical.soil"))
                .toExecutable();
        var specs = CaseStudyReplayer.loadInvariants(MEDICAL.resolve("invariants.ocl"));
        assertEquals(expected.size(), specs.size());
        for (var spec : specs) {
            String key = spec.context() + "::" + spec.name();
            Set<String> core = Set.copyOf(CoreOracle.violationsOclEq(
                    spec.source(), fixture.schema(), fixture.snapshot()));
            if (MEDICAL_TYPED_BOTTOM_REFINEMENTS.containsKey(key)) {
                assertEquals(expected.get(key), core,
                        "CoreOracle must retain the documented OCL_val bottom policy: " + key);
                assertEquals(MEDICAL_TYPED_BOTTOM_REFINEMENTS.get(key),
                        use.violations().get(key),
                        "USE side of documented refinement difference: " + key);
            } else {
                assertEquals(use.violations().get(key), core,
                        "V_USE vs CoreOracle: " + key);
            }
        }
        assertTrue(use.objectEvaluations() > specs.size(),
                "USE must evaluate invariant bodies on concrete context objects");
        System.out.println("PASS: V_USE Medical: 36 invariants; 34 exact "
                + "Core agreements; 2 declared typed-bottom refinement differences; "
                + use.objectEvaluations() + " USE object evaluations");
    }

    @Test
    void useAgreesWithCarRentalExpectedIdsAndAdmittedCoreOracleCases() throws Exception {
        Path invariants = CAR_RENTAL.resolve("invariants-extended.ocl");
        var use = UseOracleAdapter.evaluate(
                CAR_RENTAL.resolve("carrentalmodel.use"), invariants,
                CAR_RENTAL.resolve("carrental-experiment.soil"));
        Map<String, Set<String>> expected = expected(
                CAR_RENTAL.resolve("expected-violations-extended.csv"));
        assertEquals(expected, use.violations(), "V_USE vs reviewed CarRental CSV");

        var fixture = CarRentalFixture.read(
                CAR_RENTAL.resolve("carrentalmodel.use"),
                CAR_RENTAL.resolve("carrental-experiment.soil")).toExecutable();
        var specs = CaseStudyReplayer.loadInvariants(invariants);
        int compared = 0;
        for (var spec : specs) {
            String key = spec.context() + "::" + spec.name();
            if (CAR_RENTAL_FRONTEND_BOUNDARY.contains(key)) {
                continue;
            }
            assertEquals(use.violations().get(key), Set.copyOf(CoreOracle.violationsOclEq(
                    spec.source(), fixture.schema(), fixture.snapshot())),
                    "V_USE vs CoreOracle: " + key);
            compared++;
        }
        assertEquals(19, compared, "unexpected admitted CarRental comparison count");
        System.out.println("PASS: V_USE CarRental: 25 invariants match reviewed IDs; "
                + "19 exact Core agreements; 6 compiler-boundary cases; "
                + use.objectEvaluations() + " USE object evaluations");
    }

    private static Map<String, Set<String>> expected(Path csv) throws Exception {
        var lines = Files.readAllLines(csv);
        assertEquals("invariant,ids,classification", lines.get(0));
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) {
                continue;
            }
            String[] fields = line.split(",", -1);
            assertEquals(3, fields.length, line);
            Set<String> ids = fields[1].isEmpty()
                    ? Set.of() : Set.of(fields[1].split(";"));
            assertEquals(null, result.putIfAbsent(fields[0], ids),
                    "duplicate expected row: " + fields[0]);
        }
        return result;
    }
}
