package org.uet.dse.neo4jtgg.ocl;

/**
 * Executable UML/OCL conformance relation used by the production binder.
 *
 * <p>The branch order is part of the certified contract: non-collection Void
 * is bottom, scalar OclAny is top, collection conformance is covariant with a
 * generic Collection target, UML nodes follow reflexive/transitive
 * generalization, and the only non-reflexive scalar coercions are
 * Integer-to-Real and UnlimitedNatural-to-Integer.</p>
 */
public final class OclTypeConformance {
    private OclTypeConformance() {
    }

    public static boolean conformsTo(OclMetamodelIndex metamodelIndex,
                                     OclTypeBinding actual,
                                     OclTypeBinding declared) {
        if (actual == null || declared == null) {
            return false;
        }
        if (!actual.isCollection() && "Void".equals(actual.typeName())) {
            return true;
        }
        if (!declared.isCollection() && !declared.isNode() && "OclAny".equals(declared.typeName())) {
            return true;
        }
        if (actual.isCollection() || declared.isCollection()) {
            if (!actual.isCollection() || !declared.isCollection()) {
                return false;
            }
            boolean compatibleKind = declared.collectionKind() == OclTypeBinding.CollectionKind.COLLECTION
                    || actual.collectionKind() == declared.collectionKind();
            return compatibleKind
                    && conformsTo(metamodelIndex, actual.elementType(), declared.elementType());
        }
        if (actual.isNode() || declared.isNode()) {
            if (!actual.isNode() || !declared.isNode()) {
                return false;
            }
            return metamodelIndex.classConformsTo(actual.typeName(), declared.typeName());
        }
        if (actual.kind() != declared.kind()) {
            return false;
        }
        return actual.typeName().equals(declared.typeName())
                || ("Integer".equals(actual.typeName()) && "Real".equals(declared.typeName()))
                || ("UnlimitedNatural".equals(actual.typeName()) && "Integer".equals(declared.typeName()));
    }
}
