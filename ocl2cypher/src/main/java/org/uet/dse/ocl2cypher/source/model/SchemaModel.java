package org.uet.dse.ocl2cypher.source.model;

import java.util.*;

/**
 * The finite source schema model {@code SM} at M1.
 *
 * <p>The three source abstraction levels are exactly
 * {@code UMLMM_{M2}, SM_{M1} : UMLMM, SN_{M0} : State(SM)}.
 * {@code SM} owns classes, attributes, associations, and qualifier
 * declarations; {@code SN} is checked to follow them.
 */
public final class SchemaModel {

    /** Direction and result metadata for one resolved UML association end. */
    public record Navigation(UmlAssociation association, boolean reverse,
                             String targetClassKey, int targetUpper, boolean unique,
                             String roleName) {
        public boolean toMany() {
            return targetUpper == -1 || targetUpper > 1;
        }
    }

    /** Resolution result for navigation from a participant to its link object. */
    public record AssociationClassNavigation(UmlAssociation association,
                                             UmlClass associationClass,
                                             boolean receiverIsTarget,
                                             int upper,
                                             boolean unique,
                                             String participantRole) {
        public boolean toMany() {
            return upper == -1 || upper > 1;
        }
    }

    private final String modelKey;
    private final Map<String, UmlClass> classes;
    private final Map<String, UmlAttribute> attributesByKey;
    private final Map<String, List<UmlAttribute>> attributesByOwner;
    private final Map<String, UmlAssociation> associationsByName;

    private SchemaModel(String modelKey,
                        Map<String, UmlClass> classes,
                        Map<String, UmlAttribute> attributesByKey,
                        Map<String, List<UmlAttribute>> attributesByOwner,
                        Map<String, UmlAssociation> associationsByName) {
        this.modelKey = Objects.requireNonNull(modelKey, "model key");
        // Keep declaration order observable.  Map.copyOf is immutable but does
        // not promise iteration order, which made graph construction and
        // serialized artifacts depend on a JDK-specific map layout.
        this.classes = Collections.unmodifiableMap(new LinkedHashMap<>(classes));
        this.attributesByKey = Collections.unmodifiableMap(new LinkedHashMap<>(attributesByKey));
        Map<String, List<UmlAttribute>> ownerCopy = new LinkedHashMap<>();
        attributesByOwner.forEach((owner, attrs) ->
                ownerCopy.put(owner, List.copyOf(attrs)));
        this.attributesByOwner = Collections.unmodifiableMap(ownerCopy);
        this.associationsByName = Collections.unmodifiableMap(new LinkedHashMap<>(associationsByName));
    }

    public static Builder builder(String modelKey) {
        return new Builder(modelKey);
    }

    public String modelKey() {
        return modelKey;
    }

    public Collection<UmlClass> classes() {
        return classes.values();
    }

    public UmlClass clazz(String key) {
        return classes.get(key);
    }

    public boolean hasClass(String key) {
        return classes.containsKey(key);
    }

    /** Reflective-transitive closure caller: true iff {@code sub ⊑* sup}. */
    public boolean conforms(String sub, String sup) {
        return conforms(sub, sup, new HashSet<>());
    }

    private boolean conforms(String sub, String sup, Set<String> visited) {
        if (Objects.equals(sub, sup)) {
            return true;
        }
        if (!visited.add(sub)) {
            return false;
        }
        UmlClass klass = classes.get(sub);
        if (klass == null) {
            return false;
        }
        for (String p : klass.directSuperclassKeys()) {
            if (conforms(p, sup, visited)) {
                return true;
            }
        }
        return false;
    }

