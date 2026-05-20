package org.uet.dse.neo4jtgg.ocl.diagnostic;

public record OclDiagnostic(
        OclDiagnosticPhase phase,
        OclDiagnosticCode code,
        String message,
        Integer line,
        Integer column,
        Integer endLine,
        Integer endColumn,
        String tokenText,
        String sourceSnippet) {
    public OclDiagnostic(OclDiagnosticPhase phase,
                         OclDiagnosticCode code,
                         String message,
                         Integer line,
                         Integer column,
                         Integer endLine,
                         Integer endColumn) {
        this(phase, code, message, line, column, endLine, endColumn, null, null);
    }

    public OclDiagnostic(OclDiagnosticPhase phase,
                         String message,
                         Integer line,
                         Integer column,
                         Integer endLine,
                         Integer endColumn) {
        this(phase, OclDiagnosticCode.GENERIC_FAILURE, message, line, column, endLine, endColumn, null, null);
    }

    public String toUserMessage() {
        if (line != null && column != null && endLine != null && endColumn != null) {
            if (line.equals(endLine) && column.equals(endColumn)) {
                return phase.name() + ": line " + line + ", column " + column + ": " + message;
            }
            return phase.name() + ": line " + line + ", column " + column +
                    " to line " + endLine + ", column " + endColumn + ": " + message;
        }
        if (line != null && column != null) {
            return phase.name() + ": line " + line + ", column " + column + ": " + message;
        }
        return phase.name() + ": " + message;
    }
}
