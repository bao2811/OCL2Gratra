package org.uet.dse.neo4jtgg.ocl;

public record OclTypeBinding(BindingKind kind, String typeName, CollectionKind collectionKind) {
    public OclTypeBinding {
        if (collectionKind == null) {
            collectionKind = CollectionKind.NONE;
        }
        if (!isCollectionKindAllowed(kind, collectionKind)) {
            throw new IllegalArgumentException("Non-collection bindings must use CollectionKind.NONE.");
        }
    }

    public static OclTypeBinding node(String className) {
        return new OclTypeBinding(BindingKind.NODE, className, CollectionKind.NONE);
    }

    public static OclTypeBinding nodeCollection(String className) {
        return nodeCollection(className, CollectionKind.COLLECTION);
    }

    public static OclTypeBinding nodeCollection(String className, CollectionKind collectionKind) {
        return new OclTypeBinding(BindingKind.NODE_COLLECTION, className, collectionKind);
    }

    public static OclTypeBinding scalar(String scalarType) {
        return new OclTypeBinding(BindingKind.SCALAR, scalarType, CollectionKind.NONE);
    }

    public static OclTypeBinding scalarCollection(String scalarType) {
        return scalarCollection(scalarType, CollectionKind.COLLECTION);
    }

    public static OclTypeBinding scalarCollection(String scalarType, CollectionKind collectionKind) {
        return new OclTypeBinding(BindingKind.SCALAR_COLLECTION, scalarType, collectionKind);
    }

    public static OclTypeBinding classReference(String className) {
        return new OclTypeBinding(BindingKind.CLASS_REFERENCE, className, CollectionKind.NONE);
    }

    public boolean isNode() {
        return kind == BindingKind.NODE;
    }

    public boolean isCollection() {
        return kind == BindingKind.NODE_COLLECTION || kind == BindingKind.SCALAR_COLLECTION;
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
        return kind == BindingKind.NODE_COLLECTION ? node(typeName) : scalar(typeName);
    }

    public OclTypeBinding withCollectionKind(CollectionKind nextCollectionKind) {
        if (!isCollection()) {
            return this;
        }
        return new OclTypeBinding(kind, typeName, nextCollectionKind);
    }

    private static boolean isCollectionKindAllowed(BindingKind kind, CollectionKind collectionKind) {
        if (kind == BindingKind.NODE_COLLECTION || kind == BindingKind.SCALAR_COLLECTION) {
            return collectionKind != CollectionKind.NONE;
        }
        return collectionKind == CollectionKind.NONE;
    }

    public enum BindingKind {
        NODE,
        NODE_COLLECTION,
        SCALAR,
        SCALAR_COLLECTION,
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
