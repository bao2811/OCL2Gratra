package org.uet.dse.ocl2cypher.core;

/** Typed Core carrier for one class invariant; only this carrier may request violations. */
public final class CoreInvariant extends CoreUnit {

    public CoreInvariant(String name, String contextClassKey,
                         CoreDeclaration selfVariable, CoreExpr body) {
        super(name, contextClassKey, selfVariable, Mode.INVARIANT, body);
    }
}
