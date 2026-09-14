package org.uet.dse.ocl2cypher.graph;

import java.util.Collections;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.uet.dse.ocl2cypher.diagnostics.Result;
import org.uet.dse.ocl2cypher.diagnostics.Stage;
import org.uet.dse.ocl2cypher.source.model.SchemaModel;
import org.uet.dse.ocl2cypher.source.model.UmlAssociation;
import org.uet.dse.ocl2cypher.source.model.UmlAttribute;

/** Instantiates the M2 {@link MetamodelMapping} templates for one M1 schema. */
public final class MetamodelTranslator {

    public enum ResultKind {
        SCALAR,
        SET,
        BAG
    }

    public record ClassBinding(String semanticSourceKey,
                               MetamodelMapping.NodeBinding declarationNode,
                               MetamodelMapping.NodeBinding objectNode,
                               MetamodelMapping.RelationshipBinding objectTyping) {
        public ClassBinding {
            Objects.requireNonNull(semanticSourceKey);
            Objects.requireNonNull(declarationNode);
            Objects.requireNonNull(objectNode);
            Objects.requireNonNull(objectTyping);
        }
    }

    public record AttributeBinding(String semanticSourceKey,
                                   String ownerClassKey,
                                   MetamodelMapping.NodeBinding declarationNode,
                                   MetamodelMapping.RelationshipBinding ownership,
                                   MetamodelMapping.RelationshipBinding slotOwnership,
                                   MetamodelMapping.RelationshipBinding slotTyping,
                                   MetamodelMapping.PropertyBinding valueProperty) {
        public AttributeBinding {
            Objects.requireNonNull(semanticSourceKey);
            Objects.requireNonNull(ownerClassKey);
            Objects.requireNonNull(declarationNode);
            Objects.requireNonNull(ownership);
            Objects.requireNonNull(slotOwnership);
            Objects.requireNonNull(slotTyping);
            Objects.requireNonNull(valueProperty);
        }
    }

    public record NavigationBinding(String semanticSourceKey,
                                    String receiverClassKey,
                                    String targetClassKey,
                                    String roleName,
                                    boolean reverse,
                                    ResultKind resultKind,
                                    String physicalType) {
        public NavigationBinding {
            Objects.requireNonNull(semanticSourceKey);
            Objects.requireNonNull(receiverClassKey);
            Objects.requireNonNull(targetClassKey);
            Objects.requireNonNull(roleName);
            Objects.requireNonNull(resultKind);
            Objects.requireNonNull(physicalType);
        }
    }

    public record AssociationClassBinding(MetamodelMapping.NodeBinding objectNode,
                                          MetamodelMapping.RelationshipBinding sourceParticipant,
                                          MetamodelMapping.RelationshipBinding targetParticipant) {
        public AssociationClassBinding {
            Objects.requireNonNull(objectNode);
            Objects.requireNonNull(sourceParticipant);
            Objects.requireNonNull(targetParticipant);
        }
    }

    public record QualifierBinding(int index,
                                   String name,
                                   org.uet.dse.ocl2cypher.runtime.OclType declaredType,
                                   MetamodelMapping.PropertyBinding property,
                                   String storageProperty,
                                   boolean finiteDomainComplete) {
        public QualifierBinding {
            if (index < 0) throw new IllegalArgumentException("negative qualifier index");
            Objects.requireNonNull(name);
            Objects.requireNonNull(declaredType);
            Objects.requireNonNull(property);
            Objects.requireNonNull(storageProperty);
        }
    }

    public record AssociationBinding(String semanticSourceKey,
                                     MetamodelMapping.RelationshipBinding link,
                                     NavigationBinding forward,
                                     NavigationBinding reverse,
                                     List<QualifierBinding> qualifiers,
                                     Optional<AssociationClassBinding> associationClass) {
        public AssociationBinding {
            Objects.requireNonNull(semanticSourceKey);
            Objects.requireNonNull(link);
            Objects.requireNonNull(forward);
            Objects.requireNonNull(reverse);
            qualifiers = List.copyOf(qualifiers);
            associationClass = Objects.requireNonNull(associationClass);
        }
    }

