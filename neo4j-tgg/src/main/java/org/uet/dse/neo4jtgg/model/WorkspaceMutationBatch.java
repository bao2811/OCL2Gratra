package org.uet.dse.neo4jtgg.model;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WorkspaceMutationBatch {
    private final WorkspaceSide receiverSide;
    private final File sourceFile;
    private final List<ImportObjectSpec> upsertObjects = new ArrayList<>();
    private final List<ImportLinkSpec> upsertLinks = new ArrayList<>();
    private final List<String> deleteObjectIds = new ArrayList<>();
    private final List<ImportLinkSpec> deleteLinks = new ArrayList<>();

    public WorkspaceMutationBatch(WorkspaceSide receiverSide, File sourceFile) {
        this.receiverSide = receiverSide;
        this.sourceFile = sourceFile;
    }

    public WorkspaceSide getReceiverSide() {
        return receiverSide;
    }

    public File getSourceFile() {
        return sourceFile;
    }

    public List<ImportObjectSpec> getUpsertObjects() {
        return Collections.unmodifiableList(upsertObjects);
    }

    public List<ImportLinkSpec> getUpsertLinks() {
        return Collections.unmodifiableList(upsertLinks);
    }

    public List<String> getDeleteObjectIds() {
        return Collections.unmodifiableList(deleteObjectIds);
    }

    public List<ImportLinkSpec> getDeleteLinks() {
        return Collections.unmodifiableList(deleteLinks);
    }

    public void addUpsertObject(ImportObjectSpec spec) {
        for (ImportObjectSpec existing : upsertObjects) {
            if (existing.getObjectName().equals(spec.getObjectName())
                    && existing.getClassName().equals(spec.getClassName())) {
                existing.getAttributes().putAll(spec.getAttributes());
                return;
            }
        }
        upsertObjects.add(spec);
    }

    public void addUpsertLink(ImportLinkSpec spec) {
        upsertLinks.add(spec);
    }

    public void addDeleteObjectId(String objectId) {
        if (objectId != null && !objectId.isBlank() && !deleteObjectIds.contains(objectId)) {
            deleteObjectIds.add(objectId);
        }
    }

    public void addDeleteLink(ImportLinkSpec spec) {
        deleteLinks.add(spec);
    }

    public boolean isEmpty() {
        return upsertObjects.isEmpty()
                && upsertLinks.isEmpty()
                && deleteObjectIds.isEmpty()
                && deleteLinks.isEmpty();
    }

    public String toDisplayText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Receiver side: ").append(receiverSide.getDisplayName());
        sb.append("\nUpsert objects: ").append(upsertObjects.size());
        for (ImportObjectSpec spec : upsertObjects) {
            sb.append("\n  + ").append(spec.getObjectName()).append(" : ").append(spec.getClassName())
                    .append(" ").append(spec.getAttributes());
        }
        sb.append("\nUpsert links: ").append(upsertLinks.size());
        for (ImportLinkSpec spec : upsertLinks) {
            sb.append("\n  + ").append(spec.getAssociationName()).append(" ").append(spec.getEndpointNames());
        }
        sb.append("\nDelete objects: ").append(deleteObjectIds.size());
        for (String objectId : deleteObjectIds) {
            sb.append("\n  - ").append(objectId);
        }
        sb.append("\nDelete links: ").append(deleteLinks.size());
        for (ImportLinkSpec spec : deleteLinks) {
            sb.append("\n  - ").append(spec.getAssociationName()).append(" ").append(spec.getEndpointNames());
        }
        return sb.toString();
    }
}
