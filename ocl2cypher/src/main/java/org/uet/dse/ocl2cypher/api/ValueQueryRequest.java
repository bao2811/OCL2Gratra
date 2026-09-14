package org.uet.dse.ocl2cypher.api;

import java.util.Objects;
import org.uet.dse.ocl2cypher.runtime.OclType;

/** Immutable public request for an independent OCL value expression. */
public record ValueQueryRequest(String expression,
                                String contextClassKey,
                                String contextVariableName,
                                OclType expectedResultType) {

    /** Backward-compatible form with no declared result-type contract. */
    public ValueQueryRequest(String expression, String contextClassKey,
                             String contextVariableName) {
        this(expression, contextClassKey, contextVariableName, null);
    }

    public ValueQueryRequest {
        Objects.requireNonNull(expression, "expression");
        if (expression.isBlank()) {
            throw new IllegalArgumentException("value-query expression must not be blank");
        }
        if ((contextClassKey == null) != (contextVariableName == null)) {
            throw new IllegalArgumentException(
                    "context class and variable must be present together");
        }
        if (contextClassKey != null
                && (contextClassKey.isBlank() || contextVariableName.isBlank())) {
            throw new IllegalArgumentException("value-query context must not be blank");
        }
    }

    public static ValueQueryRequest contextless(String expression) {
        return new ValueQueryRequest(expression, null, null, null);
    }

    public static ValueQueryRequest contextual(String expression, String contextClassKey) {
        return new ValueQueryRequest(expression, contextClassKey, "self", null);
    }

    /** Return a request that requires {@code E_SM} to infer exactly this result type. */
    public ValueQueryRequest expecting(OclType resultType) {
        return new ValueQueryRequest(expression, contextClassKey, contextVariableName,
                Objects.requireNonNull(resultType, "expected result type"));
    }
}
