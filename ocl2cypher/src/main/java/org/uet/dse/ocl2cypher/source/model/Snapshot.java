package org.uet.dse.ocl2cypher.source.model;

import java.util.*;
import org.uet.dse.ocl2cypher.runtime.OclValue;
import org.uet.dse.ocl2cypher.runtime.OclEquality;

/**
 * The finite source snapshot {@code SN} at M0: typed objects, attribute
 * bindings, and typed binary links. All carriers are finite and every object
 * has exactly one dynamic class. The model is in-memory and immutable.
 *
 * <p>The snapshot is checked to {@code conform} to one concrete
 * {@link SchemaModel}: attribute and link validity, a single dynamic class per
 * object, acyclic inheritance, and qualifier arity/type. The graph
 * construction pass then builds one unified {@code G} whose snapshot
 * projection mirrors this instance.
 */
public final class Snapshot {

    public static final class ObjectDef {
        public final String stableId;
        public final String dynamicClassKey;

        public ObjectDef(String stableId, String dynamicClassKey) {
            this.stableId = Objects.requireNonNull(stableId, "stableId");
            this.dynamicClassKey = Objects.requireNonNull(dynamicClassKey, "dynamicClass");
        }

        public String stableId() {
            return stableId;
        }

        public String dynamicClassKey() {
            return dynamicClassKey;
        }
    }

    public static final class LinkDef {
        public final String associationName;
        public final String sourceStableId;
        public final String targetStableId;
        public final List<QualifierValue> qualifiers;
        /** Source identity of the link object; null for an ordinary association link. */
        public final String associationClassObjectStableId;

        public LinkDef(String associationName, String sourceStableId, String targetStableId,
                       List<QualifierValue> qualifiers) {
            this(associationName, sourceStableId, targetStableId, qualifiers, null);
        }

        public LinkDef(String associationName, String sourceStableId, String targetStableId,
                       List<QualifierValue> qualifiers,
                       String associationClassObjectStableId) {
            this.associationName = Objects.requireNonNull(associationName, "assoc");
            this.sourceStableId = Objects.requireNonNull(sourceStableId, "source");
            this.targetStableId = Objects.requireNonNull(targetStableId, "target");
            this.qualifiers = List.copyOf(Objects.requireNonNull(qualifiers, "qualifiers"));
            this.associationClassObjectStableId = associationClassObjectStableId;
        }

        public LinkDef(String associationName, String sourceStableId, String targetStableId) {
            this(associationName, sourceStableId, targetStableId, List.of());
        }
    }

    private final Map<String, ObjectDef> objects;
    private final Map<String, Map<String, OclValue>> attributeSlots;
    private final List<LinkDef> links;

