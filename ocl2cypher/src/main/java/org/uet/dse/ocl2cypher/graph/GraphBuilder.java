package org.uet.dse.ocl2cypher.graph;

import java.util.*;
import org.uet.dse.ocl2cypher.diagnostics.Result;
import org.uet.dse.ocl2cypher.diagnostics.Stage;
import org.uet.dse.ocl2cypher.source.model.SchemaModel;
import org.uet.dse.ocl2cypher.source.model.Snapshot;

/**
 * {@code F_G^{T_MM}}: schema/snapshot to a single {@link GraphModel}.
 *
 * <p>The construction is parameterized by the witness
 * {@code M = rules(T_MM)} in the simple but exact sense that the four
 * partitions are built in tandem from one catalog: the schema fragment from
 * {@code SM}, the snapshot fragment from {@code SN} linked by the typing
 * fragment, and no node/relationship has a type outside the catalog.
 */
public final class GraphBuilder {

    private GraphBuilder() {
    }

    /** One build outcome whose {@code correspondence} is already the {@code ValidRep} witness {@code mu}. */
    public record GraphBuildArtifact(GraphModel graph,
                                     Map<String, String> correspondence,
                                     MetamodelTranslator.Catalogue catalogue) {
        public GraphBuildArtifact {
            Objects.requireNonNull(graph);
            correspondence = Map.copyOf(correspondence);
            Objects.requireNonNull(catalogue);
        }
    }

