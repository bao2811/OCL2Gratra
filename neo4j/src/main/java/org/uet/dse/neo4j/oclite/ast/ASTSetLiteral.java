package org.uet.dse.neo4j.oclite.ast;

import java.util.List;

/** Finite OCL Set literal whose element type is resolved by semantic binding. */
public class ASTSetLiteral extends ASTExpression {
    public final List<ASTExpression> elements;

    public ASTSetLiteral(List<ASTExpression> elements) {
        this.elements = List.copyOf(elements);
    }
}
