package org.uet.dse.neo4jtgg.service;

import org.uet.dse.neo4jtgg.model.ImportBatch;
import org.uet.dse.neo4jtgg.model.IncrementalApplyResult;
import org.uet.dse.neo4jtgg.model.IncrementalSyncProposal;
import org.uet.dse.neo4jtgg.model.TggWorkspaceContext;
import org.uet.dse.neo4jtgg.model.WorkspaceSide;
import org.uet.dse.neo4jtgg.engine.TransformationReport;

import java.io.File;

public interface TggWorkspaceService {
    TggWorkspaceContext getOrCreateContext();

    TggWorkspaceContext createDefaultContext(TggWorkspaceContext context);

    void loadWorkspace(TggWorkspaceContext context) throws Exception;

    void openGraphViews(TggWorkspaceContext context);

    String buildSnapshotText(TggWorkspaceContext context, WorkspaceSide side);

    ImportBatch previewImport(TggWorkspaceContext context, WorkspaceSide side, File sourceFile) throws Exception;

    void applyImport(TggWorkspaceContext context, ImportBatch batch) throws Exception;

    void refreshViews();

    void insertSelectedRuleText(TggWorkspaceContext context, WorkspaceSide side, String ruleName);

    TransformationReport previewForwardTransformation(TggWorkspaceContext context);

    TransformationReport runForwardTransformation(TggWorkspaceContext context);

    TransformationReport previewBackwardTransformation(TggWorkspaceContext context);

    TransformationReport runBackwardTransformation(TggWorkspaceContext context);

    IncrementalSyncProposal previewIncrementalRemoteChanges(TggWorkspaceContext context);

    IncrementalApplyResult applyPendingIncrementalProposal(TggWorkspaceContext context);

    IncrementalApplyResult discardPendingIncrementalProposal(TggWorkspaceContext context);

    IncrementalApplyResult refreshIncrementalBaseline(TggWorkspaceContext context);
}
