package org.uet.dse.neo4j.sync.object;

import org.neo4j.driver.Session;
import org.neo4j.driver.TransactionContext;
import org.tzi.use.uml.mm.MAssociationEnd;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.*;
import org.uet.dse.neo4j.helper.ValueMapper;
import org.uet.dse.neo4j.encoding.CanonicalGraphEncoding;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.uet.dse.neo4j.manager.WorkLogManager;
import org.uet.dse.neo4j.model.LinkState;
import org.uet.dse.neo4j.model.ObjectState;
import org.uet.dse.neo4j.repo.Neo4jObjectRepository;
import org.uet.dse.neo4j.sync.helper.QualifierValueCodec;
import org.uet.dse.neo4j.sync.helper.UmlTypeTranslator;

import java.util.*;
import java.util.stream.Collectors;

public class ObjectPushService {
    private final Neo4jObjectRepository objectRepository;
    private final MSystem system;

    public ObjectPushService(Neo4jObjectRepository objectRepository, MSystem system) {
        this.objectRepository = objectRepository;
        this.system = system;
    }

    public void pushToNeo4j(ObjectDiff diff) {
        long objectTimestamp = System.currentTimeMillis();
        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            session.executeWrite(tx -> {
                deleteStaleObjects(tx, diff.neo4jOnlyObjects);
                deleteStaleLinks(tx, diff.neo4jOnlyLinks);
                pushModifiedObjects(tx, diff);
                pushChangedLinks(tx, diff);
                updateObjectVersion(tx, objectTimestamp);
                return null;
            });

            ObjectSyncCoordinator.updateLocalSyncTimestamp(objectTimestamp);
            WorkLogManager.getInstance().log("SYNC", "Object & Link sync completed.");
        }
    }

    private static void updateObjectVersion(TransactionContext tx, long timestamp) {
        String cypher = "MERGE (v:ModelVersion {id: 'CURRENT'}) SET v.objectTimestamp = $ts";
        tx.run(cypher, Map.of("ts", timestamp));
    }

    public static void updateObjectVersionOnServer() {
        long ts = System.currentTimeMillis();

        try (org.neo4j.driver.Session session = Neo4jDriverManager.getInstance().openSession()) {
            String cypher = "MERGE (v:ModelVersion {id: 'CURRENT'}) SET v.objectTimestamp = $ts";
            session.run(cypher, org.neo4j.driver.Values.parameters("ts", ts));

            ObjectSyncCoordinator.updateLocalSyncTimestamp(ts);
        }
    }

    private void deleteStaleObjects(TransactionContext tx, Collection<String> objectNames) {
        objectRepository.deleteObjectsDeeplyBatch(tx, system.model().name(), objectNames);
        if (!objectNames.isEmpty()) {
            WorkLogManager.getInstance().log("CLEANUP", "Deep deleted objects: " + objectNames.size());
        }
    }

    private void deleteStaleLinks(TransactionContext tx, Collection<String> linkNames) {
        objectRepository.deleteLinksBatch(tx, system.model().name(), linkNames);
        if (!linkNames.isEmpty()) {
            tx.run("MATCH (h:LinkHub) WHERE h.name IN $names OR h.use_id IN $names DETACH DELETE h",
                    Map.of("names", linkNames));
        }
    }

    private void pushModifiedObjects(TransactionContext tx, ObjectDiff diff) {
        MSystemState state = system.state();
        List<MObject> objects = new ArrayList<>();
        LinkedHashSet<String> names = new LinkedHashSet<>(diff.javaOnlyObjects);
        names.addAll(diff.mismatchedObjects);
        for (String name : names) {
            MObject object = state.objectByName(name);
            if (object != null) objects.add(object);
        }
        Set<String> classes = objects.stream().map(object -> object.cls().name()).collect(Collectors.toSet());
        if (!objectRepository.checkClassesExist(tx, system.model().name(), classes)) {
            throw new RuntimeException("One or more M1 classifiers are missing in Neo4j M2: " + classes);
        }

        objects.stream().collect(Collectors.groupingBy(object -> object.cls().name()))
                .forEach((className, classObjects) -> {
                    List<Map<String, Object>> rows = classObjects.stream().map(this::objectRow).toList();
                    objectRepository.upsertObjectNodesBatch(tx, system.model().name(), className, rows);
                });

        List<Map<String, Object>> scalarRows = new ArrayList<>();
        for (MObject object : objects) {
            Set<String> changedAttributes = changedAttributes(object, diff);
            for (org.tzi.use.uml.mm.MAttribute attribute : object.cls().allAttributes()) {
                if (!changedAttributes.contains(attribute.name())) continue;
                Value useValue = object.state(state).attributeValue(attribute);
                Object mappedValue = ValueMapper.mapUseValue(useValue);
                org.tzi.use.uml.ocl.type.Type type = attribute.type();
                boolean objectReference = isObjectReferenceType(type);
                boolean nested = UmlTypeTranslator.getCollectionDepth(type) > 1;
                Map<String, Object> metadata = attributeMetadata(attribute, objectReference, nested);
                if (!objectReference && !nested) {
                    scalarRows.add(scalarAttributeRow(object, attribute, mappedValue, metadata));
                } else {
                    objectRepository.setAttributeValueNode(tx, system.model().name(), object.name(),
                            attribute.owner().name(), attribute.name(), mappedValue, metadata);
                }
            }
        }
        objectRepository.setScalarAttributeValuesBatch(tx, system.model().name(), scalarRows);
    }

    private Map<String, Object> objectRow(MObject object) {
        List<String> conformingClasses = new ArrayList<>();
        conformingClasses.add(object.cls().name());
        object.cls().allParents().forEach(parent -> conformingClasses.add(parent.name()));
        return Map.of(
                "objName", object.name(),
                "objectKey", CanonicalGraphEncoding.objectKey(system.model().name(), object.name()),
                "runtimeClassKey", CanonicalGraphEncoding.classKey(
                        system.model().name(), object.cls().name()),
                "classKeys", conformingClasses.stream()
                        .map(name -> CanonicalGraphEncoding.classKey(system.model().name(), name)).toList());
    }

    private Set<String> changedAttributes(MObject object, ObjectDiff diff) {
        if (diff.javaOnlyObjects.contains(object.name())) {
            return object.cls().allAttributes().stream().map(attribute -> attribute.name())
                    .collect(Collectors.toSet());
        }
        ObjectState current = diff.javaSnapshot.objects.get(object.name());
        ObjectState stored = diff.neo4jSnapshot.objects.get(object.name());
        if (current == null || stored == null || !Objects.equals(current.className, stored.className)) {
            return object.cls().allAttributes().stream().map(attribute -> attribute.name())
                    .collect(Collectors.toSet());
        }
        Set<String> changed = new HashSet<>();
        for (org.tzi.use.uml.mm.MAttribute attribute : object.cls().allAttributes()) {
            Object left = current.primitiveValues.containsKey(attribute.name())
                    ? current.primitiveValues.get(attribute.name()) : current.objectReferences.get(attribute.name());
            Object right = stored.primitiveValues.containsKey(attribute.name())
                    ? stored.primitiveValues.get(attribute.name()) : stored.objectReferences.get(attribute.name());
            if (!current.isEqualValue(left, right)) changed.add(attribute.name());
        }
        return changed;
    }

    private Map<String, Object> attributeMetadata(org.tzi.use.uml.mm.MAttribute attribute,
                                                   boolean objectReference, boolean nested) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("type", UmlTypeTranslator.toDatabaseType(attribute.type()));
        metadata.put("isCollection", attribute.type().isKindOfCollection(
                org.tzi.use.uml.ocl.type.Type.VoidHandling.EXCLUDE_VOID));
        metadata.put("collectionType", UmlTypeTranslator.getCollectionType(attribute.type()));
        metadata.put("isNestedCollection", nested);
        metadata.put("isObjectReference", objectReference);
        return metadata;
    }

    private Map<String, Object> scalarAttributeRow(MObject object,
                                                    org.tzi.use.uml.mm.MAttribute attribute,
                                                    Object value, Map<String, Object> metadata) {
        boolean collection = (boolean) metadata.get("isCollection");
        Object storedValue = "Undefined";
        if (value != null && !"Undefined".equals(value)) {
            if (collection && value instanceof Map<?, ?> map) {
                Object rawItems = map.get("items");
                List<?> items = rawItems instanceof List<?> list ? list : List.of();
                storedValue = items.isEmpty() ? "COLLECTION_EMPTY" : items.stream()
                        .map(item -> item == null ? "null" : item.toString())
                        .collect(Collectors.joining(" | "));
            } else {
                // Canonical scalar payloads are unquoted strings. The renderer
                // applies the static OCL type when reading them back.
                storedValue = value.toString();
            }
        }
        String modelName = system.model().name();
        return Map.of(
                "objectKey", CanonicalGraphEncoding.objectKey(modelName, object.name()),
                "attributeKey", CanonicalGraphEncoding.attributeKey(
                        modelName, attribute.owner().name(), attribute.name()),
                "slotKey", CanonicalGraphEncoding.attributeSlotKey(
                        modelName, object.name(), attribute.owner().name(), attribute.name()),
                "valId", object.name() + "_" + attribute.name(),
                "type", metadata.get("type"),
                "value", storedValue,
                "isCollection", collection,
                "collectionType", metadata.get("collectionType"));
    }

    private void pushChangedLinks(TransactionContext tx, ObjectDiff diff) {
        Set<String> changed = new LinkedHashSet<>(diff.javaOnlyLinks);
        changed.addAll(diff.mismatchedLinks);
        if (changed.isEmpty()) return;

        Map<String, MLink> linksByIdentity = new LinkedHashMap<>();
        for (MLink link : system.state().allLinks()) linksByIdentity.put(linkIdentity(link), link);
        List<MLink> fallback = new ArrayList<>();
        Map<String, List<Map<String, Object>>> rowsByLabel = new LinkedHashMap<>();
        for (String identity : changed) {
            MLink link = linksByIdentity.get(identity);
            if (link == null) continue;
            if (link instanceof MLinkObject || link.linkedObjects().size() > 2) {
                fallback.add(link);
            } else {
                String label = binaryLinkLabel(link);
                rowsByLabel.computeIfAbsent(label, ignored -> new ArrayList<>())
                        .add(binaryLinkRow(link));
            }
        }
        rowsByLabel.forEach((label, rows) -> objectRepository.upsertBinaryLinksBatch(
                tx, system.model().name(), label, rows));
        fallback.forEach(link -> pushLink(tx, link));
    }

    private String linkIdentity(MLink link) {
        List<String> participants = link.linkedObjects().stream().map(MObject::name).toList();
        List<List<String>> qualifiers = new ArrayList<>();
        for (List<Value> values : link.getQualifier()) {
            qualifiers.add(QualifierValueCodec.encodeQualifierValues(values));
        }
        String linkObjectName = link instanceof MLinkObject object ? object.name() : null;
        return LinkState.buildIdentity(link.association().name(), participants, qualifiers, linkObjectName);
    }

    private String binaryLinkLabel(MLink link) {
        int maxKind = link.association().associationEnds().stream()
                .mapToInt(MAssociationEnd::aggregationKind).max().orElse(0);
        return maxKind == 2 ? "LinkComposeOf" : maxKind == 1 ? "LinkAggregates" : "LinkAssociateWith";
    }

    private Map<String, Object> binaryLinkRow(MLink link) {
        MAssociationEnd source = link.association().associationEnds().get(0);
        MAssociationEnd target = link.association().associationEnds().get(1);
        List<List<String>> qualifiers = new ArrayList<>();
        for (List<Value> values : link.getQualifier()) {
            qualifiers.add(QualifierValueCodec.encodeQualifierValues(values));
        }
        while (qualifiers.size() < 2) qualifiers.add(List.of());
        String modelName = system.model().name();
        return Map.of(
                "sourceKey", CanonicalGraphEncoding.objectKey(modelName, link.linkedObjects().get(0).name()),
                "targetKey", CanonicalGraphEncoding.objectKey(modelName, link.linkedObjects().get(1).name()),
                "associationKey", CanonicalGraphEncoding.associationKey(modelName, link.association().name()),
                "linkKey", CanonicalGraphEncoding.binaryLinkKey(modelName, link.association().name(),
                        link.linkedObjects().get(0).name(), link.linkedObjects().get(1).name(),
                        qualifiers.get(0), qualifiers.get(1)),
                "name", link.association().name(),
                "sourceRole", source.name(),
                "targetRole", target.name(),
                "sourceQualifiers", qualifiers.get(0),
                "targetQualifiers", qualifiers.get(1));
    }

    private void pushLink(TransactionContext tx, MLink link) {
        if (link instanceof MLinkObject) {
            processPushLinkObject(tx, (MLinkObject) link);
        } else if (link.linkedObjects().size() > 2) {
            processPushTernaryLink(tx, link);
        } else {
            processPushBinaryLink(tx, link);
        }
    }

    private void processPushBinaryLink(TransactionContext tx, MLink link) {
        MObject s = link.linkedObjects().get(0);
        MObject t = link.linkedObjects().get(1);
        MAssociationEnd endS = link.association().associationEnds().get(0);
        MAssociationEnd endT = link.association().associationEnds().get(1);

        String label = "LinkAssociateWith";
        if (endT.aggregationKind() == 2 || endS.aggregationKind() == 2) label = "LinkComposeOf";
        else if (endT.aggregationKind() == 1 || endS.aggregationKind() == 1) label = "LinkAggregates";

        List<List<String>> qualifierValues = new ArrayList<>();
        for (List<Value> endQualifiers : link.getQualifier()) {
            qualifierValues.add(QualifierValueCodec.encodeQualifierValues(endQualifiers));
        }
        while (qualifierValues.size() < 2) {
            qualifierValues.add(List.of());
        }

        objectRepository.upsertBinaryLink(tx, system.model().name(), s.name(), t.name(), link.association().name(),
                label, endS.name(), endT.name(), qualifierValues.get(0), qualifierValues.get(1));
    }

    private void processPushTernaryLink(TransactionContext tx, MLink link) {
        List<Map<String, Object>> participants = new ArrayList<>();
        for (int i = 0; i < link.linkedObjects().size(); i++) {
            MObject obj = link.linkedObjects().get(i);
            MAssociationEnd end = link.association().associationEnds().get(i);

            Map<String, Object> p = new HashMap<>();
            p.put("objName", obj.name());
            p.put("role", end.name());
            p.put("index", i);
            p.put("label", "LinkAssociateWith");
            participants.add(p);
        }
        objectRepository.upsertTernaryLink(tx, link.association().name(), participants);
    }

    private void processPushLinkObject(TransactionContext tx, MLinkObject lo) {
        List<Map<String, Object>> participants = new ArrayList<>();
        for (int i = 0; i < lo.linkedObjects().size(); i++) {
            Map<String, Object> p = new HashMap<>();
            p.put("objName", lo.linkedObjects().get(i).name());
            p.put("role", lo.association().associationEnds().get(i).name());
            p.put("label", "LinkAssociateWith");
            participants.add(p);
        }

        objectRepository.upsertLinkObject(tx, lo.name(), lo.cls().name(), participants);
    }

    public void pushObject(TransactionContext tx, MObject obj, MSystemState state) {
        String objName = obj.name();
        String clsName = obj.cls().name();

        List<String> conformingClasses = new ArrayList<>();
        conformingClasses.add(clsName);
        obj.cls().allParents().forEach(parent -> conformingClasses.add(parent.name()));
        objectRepository.upsertObjectNode(tx, system.model().name(), objName, clsName, conformingClasses);

        for (org.tzi.use.uml.mm.MAttribute attr : obj.cls().allAttributes()) {
            Value useVal = obj.state(state).attributeValue(attr);
            String ownerClassName = attr.owner().name();
            org.tzi.use.uml.ocl.type.Type type = attr.type();
            boolean isObjRef = isObjectReferenceType(type);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("type", UmlTypeTranslator.toDatabaseType(attr.type()));
            metadata.put("isCollection", attr.type().isKindOfCollection(org.tzi.use.uml.ocl.type.Type.VoidHandling.EXCLUDE_VOID));
            metadata.put("collectionType", UmlTypeTranslator.getCollectionType(attr.type()));
            metadata.put("isNestedCollection", UmlTypeTranslator.getCollectionDepth(attr.type()) > 1);
            metadata.put("isObjectReference", isObjRef);



            Object mappedValue = ValueMapper.mapUseValue(useVal);

            objectRepository.setAttributeValueNode(tx, system.model().name(), objName, ownerClassName,
                    attr.name(), mappedValue, metadata);
        }
    }

    private boolean isObjectReferenceType(org.tzi.use.uml.ocl.type.Type type) {
        if (type.isKindOfClass(org.tzi.use.uml.ocl.type.Type.VoidHandling.EXCLUDE_VOID)) return true;

        if (type.isKindOfCollection(org.tzi.use.uml.ocl.type.Type.VoidHandling.EXCLUDE_VOID)) {
            return isObjectReferenceType(((org.tzi.use.uml.ocl.type.CollectionType) type).elemType());
        }

        return false;
    }


}
