package org.uet.dse.neo4jtgg.engine;

/**
 * Represents an attribute assignment in a TGG rule, e.g.:
 * {@code self.mp.name := self.f.familyFather.name + ', ' + self.f.name}
 */
public record AttributeAssignment(String targetPath, String sourceExpression) {

    /**
     * Parse an assignment from its raw text form.
     * Expected format: {@code target.path := source expression}
     */
    public static AttributeAssignment parse(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return null;
        }
        String trimmed = rawText.trim();
        // Remove surrounding backticks if present
        if (trimmed.startsWith("`") && trimmed.endsWith("`")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        if (trimmed.isEmpty()) {
            return null;
        }

        int assignIndex = trimmed.indexOf(":=");
        if (assignIndex < 0) {
            return null;
        }

        String targetPath = trimmed.substring(0, assignIndex).trim();
        String sourceExpression = trimmed.substring(assignIndex + 2).trim();

        if (targetPath.isEmpty() || sourceExpression.isEmpty()) {
            return null;
        }

        return new AttributeAssignment(targetPath, sourceExpression);
    }

    public String toDisplayText() {
        return targetPath + " := " + sourceExpression;
    }
}