    private Snapshot(Map<String, ObjectDef> objects,
                     Map<String, Map<String, OclValue>> attributeSlots,
                     List<LinkDef> links) {
        // Preserve source declaration order while exposing immutable views;
        // this keeps generated graph artifacts reproducible across runs.
        this.objects = Collections.unmodifiableMap(new LinkedHashMap<>(objects));
        Map<String, Map<String, OclValue>> slotsCopy = new LinkedHashMap<>();
        objects.keySet().forEach(k -> slotsCopy.put(k,
                Collections.unmodifiableMap(new LinkedHashMap<>(
                        attributeSlots.getOrDefault(k, Map.of())))));
        this.attributeSlots = Collections.unmodifiableMap(slotsCopy);
        this.links = List.copyOf(links);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Collection<ObjectDef> objects() {
        return objects.values();
    }

    /** {@code |Inst_SN(C)|}: identity via {@code stableId} where {@code dyn(o) ⊑* C}. */
    public List<String> objectsOfClass(SchemaModel sm, String classKey) {
        List<String> r = new ArrayList<>();
        for (ObjectDef o : objects.values()) {
            if (sm.conforms(o.dynamicClassKey, classKey)) {
                r.add(o.stableId);
            }
        }
        return Collections.unmodifiableList(r);
    }

    public boolean hasObject(String stableId) {
        return objects.containsKey(stableId);
    }

    public ObjectDef object(String stableId) {
        return Objects.requireNonNull(objects.get(stableId), "no such object " + stableId);
    }

    /**
     * Missing scalar slot denotes no stored property; the interpreter then
     * answers the declared typed bottom, not an empty string or native null.
     */
    public Optional<OclValue> attributeSlot(String stableId, String attrName) {
        return Optional.ofNullable(
                attributeSlots.getOrDefault(stableId, Map.of()).get(attrName));
    }

    /** Immutable slot view used by source well-formedness and ValidRep checks. */
    public Map<String, OclValue> attributeSlots(String stableId) {
        return attributeSlots.getOrDefault(stableId, Map.of());
    }

    public List<LinkDef> links() {
        return links;
    }

    public List<String> linkTargets(SchemaModel sm, String role, String sourceStableId) {
        return linkTargets(sm, role, sourceStableId, false, List.of());
    }

    public List<String> linkTargets(SchemaModel sm, String role, String sourceStableId,
                                    boolean reverse, List<OclValue> qualifierValues) {
        var object = object(sourceStableId);
        var navigation = sm.navigation(object.dynamicClassKey, role);
        if (navigation == null || navigation.reverse() != reverse) {
            throw new IllegalArgumentException("no association for role " + role);
        }
        var assoc = navigation.association();
        // An empty qualifier list is the low-level observer's wildcard read;
        // OCL admission enforces explicit arity for qualified navigation.
        if (!qualifierValues.isEmpty()
                && qualifierValues.size() != assoc.qualifierNames().size()) {
            return List.of();
        }
        List<String> r = new ArrayList<>();
        for (LinkDef l : links) {
            boolean endpoint = reverse
                    ? l.targetStableId.equals(sourceStableId)
                    : l.sourceStableId.equals(sourceStableId);
            UmlAssociation linkAssociation = sm.associationByName(l.associationName);
            if (linkAssociation == null) {
                linkAssociation = sm.associationByRole(l.associationName);
            }
            if (linkAssociation == null || !linkAssociation.key().equals(assoc.key()) || !endpoint) {
                continue;
            }
            if (!qualifierValues.isEmpty()) {
                boolean matches = l.qualifiers.size() == qualifierValues.size();
                for (int i = 0; matches && i < qualifierValues.size(); i++) {
                    matches = l.qualifiers.get(i).name().equals(assoc.qualifierNames().get(i));
                    Object raw = l.qualifiers.get(i).value();
                    OclValue expected = qualifierValues.get(i);
                    matches = raw instanceof OclValue ov
                            ? org.uet.dse.ocl2cypher.runtime.OclEquality.equal(ov, expected)
                                    == org.uet.dse.ocl2cypher.runtime.OclEquality.BoolKind.TRUE
                            : String.valueOf(raw).equals(qualifierText(expected));
                }
                if (!matches) {
                    continue;
                }
            }
            r.add(reverse ? l.sourceStableId : l.targetStableId);
        }
        return Collections.unmodifiableList(r);
    }

    /** Source observer for participant-to-association-class-object navigation. */
    public List<String> associationClassObjects(SchemaModel sm, String associationClassName,
                                                String participantStableId,
                                                List<OclValue> qualifierValues) {
        var navigation = sm.associationClassNavigation(
                object(participantStableId).dynamicClassKey, associationClassName);
        if (navigation == null) {
            throw new IllegalArgumentException("no association-class navigation "
                    + associationClassName);
        }
        if (!qualifierValues.isEmpty()
                && qualifierValues.size() != navigation.association().qualifierNames().size()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (LinkDef link : links) {
            if (link.associationClassObjectStableId == null) continue;
            UmlAssociation association = sm.associationByName(link.associationName);
            if (association == null || !association.key().equals(
                    navigation.association().key())) continue;
            String participant = navigation.receiverIsTarget()
                    ? link.targetStableId : link.sourceStableId;
            if (!participantStableId.equals(participant)) continue;
            if (!qualifierValues.isEmpty()) {
                boolean matches = link.qualifiers.size() == qualifierValues.size();
                for (int i = 0; matches && i < qualifierValues.size(); i++) {
                    Object raw = link.qualifiers.get(i).value();
                    matches = raw instanceof OclValue value
                            && OclEquality.equal(value, qualifierValues.get(i))
                                    == OclEquality.BoolKind.TRUE;
                }
                if (!matches) continue;
            }
            result.add(link.associationClassObjectStableId);
        }
        return Collections.unmodifiableList(result);
    }

    private static String qualifierText(OclValue value) {
        if (value instanceof OclValue.IntegerValue i) return i.value().toString();
        if (value instanceof OclValue.RealValue r) return r.value().toPlainString();
        if (value instanceof OclValue.StringValue s) return s.value();
        if (value instanceof OclValue.BooleanValue b) return Boolean.toString(
                b.bool() == OclValue.BooleanValue.Bool3.TRUE);
        return String.valueOf(value);
    }

    public static final class Builder {
        private final Map<String, ObjectDef> objects = new LinkedHashMap<>();
        private final Map<String, Map<String, OclValue>> slots = new LinkedHashMap<>();
        private final List<LinkDef> links = new ArrayList<>();

        public Builder object(String stableId, String dynamicClass) {
            if (objects.containsKey(stableId)) {
                throw new IllegalArgumentException("duplicate object identity: " + stableId);
            }
            objects.put(stableId, new ObjectDef(stableId, dynamicClass));
            slots.computeIfAbsent(stableId, k -> new LinkedHashMap<>());
            return this;
        }

        public Builder attribute(String stableId, String attrName, OclValue value) {
            if (!objects.containsKey(stableId)) {
                throw new IllegalArgumentException("attribute owner is not an object: " + stableId);
            }
            Map<String, OclValue> objectSlots = slots.computeIfAbsent(
                    stableId, k -> new LinkedHashMap<>());
            if (objectSlots.containsKey(attrName)) {
                throw new IllegalArgumentException("duplicate attribute slot: "
                        + stableId + "." + attrName);
            }
            objectSlots.put(attrName, Objects.requireNonNull(value, "attribute value"));
            return this;
        }

        public Builder link(String associationName, String sourceStableId, String targetStableId) {
            if (!objects.containsKey(sourceStableId) || !objects.containsKey(targetStableId)) {
                throw new IllegalArgumentException("link endpoint is not an object: "
                        + sourceStableId + " -> " + targetStableId);
            }
            links.add(new LinkDef(associationName, sourceStableId, targetStableId));
            return this;
        }

        public Builder link(String associationName, String sourceStableId, String targetStableId,
                            List<QualifierValue> qualifiers) {
            if (!objects.containsKey(sourceStableId) || !objects.containsKey(targetStableId)) {
                throw new IllegalArgumentException("link endpoint is not an object: "
                        + sourceStableId + " -> " + targetStableId);
            }
            links.add(new LinkDef(associationName, sourceStableId, targetStableId, qualifiers));
            return this;
        }

        /** Bind one association-class object identity to exactly one link occurrence. */
        public Builder associationClassLink(String objectStableId, String associationName,
                                            String sourceStableId, String targetStableId) {
            return associationClassLink(objectStableId, associationName, sourceStableId,
                    targetStableId, List.of());
        }

        public Builder associationClassLink(String objectStableId, String associationName,
                                            String sourceStableId, String targetStableId,
                                            List<QualifierValue> qualifiers) {
            if (!objects.containsKey(objectStableId)) {
                throw new IllegalArgumentException("association-class occurrence is not an object: "
                        + objectStableId);
            }
            if (!objects.containsKey(sourceStableId) || !objects.containsKey(targetStableId)) {
                throw new IllegalArgumentException("association-class participant is not an object: "
                        + sourceStableId + " -> " + targetStableId);
            }
            if (links.stream().anyMatch(link -> objectStableId.equals(
                    link.associationClassObjectStableId))) {
                throw new IllegalArgumentException("association-class object has multiple links: "
                        + objectStableId);
            }
            links.add(new LinkDef(associationName, sourceStableId, targetStableId,
                    qualifiers, objectStableId));
            return this;
        }

        public Snapshot build() {
            return new Snapshot(objects, slots, links);
        }
    }
}
