package org.uet.dse.neo4jtgg.engine;

import org.junit.jupiter.api.Test;
import org.uet.dse.neo4j.model.FullObjectSnapshot;
import org.uet.dse.neo4j.model.LinkState;
import org.uet.dse.neo4j.model.ObjectState;
import org.uet.dse.neo4jtgg.model.TggRuleInfo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RuleBindingResolverTest {

    @Test
    void testResolveBindings_SimpleTypeMatch() {
        FullObjectSnapshot snapshot = new FullObjectSnapshot();
        snapshot.objects.put("a1", makeObject("a1", "ClassA", Map.of("x", "1")));
        snapshot.objects.put("a2", makeObject("a2", "ClassA", Map.of("x", "2")));
        SnapshotIndex index = new SnapshotIndex(snapshot);

        List<Map<String, String>> bindings = RuleBindingResolver.resolveBindings(
                Map.of("v", "ClassA"), List.of(), List.of(), index);

        assertEquals(2, bindings.size());
        assertTrue(bindings.stream().anyMatch(b -> "a1".equals(b.get("v"))));
        assertTrue(bindings.stream().anyMatch(b -> "a2".equals(b.get("v"))));
    }

    @Test
    void testResolveBindings_WithAssociation() {
        FullObjectSnapshot snapshot = new FullObjectSnapshot();
        snapshot.objects.put("f1", makeObject("f1", "Family", Map.of("name", "Simpson")));
        snapshot.objects.put("m1", makeObject("m1", "FamilyMember", Map.of("name", "Homer")));
        snapshot.objects.put("m2", makeObject("m2", "FamilyMember", Map.of("name", "Bart")));
        LinkState link = new LinkState();
        link.assocName = "Father";
        link.participants = List.of("f1", "m1");
        snapshot.links.put(link.getIdentity(), link);

        SnapshotIndex index = new SnapshotIndex(snapshot);

        List<Map<String, String>> bindings = RuleBindingResolver.resolveBindings(
                Map.of("fm", "Family", "father", "FamilyMember"),
                List.of(new TggRuleInfo.AssociationPattern("fm", "father", "Father")),
                List.of(), index);

        // Only m1 is linked via Father, not m2
        assertEquals(1, bindings.size());
        assertEquals("f1", bindings.get(0).get("fm"));
        assertEquals("m1", bindings.get(0).get("father"));
    }

    @Test
    void testResolveBindings_WithPredicate_NotUndefined() {
        FullObjectSnapshot snapshot = new FullObjectSnapshot();
        snapshot.objects.put("a1", makeObject("a1", "TypeA", Map.of("name", "Valid")));
        snapshot.objects.put("a2", makeObject("a2", "TypeA", Map.of()));
        SnapshotIndex index = new SnapshotIndex(snapshot);

        List<Map<String, String>> bindings = RuleBindingResolver.resolveBindings(
                Map.of("v", "TypeA"), List.of(),
                List.of("v.name <> Undefined"), index);

        assertEquals(1, bindings.size());
        assertEquals("a1", bindings.get(0).get("v"));
    }

    @Test
    void testResolveBindings_EmptyVariables() {
        SnapshotIndex index = new SnapshotIndex(new FullObjectSnapshot());
        List<Map<String, String>> bindings = RuleBindingResolver.resolveBindings(
                Map.of(), List.of(), List.of(), index);

        assertEquals(1, bindings.size());
        assertTrue(bindings.get(0).isEmpty());
    }

    @Test
    void testResolveBindings_NoMatches() {
        SnapshotIndex index = new SnapshotIndex(new FullObjectSnapshot());
        List<Map<String, String>> bindings = RuleBindingResolver.resolveBindings(
                Map.of("v", "NonExistent"), List.of(), List.of(), index);

        assertTrue(bindings.isEmpty());
    }

    @Test
    void testResolveBindings_MultipleAssociations() {
        FullObjectSnapshot snapshot = new FullObjectSnapshot();
        snapshot.objects.put("f1", makeObject("f1", "Family", Map.of()));
        snapshot.objects.put("m1", makeObject("m1", "Member", Map.of()));
        snapshot.objects.put("m2", makeObject("m2", "Member", Map.of()));
        LinkState l1 = new LinkState();
        l1.assocName = "Father";
        l1.participants = List.of("f1", "m1");
        snapshot.links.put(l1.getIdentity(), l1);
        LinkState l2 = new LinkState();
        l2.assocName = "Mother";
        l2.participants = List.of("f1", "m2");
        snapshot.links.put(l2.getIdentity(), l2);

        SnapshotIndex index = new SnapshotIndex(snapshot);

        // Match family with both father AND mother
        List<Map<String, String>> bindings = RuleBindingResolver.resolveBindings(
                Map.of("fm", "Family", "father", "Member", "mother", "Member"),
                List.of(new TggRuleInfo.AssociationPattern("fm", "father", "Father"),
                        new TggRuleInfo.AssociationPattern("fm", "mother", "Mother")),
                List.of(), index);

        assertEquals(1, bindings.size());
        assertEquals("f1", bindings.get(0).get("fm"));
        assertEquals("m1", bindings.get(0).get("father"));
        assertEquals("m2", bindings.get(0).get("mother"));
    }

    @Test
    void testBuildCorrKey() {
        // Use LinkedHashMap for deterministic order
        LinkedHashMap<String, String> binding = new LinkedHashMap<>();
        binding.put("src", "obj1");
        binding.put("tgt", "obj2");
        String key = RuleBindingResolver.buildCorrKey("RuleName", binding);
        assertEquals("RuleName_obj1_obj2", key);
    }

    @Test
    void testFindExistingSingleton_Found() {
        FullObjectSnapshot snapshot = new FullObjectSnapshot();
        snapshot.objects.put("pr1", makeObject("pr1", "PersonRegister", Map.of()));
        SnapshotIndex index = new SnapshotIndex(snapshot);

        assertEquals("pr1", RuleBindingResolver.findExistingSingleton("PersonRegister", index));
    }

    @Test
    void testFindExistingSingleton_NotSingletonClass() {
        FullObjectSnapshot snapshot = new FullObjectSnapshot();
        snapshot.objects.put("m1", makeObject("m1", "Male", Map.of()));
        SnapshotIndex index = new SnapshotIndex(snapshot);

        assertNull(RuleBindingResolver.findExistingSingleton("Male", index));
    }

    @Test
    void testEvaluatePredicate_Equals() {
        FullObjectSnapshot snapshot = new FullObjectSnapshot();
        snapshot.objects.put("obj1", makeObject("obj1", "Type", Map.of("status", "active")));
        SnapshotIndex index = new SnapshotIndex(snapshot);

        assertTrue(RuleBindingResolver.evaluatePredicate(
                "v.status = 'active'", Map.of("v", "obj1"), index));
        assertFalse(RuleBindingResolver.evaluatePredicate(
                "v.status = 'inactive'", Map.of("v", "obj1"), index));
    }

    @Test
    void testEvaluatePredicate_NotEquals() {
        FullObjectSnapshot snapshot = new FullObjectSnapshot();
        snapshot.objects.put("obj1", makeObject("obj1", "Type", Map.of("name", "Test")));
        SnapshotIndex index = new SnapshotIndex(snapshot);

        assertTrue(RuleBindingResolver.evaluatePredicate(
                "v.name <> Undefined", Map.of("v", "obj1"), index));
    }

    @Test
    void testEvaluatePredicate_EmptyString() {
        SnapshotIndex index = new SnapshotIndex(new FullObjectSnapshot());
        assertTrue(RuleBindingResolver.evaluatePredicate("", Map.of(), index));
        assertTrue(RuleBindingResolver.evaluatePredicate("  ", Map.of(), index));
    }

    private ObjectState makeObject(String name, String className, Map<String, Object> attributes) {
        ObjectState state = new ObjectState();
        state.name = name;
        state.className = className;
        state.primitiveValues = new LinkedHashMap<>(attributes);
        state.objectReferences = new LinkedHashMap<>();
        return state;
    }
}
