package org.uet.dse.neo4j.oclite.ast;

import java.util.List;

/** Non-Set OCL collection literal retained for diagnostics/admission. */
public final class ASTCollectionLiteral extends ASTExpression {
    public final String kind;
    public final List<ASTExpression> elements;

    public ASTCollectionLiteral(String kind, List<ASTExpression> elements) {
        this.kind = kind;
        this.elements = List.copyOf(elements);
    }
}
