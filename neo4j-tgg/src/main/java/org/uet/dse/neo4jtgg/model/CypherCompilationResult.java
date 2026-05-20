package org.uet.dse.neo4jtgg.model;

import org.uet.dse.neo4jtgg.ocl.diagnostic.OclDiagnostic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CypherCompilationResult {
    private final boolean supported;
    private final String cypher;
    private final Map<String, Object> parameters;
    private final String reason;
    private final boolean invariantQuery;
    private final OclDiagnostic diagnostic;
    private final List<OclDiagnostic> diagnostics;

    public CypherCompilationResult(boolean supported, String cypher, Map<String, Object> parameters,
                                   String reason, boolean invariantQuery) {
        this(supported, cypher, parameters, reason, invariantQuery, List.of());
    }

    public CypherCompilationResult(boolean supported, String cypher, Map<String, Object> parameters,
                                   String reason, boolean invariantQuery, OclDiagnostic diagnostic) {
        this(supported, cypher, parameters, reason, invariantQuery,
                diagnostic != null ? List.of(diagnostic) : List.of());
    }

    public CypherCompilationResult(boolean supported, String cypher, Map<String, Object> parameters,
                                   String reason, boolean invariantQuery, List<OclDiagnostic> diagnostics) {
        this.supported = supported;
        this.cypher = cypher;
        this.parameters = new LinkedHashMap<>(parameters);
        this.reason = reason;
        this.invariantQuery = invariantQuery;
        this.diagnostics = List.copyOf(new ArrayList<>(diagnostics));
        this.diagnostic = this.diagnostics.isEmpty() ? null : this.diagnostics.get(0);
    }

    public boolean isSupported() {
        return supported;
    }

    public String getCypher() {
        return cypher;
    }

    public Map<String, Object> getParameters() {
        return Collections.unmodifiableMap(parameters);
    }

    public String getReason() {
        return reason;
    }

    public boolean isInvariantQuery() {
        return invariantQuery;
    }

    public OclDiagnostic getDiagnostic() {
        return diagnostic;
    }

    public List<OclDiagnostic> getDiagnostics() {
        return Collections.unmodifiableList(diagnostics);
    }
}
