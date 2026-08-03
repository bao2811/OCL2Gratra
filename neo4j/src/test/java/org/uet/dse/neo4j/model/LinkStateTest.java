package org.uet.dse.neo4j.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkStateTest {
    @Test
    void emptyQualifierSlotsDoNotChangeUnqualifiedIdentity() {
        String withoutSlots = LinkState.buildIdentity(
                "Employment", List.of("company", "person"), List.of(), null);
        String withEmptySlots = LinkState.buildIdentity(
                "Employment", List.of("company", "person"), List.of(List.of(), List.of()), null);

        assertEquals(withoutSlots, withEmptySlots);
        assertFalse(withEmptySlots.contains("@q"));
    }

    @Test
    void qualifiedIdentityRetainsEndPositionAndOrderedValues() {
        String identity = LinkState.buildIdentity(
                "Enrollment", List.of("student", "course"),
                List.of(List.of(), List.of("2026", "A")), null);

        assertTrue(identity.endsWith(
                "::sourceQualifiers=0:::targetQualifiers=2:4:20261:A"));
    }

    @Test
    void semanticComparisonNormalizesOnlyCompletelyEmptyQualifierLists() {
        LinkState left = link(List.of());
        LinkState right = link(List.of(List.of(), List.of()));
        assertTrue(left.isSameAs(right));

        right.qualifierValues = List.of(List.of(), List.of("2026"));
        assertFalse(left.isSameAs(right));
    }

    private LinkState link(List<List<String>> qualifiers) {
        LinkState state = new LinkState();
        state.assocName = "Employment";
        state.edgeLabel = "LinkAssociateWith";
        state.participants = List.of("company", "person");
        state.qualifierValues = qualifiers;
        return state;
    }
}
