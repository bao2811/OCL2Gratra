package org.uet.dse.neo4jtgg.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class OclOperationRuleInputs {
    private final String currentSelfObjectId;
    private final String preSelfObjectId;
    private final Object resultValue;
    private final Map<String, Object> parameterValues;

    public OclOperationRuleInputs(Map<String, Object> parameterValues) {
        this(null, null, null, parameterValues);
    }

    public OclOperationRuleInputs(String currentSelfObjectId,
                                  String preSelfObjectId,
                                  Object resultValue,
                                  Map<String, Object> parameterValues) {
        this.currentSelfObjectId = currentSelfObjectId;
        this.preSelfObjectId = preSelfObjectId;
        this.resultValue = resultValue;
        this.parameterValues = Map.copyOf(new LinkedHashMap<>(parameterValues != null ? parameterValues : Map.of()));
    }

    public String getCurrentSelfObjectId() {
        return currentSelfObjectId;
    }

    public String getPreSelfObjectId() {
        return preSelfObjectId;
    }

    public Object getResultValue() {
        return resultValue;
    }

    public Map<String, Object> getParameterValues() {
        return parameterValues;
    }
}
