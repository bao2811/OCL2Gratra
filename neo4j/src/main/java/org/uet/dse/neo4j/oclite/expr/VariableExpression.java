package org.uet.dse.neo4j.oclite.expr;


public class VariableExpression implements ExpressionNode {
    private final String name;
    private String className;

    public VariableExpression(String name) {
        this.name = name;
    }

    @Override
    public Object evaluate(ExecutionContext ctx) {
        return ctx.resolveVariable(this.name);
    }

    public String getVarName() {
        return this.name;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getClassName() {
        return this.className;
    }

    @Override
    public String toString() {
        return name + (className != null ? ":" + className : "");
    }
}