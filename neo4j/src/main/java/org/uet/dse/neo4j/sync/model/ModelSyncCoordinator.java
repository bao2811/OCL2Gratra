package org.uet.dse.neo4j.sync.model;

import org.neo4j.driver.*;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.main.Session;
import org.tzi.use.uml.mm.*;
import org.tzi.use.uml.sys.MSystem;
import org.uet.dse.neo4j.gui.ModelExplorerDialog;
import org.uet.dse.neo4j.manager.Neo4jAuditLogService;
import org.uet.dse.neo4j.model.ClassState;
import org.uet.dse.neo4j.model.FullModelSnapshot;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.uet.dse.neo4j.manager.WorkLogManager;
import org.uet.dse.neo4j.sync.lock.LockManager;
import org.uet.dse.neo4j.sync.model.simpleDomain.KusakabeModelDiff;
import javax.swing.*;
import java.awt.*;
import java.util.*;

public class ModelSyncCoordinator {
    private final Session useSession;
    private final MModel useModel;

    private final MainWindow mainWindow;
    private final UseModelApi modelApi;

    private final ModelSnapshotsAnalyzer modelSnapshotsAnalyzer;

    public ModelSyncCoordinator(Session session, MainWindow mainWindow, MModel useModel, UseModelApi modelApi) {
        this.useSession = session;
        this.mainWindow = mainWindow;
        this.useModel = useModel;
        this.modelApi = modelApi;
        //configured
        this.modelSnapshotsAnalyzer = new ModelIncrementalCompare(useModel);
    }

    public ModelSyncCoordinator(Session session, MainWindow mainWindow) {
        this.useSession = session;
        this.mainWindow = mainWindow;
        this.useModel = session.system().model();
        this.modelApi = new UseModelApi(session.system().model());
        this.modelSnapshotsAnalyzer = new ModelIncrementalCompare(useModel);
    }

    public ModelSyncCoordinator(Session session) {
        this.useSession = session;
        this.mainWindow = null;
        this.useModel = session.system().model();
        this.modelApi = new UseModelApi(session.system().model());
        this.modelSnapshotsAnalyzer = new ModelIncrementalCompare(useModel);
    }

    public MModel getUseModel() {
        return useModel;
    }

    public UseModelApi getModelApi() {
        return modelApi;
    }

    public ModelSnapshotsAnalyzer getModelSnapshotsAnalyzer() {
        return this.modelSnapshotsAnalyzer;
    }

    public void syncForward() {
        //get user identification
        String userIdentifier = Neo4jDriverManager.getInstance().getSessionManager().getUserDisplayName();
        Neo4jAuditLogService.log("START", "MODEL_SYNC", "Global", "User initiated manual model synchronization.");

        //lock db while working
        LockManager.LockResult lock = LockManager.acquireLock(userIdentifier);
        if(!checkLock(lock)) return;
        try {
            //bug
            ModelDiff diff = modelSnapshotsAnalyzer.compareWithNeo4j();
            //assums that model name doesnt matter
            KusakabeModelDiff kusakabeModelDiff = (KusakabeModelDiff) diff;

            if (kusakabeModelDiff.hasConflict()) {
                JOptionPane.showMessageDialog(
                    null,
                    "The model is in conflict.",
                    "Conflict Detected",
                    JOptionPane.WARNING_MESSAGE
                );
                return;
            }


            ModelSnapshotsAnalyzer baseBean = new ModelSnapshotCompareV1(this.useModel);
            Boolean freeForAllPushItAll = true;//baseBean.checkBlankWorkingSpace();

            if (freeForAllPushItAll == null) {
                JOptionPane.showMessageDialog(null, "Bug");
                return;
            }
            if (freeForAllPushItAll) {
                //overwrite and add stuff that is in USE but not neo4j
                CoreModelPushService coreModelPushService = new CoreModelPushService(this.modelApi);
                coreModelPushService.pushModelToNeo4j();
            } else {
                IncrementalModelPushService incrementalModelPushService = new IncrementalModelPushService(this.modelApi);
                incrementalModelPushService.pushModelToNeo4j(diff, this.useModel);

            }

            WorkLogManager.getInstance().log("SYNC_FORWARD_SUCCESS", "Model pushed by: " + userIdentifier);
            JOptionPane.showMessageDialog(null, "Synchronization to Neo4j completed successfully!");

        } catch (Exception e) {
            WorkLogManager.getInstance().log("SYNC_FORWARD_ERROR", e.getMessage());
            JOptionPane.showMessageDialog(null, "Sync failed: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        } finally {
            LockManager.releaseLock();
        }
    }

    private boolean checkLock(LockManager.LockResult lock) {
        if (!lock.success) {
            JOptionPane.showMessageDialog(null,
                "Database is currently LOCKED by:\n" + lock.owner +
                    "\nPlease wait until they finish or the lock expires.",
                "Sync Blocked", JOptionPane.WARNING_MESSAGE);

            Neo4jAuditLogService.log("LOCK_DENIED", "DATABASE", "Ticket", "Sync attempt blocked by: " + lock.owner);
            return false;
        }
        return true;
    }


    public void syncBackward() {
        ModelExplorerDialog explorer = new ModelExplorerDialog(mainWindow);
        explorer.setVisible(true);

        String selectedModelName = explorer.getSelectedModelName();
        if (selectedModelName == null) return;
        String user = Neo4jDriverManager.getInstance().getSessionManager().getUserDisplayName();
        LockManager.LockResult lock = LockManager.acquireLock(user);
        if (!lock.success) {
            JOptionPane.showMessageDialog(null, "Database is locked by: " + lock.owner);
            return;
        }

        SwingUtilities.invokeLater(() -> {
            try {
                FullModelSnapshot fullModelSnapshot = getFullModelSnapshotFromNeo4j(selectedModelName);
                Map<String, ClassState> dbSnapshot = fullModelSnapshot.classes;
                if (dbSnapshot.isEmpty()) {
                    JOptionPane.showMessageDialog(null, "No model found in Database.");
                    return;
                }

                String actualModelName = getModelNameFromDb(); // Hàm này đọc từ node ModelVersion

                //create new model
                ModelFactory factory = new ModelFactory();
                MModel newModel = factory.createModel(selectedModelName);

                ModelPullService modelPullService = new ModelPullService();
                modelPullService.pullModelFromNeo4jAfterSnapshotComparing(newModel, fullModelSnapshot);

                MSystem newSystem = new MSystem(newModel);
                useSession.setSystem(newSystem);

                //update & reload folder structure
                mainWindow.getModelBrowser().updateUI();
                // mainWindow.showModelStatus();
                mainWindow.logWriter().println("Neo4j Sync: Model '" + actualModelName + "' successfully pulled.");

                JOptionPane.showMessageDialog(mainWindow, "Sync Backward Success!");

            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "Sync Error: " + e.getMessage());
            } finally {
                LockManager.releaseLock();
            }
        });
    }


