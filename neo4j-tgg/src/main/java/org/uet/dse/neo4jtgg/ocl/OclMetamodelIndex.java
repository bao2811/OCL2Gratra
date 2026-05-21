package org.uet.dse.neo4jtgg.ocl;

import org.tzi.use.uml.mm.MAssociation;
import org.tzi.use.uml.mm.MAssociationEnd;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.mm.MClassifier;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.mm.MClassImpl;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.MOperation;
import org.tzi.use.uml.mm.MNavigableElement;
import org.tzi.use.uml.ocl.type.BagType;
import org.tzi.use.uml.ocl.type.CollectionType;
import org.tzi.use.uml.ocl.type.OrderedSetType;
import org.tzi.use.uml.ocl.type.SequenceType;
import org.tzi.use.uml.ocl.type.SetType;
import org.tzi.use.uml.ocl.type.Type;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclDiagnosticCode;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclCodedUnsupportedOperationException;

import java.util.LinkedHashMap;
import java.util.Map;

public class OclMetamodelIndex {
    private final MModel model;
    private final Map<String, ClassInfo> classes;

    public OclMetamodelIndex(MModel model) {
        this.model = model;
        this.classes = buildClassIndex(model);
    }

    public MModel getModel() {
        return model;
    }

    public ClassInfo getClassInfo(String className) {
        return classes.get(className);
    }

