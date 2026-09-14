package org.uet.dse.ocl2cypher.graph;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.uet.dse.ocl2cypher.runtime.OclType;

/**
 * The closed M2 witness {@code rules(T_MM)} for the supported UMLMM profile.
 *
 * <p>This object contains vocabulary only.  It has no M1 declaration keys and
 * creates no graph elements.  {@link MetamodelTranslator} instantiates these
 * templates for one {@code SchemaModel}; {@link GraphBuilder} then consumes
 * that catalogue instead of embedding physical PGMM names in its rules.
 */
public final class MetamodelMapping {

    public enum NodeKind {
        MODEL,
        CLASS,
        ATTRIBUTE,
        ASSOCIATION,
        OBJECT,
        ASSOCIATION_CLASS_OBJECT,
        ATTRIBUTE_VALUE
    }

    public enum RelationshipKind {
        GENERALIZATION,
        CLASS_ATTRIBUTE,
        ASSOCIATION_SOURCE_END,
        ASSOCIATION_TARGET_END,
        ASSOCIATION_CLASS_SOURCE_PARTICIPANT,
        ASSOCIATION_CLASS_TARGET_PARTICIPANT,
        OBJECT_TYPING,
        OBJECT_ATTRIBUTE,
        SLOT_TYPING,
        BINARY_LINK
    }

    public enum KeyKind {
        MODEL_KEY,
        CLASS_KEY,
        ATTRIBUTE_KEY,
        ASSOCIATION_KEY,
        OBJECT_KEY,
        SLOT_KEY,
        GENERALIZATION_KEY,
        LINK_KEY
    }

    public record NodeBinding(NodeKind sourceKind,
                              GraphModel.Projection projection,
                              String observationRole,
                              List<String> labels,
                              KeyKind keyKind) {
        public NodeBinding {
            Objects.requireNonNull(sourceKind);
            Objects.requireNonNull(projection);
            Objects.requireNonNull(observationRole);
            labels = List.copyOf(labels);
            if (labels.isEmpty()) {
                throw new IllegalArgumentException("node binding requires a label");
            }
            Objects.requireNonNull(keyKind);
        }
    }

    public record RelationshipBinding(RelationshipKind sourceReference,
                                      GraphModel.Projection projection,
                                      String physicalType,
                                      NodeKind sourceKind,
                                      NodeKind targetKind,
                                      KeyKind keyKind) {
        public RelationshipBinding {
            Objects.requireNonNull(sourceReference);
            Objects.requireNonNull(projection);
            Objects.requireNonNull(physicalType);
            Objects.requireNonNull(sourceKind);
            Objects.requireNonNull(targetKind);
            Objects.requireNonNull(keyKind);
        }
    }

    public record PropertyBinding(OclType.Kind sourceKind,
                                  String physicalName,
                                  String codecId) {
        public PropertyBinding {
            Objects.requireNonNull(sourceKind);
            Objects.requireNonNull(physicalName);
            Objects.requireNonNull(codecId);
        }
    }

    private final Map<NodeKind, NodeBinding> nodes;
    private final Map<RelationshipKind, RelationshipBinding> relationships;
    private final Map<OclType.Kind, PropertyBinding> properties;

    private MetamodelMapping(Map<NodeKind, NodeBinding> nodes,
                             Map<RelationshipKind, RelationshipBinding> relationships,
                             Map<OclType.Kind, PropertyBinding> properties) {
        this.nodes = immutableEnumMap(NodeKind.class, nodes);
        this.relationships = immutableEnumMap(RelationshipKind.class, relationships);
        this.properties = immutableEnumMap(OclType.Kind.class, properties);
        requireComplete(NodeKind.values(), this.nodes, "node");
        requireComplete(RelationshipKind.values(), this.relationships, "relationship");
        for (OclType.Kind kind : List.of(OclType.Kind.BOOLEAN, OclType.Kind.INTEGER,
                OclType.Kind.REAL, OclType.Kind.STRING, OclType.Kind.CLASS)) {
            if (!this.properties.containsKey(kind)) {
                throw new IllegalArgumentException("missing property binding for " + kind);
            }
        }
    }

