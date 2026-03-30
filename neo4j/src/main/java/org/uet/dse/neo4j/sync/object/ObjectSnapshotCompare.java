package org.uet.dse.neo4j.sync.object;

import org.tzi.use.uml.sys.MSystem;
import org.uet.dse.neo4j.model.FullObjectSnapshot;
import org.uet.dse.neo4j.model.ObjectState;

import java.util.*;
import java.util.stream.Collectors;

public class ObjectSnapshotCompare {
    private MSystem system;

    public ObjectSnapshotCompare(MSystem system) {
        this.system = system;
    }

    public ObjectDiff compareObjects() {
        Neo4jObjectSnapshotUtils neo4jObjectSnapshotUtils = new Neo4jObjectSnapshotUtils();
        USEObjectSnapshotUtils useObjectSnapshotUtils = new USEObjectSnapshotUtils(system);
        FullObjectSnapshot javaSnap = useObjectSnapshotUtils.getJavaObjectSnapshot();
        FullObjectSnapshot neoSnap = neo4jObjectSnapshotUtils.getNeo4jObjectSnapshot(system.model().name());
        ObjectDiff diff = new ObjectDiff();
        diff.javaSnapshot  = javaSnap;
        diff.neo4jSnapshot = neoSnap;

        compareObjects(javaSnap, neoSnap, diff);
        compareLinks(javaSnap, neoSnap, diff);

        return diff;
    }

    public ObjectDiff compareObjects(FullObjectSnapshot javaSnap, FullObjectSnapshot neoSnap) {
        ObjectDiff diff = new ObjectDiff();
        diff.javaSnapshot  = javaSnap;
        diff.neo4jSnapshot = neoSnap;

        compareObjects(javaSnap, neoSnap, diff);
        compareLinks(javaSnap, neoSnap, diff);

        return diff;
    }

    private void compareObjects(FullObjectSnapshot javaSnap, FullObjectSnapshot neoSnap, ObjectDiff diff) {
        diffKeys(
                javaSnap.objects.keySet(),
                neoSnap.objects.keySet(),
                diff.javaOnlyObjects,
                diff.neo4jOnlyObjects
        );

        for (String name : sharedKeys(javaSnap.objects, neoSnap.objects)) {
            ObjectState javaObj = javaSnap.objects.get(name);
            ObjectState neoObj  = neoSnap.objects.get(name);

            if (!javaObj.isSameAs(neoObj)) {
                diff.mismatchedObjects.add(name);
                diff.mismatchDetails.put(name, findObjectMismatchDetails(javaObj, neoObj));
            }
        }
    }

    private void compareLinks(FullObjectSnapshot javaSnap, FullObjectSnapshot neoSnap, ObjectDiff diff) {
        diffKeys(
                javaSnap.links.keySet(),
                neoSnap.links.keySet(),
                diff.javaOnlyLinks,
                diff.neo4jOnlyLinks
        );

        for (String identity : sharedKeys(javaSnap.links, neoSnap.links)) {
            if (!javaSnap.links.get(identity).isSameAs(neoSnap.links.get(identity))) {
                diff.mismatchedLinks.add(identity);
            }
        }
    }

    /**
     * Partitions two key-sets into left-only and right-only buckets.
     * Shared keys are intentionally excluded — callers handle them separately.
     */
    public static void diffKeys(
            Set<String> leftKeys,
            Set<String> rightKeys,
            Collection<String> leftOnly,
            Collection<String> rightOnly) {

        leftKeys .stream().filter(k -> !rightKeys.contains(k)).forEach(leftOnly::add);
        rightKeys.stream().filter(k -> !leftKeys.contains(k)).forEach(rightOnly::add);
    }

    /**
     * Returns the intersection of two maps' key-sets.
     */
    public static <V> Set<String> sharedKeys(Map<String, V> left, Map<String, V> right) {
        return left.keySet().stream()
                .filter(right::containsKey)
                .collect(Collectors.toSet());
    }
    private List<String> findObjectMismatchDetails(ObjectState javaObj, ObjectState neoObj) {
        List<String> details = new ArrayList<>();
        Set<String> allAttrKeys = new HashSet<>(javaObj.primitiveValues.keySet());
        allAttrKeys.addAll(neoObj.primitiveValues.keySet());

        for (String attr : allAttrKeys) {
            Object v1 = javaObj.primitiveValues.get(attr);
            Object v2 = neoObj.primitiveValues.get(attr);
            if (!javaObj.isEqualValue(v1, v2)) {
                details.add(String.format("Attr '%s' mismatch: Java(%s) vs DB(%s)",
                    attr, formatVal(v1), formatVal(v2)));
            }
        }

        Set<String> allRefKeys = new HashSet<>(javaObj.objectReferences.keySet());
        allRefKeys.addAll(neoObj.objectReferences.keySet());

        for (String role : allRefKeys) {
            Object r1 = javaObj.objectReferences.get(role);
            Object r2 = neoObj.objectReferences.get(role);
            if (!javaObj.isEqualValue(r1, r2)) {
                details.add(String.format("Ref '%s' mismatch: Java(%s) vs DB(%s)",
                    role, formatVal(r1), formatVal(r2)));
            }
        }

        return details;
    }

    private String formatVal(Object val) {
        if (val == null) return "null";
        if (val instanceof List) {
            return "[" + ((List<?>) val).stream().map(Object::toString).collect(java.util.stream.Collectors.joining(", ")) + "]";
        }
        return val.toString();
    }
    private List<String> findObjectMismatchDetailsold(ObjectState javaObj, ObjectState neoObj) {
        List<String> details = new ArrayList<>();

        diffPrimitiveValues(javaObj, neoObj, details);
        diffObjectReferences(javaObj, neoObj, details);

        return details;
    }

    private void diffPrimitiveValues(ObjectState javaObj, ObjectState neoObj, List<String> details) {
        collectValueMismatches(
                javaObj.primitiveValues,
                neoObj.primitiveValues,
                (attr, jVal, nVal) -> String.format("Attr '%s' mismatch: Java(%s) vs DB(%s)", attr, jVal, nVal),
                details
        );
    }

    private void diffObjectReferences(ObjectState javaObj, ObjectState neoObj, List<String> details) {
        collectValueMismatches(
                javaObj.objectReferences,
                neoObj.objectReferences,
                (role, jVal, nVal) -> String.format("Reference '%s' has different targets: Java(%s) vs DB(%s)", role, jVal, nVal),
                details
        );
    }

    /**
     * Unions the keys of both maps and reports any key whose stringified values differ.
     * Null values on either side are represented as the literal "null" for readability.
     *
     * @param messageFormatter (key, leftValue, rightValue) → human-readable detail string
     */
    private static <V> void collectValueMismatches(
            Map<String, V> left,
            Map<String, V> right,
            TriFunction<String, String, String, String> messageFormatter,
            List<String> details) {

        Set<String> allKeys = unionKeys(left, right);

        for (String key : allKeys) {
            String leftStr  = stringify(left.get(key));
            String rightStr = stringify(right.get(key));

            if (!leftStr.equals(rightStr)) {
                details.add(messageFormatter.apply(key, leftStr, rightStr));
            }
        }
    }

    private static <V> Set<String> unionKeys(Map<String, V> left, Map<String, V> right) {
        Set<String> keys = new HashSet<>(left.keySet());
        keys.addAll(right.keySet());
        return keys;
    }

    private static String stringify(Object value) {
        return (value == null) ? "null" : value.toString();
    }

    @FunctionalInterface
    private interface TriFunction<A, B, C, R> {
        R apply(A a, B b, C c);
    }

}
