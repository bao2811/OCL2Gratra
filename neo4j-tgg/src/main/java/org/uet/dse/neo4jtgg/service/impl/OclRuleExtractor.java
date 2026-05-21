package org.uet.dse.neo4jtgg.service.impl;

import org.uet.dse.neo4j.oclite.ast.ASTContext;
import org.uet.dse.neo4j.oclite.ast.ASTExpression;
import org.uet.dse.neo4j.oclite.ast.ASTFile;
import org.uet.dse.neo4j.oclite.ast.ASTAttributeConstraint;
import org.uet.dse.neo4j.oclite.ast.ASTOperationConstraint;
import org.uet.dse.neo4jtgg.model.OclRuleDescriptor;

import java.util.ArrayList;
import java.util.List;

final class OclRuleExtractor {
    private OclRuleExtractor() {
    }

    static List<OclRuleDescriptor> extractRules(ASTFile astFile) {
        List<OclRuleDescriptor> rules = new ArrayList<>();
        for (ASTContext context : astFile.invariants()) {
            rules.add(OclRuleDescriptor.forInvariant(context));
        }
        for (ASTOperationConstraint constraint : astFile.operationConstraints()) {
            rules.add(OclRuleDescriptor.forOperationConstraint(constraint));
        }
        for (ASTAttributeConstraint constraint : astFile.attributeConstraints()) {
            rules.add(OclRuleDescriptor.forAttributeConstraint(constraint));
        }
        int expressionIndex = 1;
        for (ASTExpression expression : astFile.freeExpressions()) {
            rules.add(OclRuleDescriptor.forFreeExpression("expression#" + expressionIndex++, expression));
        }
        return List.copyOf(rules);
    }
}
