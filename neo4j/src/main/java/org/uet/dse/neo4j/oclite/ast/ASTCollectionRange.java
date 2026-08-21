package org.uet.dse.neo4j.oclite.ast;

/** A collection-literal range such as 1..10. */
public final class ASTCollectionRange extends ASTExpression {
    public final ASTExpression first;
    public final ASTExpression last;

    public ASTCollectionRange(ASTExpression first, ASTExpression last) {
        this.first = first;
        this.last = last;
    }
}
