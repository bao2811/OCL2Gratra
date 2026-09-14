package org.uet.dse.neo4j.oclite.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ASTFile extends ASTNode {
    public List<ASTNode> elements = new ArrayList<>();

    public void addElement(ASTNode element) {
        if (element != null) {
            elements.add(element);
        }
    }

    public List<ASTNode> elements() {
        return Collections.unmodifiableList(elements);
    }

    public List<ASTContext> invariants() {
        List<ASTContext> contexts = new ArrayList<>();
        for (ASTNode element : elements) {
            if (element instanceof ASTContext context) {
                contexts.add(context);
            }
        }
        return List.copyOf(contexts);
    }

    public List<ASTExpression> freeExpressions() {
        List<ASTExpression> expressions = new ArrayList<>();
        for (ASTNode element : elements) {
            if (element instanceof ASTExpression expression) {
                expressions.add(expression);
            }
        }
        return List.copyOf(expressions);
    }

    public List<ASTOperationConstraint> operationConstraints() {
        List<ASTOperationConstraint> constraints = new ArrayList<>();
        for (ASTNode element : elements) {
            if (element instanceof ASTOperationConstraint constraint) {
                constraints.add(constraint);
            }
        }
        return List.copyOf(constraints);
    }

    public List<ASTAttributeConstraint> attributeConstraints() {
        List<ASTAttributeConstraint> constraints = new ArrayList<>();
        for (ASTNode element : elements) {
            if (element instanceof ASTAttributeConstraint constraint) {
                constraints.add(constraint);
            }
        }
        return List.copyOf(constraints);
    }
}
