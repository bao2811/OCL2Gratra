package org.uet.dse.neo4jtgg.ocl.ir;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.uet.dse.neo4j.encoding.CanonicalGraphEncoding;
import org.uet.dse.neo4j.encoding.CanonicalGraphVocabulary;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Full dynamic-EMF PGMM instance to Java encoding conformance check. */
public final class CanonicalPgmmEmfConformance {
    private CanonicalPgmmEmfConformance() {
    }

    public static Report check(Path ecore, Path instance) throws IOException {
        DynamicEmfModelValidator.LoadedModel loaded = DynamicEmfModelValidator.load(ecore, instance);
        List<String> errors = new ArrayList<>();
        loaded.issues().forEach(issue -> errors.add(issue.code() + "@" + issue.path() + ": " + issue.message()));
        EObject root = loaded.root();
        if (root == null) return new Report(loaded, errors);
        require(root, "profileId", CanonicalGraphEncoding.PROFILE_ID, errors);
        EObject scope = child(root, "modelScope");
        require(scope, "keyDelimiter", "::", errors);

        Map<String, EObject> nodes = index(list(root, "nodeTypes"), "name", errors);
        requireNode(nodes, "Object", Set.of("Object"), Set.of("modelKey", "use_id", "objectKey"), errors);
        requireNode(nodes, "UmlClass", Set.of("UmlClass"), Set.of("modelKey", "classKey"), errors);
        requireNode(nodes, "AttributeValue", Set.of("AttributeValue"),
                Set.of("modelKey", "attributeKey", "value"), errors);

        Map<String, EObject> relationships = index(list(root, "relationshipTypes"), "name", errors);
        requireRelationship(relationships, CanonicalGraphVocabulary.OBJECT_INSTANCE_OF,
                CanonicalGraphVocabulary.OBJECT_INSTANCE_OF, "Object", "UmlClass", errors);
        requireRelationship(relationships, "ObjectHasAttribute", "ObjectHasAttribute",
                "Object", "AttributeValue", errors);
        requireRelationship(relationships, "Link", "Link", "Object", "Object", errors);

        Map<String, EObject> keys = index(list(root, "keyDefinitions"), "name", errors);
        requireKey(keys, "objectKey", "Object", List.of("modelKey", "use_id"), errors);
        requireKey(keys, "classKey", "UmlClass", List.of("modelKey", "classKey"), errors);

        Map<String, EObject> codecs = index(list(root, "codecDefinitions"), "codecId", errors);
        EObject scalar = codecs.get("scalar-v1");
        require(scalar, "bottomToken", "v1|V", errors);
        require(scalar, "storageType", "STRING", errors);
        require(scalar, "injective", "true", errors);

        Set<String> accessors = list(root, "accessorDefinitions").stream()
                .map(accessor -> string(accessor, "kind")).collect(Collectors.toSet());
        Set<String> requiredAccessors = Set.of("CONTEXT", "ALL_INSTANCES", "ATTRIBUTE", "NAVIGATION",
                "OBJECT_IDENTITY");
        if (!accessors.containsAll(requiredAccessors)) {
            errors.add("Missing accessors " + difference(requiredAccessors, accessors));
        }
        for (EObject accessor : list(root, "accessorDefinitions")) {
            if (!Boolean.parseBoolean(string(accessor, "bottomSafe"))) {
                errors.add("Accessor is not bottom-safe: " + string(accessor, "name"));
            }
        }

        Set<String> constraints = list(root, "wfConstraints").stream()
                .map(item -> string(item, "constraintId")).collect(Collectors.toSet());
        if (!constraints.containsAll(Set.of("PGMM-WF-MODEL-SCOPE", "PGMM-WF-BOTTOM"))) {
            errors.add("Missing PGMM well-formedness constraints");
        }
        return new Report(loaded, errors);
    }

    private static void requireNode(Map<String, EObject> nodes, String name, Set<String> labels,
                                    Set<String> properties, List<String> errors) {
        EObject node = nodes.get(name);
        if (node == null) {
            errors.add("Missing node type " + name);
            return;
        }
        Set<String> actualLabels = strings(node, "labels");
        if (!actualLabels.equals(labels)) errors.add(name + " labels expected " + labels + " but were " + actualLabels);
        Set<String> actualProperties = list(node, "properties").stream()
                .map(property -> string(property, "physicalName")).collect(Collectors.toSet());
        if (!actualProperties.containsAll(properties)) {
            errors.add(name + " missing properties " + difference(properties, actualProperties));
        }
    }