    /** Returns true when the inheritance relation contains a directed cycle. */
    public boolean hasInheritanceCycle() {
        Set<String> visiting = new HashSet<>();
        Set<String> finished = new HashSet<>();
        for (String key : classes.keySet()) {
            if (hasCycleFrom(key, visiting, finished)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasCycleFrom(String key, Set<String> visiting, Set<String> finished) {
        if (finished.contains(key)) {
            return false;
        }
        if (!visiting.add(key)) {
            return true;
        }
        UmlClass klass = classes.get(key);
        if (klass != null) {
            for (String parent : klass.directSuperclassKeys()) {
                if (hasCycleFrom(parent, visiting, finished)) {
                    return true;
                }
            }
        }
        visiting.remove(key);
        finished.add(key);
        return false;
    }

    public UmlAttribute attribute(String ownerClass, String name) {
        List<UmlAttribute> byOwner = attributesByOwner.getOrDefault(ownerClass, List.of());
        for (UmlAttribute a : byOwner) {
            if (a.name().equals(name)) {
                return a;
            }
        }
        return null;
    }

    /** Lookup by stable declaration identity, e.g. {@code Person::age}. */
    public UmlAttribute attributeByKey(String key) {
        return attributesByKey.get(key);
    }

    /** Every declared attribute of the model, each owned by exactly one class. */
    public Collection<UmlAttribute> attributes() {
        return attributesByKey.values();
    }

    /** Attributes declared directly on {@code ownerClassKey} (no inherited lookup). */
    public List<UmlAttribute> ownAttributes(String ownerClassKey) {
        return List.copyOf(attributesByOwner.getOrDefault(ownerClassKey, List.of()));
    }

    /**
     * Legacy unscoped lookup for uniquely named link declarations. Ambiguous
     * roles fail explicitly; OCL property resolution must use navigation(receiver, role).
     */
    public UmlAssociation associationByRole(String role) {
        UmlAssociation found = null;
        for (UmlAssociation a : associationsByName.values()) {
            if (!role.equals(a.sourceRole()) && !role.equals(a.targetRole()) && !role.equals(a.name())) continue;
            if (found != null) throw new IllegalArgumentException("ambiguous unscoped association role: " + role);
            found = a;
        }
        return found;
    }

    public UmlAssociation associationByName(String name) {
        return associationsByName.get(name);
    }

    /**
     * Association classes use one stable UML identity for their classifier and
     * association views.  The Java model represents those views by a
     * {@link UmlClass} and {@link UmlAssociation} having the same key.
     */
    public UmlClass associationClassByName(String name) {
        UmlAssociation association = associationsByName.get(name);
        if (association == null) {
            association = associationsByName.values().stream()
                    .filter(a -> a.key().equals(name))
                    .findFirst().orElse(null);
        }
        if (association == null) return null;
        UmlClass classifier = classes.get(association.key());
        return classifier != null && classifier.isAssociationClass() ? classifier : null;
    }

    /** Resolve {@code participant.AssociationClassName} by receiver type. */
    public AssociationClassNavigation associationClassNavigation(String receiverClassKey,
                                                                  String name) {
        UmlClass classifier = associationClassByName(name);
        if (classifier == null || !hasClass(receiverClassKey)) return null;
        UmlAssociation association = associationsByName.values().stream()
                .filter(a -> a.key().equals(classifier.key()))
                .findFirst().orElse(null);
        if (association == null) return null;
        boolean atSource = conforms(receiverClassKey, association.sourceClassKey());
        boolean atTarget = conforms(receiverClassKey, association.targetClassKey());
        if (atSource == atTarget) {
            if (atSource) {
                throw new IllegalArgumentException("ambiguous association-class participant "
                        + receiverClassKey + "::" + name);
            }
            return null;
        }
        return atSource
                ? new AssociationClassNavigation(association, classifier, false,
                        association.targetUpper(), association.isUnique(), association.sourceRole())
                : new AssociationClassNavigation(association, classifier, true,
                        association.sourceUpper(), association.isUnique(), association.targetRole());
    }

    /** Every binary association declaration in deterministic schema order. */
    public Collection<UmlAssociation> associations() {
        return associationsByName.values();
    }

    /** Resolve a role from a receiver class, including reverse-end navigation. */
    public Navigation navigation(String receiverClassKey, String roleName) {
        if (!hasClass(receiverClassKey)) return null;
        Navigation found = null;
        for (UmlAssociation a : associationsByName.values()) {
            if (roleName.equals(a.targetRole()) && conforms(receiverClassKey, a.sourceClassKey())) {
                if (found != null) throw new IllegalArgumentException("ambiguous role " + receiverClassKey + "::" + roleName);
                found = new Navigation(a, false, a.targetClassKey(), a.targetUpper(), a.isUnique(), roleName);
            }
            if (roleName.equals(a.sourceRole()) && conforms(receiverClassKey, a.targetClassKey())) {
                if (found != null) throw new IllegalArgumentException("ambiguous role " + receiverClassKey + "::" + roleName);
                found = new Navigation(a, true, a.sourceClassKey(), a.sourceUpper(), a.isUnique(), roleName);
            }
        }
        return found;
    }

    public static final class Builder {
        private final String modelKey;
        private final Map<String, UmlClass> classes = new LinkedHashMap<>();
        private final Map<String, UmlAttribute> attributesByKey = new LinkedHashMap<>();
        private final Map<String, List<UmlAttribute>> attributesByOwner = new LinkedHashMap<>();
        private final Map<String, UmlAssociation> associationsByName = new LinkedHashMap<>();

        private Builder(String modelKey) {
            this.modelKey = modelKey;
        }

        public Builder clazz(UmlClass c) {
            Objects.requireNonNull(c, "class");
            if (classes.putIfAbsent(c.key(), c) != null) {
                throw new IllegalArgumentException("duplicate class declaration: " + c.key());
            }
            return this;
        }

        public Builder attribute(UmlAttribute a) {
            Objects.requireNonNull(a, "attribute");
            if (attributesByKey.containsKey(a.key())) {
                throw new IllegalArgumentException("duplicate attribute declaration: " + a.key());
            }
            List<UmlAttribute> ownerAttributes = attributesByOwner
                    .computeIfAbsent(a.ownerClassKey(), k -> new ArrayList<>());
            if (ownerAttributes.stream().anyMatch(existing -> existing.name().equals(a.name()))) {
                throw new IllegalArgumentException("duplicate attribute name on class "
                        + a.ownerClassKey() + ": " + a.name());
            }
            attributesByKey.put(a.key(), a);
            ownerAttributes.add(a);
            return this;
        }

        public Builder association(UmlAssociation a) {
            Objects.requireNonNull(a, "association");
            if (associationsByName.containsKey(a.name())) {
                throw new IllegalArgumentException("duplicate association declaration: " + a.name());
            }
            if (associationsByName.values().stream().anyMatch(existing -> existing.key().equals(a.key()))) {
                throw new IllegalArgumentException("duplicate association key: " + a.key());
            }
            // A source role is observed from the target class, and vice versa.
            // Reuse across different receivers is legal; duplicate declarations
            // on the same receiver are not. Inherited ambiguity is checked by navigation.
            for (UmlAssociation existing : associationsByName.values()) {
                if (sameRole(a.sourceClassKey(), a.targetRole(), existing.sourceClassKey(), existing.targetRole())
                        || sameRole(a.sourceClassKey(), a.targetRole(), existing.targetClassKey(), existing.sourceRole())
                        || sameRole(a.targetClassKey(), a.sourceRole(), existing.sourceClassKey(), existing.targetRole())
                        || sameRole(a.targetClassKey(), a.sourceRole(), existing.targetClassKey(), existing.sourceRole())) {
                    throw new IllegalArgumentException("duplicate association role on the same receiver: "
                            + existing.name() + " / " + a.name());
                }
            }
            associationsByName.put(a.name(), a);
            // Role names are scoped by receiver, not globally unique.
            return this;
        }

        public SchemaModel build() {
            SchemaModel model = new SchemaModel(modelKey, classes, attributesByKey,
                    attributesByOwner, associationsByName);
            for (UmlClass classifier : classes.values()) {
                if (!classifier.isAssociationClass()) continue;
                UmlAssociation association = associationsByName.values().stream()
                        .filter(a -> a.key().equals(classifier.key()))
                        .findFirst().orElse(null);
                if (association == null) {
                    throw new IllegalArgumentException("association-class classifier has no "
                            + "association view with the same key: " + classifier.key());
                }
                for (UmlClass candidate : classes.values()) {
                    if (!candidate.key().equals(classifier.key())
                            && model.conforms(candidate.key(), classifier.key())) {
                        throw new IllegalArgumentException("association-class classifier must "
                                + "be a leaf in the executable UML profile: "
                                + classifier.key() + " <- " + candidate.key());
                    }
                }
            }
            return model;
        }

        private static boolean sameRole(String receiver, String role, String otherReceiver, String otherRole) {
            return receiver.equals(otherReceiver) && role.equals(otherRole);
        }
    }
}
