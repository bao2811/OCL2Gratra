package org.uet.dse.neo4jtgg.ocl;

public record OclTypeBinding(BindingKind kind, String typeName, CollectionKind collectionKind,
                             OclTypeBinding elementBinding) {
    public OclTypeBinding {
        if (collectionKind == null) {
            collectionKind = CollectionKind.NONE;
        }
        if (isCollectionKind(kind)) {
            if (elementBinding == null) {
                elementBinding = switch (kind) {
                    case NODE_COLLECTION -> node(typeName);
                    case SCALAR_COLLECTION -> scalar(typeName);
                    case COLLECTION -> throw new IllegalArgumentException(
                            "Generic collection bindings must provide an element binding.");
                    default -> null;
                };
            }
        } else {
            elementBinding = null;
        }
        if (!isCollectionKindAllowed(kind, collectionKind)) {
            throw new IllegalArgumentException("Non-collection bindings must use CollectionKind.NONE.");
        }
    }

    public static OclTypeBinding node(String className) {
        return new OclTypeBinding(BindingKind.NODE, className, CollectionKind.NONE, null);
    }

    public static OclTypeBinding nodeCollection(String className) {
        return nodeCollection(className, CollectionKind.COLLECTION);
    }

    public static OclTypeBinding nodeCollection(String className, CollectionKind collectionKind) {
        return new OclTypeBinding(BindingKind.NODE_COLLECTION, className, collectionKind, null);
    }

    public static OclTypeBinding scalar(String scalarType) {
        return new OclTypeBinding(BindingKind.SCALAR, scalarType, CollectionKind.NONE, null);
    }

    public static OclTypeBinding scalarCollection(String scalarType) {
        return scalarCollection(scalarType, CollectionKind.COLLECTION);
    }

    public static OclTypeBinding scalarCollection(String scalarType, CollectionKind collectionKind) {
        return new OclTypeBinding(BindingKind.SCALAR_COLLECTION, scalarType, collectionKind, null);
    }

    public static OclTypeBinding classReference(String className) {
        return new OclTypeBinding(BindingKind.CLASS_REFERENCE, className, CollectionKind.NONE, null);
    }

    public static OclTypeBinding collectionOf(OclTypeBinding elementType, CollectionKind collectionKind) {
        if (elementType == null) {
            throw new IllegalArgumentException("Collection bindings must provide an element type.");
        }
        if (elementType.isNode()) {
            return nodeCollection(elementType.typeName(), collectionKind);
        }
        if (!elementType.isCollection()) {
            return scalarCollection(elementType.typeName(), collectionKind);
        }
        return new OclTypeBinding(BindingKind.COLLECTION, elementType.typeName(), collectionKind, elementType);
    }

    public boolean isNode() {
        return kind == BindingKind.NODE;
    }

    public boolean isCollection() {
        return isCollectionKind(kind);
    }

    public boolean isClassReference() {
        return kind == BindingKind.CLASS_REFERENCE;
    }

    public boolean isOrderedCollection() {
        return collectionKind == CollectionKind.SEQUENCE || collectionKind == CollectionKind.ORDERED_SET;
    }

    public boolean isUniqueCollection() {
        return collectionKind == CollectionKind.SET || collectionKind == CollectionKind.ORDERED_SET;
    }

    public OclTypeBinding elementType() {
        if (!isCollection()) {
            return this;
        }
        return elementBinding;
    }

    public OclTypeBinding withCollectionKind(CollectionKind nextCollectionKind) {
        if (!isCollection()) {
            return this;
        }
        return new OclTypeBinding(kind, typeName, nextCollectionKind, elementBinding);
    }

    private static boolean isCollectionKindAllowed(BindingKind kind, CollectionKind collectionKind) {
        if (isCollectionKind(kind)) {
            return collectionKind != CollectionKind.NONE;
        }
        return collectionKind == CollectionKind.NONE;
    }

    private static boolean isCollectionKind(BindingKind kind) {
        return kind == BindingKind.NODE_COLLECTION
                || kind == BindingKind.SCALAR_COLLECTION
                || kind == BindingKind.COLLECTION;
    }

    public enum BindingKind {
        NODE,
        NODE_COLLECTION,
        SCALAR,
        SCALAR_COLLECTION,
        COLLECTION,
        CLASS_REFERENCE
    }

    public enum CollectionKind {
        NONE,
        COLLECTION,
        SET,
        BAG,
        SEQUENCE,
        ORDERED_SET
    }
}
