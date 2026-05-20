package org.uet.dse.neo4jtgg.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class OclValidationResult {
    private final boolean success;
    private final String summary;
    private final String generatedCypher;
    private final boolean fallbackUsed;
    private final Map<String, String> violations;

    public OclValidationResult(boolean success, String summary, String generatedCypher, boolean fallbackUsed,
                               Map<String, String> violations) {
        this.success = success;
        this.summary = summary;
        this.generatedCypher = generatedCypher;
        this.fallbackUsed = fallbackUsed;
        this.violations = new LinkedHashMap<>(violations);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getSummary() {
        return summary;
    }

    public String getGeneratedCypher() {
        return generatedCypher;
    }

    public boolean isFallbackUsed() {
        return fallbackUsed;
    }

    public Map<String, String> getViolations() {
        return Collections.unmodifiableMap(violations);
    }

    public String toDisplayText() {
        StringBuilder sb = new StringBuilder(summary);
        if (generatedCypher != null && !generatedCypher.isBlank()) {
            sb.append("\n\nGenerated Cypher:\n").append(generatedCypher);
        }
        if (fallbackUsed) {
            sb.append("\n\nExecution mode: fallback evaluator on Neo4j repository");
        }
        if (!violations.isEmpty()) {
            sb.append("\n\nViolations:\n");
            for (Map.Entry<String, String> violation : violations.entrySet()) {
                sb.append("- ").append(violation.getKey()).append(": ").append(violation.getValue()).append('\n');
            }
        }
        return sb.toString().trim();
    }
}
