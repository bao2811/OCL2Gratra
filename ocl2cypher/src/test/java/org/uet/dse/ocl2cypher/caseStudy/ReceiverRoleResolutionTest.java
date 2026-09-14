package org.uet.dse.ocl2cypher.caseStudy;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.uet.dse.ocl2cypher.source.model.*;
import org.uet.dse.ocl2cypher.api.CoreOracle;
import static org.junit.jupiter.api.Assertions.*;

class ReceiverRoleResolutionTest {
    @Test void convertsWholeUmlmmFixtureAndResolvesReusedRoles() throws Exception {
        Path base = Path.of("..", "examples", "carrental", "umlmm");
        var f = CarRentalFixture.read(base.resolve("carrentalmodel.use"), base.resolve("carrental-experiment.soil"));
        var e = f.toExecutable();
        assertEquals(54, e.snapshot().links().size());
        assertEquals(37, e.snapshot().objects().size());
        for (var row : List.of(
                List.of("Branch", "car", "Fleet"), List.of("CarGroup", "car", "Classification"),
                List.of("Rental", "car", "Assignment"), List.of("Car", "branch", "Fleet"),
                List.of("Rental", "branch", "Provider"), List.of("Branch", "carGroup", "Offers"),
                List.of("Car", "carGroup", "Classification"), List.of("Rental", "carGroup", "Reservation")))
            assertEquals(row.get(2), e.schema().navigation(row.get(0), row.get(1)).association().name());
        assertThrows(IllegalArgumentException.class, () -> e.schema().associationByRole("car"));
        assertEquals(List.of("car1", "car2", "car3", "car4"),
                e.snapshot().linkTargets(e.schema(), "car", "branch"));
        assertEquals(java.util.Set.of("cargroup_empty"), java.util.Set.copyOf(CoreOracle.violationsOclEq(
                "context CarGroup inv HasCar: self.car->notEmpty()", e.schema(), e.snapshot())));
    }

    @Test void inheritedAndReverseRolesWorkButAmbiguityNeverSelectsFirst() {
        var b = SchemaModel.builder("roles").clazz(UmlClass.of("A")).clazz(UmlClass.of("B"))
                .clazz(UmlClass.of("Sub", "A"));
        b.association(UmlAssociation.binary("First", "A", "owner", "B", "items"));
        var s = b.build();
        assertEquals("First", s.navigation("Sub", "items").association().name());
        assertTrue(s.navigation("B", "owner").reverse());
        assertNull(s.navigation("B", "items"));
        assertNull(s.navigation("Missing", "items"));
        b.association(UmlAssociation.binary("Second", "Sub", "otherOwner", "B", "items"));
        assertThrows(IllegalArgumentException.class, () -> b.build().navigation("Sub", "items"));
        var result = org.uet.dse.ocl2cypher.api.FrontendCompiler.compile(
                "context Sub inv Ambiguous: self.items->notEmpty()", b.build());
        assertTrue(result.isFailure(), "ambiguous navigation must be a diagnostic, not an arbitrary association");
    }

    @Test void sameNameSelfAssociationEndsAreAmbiguous() {
        var s = SchemaModel.builder("self").clazz(UmlClass.of("A"))
                .association(UmlAssociation.binary("Loop", "A", "peer", "A", "peer")).build();
        assertThrows(IllegalArgumentException.class, () -> s.navigation("A", "peer"));
    }

    @Test void duplicateRolesOnSameReceiverAreRejectedWithoutMutatingBuilder() {
        var b = SchemaModel.builder("roles").clazz(UmlClass.of("A"))
                .clazz(UmlClass.of("B")).clazz(UmlClass.of("C"));
        b.association(UmlAssociation.binary("First", "A", "owner", "B", "items"));
        // Reversed declaration order must not bypass the receiver-scoped check.
        assertThrows(IllegalArgumentException.class, () -> b.association(
                UmlAssociation.binary("Second", "C", "items", "A", "otherOwner")));
        assertEquals(1, b.build().associations().size());
        assertEquals("First", b.build().navigation("A", "items").association().name());
        b.association(UmlAssociation.binary("LegalReuse", "C", "anotherOwner", "B", "items"));
        assertEquals("LegalReuse", b.build().navigation("C", "items").association().name());
    }
}