    public static Result<GraphBuildArtifact> build(SchemaModel sm, Snapshot sn) {
        Result<MetamodelTranslator.Catalogue> translated =
                MetamodelTranslator.translate(sm);
        if (translated.isFailure()) {
            return Result.failure(translated.primaryDiagnostic());
        }
        MetamodelTranslator.Catalogue catalogue = translated.value();
        MetamodelMapping mapping = catalogue.mapping();
        String modelKey = sm.modelKey();
        GraphModel g = new GraphModel(modelKey);
        Map<String, String> mu = new LinkedHashMap<>();

        // G_repository
        var modelNode = mapping.node(MetamodelMapping.NodeKind.MODEL);
        g.addNode(new GraphModel.Node(GraphKey.model(modelKey), modelKey,
                modelNode.projection(), modelNode.observationRole(),
                modelNode.labels(), Map.of("modelKey", modelKey)));

        // G_schema: classes, attribute declarations with MIME-controlled typing,
        // and generalization. The caller there relies solely on one closed
        // catalogue; the construction there relies solely on that
        // designated witness binding.
        var generalization = mapping.relationship(
                MetamodelMapping.RelationshipKind.GENERALIZATION);
        for (var c : sm.classes()) {
            var classBinding = catalogue.clazz(c.key());
            g.addNode(new GraphModel.Node(GraphKey.clazz(modelKey, c.key()), modelKey,
                    classBinding.declarationNode().projection(),
                    classBinding.declarationNode().observationRole(),
                    classBinding.declarationNode().labels(),
                    Map.of("modelKey", modelKey,
                            "classKey", c.key(),
                            "qualifiedName", c.qualifiedName(),
                            "isAbstract", String.valueOf(c.isAbstract()),
                            "isAssociationClass", String.valueOf(c.isAssociationClass()))));
        }
        for (var c : sm.classes()) {
            for (String sup : c.directSuperclassKeys()) {
                g.addRelationship(new GraphModel.Relationship(
                        GraphKey.of(modelKey, GraphKey.Kind.GENERALIZATION,
                                c.key(), sup), modelKey,
                        generalization.projection(), generalization.physicalType(),
                        GraphKey.clazz(modelKey, c.key()), GraphKey.clazz(modelKey, sup),
                        Map.of("modelKey", modelKey)));
            }
            for (var attr : sm.ownAttributes(c.key())) {
                var attribute = catalogue.attribute(attr.key());
                var declarationNode = attribute.declarationNode();
                g.addNode(new GraphModel.Node(GraphKey.attribute(modelKey, attr.key()), modelKey,
                        declarationNode.projection(), declarationNode.observationRole(),
                        declarationNode.labels(), Map.of("modelKey", modelKey,
                                "attributeKey", attr.key(),
                                "ownerClassKey", attr.ownerClassKey(),
                                "name", attr.name(),
                                "declaredType", attr.declaredType().toString())));
                g.addRelationship(new GraphModel.Relationship(
                        GraphKey.of(modelKey, GraphKey.Kind.CLASS_ATTRIBUTE,
                                c.key(), attr.key()), modelKey,
                        attribute.ownership().projection(),
                        attribute.ownership().physicalType(),
                        GraphKey.clazz(modelKey, c.key()),
                        GraphKey.attribute(modelKey, attr.key()), Map.of("modelKey", modelKey)));
            }
        }

        // Schema-level association declarations and their canonically ordered
        // member ends are instantiated from the T_MM catalogue. Link
        // occurrences below refer back through associationKey metadata.
        var associationNode = mapping.node(MetamodelMapping.NodeKind.ASSOCIATION);
        var sourceEnd = mapping.relationship(
                MetamodelMapping.RelationshipKind.ASSOCIATION_SOURCE_END);
        var targetEnd = mapping.relationship(
                MetamodelMapping.RelationshipKind.ASSOCIATION_TARGET_END);
        for (var association : sm.associations()) {
            var binding = catalogue.association(association.key());
            String associationNodeKey = GraphKey.association(modelKey, association.key());
            g.addNode(new GraphModel.Node(associationNodeKey, modelKey,
                    associationNode.projection(), associationNode.observationRole(),
                    associationNode.labels(), Map.of(
                            "modelKey", modelKey,
                            "associationKey", association.key(),
                            "name", association.name(),
                            "isAssociationClass",
                            String.valueOf(binding.associationClass().isPresent()))));
            g.addRelationship(new GraphModel.Relationship(
                    GraphKey.of(modelKey, GraphKey.Kind.ASSOCIATION_END,
                            association.key(), "source"), modelKey,
                    sourceEnd.projection(), sourceEnd.physicalType(), associationNodeKey,
                    GraphKey.clazz(modelKey, association.sourceClassKey()), endProperties(modelKey,
                            association.sourceRole(), association.sourceLower(),
                            association.sourceUpper(), association.isOrdered(),
                            association.isUnique(), "source")));
            g.addRelationship(new GraphModel.Relationship(
                    GraphKey.of(modelKey, GraphKey.Kind.ASSOCIATION_END,
                            association.key(), "target"), modelKey,
                    targetEnd.projection(), targetEnd.physicalType(), associationNodeKey,
                    GraphKey.clazz(modelKey, association.targetClassKey()), endProperties(modelKey,
                            association.targetRole(), association.targetLower(),
                            association.targetUpper(), association.isOrdered(),
                            association.isUnique(), "target")));
        }

        Map<String, Snapshot.LinkDef> associationClassLinks = new LinkedHashMap<>();
        for (Snapshot.LinkDef link : sn.links()) {
            if (link.associationClassObjectStableId != null) {
                if (associationClassLinks.putIfAbsent(link.associationClassObjectStableId, link)
                        != null) {
                    return Result.failure(Stage.F_G, "G_ASSOCIATION_CLASS_ENCODING",
                            "association-class object is bound to more than one link: "
                                    + link.associationClassObjectStableId);
                }
            }
        }

        // G_snapshot + G_type
        for (var obj : sn.objects()) {
            if (!sm.hasClass(obj.dynamicClassKey)) {
                return Result.failure(Stage.F_G, "G_SOURCE_WF",
                        "dynamic class not in SM: " + obj.dynamicClassKey);
            }
            String nodeKey = GraphKey.object(modelKey, obj.stableId);
            var dynamicClass = sm.clazz(obj.dynamicClassKey);
            boolean associationClassObject = dynamicClass.isAssociationClass();
            Snapshot.LinkDef associationClassLink = associationClassLinks.get(obj.stableId);
            if (associationClassObject != (associationClassLink != null)) {
                return Result.failure(Stage.F_G, "G_ASSOCIATION_CLASS_ENCODING",
                        associationClassObject
                                ? "association-class object has no link occurrence: " + obj.stableId
                                : "ordinary object is used as an association-class occurrence: "
                                        + obj.stableId);
            }
            Map<String, String> objectProperties = new LinkedHashMap<>();
            objectProperties.put("modelKey", modelKey);
            objectProperties.put("objectKey", obj.stableId);
            objectProperties.put("use_id", obj.stableId);
            objectProperties.put("stableKey", obj.stableId);
            if (associationClassObject) {
                objectProperties.put("associationKey", obj.dynamicClassKey);
            }
            var objectNode = catalogue.clazz(obj.dynamicClassKey).objectNode();
            g.addNode(new GraphModel.Node(nodeKey, modelKey,
                    objectNode.projection(), objectNode.observationRole(), objectNode.labels(),
                    objectProperties));
            var objectTyping = mapping.relationship(
                    MetamodelMapping.RelationshipKind.OBJECT_TYPING);
            g.addRelationship(new GraphModel.Relationship(
                    GraphKey.of(modelKey, GraphKey.Kind.OBJECT_TYPING,
                            obj.stableId), modelKey,
                    objectTyping.projection(), objectTyping.physicalType(),
                    nodeKey, GraphKey.clazz(modelKey, obj.dynamicClassKey),
                    Map.of("modelKey", modelKey)));
            mu.put(obj.stableId, nodeKey);
        }
        for (var obj : sn.objects()) {
            String objKey = mu.get(obj.stableId);
            for (var attr : attrsOf(sm, obj.dynamicClassKey)) {
                var attribute = catalogue.attribute(attr.key());
                var slot = sn.attributeSlot(obj.stableId, attr.name());
                if (slot.isEmpty()) {
                    continue; // absent scalar slot -> typed bottom, not an empty string
                }
                String slotKey = GraphKey.slot(modelKey, obj.stableId, attr.key());
                GraphValueCodec.EncodedValue encoded;
                try {
                    GraphValueCodec.validateObjectReference(
                            attr.declaredType(), slot.get(), sm, sn);
                    encoded = GraphValueCodec.encode(attr.declaredType(), slot.get());
                } catch (GraphValueCodec.CodecException e) {
                    return Result.failure(Stage.F_G, e.code(), e.getMessage());
                }
                Map<String, String> slotProperties = new LinkedHashMap<>();
                slotProperties.put("modelKey", modelKey);
                slotProperties.put("slotKey", slotKey);
                slotProperties.put("attributeKey", attr.key());
                slotProperties.put(GraphValueCodec.VALUE_STATE, encoded.state());
                slotProperties.put(GraphValueCodec.VALUE_TYPE, encoded.typeTag());
                slotProperties.put(GraphValueCodec.CODEC_ID, encoded.codecId());
                if (GraphValueCodec.DEFINED.equals(encoded.state())) {
                    slotProperties.put(attribute.valueProperty().physicalName(),
                            encoded.payload());
                }
                var slotNode = mapping.node(MetamodelMapping.NodeKind.ATTRIBUTE_VALUE);
                g.addNode(new GraphModel.Node(slotKey, modelKey,
                        slotNode.projection(), slotNode.observationRole(),
                        slotNode.labels(), slotProperties));
                g.addRelationship(new GraphModel.Relationship(
                        GraphKey.of(modelKey, GraphKey.Kind.SLOT_OWNERSHIP,
                                obj.stableId, attr.key()), modelKey,
                        attribute.slotOwnership().projection(),
                        attribute.slotOwnership().physicalType(),
                        objKey, slotKey, Map.of("modelKey", modelKey)));
                g.addRelationship(new GraphModel.Relationship(
                        GraphKey.of(modelKey, GraphKey.Kind.SLOT_TYPING,
                                obj.stableId, attr.key()), modelKey,
                        attribute.slotTyping().projection(),
                        attribute.slotTyping().physicalType(),
                        slotKey, GraphKey.attribute(modelKey, attr.key()),
                        Map.of("modelKey", modelKey)));
            }
        }
        // Resolve and canonically order links before assigning graph keys.  A
        // list ordinal is not a source identity: reordering two distinct links
        // must not rename either relationship.  Only occurrences with the
        // same canonical association/endpoints/qualifiers need a local rank.
        record ResolvedLink(Snapshot.LinkDef link,
                            org.uet.dse.ocl2cypher.source.model.UmlAssociation association,
                            String tuple,
                            List<String> qualifierPayloads) {}
        List<ResolvedLink> resolvedLinks = new ArrayList<>();
        for (var link : sn.links()) {
            var assoc = sm.associationByName(link.associationName);
            if (assoc == null) {
                assoc = sm.associationByRole(link.associationName);
            }
            if (assoc == null) {
                return Result.failure(Stage.F_G, "G_UNMAPPED_ASSOCIATION",
                        "no association for link " + link.associationName);
            }
            String srcKey = mu.get(link.sourceStableId);
            String tgtKey = mu.get(link.targetStableId);
            if (srcKey == null || tgtKey == null) {
                return Result.failure(Stage.F_G, "G_DANGLING_LINK",
                        "link endpoints not in snapshot: " + link.sourceStableId + " -> " + link.targetStableId);
            }
            if (link.qualifiers.size() != assoc.qualifiers().size()) {
                return Result.failure(Stage.F_G, "G_QUALIFIER_ARITY",
                        "qualifier arity differs for " + assoc.name());
            }
            List<String> qualifierPayloads = new ArrayList<>();
            try {
                for (int qi = 0; qi < link.qualifiers.size(); qi++) {
                    var declaration = assoc.qualifiers().get(qi);
                    Object raw = link.qualifiers.get(qi).value();
                    if (!(raw instanceof org.uet.dse.ocl2cypher.runtime.OclValue value)) {
                        return Result.failure(Stage.F_G, "G_CODEC_CARRIER",
                                "qualifier value is not an OCL scalar: "
                                        + assoc.name() + "." + declaration.name());
                    }
                    GraphValueCodec.validateObjectReference(
                            declaration.declaredType(), value, sm, sn);
                    qualifierPayloads.add(qualifierPayload(
                            declaration.declaredType(), value));
                }
            } catch (GraphValueCodec.CodecException e) {
                return Result.failure(Stage.F_G, e.code(), e.getMessage());
            }
            resolvedLinks.add(new ResolvedLink(link, assoc,
                    canonicalLinkTuple(assoc, link, qualifierPayloads),
                    List.copyOf(qualifierPayloads)));
        }
        resolvedLinks.sort(java.util.Comparator.comparing(ResolvedLink::tuple));
        Map<String, Integer> occurrenceByTuple = new LinkedHashMap<>();
        for (var resolved : resolvedLinks) {
            var link = resolved.link();
            var assoc = resolved.association();
            var associationBinding = catalogue.association(assoc.key());
            String srcKey = mu.get(link.sourceStableId);
            String tgtKey = mu.get(link.targetStableId);
            String tuple = resolved.tuple();
            int occurrence = occurrenceByTuple.merge(tuple, 1, Integer::sum) - 1;
            String linkKey = GraphKey.of(modelKey, GraphKey.Kind.LINK,
                    tuple, String.valueOf(occurrence));
            Map<String, String> linkProps = new LinkedHashMap<>();
            linkProps.put("associationKey", assoc.key());
            linkProps.put("associationName", assoc.name());
            linkProps.put("sourceRole", assoc.sourceRole());
            linkProps.put("targetRole", assoc.targetRole());
            linkProps.put("linkKey", linkKey);
            linkProps.put("modelKey", modelKey);
            for (int qi = 0; qi < link.qualifiers.size(); qi++) {
                linkProps.put(associationBinding.qualifiers().get(qi).storageProperty(),
                        resolved.qualifierPayloads().get(qi));
            }
            var associationClass = sm.clazz(assoc.key());
            boolean isAssociationClass = associationClass != null
                    && associationClass.isAssociationClass();
            if (isAssociationClass) {
                var associationClassBinding = associationBinding.associationClass()
                        .orElseThrow(() -> new IllegalStateException(
                                "association class missing from T_MM catalogue: " + assoc.key()));
                String occurrenceId = link.associationClassObjectStableId;
                if (occurrenceId == null || !sn.hasObject(occurrenceId)
                        || !assoc.key().equals(sn.object(occurrenceId).dynamicClassKey())) {
                    return Result.failure(Stage.F_G, "G_ASSOCIATION_CLASS_ENCODING",
                            "association-class link requires an object of " + assoc.key());
                }
                String occurrenceKey = mu.get(occurrenceId);
                linkProps.put("associationClassObjectKey", occurrenceId);
                g.addRelationship(new GraphModel.Relationship(
                        GraphKey.of(modelKey,
                                GraphKey.Kind.ASSOCIATION_CLASS_PARTICIPANT,
                                linkKey, "source"), modelKey,
                        associationClassBinding.sourceParticipant().projection(),
                        associationClassBinding.sourceParticipant().physicalType(),
                        occurrenceKey, srcKey, linkProps));
                g.addRelationship(new GraphModel.Relationship(
                        GraphKey.of(modelKey,
                                GraphKey.Kind.ASSOCIATION_CLASS_PARTICIPANT,
                                linkKey, "target"), modelKey,
                        associationClassBinding.targetParticipant().projection(),
                        associationClassBinding.targetParticipant().physicalType(),
                        occurrenceKey, tgtKey, linkProps));
            } else {
                if (link.associationClassObjectStableId != null) {
                    return Result.failure(Stage.F_G, "G_ASSOCIATION_CLASS_ENCODING",
                            "ordinary association cannot own a link object: " + assoc.key());
                }
                g.addRelationship(new GraphModel.Relationship(
                        linkKey, modelKey, associationBinding.link().projection(),
                        associationBinding.link().physicalType(), srcKey, tgtKey,
                        linkProps));
            }
        }
        for (var n : g.nodes()) {
            if (!n.properties().containsKey("modelKey")) {
                return Result.failure(Stage.F_G, "G_MISSING_MODEL_SCOPE",
                        "no modelKey on node " + n.stableKey());
            }
        }
        for (var r : g.relationships()) {
            if (!r.properties().containsKey("modelKey")) {
                return Result.failure(Stage.F_G, "G_MISSING_MODEL_SCOPE",
                        "no modelKey on relationship " + r.stableKey());
            }
        }

        Result<ValidRepChecker.Witness> valid = ValidRepChecker.check(sm, sn, g);
        if (valid.isFailure()) {
            return Result.failure(valid.primaryDiagnostic());
        }
        return Result.success(new GraphBuildArtifact(
                g, valid.value().objectCorrespondence(), catalogue));
    }

