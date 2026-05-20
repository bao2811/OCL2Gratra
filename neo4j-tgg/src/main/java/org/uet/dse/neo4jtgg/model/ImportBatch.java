package org.uet.dse.neo4jtgg.model;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ImportBatch {
    private final WorkspaceSide side;
    private final File sourceFile;
    private final List<ImportObjectSpec> objects = new ArrayList<>();
    private final List<ImportLinkSpec> links = new ArrayList<>();

    public ImportBatch(WorkspaceSide side, File sourceFile) {
        this.side = side;
        this.sourceFile = sourceFile;
    }

    public WorkspaceSide getSide() {
        return side;
    }

    public File getSourceFile() {
        return sourceFile;
    }

    public List<ImportObjectSpec> getObjects() {
        return Collections.unmodifiableList(objects);
    }

    public List<ImportLinkSpec> getLinks() {
        return Collections.unmodifiableList(links);
    }

    public void addObject(ImportObjectSpec spec) {
        objects.add(spec);
    }

    public void addLink(ImportLinkSpec spec) {
        links.add(spec);
    }
}
