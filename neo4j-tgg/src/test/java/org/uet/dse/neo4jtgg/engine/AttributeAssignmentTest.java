package org.uet.dse.neo4jtgg.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class AttributeAssignmentTest {

    @Test
    void testParse_SimpleAssignment() {
        AttributeAssignment assignment = AttributeAssignment.parse("self.mp.name := self.f.name");
        assertNotNull(assignment);
        assertEquals("self.mp.name", assignment.targetPath());
        assertEquals("self.f.name", assignment.sourceExpression());
    }

    @Test
    void testParse_ConcatenationExpression() {
        AttributeAssignment assignment = AttributeAssignment.parse(
                "self.mp.name := self.f.familyFather.name + ', ' + self.f.name");
        assertNotNull(assignment);
        assertEquals("self.mp.name", assignment.targetPath());
        assertEquals("self.f.familyFather.name + ', ' + self.f.name", assignment.sourceExpression());
    }

    @Test
    void testParse_WithBackticks() {
        AttributeAssignment assignment = AttributeAssignment.parse(
                "`self.mp.name := self.f.name`");
        assertNotNull(assignment);
        assertEquals("self.mp.name", assignment.targetPath());
        assertEquals("self.f.name", assignment.sourceExpression());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "``", "no assignment here", "just text"})
    void testParse_InvalidInputs_ReturnsNull(String input) {
        assertNull(AttributeAssignment.parse(input));
    }

    @Test
    void testParse_EmptyBackticks_ReturnsNull() {
        assertNull(AttributeAssignment.parse("``"));
    }

    @Test
    void testToDisplayText() {
        AttributeAssignment assignment = new AttributeAssignment("self.x.name", "self.y.value");
        assertEquals("self.x.name := self.y.value", assignment.toDisplayText());
    }

    @Test
    void testParse_MissingRightSide_ReturnsNull() {
        assertNull(AttributeAssignment.parse("self.x.name :="));
    }

    @Test
    void testParse_MissingLeftSide_ReturnsNull() {
        assertNull(AttributeAssignment.parse(":= self.y.value"));
    }
}
