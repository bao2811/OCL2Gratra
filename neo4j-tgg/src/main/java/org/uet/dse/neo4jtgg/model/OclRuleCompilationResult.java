package org.uet.dse.neo4jtgg.model;

import org.uet.dse.neo4jtgg.ocl.diagnostic.OclDiagnostic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OclRuleCompilationResult {
    private final OclRuleOwnerKind ownerKind;
    private final OclRuleKind ruleKind;
    private final String contextClassName;
    private final String operationName;
    private final String attributeName;
    private final String invariantName;
    private final CypherCompilationResult compilation;
    private final long compilationTimeMs;
    private final OclResultLocation resultLocation;
    private final List<String> requiredInputs;

    public OclRuleCompilationResult(String contextClassName, String invariantName, CypherCompilationResult compilation) {
        this(OclRuleOwnerKind.CLASS, OclRuleKind.INV, contextClassName, null, null, invariantName, compilation, 0L,
                inferResultLocation(contextClassName, invariantName, compilation), List.of());
    }

    public OclRuleCompilationResult(OclRuleOwnerKind ownerKind,
                                    OclRuleKind ruleKind,
                                    String contextClassName,
                                    String operationName,
                                    String attributeName,
                                    String invariantName,
                                    CypherCompilationResult compilation,
                                    long compilationTimeMs,
                                    OclResultLocation resultLocation,
                                    List<String> requiredInputs) {
        this.ownerKind = ownerKind;
        this.ruleKind = ruleKind;
        this.contextClassName = contextClassName;
        this.operationName = operationName;
        this.attributeName = attributeName;
        this.invariantName = invariantName;
        this.compilation = compilation;
        this.compilationTimeMs = compilationTimeMs;
        this.resultLocation = resultLocation;
        this.requiredInputs = List.copyOf(new ArrayList<>(requiredInputs != null ? requiredInputs : List.of()));
    }

    public OclRuleOwnerKind getOwnerKind() {
        return ownerKind;
    }

    public OclRuleKind getRuleKind() {
        return ruleKind;
    }

    public String getContextClassName() {
        return contextClassName;
    }

    public String getOperationName() {
        return operationName;
    }

    public String getAttributeName() {
        return attributeName;
    }

    public String getInvariantName() {
        return invariantName;
    }

    public CypherCompilationResult getCompilation() {
        return compilation;
    }

    public long getCompilationTimeMs() {
        return compilationTimeMs;
    }

    public OclResultLocation getResultLocation() {
        return resultLocation;
    }

    public List<String> getRequiredInputs() {
        return requiredInputs;
    }

    public boolean isSupported() {
        return compilation.isSupported();
    }

    public String getCypher() {
        return compilation.getCypher();
    }

    public Map<String, Object> getParameters() {
        return compilation.getParameters();
    }

    public String getReason() {
        return compilation.getReason();
    }

    public List<OclDiagnostic> getDiagnostics() {
        return compilation.getDiagnostics();
    }

    private static OclResultLocation inferResultLocation(String contextClassName,
                                                         String invariantName,
                                                         CypherCompilationResult compilation) {
        if (compilation != null && !compilation.getDiagnostics().isEmpty()) {
            return OclResultLocation.fromDiagnostic(contextClassName, invariantName, compilation.getDiagnostics().get(0));
        }
        return new OclResultLocation(contextClassName, invariantName, null, null, null, null, null, null, List.of());
    }
}
