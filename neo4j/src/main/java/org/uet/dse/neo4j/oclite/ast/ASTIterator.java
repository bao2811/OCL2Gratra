package org.uet.dse.neo4j.oclite.ast;

import java.util.List;

public class ASTIterator extends ASTExpression {
    public ASTExpression source;
    public String iteratorName;
    public String iteratorTypeName;
    public String operation;
    public ASTExpression body;
    public List<ASTVariableDeclaration> iteratorVariables;

    public ASTIterator(ASTExpression source, String op, String var, ASTExpression body) {
        this(source, op, var, null, body);
    }

    public ASTIterator(ASTExpression source, String op, String var, String typeName, ASTExpression body) {
        this(source, op, List.of(new ASTVariableDeclaration(var, typeName)), body);
    }

    public ASTIterator(ASTExpression source, String op,
                       List<ASTVariableDeclaration> iteratorVariables, ASTExpression body) {
        this.source = source;
        this.operation = op;
        this.iteratorVariables = List.copyOf(iteratorVariables);
        ASTVariableDeclaration first = this.iteratorVariables.isEmpty()
                ? new ASTVariableDeclaration("", null)
                : this.iteratorVariables.get(0);
        this.iteratorName = first.name();
        this.iteratorTypeName = first.typeName();
        this.body = body;
    }
}
