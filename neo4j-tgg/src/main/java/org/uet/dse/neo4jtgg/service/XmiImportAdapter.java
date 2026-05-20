package org.uet.dse.neo4jtgg.service;

import org.uet.dse.neo4jtgg.model.ImportBatch;
import org.uet.dse.neo4jtgg.model.TggWorkspaceContext;
import org.uet.dse.neo4jtgg.model.WorkspaceSide;

import java.io.File;

public interface XmiImportAdapter {
    boolean supports(WorkspaceSide side);

    ImportBatch parse(TggWorkspaceContext context, WorkspaceSide side, File file) throws Exception;
}
