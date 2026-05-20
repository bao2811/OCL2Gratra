package org.uet.dse.neo4j.sync.object;

import org.tzi.use.api.UseSystemApi;
import org.tzi.use.uml.sys.*;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.uet.dse.neo4j.manager.WorkLogManager;
import org.uet.dse.neo4j.repo.Neo4jModelRepository;
import org.uet.dse.neo4j.repo.Neo4jObjectRepository;
import org.uet.dse.neo4j.repo.Neo4jVersionRepository;
import org.uet.dse.neo4j.sync.LegacySyncGuard;
import org.uet.dse.neo4j.sync.helper.GUIHelper;
import org.uet.dse.neo4j.sync.lock.LockManager;
import org.uet.dse.neo4j.sync.feedback.SyncFeedbackHandler;
import org.uet.dse.neo4j.sync.feedback.object.AutoPullFeedbackHandler;
import org.uet.dse.neo4j.sync.feedback.object.AutoSyncFeedbackHandler;
import org.uet.dse.neo4j.sync.feedback.object.ManualPullFeedbackHandler;
import org.uet.dse.neo4j.sync.feedback.object.ManualSyncFeedbackHandler;

public class ObjectSyncCoordinator {
    //changes from user /plugin
    private static boolean isApplyingInternalChange = false;
    private static long lastLocalObjectSyncTimestamp = 0;
    private final MSystem system;
    private final Neo4jModelRepository repo;

    private final Neo4jObjectRepository objectRepository;
    private final UseSystemApi systemApi;

    private final ObjectSnapshotCompare objectSnapshotCompare;

    private final ObjectPushService objectPushService;
    private final ObjectPullService objectPullService;

    public ObjectSyncCoordinator(MSystem system) {
        this.system = system;
        this.systemApi = UseSystemApi.create(system, true);
        this.repo = new Neo4jModelRepository();
        this.objectRepository = new Neo4jObjectRepository();
        objectSnapshotCompare = new ObjectSnapshotCompare(this.system);
        objectPushService = new ObjectPushService(objectRepository, system);
        objectPullService = new ObjectPullService(systemApi, system);
    }

    /**
     * check model version (important)
     */
    private boolean isModelInSync() {
        long localHash = ObjectSyncHelper.calculateModelHash(system.model());
        Neo4jVersionRepository versionRepository = new Neo4jVersionRepository();
        long remoteHash = versionRepository.getRemoteModelHash();
        return localHash != -1 && localHash == remoteHash;
    }

    public void syncObjectsForward(boolean isManual) {
        if (LegacySyncGuard.isDisabled()) {
            throw new IllegalStateException(LegacySyncGuard.getReason());
        }
        SyncFeedbackHandler feedback = isManual
                ? new ManualSyncFeedbackHandler(GUIHelper::showSyncPreview)
                : new AutoSyncFeedbackHandler();

        syncObjectsForward(feedback);
    }

    private void syncObjectsForward(SyncFeedbackHandler feedback) {
        if (!isModelInSync()) {
            feedback.onModelOutOfSync();
            return;
        }

        ObjectDiff diff = objectSnapshotCompare.compareObjects();
        if (!diff.hasDifference()) {
            feedback.onAlreadyInSync();
            return;
        }

        if (!feedback.onPreviewAndConfirm(diff)) {
            return;
        }

        try {
            objectPushService.pushToNeo4j(diff);
            feedback.onSuccess(diff);
        } catch (Exception e) {
            feedback.onFailure(e);
        }
    }

    public void syncObjectsBackward(boolean isManual) {
        if (LegacySyncGuard.isDisabled()) {
            throw new IllegalStateException(LegacySyncGuard.getReason());
        }
        SyncFeedbackHandler feedback = isManual
                ? new ManualPullFeedbackHandler()
                : new AutoPullFeedbackHandler();

        syncObjectsBackward(feedback);
    }

