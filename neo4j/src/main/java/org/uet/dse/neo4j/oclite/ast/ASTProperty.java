package org.uet.dse.neo4j.oclite.ast;

import java.util.List;

public class ASTProperty extends ASTExpression {
    public ASTExpression source;
    public String name;
    public List<ASTExpression> qualifiers;
    public boolean atPre;

    public ASTProperty(ASTExpression s, String n) {
        this(s, n, List.of());
    }

    public ASTProperty(ASTExpression s, String n, List<ASTExpression> qualifiers) {
        this(s, n, qualifiers, false);
    }

    public ASTProperty(ASTExpression s, String n, List<ASTExpression> qualifiers, boolean atPre) {
        this.source = s;
        this.name = n;
        this.qualifiers = qualifiers == null ? List.of() : List.copyOf(qualifiers);
        this.atPre = atPre;
    }

    public boolean hasQualifiers() {
        return !qualifiers.isEmpty();
    }
}