    private static String qualifierPayload(
            org.uet.dse.ocl2cypher.runtime.OclType declared,
            org.uet.dse.ocl2cypher.runtime.OclValue value) {
        GraphValueCodec.EncodedValue encoded = GraphValueCodec.encode(declared, value);
        if (!GraphValueCodec.DEFINED.equals(encoded.state())) {
            throw new GraphValueCodec.CodecException("G_CODEC_QUALIFIER_BOTTOM",
                    "qualifiers must be defined scalar values");
        }
        return encoded.payload();
    }

    private static String canonicalLinkTuple(
            org.uet.dse.ocl2cypher.source.model.UmlAssociation association,
            Snapshot.LinkDef link,
            List<String> qualifierPayloads) {
        StringBuilder out = new StringBuilder();
        appendKeyPart(out, "a", association.key());
        appendKeyPart(out, "s", link.sourceStableId);
        appendKeyPart(out, "t", link.targetStableId);
        if (link.associationClassObjectStableId != null) {
            // The link object is the source-stable occurrence identity.  Keep
            // it in the tuple so reordering equal-participant AC links cannot
            // rename their participant relationships.
            appendKeyPart(out, "ac", link.associationClassObjectStableId);
        }
        for (int i = 0; i < link.qualifiers.size(); i++) {
            var qualifier = link.qualifiers.get(i);
            appendKeyPart(out, "qn", qualifier.name());
            appendKeyPart(out, "qv", qualifierPayloads.get(i));
        }
        return out.toString();
    }

