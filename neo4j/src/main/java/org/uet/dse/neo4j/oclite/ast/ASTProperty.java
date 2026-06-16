package org.uet.dse.neo4j.oclite.ast;

import java.util.List;

public class ASTProperty extends ASTExpression {
    public ASTExpression source;
    public String name;
    public List<ASTExpression> qualifiers;

    public ASTProperty(ASTExpression s, String n) {
        this(s, n, List.of());
    }

    public ASTProperty(ASTExpression s, String n, List<ASTExpression> qualifiers) {
        this.source = s;
        this.name = n;
        this.qualifiers = qualifiers == null ? List.of() : List.copyOf(qualifiers);
    }

    public boolean hasQualifiers() {
        return !qualifiers.isEmpty();
    }
}
