package org.uet.dse.neo4j.sync.model;

import java.util.*;

/**
 * this class holds the different between 2 snapshot of model in USE and Neo4j
 * includes:
 * - class
 * - associations
 *
 * //big bug: hasnt carefully handle operations, invariants, mismatched atributes, enum
 */
public class ModelDiff {
    public List<String> javaOnlyClasses = new ArrayList<>();
    public List<String> neo4jOnlyClasses = new ArrayList<>();
    public List<String> mismatchedClasses = new ArrayList<>();

    public Map<String, List<String>> javaOnlyAttributes = new HashMap<>();
    public Map<String, List<String>> neo4jOnlyAttributes = new HashMap<>();

    public List<String> javaOnlyAssociations = new ArrayList<>();
    public List<String> neo4jOnlyAssociations = new ArrayList<>();
    public List<String> mismatchedAssociations = new ArrayList<>();

    public boolean hasDifference() {
        return !javaOnlyClasses.isEmpty() ||
                !neo4jOnlyClasses.isEmpty() ||
                !mismatchedClasses.isEmpty() ||
                !javaOnlyAssociations.isEmpty() ||
                !neo4jOnlyAssociations.isEmpty() ||
                !mismatchedAssociations.isEmpty();
    }


    public String generateForwardReport() {
        StringBuilder sb = new StringBuilder("===== SYNC FORWARD: PUSH TO DATABASE =====\n\n");

        if (!javaOnlyClasses.isEmpty())
            sb.append("[+] NEW CLASSES/ENUMS: ").append(javaOnlyClasses).append("\n");

        if (!neo4jOnlyClasses.isEmpty())
            sb.append("[!] WILL DELETE FROM DB: ").append(neo4jOnlyClasses).append(" (Not in USE model)\n");

        if (!mismatchedClasses.isEmpty()) {
            sb.append("[*] SCHEMA UPDATES (Attributes/Operations):\n");
            for (String cls : mismatchedClasses) {
                sb.append("    - ").append(cls).append("\n");
            }
        }

        sb.append("\n--- Relationship Changes ---\n");
        if (!javaOnlyAssociations.isEmpty())
            sb.append("[+] NEW LINKS: ").append(javaOnlyAssociations).append("\n");

        if (!neo4jOnlyAssociations.isEmpty())
            sb.append("[!] REMOVE FROM DB: ").append(neo4jOnlyAssociations).append("\n");

        if (!mismatchedAssociations.isEmpty())
            sb.append("[*] UPDATE LINK METADATA: ").append(mismatchedAssociations).append("\n");

        if (!hasDifference()) sb.append("No changes detected. Database is up-to-date.");
        return sb.toString();
    }

    public String generateBackwardReport() {
        StringBuilder sb = new StringBuilder("===== SYNC BACKWARD: PULL FROM DATABASE =====\n\n");

        if (!neo4jOnlyClasses.isEmpty())
            sb.append("[+] NEW FROM DB: ").append(neo4jOnlyClasses).append("\n");

        if (!javaOnlyClasses.isEmpty())
            sb.append("[-] WILL REMOVE FROM USE: ").append(javaOnlyClasses).append("\n");

        if (!mismatchedClasses.isEmpty())
            sb.append("[*] LOCAL SCHEMA UPDATES: ").append(mismatchedClasses).append("\n");

        sb.append("\n--- Relationship Changes ---\n");
        if (!neo4jOnlyAssociations.isEmpty())
            sb.append("[+] NEW LINKS FROM DB: ").append(neo4jOnlyAssociations).append("\n");

        if (!javaOnlyAssociations.isEmpty())
            sb.append("[-] REMOVE LINKS FROM USE: ").append(javaOnlyAssociations).append("\n");

        if (!hasDifference()) sb.append("No changes detected. Your local model is in sync.");
        return sb.toString();
    }

    // --- Getters & Setters ---
    public List<String> getJavaOnlyClasses() { return javaOnlyClasses; }
    public List<String> getNeo4jOnlyClasses() { return neo4jOnlyClasses; }
    public List<String> getMismatchedClasses() { return mismatchedClasses; }
    public List<String> getJavaOnlyAssociations() { return javaOnlyAssociations; }
    public List<String> getNeo4jOnlyAssociations() { return neo4jOnlyAssociations; }
    public List<String> getMismatchedAssociations() { return mismatchedAssociations; }
    public Map<String, List<String>> getJavaOnlyAttributes() { return javaOnlyAttributes; }
    public Map<String, List<String>> getNeo4jOnlyAttributes() { return neo4jOnlyAttributes; }
}