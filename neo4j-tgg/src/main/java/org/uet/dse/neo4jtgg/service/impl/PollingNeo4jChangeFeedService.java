package org.uet.dse.neo4jtgg.service.impl;

import javax.swing.Timer;

import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.uet.dse.neo4j.repo.Neo4jVersionRepository;
import org.uet.dse.neo4jtgg.model.GuardReport;
import org.uet.dse.neo4jtgg.model.TggWorkspaceContext;
import org.uet.dse.neo4jtgg.service.ChangeGuardService;
import org.uet.dse.neo4jtgg.service.Neo4jChangeFeedService;

public class PollingNeo4jChangeFeedService implements Neo4jChangeFeedService {

    private final ChangeGuardService guardService;
    private Timer timer;
    private long lastObjectTimestamp;

    public PollingNeo4jChangeFeedService(ChangeGuardService guardService) {
        this.guardService = guardService;
    }

    @Override
    public void start(TggWorkspaceContext context, Runnable onChange) {
        stop();
        if (Neo4jDriverManager.getInstance() == null || !Neo4jDriverManager.getInstance().isConnected()) {
            context.setRemoteChangeSummary("Neo4j not connected; CDC polling disabled.");
            return;
        }

        lastObjectTimestamp = new Neo4jVersionRepository().getRemoteObjectTimestamp();
        timer = new Timer(5000, e -> poll(context, onChange));
        timer.start();
    }

    private void poll(TggWorkspaceContext context, Runnable onChange) {
        if (Neo4jDriverManager.getInstance() == null || !Neo4jDriverManager.getInstance().isConnected()) {
            context.setRemoteChangeSummary("Neo4j connection lost.");
            onChange.run();
            return;
        }

        long remoteTimestamp = new Neo4jVersionRepository().getRemoteObjectTimestamp();
        if (remoteTimestamp > 0 && remoteTimestamp != lastObjectTimestamp) {
            GuardReport report = guardService.validateRemotePull(context);
            if (context.getCurrentProposal() != null
                    && context.getCurrentProposal().status() == org.uet.dse.neo4jtgg.model.IncrementalSyncStatus.PENDING) {
                context.setRemoteChangeSummary("Remote Neo4j delta detected, but an incremental proposal is already pending.\n"
                        + report.toDisplayText());
            } else {
                previewRemoteProposal(context);
                context.setRemoteChangeSummary("Remote Neo4j delta detected.\n" + report.toDisplayText());
            }
            lastObjectTimestamp = remoteTimestamp;
            onChange.run();
        }
    }

    @Override
    public void stop() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }

    @Override
    public void previewRemoteProposal(TggWorkspaceContext context) {
        DefaultTggWorkspaceService.getInstance().previewIncrementalRemoteChanges(context);
    }
}
