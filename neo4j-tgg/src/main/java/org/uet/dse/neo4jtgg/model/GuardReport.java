package org.uet.dse.neo4jtgg.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GuardReport {
    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    public void addError(String error) {
        errors.add(error);
    }

    public void addWarning(String warning) {
        warnings.add(warning);
    }

    public boolean isAllowed() {
        return errors.isEmpty();
    }

    public String toDisplayText() {
        StringBuilder sb = new StringBuilder();
        if (errors.isEmpty()) {
            sb.append("Guard status: ALLOWED\n");
        } else {
            sb.append("Guard status: BLOCKED\n");
            for (String error : errors) {
                sb.append("ERROR: ").append(error).append('\n');
            }
        }
        for (String warning : warnings) {
            sb.append("WARNING: ").append(warning).append('\n');
        }
        return sb.toString().trim();
    }
}