    /** Canonical supported UMLMM-to-PGMM profile. */
    public static MetamodelMapping canonical() {
        Map<NodeKind, NodeBinding> nodes = new EnumMap<>(NodeKind.class);
        nodes.put(NodeKind.MODEL, node(NodeKind.MODEL,
                GraphModel.Projection.REPOSITORY_CONTROL, "MODEL_ROOT", "Model",
                KeyKind.MODEL_KEY));
        nodes.put(NodeKind.CLASS, node(NodeKind.CLASS,
                GraphModel.Projection.SCHEMA, "UML_CLASS", "UmlClass",
                KeyKind.CLASS_KEY));
        nodes.put(NodeKind.ATTRIBUTE, node(NodeKind.ATTRIBUTE,
                GraphModel.Projection.SCHEMA, "ATTRIBUTE_DECLARATION", "Attribute",
                KeyKind.ATTRIBUTE_KEY));
        nodes.put(NodeKind.ASSOCIATION, node(NodeKind.ASSOCIATION,
                GraphModel.Projection.SCHEMA, "ASSOCIATION_DECLARATION", "Association",
                KeyKind.ASSOCIATION_KEY));
        nodes.put(NodeKind.OBJECT, node(NodeKind.OBJECT,
                GraphModel.Projection.INSTANCE, "OBJECT", "Object", KeyKind.OBJECT_KEY));
        nodes.put(NodeKind.ASSOCIATION_CLASS_OBJECT,
                new NodeBinding(NodeKind.ASSOCIATION_CLASS_OBJECT,
                        GraphModel.Projection.INSTANCE, "ASSOCIATION_CLASS_OBJECT",
                        List.of("Object", "AssociationClassObject"), KeyKind.OBJECT_KEY));
        nodes.put(NodeKind.ATTRIBUTE_VALUE, node(NodeKind.ATTRIBUTE_VALUE,
                GraphModel.Projection.INSTANCE, "ATTRIBUTE_VALUE", "AttributeValue",
                KeyKind.SLOT_KEY));

        Map<RelationshipKind, RelationshipBinding> relationships =
                new EnumMap<>(RelationshipKind.class);
        relationships.put(RelationshipKind.GENERALIZATION, relationship(
                RelationshipKind.GENERALIZATION, GraphModel.Projection.SCHEMA,
                GraphModel.EXTENDS, NodeKind.CLASS, NodeKind.CLASS,
                KeyKind.GENERALIZATION_KEY));
        relationships.put(RelationshipKind.CLASS_ATTRIBUTE, relationship(
                RelationshipKind.CLASS_ATTRIBUTE, GraphModel.Projection.SCHEMA,
                GraphModel.HAS_ATTRIBUTE, NodeKind.CLASS, NodeKind.ATTRIBUTE,
                KeyKind.ATTRIBUTE_KEY));
        relationships.put(RelationshipKind.ASSOCIATION_SOURCE_END, relationship(
                RelationshipKind.ASSOCIATION_SOURCE_END, GraphModel.Projection.SCHEMA,
                GraphModel.ASSOCIATION_SOURCE_END, NodeKind.ASSOCIATION, NodeKind.CLASS,
                KeyKind.ASSOCIATION_KEY));
        relationships.put(RelationshipKind.ASSOCIATION_TARGET_END, relationship(
                RelationshipKind.ASSOCIATION_TARGET_END, GraphModel.Projection.SCHEMA,
                GraphModel.ASSOCIATION_TARGET_END, NodeKind.ASSOCIATION, NodeKind.CLASS,
                KeyKind.ASSOCIATION_KEY));
        relationships.put(RelationshipKind.ASSOCIATION_CLASS_SOURCE_PARTICIPANT,
                relationship(RelationshipKind.ASSOCIATION_CLASS_SOURCE_PARTICIPANT,
                        GraphModel.Projection.INSTANCE, "$Association_SourceParticipant",
                        NodeKind.ASSOCIATION_CLASS_OBJECT, NodeKind.OBJECT,
                        KeyKind.LINK_KEY));
        relationships.put(RelationshipKind.ASSOCIATION_CLASS_TARGET_PARTICIPANT,
                relationship(RelationshipKind.ASSOCIATION_CLASS_TARGET_PARTICIPANT,
                        GraphModel.Projection.INSTANCE, "$Association_TargetParticipant",
                        NodeKind.ASSOCIATION_CLASS_OBJECT, NodeKind.OBJECT,
                        KeyKind.LINK_KEY));
        relationships.put(RelationshipKind.OBJECT_TYPING, relationship(
                RelationshipKind.OBJECT_TYPING, GraphModel.Projection.TYPING,
                GraphModel.OBJECT_INSTANCE_OF, NodeKind.OBJECT, NodeKind.CLASS,
                KeyKind.OBJECT_KEY));
        relationships.put(RelationshipKind.OBJECT_ATTRIBUTE, relationship(
                RelationshipKind.OBJECT_ATTRIBUTE, GraphModel.Projection.INSTANCE,
                GraphModel.OBJECT_HAS_ATTRIBUTE, NodeKind.OBJECT,
                NodeKind.ATTRIBUTE_VALUE, KeyKind.SLOT_KEY));
        relationships.put(RelationshipKind.SLOT_TYPING, relationship(
                RelationshipKind.SLOT_TYPING, GraphModel.Projection.TYPING,
                GraphModel.INSTANCE_OF, NodeKind.ATTRIBUTE_VALUE, NodeKind.ATTRIBUTE,
                KeyKind.SLOT_KEY));
        relationships.put(RelationshipKind.BINARY_LINK, relationship(
                RelationshipKind.BINARY_LINK, GraphModel.Projection.INSTANCE,
                GraphModel.LINK_ASSOCIATE_WITH, NodeKind.OBJECT, NodeKind.OBJECT,
                KeyKind.LINK_KEY));

        Map<OclType.Kind, PropertyBinding> properties = new EnumMap<>(OclType.Kind.class);
        for (OclType type : List.of(OclType.BOOLEAN, OclType.INTEGER,
                OclType.REAL, OclType.STRING, OclType.clazz("$Class"))) {
            properties.put(type.kind(), new PropertyBinding(type.kind(),
                    GraphValueCodec.PAYLOAD, GraphValueCodec.codecId(type)));
        }
        return new MetamodelMapping(nodes, relationships, properties);
    }

