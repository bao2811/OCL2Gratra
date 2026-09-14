package org.uet.dse.neo4jtgg.service;

import java.util.Map;

import org.uet.dse.neo4jtgg.model.OclFileValidationResult;
import org.uet.dse.neo4jtgg.model.OclOperationRuleInputs;
import org.uet.dse.neo4jtgg.model.OclRuleValidationResult;
import org.uet.dse.neo4jtgg.model.OclValidationResult;
import org.uet.dse.neo4jtgg.model.TggWorkspaceContext;
import org.uet.dse.neo4jtgg.model.WorkspaceSide;

public interface OclValidationService {
    OclValidationResult validate(TggWorkspaceContext context, WorkspaceSide side, String oclExpression);
    OclFileValidationResult validateFile(TggWorkspaceContext context, WorkspaceSide side, String oclText);
    OclFileValidationResult validateFile(TggWorkspaceContext context, WorkspaceSide side, String oclText,
                                         Map<String, Map<String, Object>> ruleParameterValues);
    OclFileValidationResult validateFile(TggWorkspaceContext context, WorkspaceSide side, String oclText,
                                         Map<String, Map<String, Object>> ruleParameterValues,
                                         boolean dualCheck);
    OclRuleValidationResult validateRule(TggWorkspaceContext context, WorkspaceSide side, String oclText,
                                         String contextClassName, String ruleName);
    OclRuleValidationResult validateRule(TggWorkspaceContext context, WorkspaceSide side, String oclText,
                                         String contextClassName, String ruleName,
                                         Map<String, Object> parameterValues);
    OclRuleValidationResult validateRule(TggWorkspaceContext context, WorkspaceSide side, String oclText,
                                         String contextClassName, String ruleName,
                                         Map<String, Object> parameterValues,
                                         boolean dualCheck);
    OclRuleValidationResult validateOperationRule(TggWorkspaceContext context, WorkspaceSide side, String oclText,
                                                  String contextClassName, String operationName, String ruleName,
                                                  Map<String, Object> parameterValues);
    OclRuleValidationResult validateOperationRule(TggWorkspaceContext context, WorkspaceSide side, String oclText,
                                                  String contextClassName, String operationName, String ruleName,
                                                  OclOperationRuleInputs inputs);
    OclRuleValidationResult validateOperationRule(TggWorkspaceContext context, WorkspaceSide side, String oclText,
                                                  String contextClassName, String operationName, String ruleName,
                                                  OclOperationRuleInputs inputs,
                                                  boolean dualCheck);
}
