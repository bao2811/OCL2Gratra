package org.uet.dse.neo4jtgg.experiment;

import org.neo4j.driver.QueryRunner;
import org.tzi.use.uml.mm.MAssociationEnd;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.sys.MLink;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;
import org.uet.dse.neo4j.encoding.CanonicalGraphEncoding;
import org.uet.dse.neo4j.helper.ValueMapper;
import org.uet.dse.neo4j.sync.helper.QualifierValueCodec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Independent source/graph observation adapters used by the PO-21 runtime gate. */
public final class AdapterAdequacySnapshotReader {
    private AdapterAdequacySnapshotReader() {
    }

    public static RepresentationAdequacyEvaluator.Snapshot source(MSystem system, String modelName) {
        if (system == null || modelName == null || modelName.isBlank()
                || !modelName.equals(system.model().name())) {
            throw new IllegalArgumentException("The source system must own the certified model " + modelName);
        }
        Map<String, Integer> objects = new LinkedHashMap<>();
        List<RepresentationAdequacyEvaluator.ObjectObservation> objectObservations = new ArrayList<>();
        Set<RepresentationAdequacyEvaluator.TypeFact> types = new LinkedHashSet<>();
        Set<RepresentationAdequacyEvaluator.AttributeFact> attributes = new LinkedHashSet<>();
        List<RepresentationAdequacyEvaluator.AttributeObservation> attributeObservations = new ArrayList<>();
        Set<RepresentationAdequacyEvaluator.LinkFact> links = new LinkedHashSet<>();
        List<RepresentationAdequacyEvaluator.LinkObservation> linkObservations = new ArrayList<>();
        Map<String, Set<String>> keys = sourceKeys(system.model(), modelName);

        var state = system.state();
        for (MObject object : state.allObjects()) {
            objects.put(object.name(), 1);
            objectObservations.add(new RepresentationAdequacyEvaluator.ObjectObservation(
                    "source:" + object.name(), object.name(),
                    CanonicalGraphEncoding.objectKey(modelName, object.name())));
            types.add(new RepresentationAdequacyEvaluator.TypeFact(object.name(),
                    CanonicalGraphEncoding.classKey(modelName, object.cls().name())));
            object.cls().allParents().forEach(parent -> types.add(
                    new RepresentationAdequacyEvaluator.TypeFact(object.name(),
                            CanonicalGraphEncoding.classKey(modelName, parent.name()))));
            for (MAttribute attribute : object.cls().allAttributes()) {
                Object mapped = ValueMapper.mapUseValue(object.state(state).attributeValue(attribute));
                String attributeKey = CanonicalGraphEncoding.attributeKey(
                        modelName, attribute.owner().name(), attribute.name());
                String payload = expectedStoredPayload(mapped);
                attributes.add(new RepresentationAdequacyEvaluator.AttributeFact(
                        object.name(), attributeKey, payload));
                attributeObservations.add(new RepresentationAdequacyEvaluator.AttributeObservation(
                        "source:" + object.name() + ":" + attributeKey,
                        object.name(), attributeKey, payload,
                        CanonicalGraphEncoding.attributeSlotKey(
                                modelName, object.name(), attribute.owner().name(), attribute.name())));
            }
        }
        for (MLink link : state.allLinks()) {
            if (link.linkedObjects().size() != 2) continue;
            MAssociationEnd sourceEnd = link.association().associationEnds().get(0);
            MAssociationEnd targetEnd = link.association().associationEnds().get(1);
            List<List<String>> qualifiers = new ArrayList<>();
            link.getQualifier().forEach(values -> qualifiers.add(
                    QualifierValueCodec.encodeQualifierValues(values)));
            while (qualifiers.size() < 2) qualifiers.add(List.of());
            String associationKey = CanonicalGraphEncoding.associationKey(modelName, link.association().name());
            String sourceId = link.linkedObjects().get(0).name();
            String targetId = link.linkedObjects().get(1).name();
            links.add(new RepresentationAdequacyEvaluator.LinkFact(
                    associationKey, sourceId, targetId, sourceEnd.name(), targetEnd.name(),
                    qualifiers.get(0), qualifiers.get(1)));
            linkObservations.add(new RepresentationAdequacyEvaluator.LinkObservation(
                    "source:" + link,
                    CanonicalGraphEncoding.binaryLinkKey(modelName, link.association().name(),
                            sourceId, targetId, qualifiers.get(0), qualifiers.get(1)),
                    associationKey, sourceId, targetId, sourceEnd.name(), targetEnd.name(),
                    qualifiers.get(0), qualifiers.get(1)));
        }
        return new RepresentationAdequacyEvaluator.Snapshot(
                objects, types, attributes, links, keys, 0, objectObservations,
                attributeObservations, linkObservations);
    }

