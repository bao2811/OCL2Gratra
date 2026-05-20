package org.uet.dse.neo4jtgg.service;

import org.uet.dse.neo4jtgg.model.GuardReport;
import org.uet.dse.neo4jtgg.model.ImportBatch;
import org.uet.dse.neo4jtgg.model.TggWorkspaceContext;

public interface ChangeGuardService {
    GuardReport validateImport(TggWorkspaceContext context, ImportBatch batch);

    GuardReport validateRemotePull(TggWorkspaceContext context);
}