    public NodeBinding node(NodeKind kind) {
        return required(nodes, kind, "node");
    }

    public RelationshipBinding relationship(RelationshipKind kind) {
        return required(relationships, kind, "relationship");
    }

    /** Instantiate one parameterized association-class participant template. */
    public RelationshipBinding associationClassParticipant(
            RelationshipKind kind, String associationKey) {
        RelationshipBinding template = relationship(kind);
        String physicalType = switch (kind) {
            case ASSOCIATION_CLASS_SOURCE_PARTICIPANT ->
                    GraphModel.associationClassSourceParticipant(associationKey);
            case ASSOCIATION_CLASS_TARGET_PARTICIPANT ->
                    GraphModel.associationClassTargetParticipant(associationKey);
            default -> throw new IllegalArgumentException(
                    "not an association-class participant template: " + kind);
        };
        return new RelationshipBinding(template.sourceReference(), template.projection(),
                physicalType, template.sourceKind(), template.targetKind(), template.keyKind());
    }

    public PropertyBinding property(OclType type) {
        if (type.isCollection()) {
            throw new IllegalArgumentException("collection attributes are outside T_MM");
        }
        return required(properties, type.kind(), "property");
    }

    public Map<NodeKind, NodeBinding> nodes() {
        return nodes;
    }

    public Map<RelationshipKind, RelationshipBinding> relationships() {
        return relationships;
    }

    private static NodeBinding node(NodeKind kind, GraphModel.Projection projection,
                                    String role, String label, KeyKind key) {
        return new NodeBinding(kind, projection, role, List.of(label), key);
    }

    private static RelationshipBinding relationship(
            RelationshipKind kind, GraphModel.Projection projection, String physicalType,
            NodeKind source, NodeKind target, KeyKind key) {
        return new RelationshipBinding(kind, projection, physicalType, source, target, key);
    }

    private static <E extends Enum<E>, V> Map<E, V> immutableEnumMap(
            Class<E> enumType, Map<E, V> values) {
        EnumMap<E, V> copy = new EnumMap<>(enumType);
        copy.putAll(values);
        return Collections.unmodifiableMap(copy);
    }

    private static <E, V> V required(Map<E, V> map, E key, String family) {
        V value = map.get(key);
        if (value == null) {
            throw new IllegalStateException("missing " + family + " binding for " + key);
        }
        return value;
    }

    private static <E> void requireComplete(E[] values, Map<E, ?> map, String family) {
        for (E value : values) {
            if (!map.containsKey(value)) {
                throw new IllegalArgumentException("missing " + family + " binding for " + value);
            }
        }
    }
}
