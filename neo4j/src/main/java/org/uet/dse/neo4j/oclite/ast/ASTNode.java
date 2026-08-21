package org.uet.dse.neo4j.oclite.ast;

public abstract class ASTNode {
    private SourceSpan sourceSpan = SourceSpan.UNKNOWN;

    public final SourceSpan sourceSpan() {
        return sourceSpan;
    }

    public final void setSourceSpan(SourceSpan sourceSpan) {
        this.sourceSpan = sourceSpan == null ? SourceSpan.UNKNOWN : sourceSpan;
    }
}
