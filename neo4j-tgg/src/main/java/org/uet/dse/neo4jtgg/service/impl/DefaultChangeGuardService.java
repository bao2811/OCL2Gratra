package org.uet.dse.neo4jtgg.service.impl;

import org.uet.dse.neo4jtgg.model.GuardReport;
import org.uet.dse.neo4jtgg.model.ImportBatch;
import org.uet.dse.neo4jtgg.model.TggWorkspaceContext;
import org.uet.dse.neo4jtgg.service.ChangeGuardService;

public class DefaultChangeGuardService implements ChangeGuardService {
    private final Neo4jWorkspaceRuntimeService runtimeService = Neo4jWorkspaceRuntimeService.getInstance();

    @Override
    public GuardReport validateImport(TggWorkspaceContext context, ImportBatch batch) {
        return runtimeService.validateImport(context, batch);
    }

    @Override
    public GuardReport validateRemotePull(TggWorkspaceContext context) {
        return runtimeService.validateRemotePull(context);
    }
}