    public static RepresentationAdequacyEvaluator.Snapshot graph(QueryRunner runner, String modelKey) {
        if (runner == null || modelKey == null || modelKey.isBlank()) {
            throw new IllegalArgumentException("A graph runner and modelKey are required");
        }
        Map<String, Integer> objects = new LinkedHashMap<>();
        List<RepresentationAdequacyEvaluator.ObjectObservation> objectObservations = new ArrayList<>();
        Set<RepresentationAdequacyEvaluator.TypeFact> types = new LinkedHashSet<>();
        Set<RepresentationAdequacyEvaluator.AttributeFact> attributes = new LinkedHashSet<>();
        List<RepresentationAdequacyEvaluator.AttributeObservation> attributeObservations = new ArrayList<>();
        Set<RepresentationAdequacyEvaluator.LinkFact> links = new LinkedHashSet<>();
        List<RepresentationAdequacyEvaluator.LinkObservation> linkObservations = new ArrayList<>();
        Set<RepresentationAdequacyEvaluator.AllInstancesFact> allInstances = new LinkedHashSet<>();
        Map<String, Set<String>> keys = new LinkedHashMap<>();
        Map<String, Object> parameters = Map.of("modelKey", modelKey);

        runner.run("MATCH (o:Object {modelKey:$modelKey}) "
                        + "RETURN elementId(o) AS token, o.use_id AS id, o.objectKey AS objectKey", parameters)
                .forEachRemaining(record -> {
                    String id = record.get("id").isNull() ? null : record.get("id").asString();
                    String objectKey = record.get("objectKey").isNull()
                            ? null : record.get("objectKey").asString();
                    objectObservations.add(new RepresentationAdequacyEvaluator.ObjectObservation(
                            record.get("token").asString(), id, objectKey));
                    if (id != null && !id.isBlank()) objects.merge(id, 1, Integer::sum);
                });
        runner.run("MATCH (o:Object {modelKey:$modelKey})-[:ObjectInstanceOf]->(c:UmlClass) "
                        + "RETURN o.use_id AS id, c.classKey AS classKey", parameters)
                .forEachRemaining(record -> types.add(new RepresentationAdequacyEvaluator.TypeFact(
                        record.get("id").asString(), record.get("classKey").asString())));
        runner.run("MATCH (o:Object)-[:ObjectInstanceOf]->(c:UmlClass {modelKey:$modelKey}) "
                        + "WHERE c.classKey IS NOT NULL RETURN DISTINCT c.classKey AS classKey, o.use_id AS id",
                        parameters)
                .forEachRemaining(record -> allInstances.add(
                        new RepresentationAdequacyEvaluator.AllInstancesFact(
                                record.get("classKey").asString(), record.get("id").asString())));
        runner.run("MATCH (o:Object {modelKey:$modelKey})-[:ObjectHasAttribute]->(v:AttributeValue) "
                        + "RETURN elementId(v) AS token, o.use_id AS id, v.attributeKey AS attributeKey, "
                        + "v.value AS value, v.slotKey AS slotKey", parameters)
                .forEachRemaining(record -> {
                    String id = record.get("id").isNull() ? null : record.get("id").asString();
                    String attributeKey = record.get("attributeKey").isNull()
                            ? null : record.get("attributeKey").asString();
                    String value = record.get("value").isNull() ? null : record.get("value").asString();
                    String slotKey = record.get("slotKey").isNull() ? null : record.get("slotKey").asString();
                    attributeObservations.add(new RepresentationAdequacyEvaluator.AttributeObservation(
                            record.get("token").asString(), id, attributeKey, value, slotKey));
                    if (id != null && attributeKey != null && value != null) {
                        attributes.add(new RepresentationAdequacyEvaluator.AttributeFact(id, attributeKey, value));
                    }
                });

        long linkRows = 0;
        var linkResult = runner.run("MATCH (a:Object {modelKey:$modelKey})-[r]->"
                        + "(b:Object {modelKey:$modelKey}) WHERE type(r) STARTS WITH 'Link' "
                        + "RETURN elementId(r) AS token,r.linkKey AS linkKey,"
                        + "r.associationKey AS associationKey, a.use_id AS source, b.use_id AS target, "
                        + "r.sourceRole AS sourceRole, r.targetRole AS targetRole, "
                        + "r.sourceQualifiers AS sourceQualifiers,r.targetQualifiers AS targetQualifiers",
                parameters);
        while (linkResult.hasNext()) {
            var record = linkResult.next();
            linkRows++;
            String associationKey = nullableString(record, "associationKey");
            String source = nullableString(record, "source");
            String target = nullableString(record, "target");
            String sourceRole = nullableString(record, "sourceRole");
            String targetRole = nullableString(record, "targetRole");
            List<String> sourceQualifiers = nullableStrings(record, "sourceQualifiers");
            List<String> targetQualifiers = nullableStrings(record, "targetQualifiers");
            linkObservations.add(new RepresentationAdequacyEvaluator.LinkObservation(
                    record.get("token").asString(), nullableString(record, "linkKey"), associationKey,
                    source, target, sourceRole, targetRole, sourceQualifiers, targetQualifiers));
            if (associationKey != null && source != null && target != null
                    && sourceRole != null && targetRole != null
                    && sourceQualifiers != null && targetQualifiers != null) {
                links.add(new RepresentationAdequacyEvaluator.LinkFact(
                        associationKey, source, target, sourceRole, targetRole,
                        sourceQualifiers, targetQualifiers));
            }
        }
        collectKeyOwners(runner, "MATCH (n:UmlClass {modelKey:$modelKey}) WHERE n.classKey IS NOT NULL "
                + "RETURN n.classKey AS key, n.name AS owner", parameters, keys);
        collectKeyOwners(runner, "MATCH (n:Attribute {modelKey:$modelKey}) WHERE n.attributeKey IS NOT NULL "
                + "RETURN n.attributeKey AS key, n.name AS owner", parameters, keys);
        collectKeyOwners(runner, "MATCH (a {modelKey:$modelKey})-[r]->(b {modelKey:$modelKey}) "
                + "WHERE r.modelKey=$modelKey AND r.associationKey IS NOT NULL "
                + "AND NOT type(r) STARTS WITH 'Link' "
                + "RETURN r.associationKey AS key, r.associationName AS owner", parameters, keys);
        return new RepresentationAdequacyEvaluator.Snapshot(objects, types, attributes, links, keys,
                linkRows - links.size(), objectObservations, attributeObservations,
                linkObservations, allInstances);
    }

