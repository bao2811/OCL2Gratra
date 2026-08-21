package org.uet.dse.neo4j.oclite.ast;

import java.util.List;

/** Broad surface node for OCL iterate; not currently admitted by OCL_val. */
public final class ASTIterate extends ASTExpression {
    public final ASTExpression source;
    public final String operation;
    public final List<ASTVariableDeclaration> iteratorVariables;
    public final ASTVariableDeclaration accumulator;
    public final ASTExpression initialValue;
    public final ASTExpression body;

    public ASTIterate(ASTExpression source, String operation,
                      List<ASTVariableDeclaration> iteratorVariables,
                      ASTVariableDeclaration accumulator,
                      ASTExpression initialValue,
                      ASTExpression body) {
        this.source = source;
        this.operation = operation;
        this.iteratorVariables = List.copyOf(iteratorVariables);
        this.accumulator = accumulator;
        this.initialValue = initialValue;
        this.body = body;
    }
}
