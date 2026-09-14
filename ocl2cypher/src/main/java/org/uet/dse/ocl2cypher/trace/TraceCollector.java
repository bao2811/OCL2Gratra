package org.uet.dse.ocl2cypher.trace;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.uet.dse.ocl2cypher.diagnostics.SourceSpan;

/**
 * Append-only sidecar for successful compiler-rule applications.
 *
 * <p>The collector is deliberately not embedded in semantic IR objects. This
 * keeps tracing observational: enabling it cannot alter typing, normalization,
 * target equality, or serialized Cypher text.
 */
public final class TraceCollector {

    private final List<Trace> traces = new ArrayList<>();

    public void record(Trace.Stage stage, String sourceId, String targetId, String ruleId) {
        Objects.requireNonNull(stage, "trace stage");
        requireId(sourceId, "source id");
        requireId(targetId, "target id");
        requireId(ruleId, "rule id");
        traces.add(new Trace(stage, sourceId, targetId, ruleId));
    }

    public void record(Trace.Stage stage, SourceSpan sourceSpan,
                       String targetId, String ruleId) {
        record(stage, sourceId(sourceSpan), targetId, ruleId);
    }

    public List<Trace> traces() {
        return List.copyOf(traces);
    }

    public static String sourceId(SourceSpan span) {
        SourceSpan actual = span == null ? SourceSpan.UNKNOWN : span;
        return "ocl:" + actual;
    }

    private static void requireId(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must be non-blank");
        }
    }
}