    private void syncObjectsBackward(SyncFeedbackHandler feedback) {
        if (!isModelInSync()) {
            feedback.onModelOutOfSync();
            return;
        }

        ObjectDiff diff = objectSnapshotCompare.compareObjects();
        if (!diff.hasDifference()) {
            feedback.onAlreadyInSync();
            return;
        }

        if (!feedback.onPreviewAndConfirm(diff)) return;

        try {
            objectPullService.pullObjectFromNeo4j(diff);
            syncLocalTimestampWithRemote();
            feedback.onSuccess(diff);
            WorkLogManager.getInstance().log("OBJECT_PULL_SUCCESS", feedback.buildSuccessMessage(diff));
        } catch (Exception e) {
            feedback.onFailure(e);
        }
    }

    private void syncLocalTimestampWithRemote() {
        long remoteTs = new Neo4jVersionRepository().getRemoteObjectTimestamp();
        updateLocalSyncTimestamp(remoteTs);
    }

    public void pushToNeo4j(ObjectDiff diff) {
        if (LegacySyncGuard.isDisabled()) {
            throw new IllegalStateException(LegacySyncGuard.getReason());
        }
        objectPushService.pushToNeo4j(diff);
    }

    public ObjectDiff compareObjects() {
        return objectSnapshotCompare.compareObjects();
    }


    public static void updateLocalSyncTimestamp(long ts) {
        lastLocalObjectSyncTimestamp = ts;
    }

    public boolean isQuickSyncCheck() {
        try {
            Neo4jVersionRepository versionRepository = new Neo4jVersionRepository();
            long remoteTs = versionRepository.getRemoteObjectTimestamp();
            //nothing found
            if (remoteTs == 0) return true;

            return lastLocalObjectSyncTimestamp == remoteTs;
        } catch (Exception e) {
            return false;
        }
    }


    public String getQuickSummary() {
        try {
            if (Neo4jDriverManager.getInstance() == null || !Neo4jDriverManager.getInstance().isConnected()) {
                return "No Connection";
            }

            if (isQuickSyncCheck()) return "All Synced";

            ObjectDiff diff = objectSnapshotCompare.compareObjects();

            if (diff == null || !diff.hasDifference()) return "All Synced";

            return String.format("%d Obj, %d Link diff",
                    (diff.javaOnlyObjects.size() + diff.mismatchedObjects.size()),
                    (diff.javaOnlyLinks.size() + diff.mismatchedLinks.size()));
        } catch (Exception e) {
            return "Check Failed";
        }
    }


    public void pushSingleObjectByName(String objName) {
//        MObject obj = system.state().objectByName(objName);
//        if (obj == null) return;
//
//        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
//            session.executeWrite(tx -> {
//                ObjectState state = captureSingleObjectState(obj);
//
//                // Lưu ý: pushSingleObject cần Transaction (tx)
//                pushSingleObject(tx, state);
//
//                return null;
//            });
//
//            Neo4jUmlBridge.updateObjectVersionOnServer();
//            System.out.println("SELECTIVE_PUSH_OBJ" + "Pushed object: " + objName);
//        }
    }


    public void syncObjectsForwardRealtime() {
        if (LegacySyncGuard.isDisabled()) return;
        String user = Neo4jDriverManager.getInstance().getSessionManager().getUserDisplayName();

        LockManager.LockResult lock = LockManager.acquireLock(user);
        if (!lock.success) return;

        try {
            if (!isModelInSync()) return;

            ObjectDiff diff = objectSnapshotCompare.compareObjects();

            if (diff.hasDifference()) {
                objectPushService.pushToNeo4j(diff);
            }
        } finally {
            LockManager.releaseLock();
        }
    }

    public void syncObjectsBackwardRealtime() {
        if (LegacySyncGuard.isDisabled()) return;
        try {
            isApplyingInternalChange = true;

            ObjectDiff diff = objectSnapshotCompare.compareObjects();
            if (diff.hasDifference()) {
                objectPullService.pullObjectFromNeo4j(diff);
                updateLocalSyncTimestamp(new Neo4jVersionRepository().getRemoteObjectTimestamp());
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            isApplyingInternalChange = false;
        }
    }

    public static boolean isProcessingInternalUpdate() {
        return isApplyingInternalChange;
    }
}
