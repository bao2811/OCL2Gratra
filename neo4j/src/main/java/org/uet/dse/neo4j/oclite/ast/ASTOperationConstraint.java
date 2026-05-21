package org.uet.dse.neo4j.oclite.ast;

import java.util.ArrayList;
import java.util.List;

public class ASTOperationConstraint extends ASTNode {
    public String className;
    public String operationName;
    public List<String> parameterNames;
    public List<String> parameterTypes;
    public String constraintKind;
    public String ruleName;
    public ASTExpression expression;

    public ASTOperationConstraint(String className,
                                  String operationName,
                                  List<String> parameterNames,
                                  List<String> parameterTypes,
                                  String constraintKind,
                                  String ruleName,
                                  ASTExpression expression) {
        this.className = className;
        this.operationName = operationName;
        this.parameterNames = new ArrayList<>(parameterNames != null ? parameterNames : List.of());
        this.parameterTypes = new ArrayList<>(parameterTypes != null ? parameterTypes : List.of());
        this.constraintKind = constraintKind;
        this.ruleName = ruleName;
        this.expression = expression;
    }
}
