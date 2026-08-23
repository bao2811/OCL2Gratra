package org.uet.dse.neo4jtgg.ocl;

import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.mm.MModel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Audited finite UML generalization index used by certified type conformance.
 *
 * <p>The transitive closure is computed from {@link MClass#parents()} rather
 * than copied from {@link MClass#allParents()}. Construction then compares the
 * independently computed closure with USE's closure and fails if they differ.
 * This makes the Java class oracle consumed by the Lean conformance boundary
 * explicit and prevents two silently divergent inheritance semantics.</p>
 */
public final class UmlClassHierarchyIndex {
    private final Map<String, Set<String>> directParents;
    private final Map<String, Set<String>> allParents;

    private UmlClassHierarchyIndex(Map<String, Set<String>> directParents,
                                   Map<String, Set<String>> allParents) {
        this.directParents = immutableCopy(directParents);
        this.allParents = immutableCopy(allParents);
    }

    public static UmlClassHierarchyIndex fromModel(MModel model) {
        if (model == null) {
            throw new IllegalArgumentException("UML model is required");
        }

        Map<String, Set<String>> direct = new LinkedHashMap<>();
        Map<String, MClass> classes = new LinkedHashMap<>();
        for (MClass modelClass : model.classes()) {
            classes.put(modelClass.name(), modelClass);
            LinkedHashSet<String> parents = new LinkedHashSet<>();
            for (MClass parent : modelClass.parents()) {
                parents.add(parent.name());
            }
            direct.put(modelClass.name(), parents);
        }

        Map<String, Set<String>> closure = computeClosure(direct);

        for (Map.Entry<String, MClass> entry : classes.entrySet()) {
            LinkedHashSet<String> useClosure = new LinkedHashSet<>();
            for (MClass parent : entry.getValue().allParents()) {
                useClosure.add(parent.name());
            }
            Set<String> computed = closure.get(entry.getKey());
            if (!computed.equals(useClosure)) {
                throw new IllegalStateException("UML parent-closure mismatch for "
                        + entry.getKey() + ": direct-closure=" + computed
                        + ", USE-allParents=" + useClosure);
            }
        }
        return new UmlClassHierarchyIndex(direct, closure);
    }

    static Map<String, Set<String>> computeClosure(Map<String, Set<String>> directParents) {
        Map<String, Set<String>> closure = new LinkedHashMap<>();
        for (String className : directParents.keySet()) {
            computeAncestors(className, directParents, closure, new LinkedHashSet<>());
        }
        return immutableCopy(closure);
    }

    public boolean conformsTo(String actualClass, String declaredClass) {
        requireKnown(actualClass);
        requireKnown(declaredClass);
        return actualClass.equals(declaredClass) || allParents.get(actualClass).contains(declaredClass);
    }

    public Set<String> directParentsOf(String className) {
        requireKnown(className);
        return directParents.get(className);
    }

    public Set<String> allParentsOf(String className) {
        requireKnown(className);
        return allParents.get(className);
    }

    private void requireKnown(String className) {
        if (className == null || !directParents.containsKey(className)) {
            throw new IllegalArgumentException("Unknown UML class: " + className);
        }
    }

    private static Set<String> computeAncestors(String className,
                                                Map<String, Set<String>> direct,
                                                Map<String, Set<String>> memo,
                                                Set<String> visiting) {
        Set<String> cached = memo.get(className);
        if (cached != null) {
            return cached;
        }
        if (!visiting.add(className)) {
            throw new IllegalArgumentException("Cyclic UML generalization involving " + className);
        }

        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String parent : direct.getOrDefault(className, Set.of())) {
            if (!direct.containsKey(parent)) {
                throw new IllegalArgumentException("Unknown UML parent " + parent + " of " + className);
            }
            result.add(parent);
            result.addAll(computeAncestors(parent, direct, memo, visiting));
        }
        visiting.remove(className);
        Set<String> immutable = Collections.unmodifiableSet(new LinkedHashSet<>(result));
        memo.put(className, immutable);
        return immutable;
    }

    private static Map<String, Set<String>> immutableCopy(Map<String, Set<String>> source) {
        LinkedHashMap<String, Set<String>> copy = new LinkedHashMap<>();
        source.forEach((name, values) ->
                copy.put(name, Collections.unmodifiableSet(new LinkedHashSet<>(values))));
        return Collections.unmodifiableMap(copy);
    }
}