    private static String nullableString(org.neo4j.driver.Record record, String name) {
        return record.get(name).isNull() ? null : record.get(name).asString();
    }

    private static List<String> nullableStrings(org.neo4j.driver.Record record, String name) {
        return record.get(name).isNull() ? null
                : record.get(name).asList(value -> value.isNull() ? null : value.asString());
    }

    private static void collectKeyOwners(QueryRunner runner, String query, Map<String, Object> parameters,
                                         Map<String, Set<String>> target) {
        runner.run(query, parameters).forEachRemaining(record -> target
                .computeIfAbsent(record.get("key").asString(), ignored -> new LinkedHashSet<>())
                .add(record.get("owner").asString()));
    }

    private static Map<String, Set<String>> sourceKeys(MModel model, String modelName) {
        Map<String, Set<String>> keys = new LinkedHashMap<>();
        model.classes().forEach(cls -> {
            keys.put(CanonicalGraphEncoding.classKey(modelName, cls.name()), Set.of(cls.name()));
            cls.attributes().forEach(attribute -> keys.put(
                    CanonicalGraphEncoding.attributeKey(modelName, cls.name(), attribute.name()),
                    Set.of(cls.name() + "_" + attribute.name())));
        });
        model.associations().forEach(association -> keys.put(
                CanonicalGraphEncoding.associationKey(modelName, association.name()),
                Set.of(association.name())));
        return Map.copyOf(keys);
    }

    private static String expectedStoredPayload(Object value) {
        if (value == null || "Undefined".equals(value)) return "Undefined";
        if (value instanceof Map<?, ?> collection) {
            Object rawItems = collection.get("items");
            List<?> items = rawItems instanceof List<?> list ? list : List.of();
            if (items.isEmpty()) return "COLLECTION_EMPTY";
            return items.stream().map(item -> item == null ? "null" : item.toString())
                    .collect(Collectors.joining(" | "));
        }
        return value.toString();
    }
}