    /** A closed, deterministic catalogue for one schema model. */
    public record Catalogue(String modelKey,
                            MetamodelMapping mapping,
                            Map<String, ClassBinding> classes,
                            Map<String, AttributeBinding> attributes,
                            Map<String, AssociationBinding> associations) {
        public Catalogue {
            Objects.requireNonNull(modelKey);
            Objects.requireNonNull(mapping);
            classes = immutableLinked(classes);
            attributes = immutableLinked(attributes);
            associations = immutableLinked(associations);
        }

        public ClassBinding clazz(String key) {
            ClassBinding binding = classes.get(key);
            if (binding == null) {
                throw new IllegalArgumentException("class is outside catalogue: " + key);
            }
            return binding;
        }

        public AttributeBinding attribute(String key) {
            AttributeBinding binding = attributes.get(key);
            if (binding == null) {
                throw new IllegalArgumentException("attribute is outside catalogue: " + key);
            }
            return binding;
        }

        public AssociationBinding association(String key) {
            AssociationBinding binding = associations.get(key);
            if (binding == null) {
                throw new IllegalArgumentException("association is outside catalogue: " + key);
            }
            return binding;
        }
    }

    private MetamodelTranslator() {
    }

    public static Result<Catalogue> translate(SchemaModel schema) {
        return translate(MetamodelMapping.canonical(), schema);
    }

