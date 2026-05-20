package org.uet.dse.neo4jtgg.ocl.diagnostic;

public class OclCompilationException extends RuntimeException {
    private final OclDiagnosticPhase phase;
    private final OclDiagnosticCode code;
    private final Integer line;
    private final Integer column;
    private final Integer endLine;
    private final Integer endColumn;
    private final String tokenText;
    private final String sourceSnippet;

    public OclCompilationException(OclDiagnosticPhase phase, String message) {
        super(message);
        this.phase = phase;
        this.code = OclDiagnosticCode.GENERIC_FAILURE;
        this.line = null;
        this.column = null;
        this.endLine = null;
        this.endColumn = null;
        this.tokenText = null;
        this.sourceSnippet = null;
    }

    public OclCompilationException(OclDiagnosticPhase phase, String message, Throwable cause) {
        super(message, cause);
        this.phase = phase;
        this.code = OclDiagnosticCode.GENERIC_FAILURE;
        this.line = null;
        this.column = null;
        this.endLine = null;
        this.endColumn = null;
        this.tokenText = null;
        this.sourceSnippet = null;
    }

    public OclCompilationException(OclDiagnosticPhase phase, String message, Integer line, Integer column) {
        this(phase, OclDiagnosticCode.GENERIC_FAILURE, message, line, column, line, column, null, null, null);
    }

    public OclCompilationException(OclDiagnosticPhase phase, String message, Integer line, Integer column, Throwable cause) {
        this(phase, OclDiagnosticCode.GENERIC_FAILURE, message, line, column, line, column, null, null, cause);
    }

    public OclCompilationException(OclDiagnosticPhase phase, String message,
                                   Integer line, Integer column,
                                   Integer endLine, Integer endColumn) {
        this(phase, OclDiagnosticCode.GENERIC_FAILURE, message, line, column, endLine, endColumn, null, null, null);
    }

    public OclCompilationException(OclDiagnosticPhase phase, String message,
                                   Integer line, Integer column,
                                   Integer endLine, Integer endColumn,
                                   String tokenText, String sourceSnippet) {
        this(phase, OclDiagnosticCode.GENERIC_FAILURE, message, line, column, endLine, endColumn, tokenText, sourceSnippet, null);
    }

    public OclCompilationException(OclDiagnosticPhase phase, OclDiagnosticCode code, String message) {
        this(phase, code, message, null, null, null, null, null, null, null);
    }

    public OclCompilationException(OclDiagnosticPhase phase, OclDiagnosticCode code, String message, Throwable cause) {
        this(phase, code, message, null, null, null, null, null, null, cause);
    }

    public OclCompilationException(OclDiagnosticPhase phase, OclDiagnosticCode code, String message,
                                   Integer line, Integer column,
                                   Integer endLine, Integer endColumn,
                                   String tokenText, String sourceSnippet) {
        this(phase, code, message, line, column, endLine, endColumn, tokenText, sourceSnippet, null);
    }

    public OclCompilationException(OclDiagnosticPhase phase, String message,
                                   Integer line, Integer column,
                                   Integer endLine, Integer endColumn,
                                   String tokenText, String sourceSnippet,
                                   Throwable cause) {
        this(phase, OclDiagnosticCode.GENERIC_FAILURE, message, line, column, endLine, endColumn, tokenText, sourceSnippet, cause);
    }

    public OclCompilationException(OclDiagnosticPhase phase, OclDiagnosticCode code, String message,
                                   Integer line, Integer column,
                                   Integer endLine, Integer endColumn,
                                   String tokenText, String sourceSnippet,
                                   Throwable cause) {
        super(message, cause);
        this.phase = phase;
        this.code = code;
        this.line = line;
        this.column = column;
        this.endLine = endLine;
        this.endColumn = endColumn;
        this.tokenText = tokenText;
        this.sourceSnippet = sourceSnippet;
    }

    public OclDiagnosticPhase phase() {
        return phase;
    }

    public Integer line() {
        return line;
    }

    public OclDiagnosticCode code() {
        return code;
    }

    public Integer column() {
        return column;
    }

    public Integer endLine() {
        return endLine;
    }

    public Integer endColumn() {
        return endColumn;
    }

    public String tokenText() {
        return tokenText;
    }

    public String sourceSnippet() {
        return sourceSnippet;
    }

    public OclDiagnostic toDiagnostic() {
        return new OclDiagnostic(phase, code, getMessage(), line, column, endLine, endColumn, tokenText, sourceSnippet);
    }

    public String toUserMessage() {
        return toDiagnostic().toUserMessage();
    }
}
