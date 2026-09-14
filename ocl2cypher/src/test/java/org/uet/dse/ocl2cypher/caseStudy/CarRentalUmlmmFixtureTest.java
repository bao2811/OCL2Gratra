package org.uet.dse.ocl2cypher.caseStudy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.runtime.OclValue;
import static org.junit.jupiter.api.Assertions.*;

class CarRentalUmlmmFixtureTest {
    private static final Path BASE = Path.of("..", "examples", "carrental");
    private static CarRentalFixture.Loaded read(Path dir) throws Exception {
        return CarRentalFixture.read(dir.resolve("carrentalmodel.use"), dir.resolve("carrental-experiment.soil"));
    }

    @Test void variantHasOnlyBinaryAssociationsAndScalarAttributes() throws Exception {
        var f = read(BASE.resolve("umlmm"));
        assertDoesNotThrow(f::requireBinaryExecutionSupport);
        assertEquals(11, f.declarations().classes().size());
        assertEquals(14, f.associations().size());
        assertEquals(37, f.objects().objects().size());
        assertEquals(54, f.tuples().size());
        assertTrue(f.tuples().stream().allMatch(t -> t.objects().size() == 2));
        var scalar = Set.of(OclType.STRING, OclType.INTEGER, OclType.REAL, OclType.BOOLEAN);
        assertTrue(f.declarations().attributes().stream().allMatch(a -> scalar.contains(a.declaredType())));
        assertTrue(f.declarations().conforms("Employee", "Person"));
        assertTrue(f.declarations().clazz("Person").isAbstract());
        assertFalse(Files.readString(BASE.resolve("umlmm/carrentalmodel.use")).contains("operations"));
    }

    @Test void reconstructsExactlyTheOriginalMaintenanceTuplesAndBinaryLinks() throws Exception {
        var original = read(BASE);
        var f = read(BASE.resolve("umlmm"));
        Set<CarRentalFixture.Tuple> recovered = new HashSet<>();
        Map<List<String>, String> depotByCheckAndCar = new HashMap<>();
        for (var o : f.objects().objects()) {
            if (!o.dynamicClassKey().equals("MaintenanceRecord")) continue;
            String depot = target(f, "MaintenanceDepot", o.stableId());
            String check = target(f, "MaintenanceCheck", o.stableId());
            String car = target(f, "MaintenanceCar", o.stableId());
            assertTrue(recovered.add(new CarRentalFixture.Tuple("Maintenance", List.of(depot, check, car))), "duplicate tuple");
            var previous = depotByCheckAndCar.putIfAbsent(List.of(check, car), depot);
            assertTrue(previous == null || previous.equals(depot), "original ternary upper bound");
        }
        assertEquals(new HashSet<>(original.tuples().stream().filter(t -> t.association().equals("Maintenance")).toList()), recovered);
        var binary = original.tuples().stream().filter(t -> !t.association().equals("Maintenance")).toList();
        assertEquals(binary, f.tuples().stream().filter(t -> original.associations().containsKey(t.association())).toList());
    }

    @Test void preservesOriginalObjectsScalarSlotsAndEmailSets() throws Exception {
        var original = read(BASE);
        var f = read(BASE.resolve("umlmm"));
        for (var o : original.objects().objects()) {
            assertEquals(o.dynamicClassKey(), f.objects().object(o.stableId()).dynamicClassKey());
            for (var slot : original.objects().attributeSlots(o.stableId()).entrySet()) {
                if (!slot.getKey().equals("email")) {
                    assertEquals(slot.getValue(), f.objects().attributeSlot(o.stableId(), slot.getKey()).orElseThrow());
                } else {
                    List<OclValue> values = f.tuples().stream()
                            .filter(t -> t.association().equals("PersonEmail") && t.objects().get(0).equals(o.stableId()))
                            .map(t -> f.objects().attributeSlot(t.objects().get(1), "value").orElseThrow()).toList();
                    assertEquals(values.size(), new HashSet<>(values).size());
                    assertEquals(slot.getValue(), new OclValue.SetValue(OclType.set(OclType.STRING), values));
                }
            }
        }
        for (var o : f.objects().objects()) if (o.dynamicClassKey().equals("EmailAddress"))
            assertEquals(1L, f.tuples().stream().filter(t -> t.association().equals("PersonEmail")
                    && t.objects().get(1).equals(o.stableId())).count());
    }

    @Test void retainsExpectedOracleAndChangesOnlyMaintenanceInvariantBodies() throws Exception {
        assertEquals(Files.readAllLines(BASE.resolve("expected-violations-extended.csv")),
                Files.readAllLines(BASE.resolve("umlmm/expected-violations-extended.csv")));
        var before = CaseStudyReplayer.loadInvariants(BASE.resolve("invariants-extended.ocl"));
        var after = CaseStudyReplayer.loadInvariants(BASE.resolve("umlmm/invariants-extended.ocl"));
        assertEquals(25, after.size());
        List<String> changed = new ArrayList<>();
        for (int i = 0; i < before.size(); i++) {
            assertEquals(before.get(i).context(), after.get(i).context());
            assertEquals(before.get(i).name(), after.get(i).name());
            if (!before.get(i).body().equals(after.get(i).body())) changed.add(after.get(i).name());
        }
        assertEquals(List.of("NotRentedDuringMaintenance", "AtMostOneServiceDepot"), changed);
    }

    private static String target(CarRentalFixture.Loaded f, String association, String record) {
        var links = f.tuples().stream().filter(t -> t.association().equals(association)
                && t.objects().get(0).equals(record)).toList();
        assertEquals(1, links.size(), association + ":" + record);
        return links.get(0).objects().get(1);
    }
}
