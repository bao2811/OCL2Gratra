package org.uet.dse.ocl2cypher.source.model;

import org.uet.dse.ocl2cypher.runtime.OclType;

/**
 * A resolved UML attribute-or-association binding produced by
 * {@code E_SM}'s elaboration. Like {@code ResolvedProperty} in
 * Typed-Core-OCL-IR.emf, its identity is the stable model key, not the
 * surface spelling.
 */
public sealed interface ResolvedProperty permits ResolvedProperty.Attribute, ResolvedProperty.AssociationEnd {

    String key();
    String qualifiedName();
    OclType declaredType();

    record Attribute(String key, String qualifiedName, String ownerClassKey, OclType declaredType)
            implements ResolvedProperty {
    }

    record AssociationEnd(String key,
                          String qualifiedName,
                          String sourceClassKey,
                          String targetClassKey,
                          String roleName,
                          boolean toMany,
                          OclType declaredType)
            implements ResolvedProperty {
    }
}
