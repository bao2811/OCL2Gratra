package org.uet.dse.neo4jtgg.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record OclDualCheckResult(
        boolean executed,
        boolean matched,
        Map<String, String> compiledViolations,
        Map<String, String> fallbackViolations,
        List<String> compiledOnlyObjectIds,
        List<String> fallbackOnlyObjectIds,
        String summary) {

    public OclDualCheckResult {
        compiledViolations = Map.copyOf(new LinkedHashMap<>(compiledViolations != null ? compiledViolations : Map.of()));
        fallbackViolations = Map.copyOf(new LinkedHashMap<>(fallbackViolations != null ? fallbackViolations : Map.of()));
        compiledOnlyObjectIds = List.copyOf(new ArrayList<>(compiledOnlyObjectIds != null ? compiledOnlyObjectIds : List.of()));
        fallbackOnlyObjectIds = List.copyOf(new ArrayList<>(fallbackOnlyObjectIds != null ? fallbackOnlyObjectIds : List.of()));
        summary = summary != null ? summary : "";
    }

    public static OclDualCheckResult disabled() {
        return new OclDualCheckResult(false, true, Map.of(), Map.of(), List.of(), List.of(), "");
    }

    public static OclDualCheckResult compare(Map<String, String> compiledViolations,
                                             Map<String, String> fallbackViolations) {
        Map<String, String> safeCompiled = compiledViolations != null ? compiledViolations : Map.of();
        Map<String, String> safeFallback = fallbackViolations != null ? fallbackViolations : Map.of();
        List<String> compiledOnly = new ArrayList<>();
        for (String objectId : safeCompiled.keySet()) {
            if (!safeFallback.containsKey(objectId)) {
                compiledOnly.add(objectId);
            }
        }
        List<String> fallbackOnly = new ArrayList<>();
        for (String objectId : safeFallback.keySet()) {
            if (!safeCompiled.containsKey(objectId)) {
                fallbackOnly.add(objectId);
            }
        }
        boolean matched = compiledOnly.isEmpty() && fallbackOnly.isEmpty();
        String summary = matched
                ? "Dual-check matched fallback oracle."
                : "Dual-check mismatch: compiledOnly=" + compiledOnly + ", fallbackOnly=" + fallbackOnly + ".";
        return new OclDualCheckResult(true, matched, safeCompiled, safeFallback, compiledOnly, fallbackOnly, summary);
    }

    public static OclDualCheckResult error(String message) {
        return new OclDualCheckResult(true, false, Map.of(), Map.of(), List.of(), List.of(),
                message != null && !message.isBlank()
                        ? "Dual-check failed: " + message
                        : "Dual-check failed.");
    }
}