    private String getModelNameFromDb() {
        String dbName = Neo4jDriverManager.getInstance().getActiveDatabase();
        try (org.neo4j.driver.Session session = Neo4jDriverManager.getInstance().getDriver().session(
                org.neo4j.driver.SessionConfig.forDatabase(dbName))) {

            org.neo4j.driver.Result res = session.run("MATCH (v:ModelVersion {id: 'CURRENT'}) RETURN v.modelName as name");
            if (res.hasNext()) {
                return res.single().get("name").asString();
            }
        }
        return "UntitledModel";
    }


    private FullModelSnapshot getFullModelSnapshotFromNeo4j(String modelName) {
        Neo4jModelSnapshotUtils neo4JModelSnapshotUtils = new Neo4jModelSnapshotUtils();
        return neo4JModelSnapshotUtils.getFullModelSnapshotFromNeo4j(modelName);
    }

    public void syncBackwardSilent() {
        try {
            FullModelSnapshot dbSnapshot = getFullModelSnapshotFromNeo4j(null);
            if (dbSnapshot.isEmpty()) return;

            String actualModelName = getModelNameFromDb();

            org.tzi.use.uml.mm.ModelFactory factory = new org.tzi.use.uml.mm.ModelFactory();
            org.tzi.use.uml.mm.MModel newModel = factory.createModel(actualModelName);

            ModelPullService modelPullService = new ModelPullService();
            modelPullService.pullModelFromNeo4jAfterSnapshotComparing(newModel, dbSnapshot);

            org.tzi.use.uml.sys.MSystem newSystem = new org.tzi.use.uml.sys.MSystem(newModel);
            useSession.setSystem(newSystem);


        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void syncForwardIncrementalCase(MModel incrementalModel) {
        //get user identification
        String userIdentifier = Neo4jDriverManager.getInstance().getSessionManager().getUserDisplayName();
        Neo4jAuditLogService.log("START", "MODEL_SYNC", "Global", "User initiated manual model synchronization.");

        //lock db while working
        LockManager.LockResult lock = LockManager.acquireLock(userIdentifier);
        if(!checkLock(lock)) return;
        try {
            //bug
            ModelDiff diff = modelSnapshotsAnalyzer.compareWithNeo4j(incrementalModel);
            //assums that model name doesnt matter
            KusakabeModelDiff kusakabeModelDiff = (KusakabeModelDiff) diff;

            if (kusakabeModelDiff.hasConflict()) {
                JOptionPane.showMessageDialog(
                    null,
                    "The model is in conflict.",
                    "Conflict Detected",
                    JOptionPane.WARNING_MESSAGE
                );
                return;
            }


            ModelSnapshotsAnalyzer baseBean = new ModelSnapshotCompareV1(this.useModel);
            Boolean freeForAllPushItAll = baseBean.checkBlankWorkingSpace();

            if (freeForAllPushItAll == null) {
                JOptionPane.showMessageDialog(null, "Bug");
                return;
            }
            if (freeForAllPushItAll) {
                //overwrite and add stuff that is in USE but not neo4j
                CoreModelPushService coreModelPushService = new CoreModelPushService(this.modelApi);
                coreModelPushService.pushModelToNeo4j();
            } else {
                IncrementalModelPushService incrementalModelPushService = new IncrementalModelPushService(this.modelApi);
                incrementalModelPushService.pushModelToNeo4j(diff, incrementalModel);

            }

            WorkLogManager.getInstance().log("SYNC_FORWARD_SUCCESS", "Model pushed by: " + userIdentifier);
            JOptionPane.showMessageDialog(null, "Synchronization to Neo4j completed successfully!");

        } catch (Exception e) {
            WorkLogManager.getInstance().log("SYNC_FORWARD_ERROR", e.getMessage());
            JOptionPane.showMessageDialog(null, "Sync failed: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        } finally {
            LockManager.releaseLock();
        }
    }

}