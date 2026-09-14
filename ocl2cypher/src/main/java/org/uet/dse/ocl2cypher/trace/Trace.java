package org.uet.dse.ocl2cypher.trace;

import java.util.Objects;

/**
 * Logical trace link from a source element to a target element via one rule.
 *
 * <p>Every successful rule application produces a trace so that later stages
 * can expose {@code sourceId → ruleId → targetId} chains and so that Rule 08
 * traceability is closed without ever attaching a trace to a failed result.
 */
public record Trace(Stage stage, String sourceId, String targetId, String ruleId) {

    public Trace {
        Objects.requireNonNull(stage, "trace stage");
        Objects.requireNonNull(sourceId, "trace source");
        Objects.requireNonNull(targetId, "trace target");
        Objects.requireNonNull(ruleId, "trace rule");
    }

    public enum Stage {
        E_SM,
        N_SM,
        T_G,
        R,
        S
    }
}
