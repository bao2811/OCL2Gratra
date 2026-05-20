package org.uet.dse.neo4j.realtime;

import org.tzi.use.main.Session;
import org.tzi.use.main.ChangeEvent;
import org.tzi.use.main.ChangeListener;
import org.uet.dse.neo4j.config.SyncConfig;
import org.uet.dse.neo4j.manager.WorkLogManager;
import org.uet.dse.neo4j.sync.LegacySyncGuard;
import org.uet.dse.neo4j.sync.object.ObjectDiff;
import org.uet.dse.neo4j.sync.object.ObjectSyncCoordinator;
import org.uet.dse.neo4j.sync.RealtimeModelSyncCoordinator;

import java.util.concurrent.*;

public class Neo4jRealTimeService implements ChangeListener, Session.EvaluatedStatementListener {

    private static Neo4jRealTimeService instance;
    private final Session session;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ScheduledExecutorService pollScheduler = Executors.newScheduledThreadPool(2);

    private long lastLocalStateFingerprint = 0L;

    private Neo4jRealTimeService(Session session) {
        this.session = session;

        pollScheduler.scheduleAtFixedRate(this::performAutoPull, 3, 3, TimeUnit.SECONDS);

        pollScheduler.scheduleAtFixedRate(this::performLocalScan, 2, 2, TimeUnit.SECONDS);
    }

    public static synchronized void start(Session session) {
        if (LegacySyncGuard.isDisabled()) {
            WorkLogManager.getInstance().log("REALTIME_BLOCKED", LegacySyncGuard.getReason());
            return;
        }
        if (instance != null) instance.stop();
        instance = new Neo4jRealTimeService(session);
        session.addChangeListener(instance);
        session.addEvaluatedStatementListener(instance);
        WorkLogManager.getInstance().log("REALTIME", "Deep Scan Service Started");
    }

    public static synchronized void stopIfRunning() {
        if (instance != null) {
            instance.stop();
            instance = null;
            WorkLogManager.getInstance().log("REALTIME", "Deep Scan Service Stopped");
        }
    }

    private void performLocalScan() {
        if (!SyncConfig.getInstance().autoPushObjectOnChange || !session.hasSystem()) return;
        if (ObjectSyncCoordinator.isProcessingInternalUpdate()) return;

        try {
            long currentFingerprint = calculateLocalStateFingerprint();

            if (lastLocalStateFingerprint != 0 && currentFingerprint != lastLocalStateFingerprint) {
                System.out.println("Real-time: Identifying changed objects...");

                ObjectSyncCoordinator objectCoord = new ObjectSyncCoordinator(session.system());

                ObjectDiff diff = objectCoord.compareObjects();

                if (diff.hasDifference()) {
                    objectCoord.pushToNeo4j(diff);
                    System.out.println("Real-time: Syncing only modified entities.");
                }

                lastLocalStateFingerprint = currentFingerprint;
            }
            if (lastLocalStateFingerprint == 0) lastLocalStateFingerprint = currentFingerprint;

        } catch (Exception e) { e.printStackTrace(); }
    }

    private long calculateLocalStateFingerprint() {
        StringBuilder sb = new StringBuilder();
        var state = session.system().state();

        state.allObjects().stream()
                .sorted((o1, o2) -> o1.name().compareTo(o2.name()))
                .forEach(obj -> {
                    sb.append(obj.name());
                    obj.state(state).attributeValueMap().forEach((attr, val) -> {
                        sb.append(attr.name()).append(val.toString());
                    });
                });

        sb.append("Links:").append(state.allLinks().size());

        return (long) sb.toString().hashCode();
    }

    private void performAutoPull() {
        if (SyncConfig.getInstance().autoPullObjectOnChange && session.hasSystem()) {
            try {
                ObjectSyncCoordinator objectCoord = new ObjectSyncCoordinator(session.system());
                if (!objectCoord.isQuickSyncCheck()) {
                    System.out.println("Auto-Pull: DB changed by another user. Syncing...");
                    objectCoord.syncObjectsBackwardRealtime();
                    lastLocalStateFingerprint = calculateLocalStateFingerprint();
                }
            } catch (Exception e) { }
        }
    }

    @Override
    public void evaluatedStatement(Session.EvaluatedStatement var1) {
        if (SyncConfig.getInstance().autoPushObjectOnChange && !ObjectSyncCoordinator.isProcessingInternalUpdate()) {
            executor.submit(() -> {
                new ObjectSyncCoordinator(session.system()).syncObjectsForward(false);
                lastLocalStateFingerprint = calculateLocalStateFingerprint();
            });
        }
    }

    @Override
    public void stateChanged(ChangeEvent var1) {
        if (SyncConfig.getInstance().autoPushModelOnImport) {
            executor.submit(() -> new RealtimeModelSyncCoordinator(session, null).syncForwardRealtime());
        }
    }

    public void stop() {
        session.removeChangeListener(this);
        session.removeEvaluatedStatementListener(this);
        executor.shutdown();
        pollScheduler.shutdown();
    }


    private void startSchedulers() {
        if (pollScheduler != null) pollScheduler.shutdownNow();

        pollScheduler = Executors.newScheduledThreadPool(2);
        int interval = SyncConfig.getInstance().syncInterval;

        pollScheduler.scheduleAtFixedRate(this::performAutoPull, interval, interval, TimeUnit.MILLISECONDS);

        pollScheduler.scheduleAtFixedRate(this::performLocalScan, interval, interval, TimeUnit.MILLISECONDS);

        System.out.println("Real-time service started with interval: " + interval + "ms");
    }

    public static synchronized void restart() {
        if (LegacySyncGuard.isDisabled()) {
            stopIfRunning();
            WorkLogManager.getInstance().log("REALTIME_BLOCKED", LegacySyncGuard.getReason());
            return;
        }
        if (instance != null) {
            instance.startSchedulers();
            WorkLogManager.getInstance().log("REALTIME", "Service restarted with new frequency.");
        }
    }
}
