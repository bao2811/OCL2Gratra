package org.uet.dse.neo4j.oclite.expr;

import org.uet.dse.neo4j.oclite.Neo4jRepository;
import org.uet.dse.neo4j.oclite.ast.*;


import org.uet.dse.neo4j.oclite.ast.*;

import java.util.*;

public class ExpressionBinder {
    private String modelName;
    private final Deque<Set<String>> scopeStack = new ArrayDeque<>();
    private Neo4jRepository neo4jRepository;
    public ExpressionBinder(String modelName) {
        this.neo4jRepository = new Neo4jRepository(modelName);
        this.modelName = modelName;
        scopeStack.push(new HashSet<>());
    }
    private void enterScope(String varName) {
        Set<String> newScope = new HashSet<>();
        newScope.add(varName);
        scopeStack.push(newScope);
    }

    private void exitScope() {
        scopeStack.pop();
    }

    private boolean isLocalVar(String name) {
        for (Set<String> scope : scopeStack) {
            if (scope.contains(name)) return true;
        }
        return false;
    }

    public ExpressionNode bind(ASTNode node) {
//        if (node instanceof ASTLiteral) {
//            return new ConstantExpression(((ASTLiteral) node).value);
//        }

        //p1
        if (node instanceof ASTVar) {
            String varName = ((ASTVar) node).name;

            if (varName.equals("self") || isLocalVar(varName)) {
                return new VariableExpression(varName);
            }
            if (isLocalVar(varName)) {
                return new VariableExpression(varName);
            }

            if (neo4jRepository.checkClassExistsInDb(varName)) {
                return new TypeExpression(varName);
            }
            if (neo4jRepository.isVariableExist(varName)) {
                return new VariableExpression(varName);
            }
            throw new RuntimeException("Semantic Error: Đối tượng '" + varName + "' không tồn tại trong Neo4j.");
        }

        if (node instanceof ASTMethodCall) {
            ASTMethodCall mc = (ASTMethodCall) node;
            ExpressionNode sourceExec = bind(mc.source);

            if (mc.methodName.equalsIgnoreCase("allInstances")) {
                // Kiểm tra xem vế trước có phải là một Class không
                if (sourceExec instanceof TypeExpression) {
                    String className = ((TypeExpression) sourceExec).getTypeName();
                    return new AllInstancesExpression(className);
                } else {
                    throw new RuntimeException("Lỗi: allInstances() chỉ được gọi trên một Class.");
                }
            }
            String mName = mc.methodName;
            if (mName.equals("isDefined")) {
                return new IsDefinedExpression(sourceExec, true);
            }
            else if (mName.equals("isUndefined")) {
                return new IsDefinedExpression(sourceExec, false);
            }


        }

        //(.)
        if (node instanceof ASTProperty) {
            ASTProperty prop = (ASTProperty) node;
            ExpressionNode sourceNode = bind(prop.source);

            if (neo4jRepository.isRelationship(sourceNode, prop.name)) {
                return new NavigationExpression(sourceNode, prop.name);
            } else {
                return new PropertyExpression(sourceNode, prop.name);
            }
        }

//        if (node instanceof ASTBinary) {
//            ASTBinary bin = (ASTBinary) node;
//            ExpressionNode left = bind(bin.left);
//            ExpressionNode right = bind(bin.right);
//            return new BinaryExpression(left, bin.op, right);
//        }
        if (node instanceof ASTBinary) {
            ASTBinary bin = (ASTBinary) node;
            ExpressionNode left = bind(bin.left);
            ExpressionNode right = bind(bin.right);

            if (bin.op.equals(">") && (bin.right instanceof ASTStringLiteral)) {
                throw new RuntimeException("nah");
            }

            return new BinaryExpression(left, bin.op, right);
        }

        if (node instanceof ASTIntegerLiteral) {
            return new ConstantExpression(((ASTIntegerLiteral) node).value);
        }

        if (node instanceof ASTStringLiteral) {
            return new ConstantExpression(((ASTStringLiteral) node).value);
        }

        if (node instanceof ASTBooleanLiteral) {
            return new ConstantExpression(((ASTBooleanLiteral)node).value);
        }

        if (node instanceof ASTNullLiteral) {
            return new ConstantExpression(null);
        }

        if (node instanceof ASTCollectionOp) {
            ASTCollectionOp colAST = (ASTCollectionOp) node;
            ExpressionNode sourceNode = bind(colAST.source);

            List<ExpressionNode> bindedArgs = new ArrayList<>();
            for (ASTNode arg : colAST.args) {
                bindedArgs.add((ExpressionNode) bind(arg));
            }

            return new CollectionOpExpression(sourceNode, colAST.opName, bindedArgs);
        }

        if (node instanceof ASTIterator) {
            ASTIterator itAST = (ASTIterator) node;
            ExpressionNode sourceNode = bind(itAST.source);
            enterScope(itAST.iteratorName);
            ExpressionNode bodyNode = bind(itAST.body);
            exitScope();

            switch (itAST.operation.toLowerCase()) {
                case "select":
                    return new SelectExpression(sourceNode, itAST.iteratorName, bodyNode);
                case "forall":
                    return new ForAllExpression(sourceNode, itAST.iteratorName, bodyNode);
                case "exists":
                    return new ExistsExpression(sourceNode, itAST.iteratorName, bodyNode);
                case "collect":
                    return new CollectExpression(sourceNode, itAST.iteratorName, bodyNode);
                case "any":
                    return new AnyExpression(sourceNode, itAST.iteratorName, bodyNode);
                default:
                    throw new RuntimeException("Chưa hỗ trợ Iterator: " + itAST.operation);
            }
        }
        if (node instanceof ASTNot) {
            ASTNot notAST = (ASTNot) node;
            ExpressionNode innerExec = bind(notAST.expression);
            return new NotExpression(innerExec);
        }


        throw new RuntimeException("Không hỗ trợ chuyển đổi Node: " + node.getClass().getSimpleName());
    }

}
