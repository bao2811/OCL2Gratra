package org.uet.dse.ocl2cypher.caseStudy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.runtime.OclValue;
import static org.junit.jupiter.api.Assertions.*;

class CarRentalFixtureTest {
    private static final Path BASE = Path.of("..", "examples", "carrental");
    @TempDir Path temp;

    @Test void readsActualModelInheritanceAttributesAndAllTuples() throws Exception {
        var f = CarRentalFixture.read(BASE.resolve("carrentalmodel.use"), BASE.resolve("carrental-experiment.soil"));
        assertEquals(9, f.declarations().classes().size());
        assertTrue(f.declarations().clazz("Person").isAbstract());
        assertTrue(f.declarations().conforms("Employee", "Person"));
        assertTrue(f.declarations().conforms("Customer", "Person"));
        assertFalse(f.declarations().hasClass("Booking"));
        assertEquals(OclType.REAL, f.declarations().attribute("Employee", "salary").declaredType());
        assertEquals(OclType.set(OclType.STRING), f.attributeType("Customer", "email"));
        assertNull(f.declarations().attribute("Person", "email"), "archival collection is not a scalar observer");
        assertEquals(OclType.STRING, f.attributeType("Customer", "firstname"));
        assertEquals(new OclValue.StringValue("Frank"),
                f.objects().attributeSlot("customer1", "firstname").orElseThrow());
        assertEquals(28, f.objects().objects().size());
        assertEquals(11, f.associations().size());
        assertEquals(43, f.tuples().size());
        assertEquals(List.of(
                new CarRentalFixture.Tuple("Maintenance", List.of("servicedepot1", "check1", "car1")),
                new CarRentalFixture.Tuple("Maintenance", List.of("servicedepot2", "check2", "car1"))),
                f.tuples().stream().filter(t -> t.association().equals("Maintenance")).toList());
        assertEquals(new OclValue.SetValue(OclType.set(OclType.STRING),
                List.of(new OclValue.StringValue("frank@example.com"))),
                f.objects().attributeSlot("customer1", "email").orElseThrow());
    }

    @Test void replayCannotSilentlyDiscardMaintenance() {
        var e = assertThrows(IOException.class, () -> CaseStudyReplayer.loadCarRental(
                BASE.resolve("carrental-experiment.soil"), BASE.resolve("invariants-extended.ocl")));
        assertTrue(e.getMessage().startsWith("CASE_STUDY_NARY_UNSUPPORTED: Maintenance"), e::getMessage);
    }

    @Test void rejectsMalformedCommandsUnknownSlotsAndWrongTupleEndpoints() throws Exception {
        String prefix = "!new Car('c')\n!new Branch('b')\n";
        for (String invalid : List.of("!insert (c, b) into Fleet", "!insert (b) into Fleet",
                "!insert (b, missing) into Fleet", "!set c.unknown := 1",
                "!set missing.id := 'x'", "!create ignored", "!new Missing('m')",
                "!new Person('p')", "!new Car('c')", "!new Car('other') garbage")) {
            Path soil = temp.resolve("invalid.soil");
            Files.writeString(soil, prefix + invalid);
            var e = assertThrows(IOException.class, () -> CarRentalFixture.read(BASE.resolve("carrentalmodel.use"), soil), invalid);
            assertTrue(e.getMessage().contains("invalid.soil:3:"), e::getMessage);
        }
    }

    @Test void missingSnapshotIsNotAnEmptySuccessfulStudy() {
        assertThrows(IOException.class, () -> CarRentalFixture.read(
                BASE.resolve("carrentalmodel.use"), temp.resolve("missing.soil")));
    }

    @Test void malformedModelAndInheritanceAreReportedAsIoFailures() throws Exception {
        Path model = temp.resolve("bad.use");
        Path soil = temp.resolve("empty.soil");
        Files.writeString(soil, "");
        for (String declarations : List.of(
                "class C\nend\nclass C\nend\n",
                "class C < Missing\nend\n",
                "class C < D\nend\nclass D < C\nend\n",
                "class C\nattributes\na : String\na : String\nend\n")) {
            Files.writeString(model, "model Bad\n" + declarations);
            assertThrows(IOException.class, () -> CarRentalFixture.read(model, soil));
        }
    }
}
