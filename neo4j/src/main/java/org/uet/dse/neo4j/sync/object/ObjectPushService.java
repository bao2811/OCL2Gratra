package org.uet.dse.neo4j.sync.object;

import org.neo4j.driver.Session;
import org.neo4j.driver.TransactionContext;
import org.tzi.use.uml.mm.MAssociationEnd;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.*;
import org.uet.dse.neo4j.helper.ValueMapper;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.uet.dse.neo4j.manager.WorkLogManager;
import org.uet.dse.neo4j.repo.Neo4jObjectRepository;
import org.uet.dse.neo4j.sync.helper.QualifierValueCodec;
import org.uet.dse.neo4j.sync.helper.UmlTypeTranslator;

import java.util.*;
import java.util.stream.Stream;

public class ObjectPushService {
    private final Neo4jObjectRepository objectRepository;
    private final MSystem system;

    public ObjectPushService(Neo4jObjectRepository objectRepository, MSystem system) {
        this.objectRepository = objectRepository;
        this.system = system;
    }

    public void pushToNeo4j(ObjectDiff diff) {
        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            session.executeWrite(tx -> {
                deleteStaleObjects(tx, diff.neo4jOnlyObjects);
                deleteStaleLinks(tx, diff.neo4jOnlyLinks);
                pushModifiedObjects(tx, diff);
                pushAllLinks(tx);
                return null;
            });

            updateObjectVersionOnServer();
            WorkLogManager.getInstance().log("SYNC", "Object & Link sync completed.");
        }
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
        for (String name : objectNames) {
            objectRepository.deleteObjectDeeply(tx, name);
            WorkLogManager.getInstance().log("CLEANUP", "Deep deleted object: " + name);
        }
    }

    private void deleteStaleLinks(TransactionContext tx, Collection<String> linkNames) {
        for (String name : linkNames) {
            tx.run("MATCH ()-[r {name: $name}]-() WHERE type(r) STARTS WITH 'Link' DELETE r",
                    Map.of("name", name));
            tx.run("MATCH (h:LinkHub {name: $name}) DETACH DELETE h",
                    Map.of("name", name));
        }
    }
    private void pushModifiedObjects(TransactionContext tx, ObjectDiff diff) {
        MSystemState state = system.state();

        Stream.concat(diff.javaOnlyObjects.stream(), diff.mismatchedObjects.stream())
                .map(state::objectByName)
                .forEach(obj -> pushValidatedObject(tx, obj, state));
    }

    private void pushValidatedObject(TransactionContext tx, MObject obj, MSystemState state) {
        String className = obj.cls().name();
        if (!objectRepository.checkClassExistsInDb(tx, className)) {
            throw new RuntimeException("Class '" + className + "' missing in DB. Push Model first.");
        }
        pushObject(tx, obj, state);
    }

    private void pushAllLinks(TransactionContext tx) {
        system.state().allLinks().forEach(link -> pushLink(tx, link));
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

        objectRepository.upsertBinaryLink(tx, s.name(), t.name(), link.association().name(),
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

        objectRepository.upsertObjectNode(tx, objName, clsName);

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

            objectRepository.setAttributeValueNode(tx, objName, ownerClassName, attr.name(), mappedValue, metadata);
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
