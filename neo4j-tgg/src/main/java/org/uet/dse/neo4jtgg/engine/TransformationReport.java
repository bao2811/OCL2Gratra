package org.uet.dse.neo4jtgg.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TransformationReport {
    private final TransformationDirection direction;
    private final TransformationMode mode;
    private final List<String> infos = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private final List<String> matchedRules = new ArrayList<>();
    private final List<String> createdObjects = new ArrayList<>();
    private final List<String> updatedObjects = new ArrayList<>();
    private final List<String> createdLinks = new ArrayList<>();
    private final List<String> createdCorrespondences = new ArrayList<>();

    public TransformationReport(TransformationDirection direction, TransformationMode mode) {
        this.direction = direction;
        this.mode = mode;
    }

    public TransformationDirection getDirection() {
        return direction;
    }

    public TransformationMode getMode() {
        return mode;
    }

    public void addInfo(String value) {
        infos.add(value);
    }

    public void addWarning(String value) {
        warnings.add(value);
    }

    public void addError(String value) {
        errors.add(value);
    }

    public void addMatchedRule(String value) {
        matchedRules.add(value);
    }

    public void addCreatedObject(String value) {
        createdObjects.add(value);
    }

    public void addUpdatedObject(String value) {
        updatedObjects.add(value);
    }

    public void addCreatedLink(String value) {
        createdLinks.add(value);
    }

    public void addCreatedCorrespondence(String value) {
        createdCorrespondences.add(value);
    }

    public List<String> getInfos() {
        return Collections.unmodifiableList(infos);
    }

    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public List<String> getMatchedRules() {
        return Collections.unmodifiableList(matchedRules);
    }

    public List<String> getCreatedObjects() {
        return Collections.unmodifiableList(createdObjects);
    }

    public List<String> getUpdatedObjects() {
        return Collections.unmodifiableList(updatedObjects);
    }

    public List<String> getCreatedLinks() {
        return Collections.unmodifiableList(createdLinks);
    }

    public List<String> getCreatedCorrespondences() {
        return Collections.unmodifiableList(createdCorrespondences);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public String toDisplayText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Transformation mode: ").append(mode).append('\n');
        sb.append("Direction: ").append(direction).append('\n');

        if (!matchedRules.isEmpty()) {
            sb.append("\nMatched rules\n");
            matchedRules.forEach(rule -> sb.append("- ").append(rule).append('\n'));
        }
        if (!createdObjects.isEmpty()) {
            sb.append("\nCreated objects\n");
            createdObjects.forEach(value -> sb.append("- ").append(value).append('\n'));
        }
        if (!updatedObjects.isEmpty()) {
            sb.append("\nUpdated objects\n");
            updatedObjects.forEach(value -> sb.append("- ").append(value).append('\n'));
        }
        if (!createdLinks.isEmpty()) {
            sb.append("\nCreated links\n");
            createdLinks.forEach(value -> sb.append("- ").append(value).append('\n'));
        }
        if (!createdCorrespondences.isEmpty()) {
            sb.append("\nCreated correspondences\n");
            createdCorrespondences.forEach(value -> sb.append("- ").append(value).append('\n'));
        }
        if (!infos.isEmpty()) {
            sb.append("\nInfo\n");
            infos.forEach(value -> sb.append("- ").append(value).append('\n'));
        }
        if (!warnings.isEmpty()) {
            sb.append("\nWarnings\n");
            warnings.forEach(value -> sb.append("- ").append(value).append('\n'));
        }
        if (!errors.isEmpty()) {
            sb.append("\nErrors\n");
            errors.forEach(value -> sb.append("- ").append(value).append('\n'));
        }

        return sb.toString().trim();
    }
}
