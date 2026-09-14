package org.uet.dse.ocl2cypher.core;

/** Typed Core carrier for an independent VALUE expression with optional object context. */
public final class CoreQuery extends CoreUnit {

    public CoreQuery(String contextClassKey, CoreDeclaration contextVariable, CoreExpr body) {
        super(null, contextClassKey, contextVariable, Mode.QUERY_VALUE, body);
    }
}