    private static void appendKeyPart(StringBuilder out, String label, String value) {
        out.append('|').append(label).append(':').append(value.length()).append(':').append(value);
    }

    private static Map<String, String> endProperties(
            String modelKey, String role, int lower, int upper,
            boolean ordered, boolean unique, String endPosition) {
        return Map.of(
                "modelKey", modelKey,
                "roleName", role,
                "lower", String.valueOf(lower),
                "upper", String.valueOf(upper),
                "ordered", String.valueOf(ordered),
                "unique", String.valueOf(unique),
                "endPosition", endPosition);
    }

    /**
     * Every attribute that the *effective* class seen at runtime declares,
     * including inherited ones. The snapshot fragment there enumerates exactly
     * those slots; no spurious inheritance lookup is needed later.
     */
    private static List<org.uet.dse.ocl2cypher.source.model.UmlAttribute> attrsOf(
            SchemaModel sm, String dynamicClassKey) {
        Set<String> visited = new LinkedHashSet<>();
        List<org.uet.dse.ocl2cypher.source.model.UmlAttribute> out = new ArrayList<>();
        Deque<String> q = new ArrayDeque<>();
        q.add(dynamicClassKey);
        while (!q.isEmpty()) {
            String cur = q.removeFirst();
            if (!visited.add(cur)) {
                continue;
            }
            out.addAll(sm.ownAttributes(cur));
            var klass = sm.clazz(cur);
            if (klass != null) {
                q.addAll(klass.directSuperclassKeys());
            }
        }
        return out;
    }
}
