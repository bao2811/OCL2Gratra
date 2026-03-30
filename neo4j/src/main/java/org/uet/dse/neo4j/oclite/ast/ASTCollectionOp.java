package org.uet.dse.neo4j.oclite.ast;

import java.util.List;

//(p1->size())
public class ASTCollectionOp extends ASTExpression {
    public ASTExpression source;
    public String opName;
    public List<ASTExpression> args;

    public ASTCollectionOp(ASTExpression source, String opName, List<ASTExpression> args) {
        this.source = source;
        this.opName = opName;
        this.args = args;
    }
}