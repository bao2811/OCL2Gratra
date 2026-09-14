package org.uet.dse.neo4jtgg.model;

import org.uet.dse.neo4jtgg.ocl.diagnostic.OclDiagnostic;

import java.util.ArrayList;
import java.util.List;

public record OclResultLocation(
        String contextClassName,
        String ruleName,
        Integer line,
        Integer column,
        Integer endLine,
        Integer endColumn,
        String tokenText,
        String sourceSnippet,
        List<String> objectIds) {

    public OclResultLocation {
        objectIds = List.copyOf(new ArrayList<>(objectIds != null ? objectIds : List.of()));
    }

    public static OclResultLocation fromDiagnostic(String contextClassName, String ruleName, OclDiagnostic diagnostic) {
        if (diagnostic == null) {
            return new OclResultLocation(contextClassName, ruleName, null, null, null, null, null, null, List.of());
        }
        return new OclResultLocation(
                contextClassName,
                ruleName,
                diagnostic.line(),
                diagnostic.column(),
                diagnostic.endLine(),
                diagnostic.endColumn(),
                diagnostic.tokenText(),
                diagnostic.sourceSnippet(),
                List.of());
    }

    public static OclResultLocation forViolations(String contextClassName, String ruleName, List<String> objectIds) {
        return new OclResultLocation(contextClassName, ruleName, null, null, null, null, null, null, objectIds);
    }
}
