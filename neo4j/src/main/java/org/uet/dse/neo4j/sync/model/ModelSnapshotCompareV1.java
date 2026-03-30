package org.uet.dse.neo4j.sync.model;

import org.tzi.use.uml.mm.MModel;
import org.uet.dse.neo4j.model.ClassState;
import org.uet.dse.neo4j.model.FullModelSnapshot;

import java.util.*;
import java.util.stream.Collectors;

//must fixing bug of missing in checking invariants, operations
public class ModelSnapshotCompareV1 implements ModelSnapshotsAnalyzer {
    private MModel useModel;

    public ModelSnapshotCompareV1(MModel useModel) {
        this.useModel = useModel;
    }

    public ModelDiff compareWithNeo4j(String modelName) {
        Neo4jModelSnapshotUtils neo4JModelSnapshotUtils = new Neo4jModelSnapshotUtils();
        USEModelSnapshotUtils useModelSnapshotUtils = new USEModelSnapshotUtils(useModel);
        FullModelSnapshot neoSnap = neo4JModelSnapshotUtils.getFullModelSnapshotFromNeo4j(modelName);
        FullModelSnapshot javaSnap = useModelSnapshotUtils.getFullModelSnapshotFromUSE();

        return compareWithNeo4j(neoSnap, javaSnap);
    }

    public ModelDiff compareWithNeo4j(FullModelSnapshot neoSnap, FullModelSnapshot javaSnap) {
        ModelDiff diff = new ModelDiff();
        compareClasses(javaSnap, neoSnap, diff);
        compareAssociations(javaSnap, neoSnap, diff);

        return diff;
    }


    private void compareClasses(FullModelSnapshot javaSnap, FullModelSnapshot neoSnap, ModelDiff diff) {
        diffKeys(
                javaSnap.classes.keySet(),
                neoSnap.classes.keySet(),
                diff.javaOnlyClasses,
                diff.neo4jOnlyClasses
        );

        for (String className : sharedKeys(javaSnap.classes, neoSnap.classes)) {
            ClassState useCls = javaSnap.classes.get(className);
            ClassState n4jCls = neoSnap.classes.get(className);

            if (!useCls.isSameAs(n4jCls)) {
                diff.mismatchedClasses.add(className);
                compareAttributes(className, useCls, n4jCls, diff);
            }
        }
    }

    private void compareAttributes(String clsName, ClassState useCls, ClassState n4jCls, ModelDiff diff) {
        List<String> javaOnly  = new ArrayList<>();
        List<String> neo4jOnly = new ArrayList<>();

        diffKeys(
                useCls.getAttributes().keySet(),
                n4jCls.getAttributes().keySet(),
                javaOnly,
                neo4jOnly
        );

        if (!javaOnly.isEmpty())  diff.javaOnlyAttributes.put(clsName, javaOnly);
        if (!neo4jOnly.isEmpty()) diff.neo4jOnlyAttributes.put(clsName, neo4jOnly);
    }


    private void compareAssociations(FullModelSnapshot javaSnap, FullModelSnapshot neoSnap, ModelDiff diff) {
        diffKeys(
                javaSnap.associations.keySet(),
                neoSnap.associations.keySet(),
                diff.javaOnlyAssociations,
                diff.neo4jOnlyAssociations
        );

        for (String name : sharedKeys(javaSnap.associations, neoSnap.associations)) {
            if (!javaSnap.associations.get(name).isSameAs(neoSnap.associations.get(name))) {
                diff.mismatchedAssociations.add(name);
            }
        }
    }


    /**
     * Partitions two key-sets into left-only and right-only buckets.
     * Shared keys are ignored here — callers handle them separately.
     */
    private static void diffKeys(
            Set<String> leftKeys,
            Set<String> rightKeys,
            Collection<String> leftOnly,
            Collection<String> rightOnly) {

        leftKeys .stream().filter(k -> !rightKeys.contains(k)).forEach(leftOnly::add);
        rightKeys.stream().filter(k -> !leftKeys.contains(k)).forEach(rightOnly::add);
    }

    /**
     * Returns keys that exist in both maps — the intersection.
     */
    private static <V> Set<String> sharedKeys(Map<String, V> left, Map<String, V> right) {
        return left.keySet().stream()
                .filter(right::containsKey)
                .collect(Collectors.toSet());
    }
}
