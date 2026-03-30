package org.uet.dse.neo4j.migration;

import java.util.*;

public class StructuralImpactAnalysis {
    // ClassName -> List of ObjectNames bị ảnh hưởng
    public Map<String, List<String>> orphanObjects = new HashMap<>();
    public int totalLinksAffected = 0;

    public boolean hasImpact() {
        return !orphanObjects.isEmpty();
    }

    public String generateImpactReport() {
        StringBuilder sb = new StringBuilder("⚠️ STRUCTURAL INTEGRITY WARNING ⚠️\n");
        sb.append("The following classes are being REMOVED from the Model.\n");
        sb.append("All associated objects will be DELETED to maintain UML consistency:\n\n");

        orphanObjects.forEach((className, objects) -> {
            sb.append(String.format("- Class [%s]: %d objects will be lost.\n", className, objects.size()));
            if (objects.size() <= 5) sb.append("  Details: ").append(objects).append("\n");
        });

        return sb.toString();
    }
}