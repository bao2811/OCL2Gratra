package org.uet.dse.neo4jtgg.service;

import org.uet.dse.neo4jtgg.model.TggWorkspaceContext;

public interface Neo4jChangeFeedService {
    void start(TggWorkspaceContext context, Runnable onChange);

    void stop();

    void previewRemoteProposal(TggWorkspaceContext context);
}
