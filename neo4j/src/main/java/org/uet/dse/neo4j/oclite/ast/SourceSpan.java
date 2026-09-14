package org.uet.dse.neo4j.oclite.ast;

/** Inclusive source coordinates attached to every parser-produced AST node. */
public record SourceSpan(
        int startOffset,
        int endOffset,
        int startLine,
        int startColumn,
        int endLine,
        int endColumn) {

    public static final SourceSpan UNKNOWN = new SourceSpan(-1, -1, -1, -1, -1, -1);

    public boolean isKnown() {
        return startOffset >= 0 && startLine >= 1 && startColumn >= 0;
    }
}
