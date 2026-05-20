package org.uet.dse.neo4jtgg.ocl.diagnostic;

public class OclCodedUnsupportedOperationException extends UnsupportedOperationException {
    private final OclDiagnosticCode code;

    public OclCodedUnsupportedOperationException(OclDiagnosticCode code, String message) {
        super(message);
        this.code = code;
    }

    public OclDiagnosticCode code() {
        return code;
    }
}
