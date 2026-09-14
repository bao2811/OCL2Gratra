package org.uet.dse.ocl2cypher.diagnostics;

/**
 * Offset/line span into the original OCL text.
 *
 * <p>Source spans are frontend metadata only. They never carry OCL semantics,
 * so a missing span must not change any admission or evaluation decision.
 */
public record SourceSpan(int startOffset, int endOffset, int startLine, int startColumn) {

    public static final SourceSpan UNKNOWN = new SourceSpan(-1, -1, -1, -1);

    public boolean isKnown() {
        return startOffset >= 0;
    }

    @Override
    public String toString() {
        if (!isKnown()) {
            return "<unknown>";
        }
        return startLine + ":" + startColumn + "(" + startOffset + ".." + endOffset + ")";
    }
}