    public MClass requireClass(String className) {
        ClassInfo classInfo = classes.get(className);
        if (classInfo == null) {
            throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.UNKNOWN_CONTEXT_CLASS,
                    "Unknown class in context: " + className);
        }
        return classInfo.modelClass();
    }

    public MAttribute resolveAttribute(String className, String attributeName) {
        ClassInfo classInfo = classes.get(className);
        return classInfo == null ? null : classInfo.attributes().get(attributeName);
    }

    public NavigationInfo resolveNavigation(String className, String roleName) {
        ClassInfo classInfo = classes.get(className);
        return classInfo == null ? null : classInfo.navigations().get(roleName);
    }

    public MOperation resolveOperation(String className, String operationName, int parameterCount) {
        ClassInfo classInfo = classes.get(className);
        if (classInfo == null) {
            return null;
        }
        return classInfo.operations().getOrDefault(operationName, java.util.List.of()).stream()
                .filter(operation -> operation.paramList().size() == parameterCount)
                .findFirst()
                .orElse(null);
    }

    public OclTypeBinding toBinding(Type type, String fallbackTypeName) {
        return toBindingInternal(type, fallbackTypeName);
    }

    private Map<String, ClassInfo> buildClassIndex(MModel model) {
        Map<String, ClassInfo> result = new LinkedHashMap<>();
        for (MClass modelClass : model.classes()) {
            Map<String, MAttribute> attributes = new LinkedHashMap<>();
            for (MAttribute attribute : modelClass.allAttributes()) {
                attributes.put(attribute.name(), attribute);
            }

            Map<String, NavigationInfo> navigations = new LinkedHashMap<>();
            if (modelClass instanceof MClassImpl impl) {
                for (Map.Entry<String, MNavigableElement> entry : impl.navigableEnds().entrySet()) {
                    navigations.put(entry.getKey(), createNavigationInfo(modelClass, entry.getKey(), entry.getValue()));
                }
            }

            Map<String, java.util.List<MOperation>> operations = new LinkedHashMap<>();
            for (MOperation operation : modelClass.operations()) {
                operations.computeIfAbsent(operation.name(), ignored -> new java.util.ArrayList<>()).add(operation);
            }

            result.put(modelClass.name(), new ClassInfo(modelClass, attributes, navigations, operations));
        }
        return result;
    }

    private NavigationInfo createNavigationInfo(MClass sourceClass, String roleName, MNavigableElement navigableElement) {
        MAssociation association = navigableElement.association();
        MAssociationEnd targetEnd = navigableElement instanceof MAssociationEnd associationEnd ? associationEnd : null;
        MAssociationEnd sourceEnd = resolveSourceEnd(sourceClass, association, targetEnd);
        Type type = sourceEnd != null && targetEnd != null
                ? targetEnd.getType(sourceClass, sourceEnd, false)
                : navigableElement instanceof MAssociationEnd associationEnd
                ? associationEnd.getType()
                : navigableElement.cls();
        OclTypeBinding binding = toBindingInternal(type, navigableElement.cls().name());
        return new NavigationInfo(
                roleName,
                association != null ? association.name() : null,
                sourceClass.name(),
                navigableElement.cls().name(),
                binding,
                sourceEnd,
                targetEnd,
                resolveDirection(association, sourceEnd, targetEnd),
                navigableElement);
    }

    private MAssociationEnd resolveSourceEnd(MClass sourceClass, MAssociation association, MAssociationEnd targetEnd) {
        if (association == null || targetEnd == null) {
            return null;
        }
        if (association.associationEnds().size() == 2) {
            for (MAssociationEnd end : association.associationEnds()) {
                if (!end.equals(targetEnd) && sourceClass.isSubClassifierOf(end.cls(), true)) {
                    return end;
                }
            }
        }

        MNavigableElement sourceEnd = association.getSourceEnd((MClassifier) sourceClass, targetEnd, null);
        return sourceEnd instanceof MAssociationEnd associationEnd ? associationEnd : null;
    }

    private NavigationDirection resolveDirection(MAssociation association, MAssociationEnd sourceEnd, MAssociationEnd targetEnd) {
        if (association == null || sourceEnd == null || targetEnd == null || association.associationEnds().size() != 2) {
            return NavigationDirection.UNDIRECTED;
        }

        MAssociationEnd first = association.associationEnds().get(0);
        MAssociationEnd second = association.associationEnds().get(1);
        if (first.equals(sourceEnd) && second.equals(targetEnd)) {
            return NavigationDirection.OUTGOING;
        }
        if (first.equals(targetEnd) && second.equals(sourceEnd)) {
            return NavigationDirection.INCOMING;
        }
        return NavigationDirection.UNDIRECTED;
    }

    private OclTypeBinding toBindingInternal(Type type, String fallbackTypeName) {
        if (type != null && type.isKindOfCollection(Type.VoidHandling.EXCLUDE_VOID) && type instanceof CollectionType collectionType) {
            Type elementType = collectionType.elemType();
            OclTypeBinding.CollectionKind collectionKind = toCollectionKind(collectionType);
            if (elementType instanceof MClass elementClass) {
                return OclTypeBinding.nodeCollection(elementClass.name(), collectionKind);
            }
            return OclTypeBinding.scalarCollection(elementType.shortName(), collectionKind);
        }
        if (type instanceof MClass cls) {
            return OclTypeBinding.node(cls.name());
        }
        if (type != null) {
            return OclTypeBinding.scalar(type.shortName());
        }
        return OclTypeBinding.nodeCollection(fallbackTypeName);
    }

    private OclTypeBinding.CollectionKind toCollectionKind(CollectionType collectionType) {
        if (collectionType instanceof SetType) {
            return OclTypeBinding.CollectionKind.SET;
        }
        if (collectionType instanceof BagType) {
            return OclTypeBinding.CollectionKind.BAG;
        }
        if (collectionType instanceof SequenceType) {
            return OclTypeBinding.CollectionKind.SEQUENCE;
        }
        if (collectionType instanceof OrderedSetType) {
            return OclTypeBinding.CollectionKind.ORDERED_SET;
        }
        return OclTypeBinding.CollectionKind.COLLECTION;
    }

    public record ClassInfo(MClass modelClass,
                            Map<String, MAttribute> attributes,
                            Map<String, NavigationInfo> navigations,
                            Map<String, java.util.List<MOperation>> operations) {
    }

    public record NavigationInfo(
            String roleName,
            String associationName,
            String sourceClassName,
            String targetClassName,
            OclTypeBinding resultBinding,
            MAssociationEnd sourceEnd,
            MAssociationEnd targetEnd,
            NavigationDirection direction,
            MNavigableElement navigableElement) {
        public boolean isBinaryAssociation() {
            return targetEnd != null && targetEnd.association() != null && targetEnd.association().associationEnds().size() == 2;
        }

        public boolean hasQualifiers() {
            return (sourceEnd != null && sourceEnd.hasQualifiers())
                    || (targetEnd != null && targetEnd.hasQualifiers())
                    || (targetEnd != null && targetEnd.association() != null && targetEnd.association().hasQualifiedEnds());
        }

        public boolean isRedefiningAssociation() {
            return targetEnd != null && targetEnd.association() != null && targetEnd.association().isRedefining();
        }

        public boolean supportsDirectCypherNavigation() {
            return isBinaryAssociation() && !hasQualifiers() && !isRedefiningAssociation();
        }

        public String unsupportedReason() {
            if (!isBinaryAssociation()) {
                return "Navigation over non-binary associations is not supported yet: " + associationName();
            }
            if (hasQualifiers()) {
                return "Navigation over qualified associations is not supported yet: " + associationName();
            }
            if (isRedefiningAssociation()) {
                return "Navigation over redefining associations is not supported yet: " + associationName();
            }
            return null;
        }

        public OclDiagnosticCode unsupportedCode() {
            if (!isBinaryAssociation()) {
                return OclDiagnosticCode.NON_BINARY_ASSOCIATION_UNSUPPORTED;
            }
            if (hasQualifiers()) {
                return OclDiagnosticCode.QUALIFIED_ASSOCIATION_UNSUPPORTED;
            }
            if (isRedefiningAssociation()) {
                return OclDiagnosticCode.REDEFINING_ASSOCIATION_UNSUPPORTED;
            }
            return OclDiagnosticCode.GENERIC_FAILURE;
        }

        public String sourceRoleName() {
            return sourceEnd != null ? sourceEnd.nameAsRolename() : null;
        }

        public String targetRoleName() {
            return targetEnd != null ? targetEnd.nameAsRolename() : null;
        }

        public String sourceMultiplicity() {
            return sourceEnd != null ? sourceEnd.multiplicity().toString() : null;
        }

        public String targetMultiplicity() {
            return targetEnd != null ? targetEnd.multiplicity().toString() : null;
        }

        public boolean sourceOrdered() {
            return sourceEnd != null && sourceEnd.isOrdered();
        }

        public boolean targetOrdered() {
            return targetEnd != null && targetEnd.isOrdered();
        }

        public boolean targetSingleValued() {
            return targetEnd != null && !targetEnd.multiplicity().isCollection();
        }
    }

    public enum NavigationDirection {
        OUTGOING,
        INCOMING,
        UNDIRECTED
    }
}
