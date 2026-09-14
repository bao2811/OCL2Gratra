package org.uet.dse.ocl2cypher.core;

import java.util.List;
import java.util.Objects;
import org.uet.dse.ocl2cypher.runtime.OclType;

/**
 * A typed invariant/query unit with one context and one body.
 *
 * <p>Invariant evaluation denies any non-{@code T} body value: {@code F} and
 * whole-collection bottom both count as violations, so the violation-set
 * equality involves a {@code ne T} guard, not a {@code eq F} check.
 */
public abstract sealed class CoreUnit permits CoreInvariant, CoreQuery {

    public enum Mode {
        INVARIANT,
        QUERY_VALUE
    }

    private final String invariantName;
    private final String contextClassKey;
    private final CoreDeclaration selfVariable;
    private final Mode mode;
    private final CoreExpr body;

    protected CoreUnit(String invariantName,
                       String contextClassKey,
                       CoreDeclaration selfVariable,
                       Mode mode,
                       CoreExpr body) {
        this.invariantName = invariantName;
        this.mode = Objects.requireNonNull(mode, "mode");
        if ((contextClassKey == null) != (selfVariable == null)) {
            throw new IllegalArgumentException(
                    "context class and self variable must be present together");
        }
        if (mode != Mode.QUERY_VALUE && contextClassKey == null) {
            throw new IllegalArgumentException("invariant/violation unit requires a context");
        }
        this.contextClassKey = contextClassKey;
        this.selfVariable = selfVariable;
        this.body = Objects.requireNonNull(body, "body");
    }

    public String invariantName() {
        return invariantName;
    }

    public String contextClassKey() {
        return contextClassKey;
    }

    public CoreDeclaration selfVariable() {
        return selfVariable;
    }

    public Mode mode() {
        return mode;
    }

    public CoreExpr body() {
        return body;
    }

    public boolean isInvariant() {
        return this instanceof CoreInvariant;
    }

    @Override
    public String toString() {
        return (mode == Mode.QUERY_VALUE ? "query" :
                (invariantName == null ? "inv" : "inv " + invariantName))
                + (contextClassKey == null ? "" : " context " + contextClassKey)
                + " mode " + mode
                + " body " + body;
    }
}
