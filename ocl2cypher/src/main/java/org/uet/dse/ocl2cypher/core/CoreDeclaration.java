package org.uet.dse.ocl2cypher.core;

import java.util.Objects;
import org.uet.dse.ocl2cypher.runtime.OclType;

/**
 * A single declaration binder inside one {@link CoreUnit}.
 *
 * <p>Identity is reference equality of this object (callers may compare by
 * Java identity or by the stable per-unit counter). The visible surface name
 * is diagnostic metadata; the binding identity is the declaration itself.
 */
public final class CoreDeclaration {

    public enum Kind {
        SELF,
        LET,
        ITERATOR,
        PARAMETER
    }

    private final int id;
    private final String name;
    private final Kind kind;
    private final OclType type;

    public CoreDeclaration(int id, String name, Kind kind, OclType type) {
        this.id = id;
        this.name = Objects.requireNonNull(name, "name");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.type = Objects.requireNonNull(type, "type");
    }

    public int id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Kind kind() {
        return kind;
    }

    public OclType type() {
        return type;
    }

    @Override
    public String toString() {
        return kind + "(" + name + ":" + type + "#" + id + ")";
    }
}