    static Result<Catalogue> translate(MetamodelMapping mapping, SchemaModel schema) {
        Objects.requireNonNull(mapping, "mapping");
        Objects.requireNonNull(schema, "schema");
        if (schema.hasInheritanceCycle()) {
            return failure("MM_INVALID_GENERALIZATION",
                    "schema inheritance relation contains a cycle");
        }

        Map<String, ClassBinding> classes = new LinkedHashMap<>();
        for (var classifier : schema.classes()) {
            for (String superclass : classifier.directSuperclassKeys()) {
                if (!schema.hasClass(superclass)) {
                    return failure("MM_INVALID_GENERALIZATION",
                            "superclass is not in schema: " + classifier.key()
                                    + " -> " + superclass);
                }
            }
            classes.put(classifier.key(), new ClassBinding(
                    classifier.key(),
                    mapping.node(MetamodelMapping.NodeKind.CLASS),
                    mapping.node(classifier.isAssociationClass()
                            ? MetamodelMapping.NodeKind.ASSOCIATION_CLASS_OBJECT
                            : MetamodelMapping.NodeKind.OBJECT),
                    mapping.relationship(MetamodelMapping.RelationshipKind.OBJECT_TYPING)));
        }

        Map<String, AttributeBinding> attributes = new LinkedHashMap<>();
        for (UmlAttribute attribute : schema.attributes()) {
            if (!schema.hasClass(attribute.ownerClassKey())) {
                return failure("MM_INCOMPLETE_OBSERVER_CATALOGUE",
                        "attribute owner is not in schema: " + attribute.key());
            }
            try {
                attributes.put(attribute.key(), new AttributeBinding(
                        attribute.key(), attribute.ownerClassKey(),
                        mapping.node(MetamodelMapping.NodeKind.ATTRIBUTE),
                        mapping.relationship(MetamodelMapping.RelationshipKind.CLASS_ATTRIBUTE),
                        mapping.relationship(MetamodelMapping.RelationshipKind.OBJECT_ATTRIBUTE),
                        mapping.relationship(MetamodelMapping.RelationshipKind.SLOT_TYPING),
                        mapping.property(attribute.declaredType())));
            } catch (IllegalArgumentException unsupported) {
                return failure("MM_UNSUPPORTED_VALUE_TYPE", unsupported.getMessage());
            }
        }

        Map<String, AssociationBinding> associations = new LinkedHashMap<>();
        for (UmlAssociation association : schema.associations()) {
            if (!schema.hasClass(association.sourceClassKey())
                    || !schema.hasClass(association.targetClassKey())) {
                return failure("MM_AMBIGUOUS_ASSOCIATION_END",
                        "association endpoint class is not in schema: " + association.key());
            }
            if (!validMultiplicity(association.sourceLower(), association.sourceUpper())
                    || !validMultiplicity(association.targetLower(), association.targetUpper())) {
                return failure("MM_INVALID_MULTIPLICITY",
                        "invalid multiplicity on association: " + association.key());
            }
            Set<String> qualifierNames = new HashSet<>();
            List<QualifierBinding> qualifiers = new ArrayList<>();
            for (int index = 0; index < association.qualifiers().size(); index++) {
                var qualifier = association.qualifiers().get(index);
                if (!qualifierNames.add(qualifier.name())) {
                    return failure("MM_AMBIGUOUS_ASSOCIATION_END",
                            "duplicate qualifier on association " + association.key()
                                    + ": " + qualifier.name());
                }
                try {
                    qualifiers.add(new QualifierBinding(index, qualifier.name(),
                            qualifier.declaredType(), mapping.property(qualifier.declaredType()),
                            "qualifier::" + index,
                            qualifier.finiteDomainComplete()));
                } catch (IllegalArgumentException unsupported) {
                    return failure("MM_UNSUPPORTED_VALUE_TYPE", unsupported.getMessage());
                }
            }

            var link = mapping.relationship(MetamodelMapping.RelationshipKind.BINARY_LINK);
            Optional<AssociationClassBinding> associationClass = Optional.empty();
            var classifier = schema.clazz(association.key());
            if (classifier != null && classifier.isAssociationClass()) {
                var node = mapping.node(MetamodelMapping.NodeKind.ASSOCIATION_CLASS_OBJECT);
                associationClass = Optional.of(new AssociationClassBinding(
                        node,
                        mapping.associationClassParticipant(
                                MetamodelMapping.RelationshipKind
                                        .ASSOCIATION_CLASS_SOURCE_PARTICIPANT,
                                association.key()),
                        mapping.associationClassParticipant(
                                MetamodelMapping.RelationshipKind
                                        .ASSOCIATION_CLASS_TARGET_PARTICIPANT,
                                association.key())));
            }
            associations.put(association.key(), new AssociationBinding(
                    association.key(), link,
                    navigation(association.key() + "::" + association.targetRole(),
                            association.sourceClassKey(), association.targetClassKey(),
                            association.targetRole(), false, association.targetUpper(),
                            association.isUnique(), link.physicalType()),
                    navigation(association.key() + "::" + association.sourceRole(),
                            association.targetClassKey(), association.sourceClassKey(),
                            association.sourceRole(), true, association.sourceUpper(),
                            association.isUnique(), link.physicalType()),
                    qualifiers,
                    associationClass));
        }
        return Result.success(new Catalogue(schema.modelKey(), mapping,
                classes, attributes, associations));
    }

    private static NavigationBinding navigation(
            String key, String receiver, String target, String role, boolean reverse,
            int upper, boolean unique, String physicalType) {
        ResultKind kind = upper == 1 ? ResultKind.SCALAR
                : unique ? ResultKind.SET : ResultKind.BAG;
        return new NavigationBinding(key, receiver, target, role, reverse, kind, physicalType);
    }

    private static boolean validMultiplicity(int lower, int upper) {
        return lower >= 0 && (upper == -1 || upper >= lower);
    }

    private static Result<Catalogue> failure(String code, String message) {
        return Result.failure(Stage.T_MM, code, message);
    }

    private static <K, V> Map<K, V> immutableLinked(Map<K, V> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
