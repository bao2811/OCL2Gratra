package org.uet.dse.neo4jtgg.experiment;

import org.tzi.use.uml.sys.MSystem;
import org.uet.dse.neo4j.oclite.ast.ASTContext;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Concrete two-sided oracle for Theorem 6 experiments. The reference side is
 * evaluated over the USE object model; the candidate side executes generated
 * Cypher and returns stable object IDs.
 */
public final class ViolationSetOracle {
    private final ObjectSideReferenceEvaluator referenceEvaluator;
    private final CypherViolationSetEvaluator cypherEvaluator;

    public ViolationSetOracle(ObjectSideReferenceEvaluator referenceEvaluator,
                              CypherViolationSetEvaluator cypherEvaluator) {
        this.referenceEvaluator = Objects.requireNonNull(referenceEvaluator, "referenceEvaluator");
        this.cypherEvaluator = Objects.requireNonNull(cypherEvaluator, "cypherEvaluator");
    }

    public ViolationOracleResult evaluate(String caseId,
                                          Set<String> contextIds,
                                          MSystem system,
                                          ASTContext invariant,
                                          String cypher,
                                          Map<String, Object> parameters) {
        return evaluate(caseId, contextIds, system, invariant, null, cypher, parameters);
    }

    public ViolationOracleResult evaluate(String caseId,
                                          Set<String> contextIds,
                                          MSystem system,
                                          ASTContext invariant,
                                          String originalExpressionSource,
                                          String cypher,
                                          Map<String, Object> parameters) {
        Set<String> referenceIds = Set.of();
        Set<String> cypherIds = Set.of();
        String referenceError = "";
        String cypherError = "";
        try {
            referenceIds = Objects.requireNonNull(
                    originalExpressionSource == null
                            ? referenceEvaluator.violationIds(system, invariant)
                            : referenceEvaluator.violationIds(system, invariant, originalExpressionSource),
                    "reference evaluator result");
        } catch (RuntimeException exception) {
            referenceError = describe(exception);
        }
        try {
            cypherIds = Objects.requireNonNull(
                    cypherEvaluator.violationIds(cypher, parameters), "Cypher evaluator result");
        } catch (RuntimeException exception) {
            cypherError = describe(exception);
        }

        if (!referenceError.isEmpty() || !cypherError.isEmpty()) {
            ViolationOracleStatus errorStatus = !referenceError.isEmpty() && !cypherError.isEmpty()
                    ? ViolationOracleStatus.BOTH_ERROR
                    : (!referenceError.isEmpty()
                    ? ViolationOracleStatus.REFERENCE_ERROR : ViolationOracleStatus.CYPHER_ERROR);
            return new ViolationOracleResult(caseId, contextIds,
                    DifferentialResult.compare(referenceIds, cypherIds), errorStatus,
                    referenceError, cypherError);
        }
        return compare(caseId, contextIds, referenceIds, cypherIds);
    }

    public static ViolationOracleResult compare(String caseId,
                                                Set<String> contextIds,
                                                Set<String> referenceIds,
                                                Set<String> cypherIds) {
        DifferentialResult differential = DifferentialResult.compare(referenceIds, cypherIds);
        ViolationOracleStatus status;
        if (differential.referenceOnlyIds().isEmpty() && differential.cypherOnlyIds().isEmpty()) {
            status = ViolationOracleStatus.EQUIVALENT;
        } else if (!differential.referenceOnlyIds().isEmpty()
                && differential.cypherOnlyIds().isEmpty()) {
            status = ViolationOracleStatus.MISSING_ONLY;
        } else if (differential.referenceOnlyIds().isEmpty()) {
            status = ViolationOracleStatus.SPURIOUS_ONLY;
        } else {
            status = ViolationOracleStatus.INCOMPARABLE;
        }
        return new ViolationOracleResult(caseId, contextIds, differential, status, "", "");
    }

    private static String describe(RuntimeException exception) {
        String message = exception.getMessage();
        return exception.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }
}