    private static void requireRelationship(Map<String, EObject> relationships, String name, String physicalType,
                                            String sourceNode, String targetNode, List<String> errors) {
        EObject relationship = relationships.get(name);
        if (relationship == null) {
            errors.add("Missing relationship type " + name);
            return;
        }
        if (!strings(relationship, "physicalTypes").contains(physicalType)) {
            errors.add(name + " missing physical type " + physicalType);
        }
        requireEndpoint(child(relationship, "sourceEnd"), sourceNode, name + ".sourceEnd", errors);
        requireEndpoint(child(relationship, "targetEnd"), targetNode, name + ".targetEnd", errors);
        require(relationship, "direction", "OUTGOING", errors);
        require(relationship, "setSemantics", "true", errors);
    }

    private static void requireEndpoint(EObject endpoint, String nodeName, String path, List<String> errors) {
        EObject node = child(endpoint, "nodeType");
        if (node == null || !nodeName.equals(string(node, "name"))) {
            errors.add(path + " expected node " + nodeName);
        }
    }

    private static void requireKey(Map<String, EObject> keys, String name, String owner,
                                   List<String> components, List<String> errors) {
        EObject key = keys.get(name);
        if (key == null) {
            errors.add("Missing key " + name);
            return;
        }
        EObject ownerType = child(key, "ownerType");
        if (ownerType == null || !owner.equals(string(ownerType, "name"))) errors.add(name + " has wrong owner");
        List<String> actual = list(key, "components").stream()
                .map(component -> string(component, "physicalName")).toList();
        if (!actual.equals(components)) errors.add(name + " components expected " + components + " but were " + actual);
        require(key, "unique", "true", errors);
        require(key, "modelScoped", "true", errors);
    }

    private static Map<String, EObject> index(List<EObject> values, String key, List<String> errors) {
        Map<String, EObject> result = new LinkedHashMap<>();
        for (EObject value : values) {
            String name = string(value, key);
            if (name == null || result.put(name, value) != null) errors.add("Duplicate/missing " + key + ": " + name);
        }
        return result;
    }

    private static Set<String> difference(Set<String> expected, Set<String> actual) {
        return expected.stream().filter(value -> !actual.contains(value)).collect(Collectors.toSet());
    }

    private static void require(EObject object, String feature, String expected, List<String> errors) {
        String actual = string(object, feature);
        if (!expected.equals(actual)) errors.add(feature + " expected " + expected + " but was " + actual);
    }

    private static EObject child(EObject object, String name) {
        if (object == null) return null;
        EStructuralFeature feature = object.eClass().getEStructuralFeature(name);
        Object value = feature == null ? null : object.eGet(feature);
        return value instanceof EObject child ? child : null;
    }

    @SuppressWarnings("unchecked")
    private static List<EObject> list(EObject object, String name) {
        if (object == null) return List.of();
        EStructuralFeature feature = object.eClass().getEStructuralFeature(name);
        return feature == null ? List.of() : (List<EObject>) object.eGet(feature);
    }

    private static Set<String> strings(EObject object, String name) {
        if (object == null) return Set.of();
        EStructuralFeature feature = object.eClass().getEStructuralFeature(name);
        Object value = feature == null ? null : object.eGet(feature);
        if (value instanceof Collection<?> values) {
            return values.stream().map(Object::toString).collect(Collectors.toSet());
        }
        return value == null ? Set.of() : Set.of(value.toString());
    }

    private static String string(EObject object, String name) {
        if (object == null) return null;
        EStructuralFeature feature = object.eClass().getEStructuralFeature(name);
        Object value = feature == null ? null : object.eGet(feature);
        return value == null ? null : value.toString();
    }

    public record Report(DynamicEmfModelValidator.LoadedModel model, List<String> errors) {
        public Report {
            errors = List.copyOf(errors);
        }

        public boolean valid() {
            return errors.isEmpty();
        }
    }
}
