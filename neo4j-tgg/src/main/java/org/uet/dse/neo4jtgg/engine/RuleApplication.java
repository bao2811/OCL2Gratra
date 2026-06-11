package org.uet.dse.neo4jtgg.engine;

import java.util.List;
import java.util.Map;

/**
 * Records a single rule application: which rule was applied, with which variable bindings
 * on source/target/correspondence sides. Used for traceability and conflict detection.
 */
public record RuleApplication(
        String ruleName,
        Map<String, String> sourceBinding,
        Map<String, String> targetBinding,
        Map<String, String> corrBinding,
        TransformationDirection direction,
        long appliedAt) {

    public RuleApplication {
        sourceBinding = sourceBinding != null ? Map.copyOf(sourceBinding) : Map.of();
        targetBinding = targetBinding != null ? Map.copyOf(targetBinding) : Map.of();
        corrBinding = corrBinding != null ? Map.copyOf(corrBinding) : Map.of();
    }

    public String toDisplayText() {
        return "Rule `" + ruleName + "` [" + direction + "] src=" + sourceBinding
                + " tgt=" + targetBinding + " corr=" + corrBinding;
    }
}
