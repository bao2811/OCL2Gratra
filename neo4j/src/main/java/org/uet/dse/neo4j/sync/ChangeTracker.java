package org.uet.dse.neo4j.sync;

import org.neo4j.driver.*;
import org.tzi.use.gui.main.MainWindow;
import org.uet.dse.neo4j.collaboration.ConsensusService;
import org.uet.dse.neo4j.gui.ProposalVotingDialog;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.uet.dse.neo4j.sync.model.ModelSyncCoordinator;
import org.uet.dse.neo4j.sync.object.ObjectSyncCoordinator;

import javax.swing.*;
import java.util.concurrent.*;

public class ChangeTracker {
    private static long lastKnownTimestamp = 0;
    private static String lastProposalId = "";
    private static boolean isApprovalDialogShown = false;
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static boolean outOfSync = false;

    public static void startMonitoring(JLabel statusLabel) {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (Neo4jDriverManager.getInstance().isConnected()) {
                    long dbTimestamp = getRemoteModelTimestamp();
                    if (lastKnownTimestamp != 0 && dbTimestamp > lastKnownTimestamp) {
                        outOfSync = true;
                        SwingUtilities.invokeLater(() -> {
                            statusLabel.setText("<html><font color='red'>⚠ DB Updated - Pull Required!</font></html>");
                        });
                    }
                    if (lastKnownTimestamp == 0) lastKnownTimestamp = dbTimestamp;
                }
            } catch (Exception e) {

            }
        }, 0, 5, TimeUnit.SECONDS); // 5 giây kiểm tra 1 lần
    }

    private static long getRemoteModelTimestamp() {
        String dbName = Neo4jDriverManager.getInstance().getActiveDatabase();
        try (Session session = Neo4jDriverManager.getInstance().getDriver().session(SessionConfig.forDatabase(dbName))) {
            Result res = session.run("MATCH (v:ModelVersion {id: 'CURRENT'}) RETURN v.timestamp AS ts");
            if (res.hasNext()) return res.single().get("ts").asLong();
        }
        return 0;
    }

    public static void updateLocalTimestamp(long ts) {
        lastKnownTimestamp = ts;
        outOfSync = false;
    }

    public static void startMonitoring(MainWindow mainWindow, org.tzi.use.main.Session session, JLabel lblModel, JLabel lblObj) {
//        scheduler.scheduleAtFixedRate(() -> {
//            try {
//                if (Neo4jDriverManager.getInstance().isConnected()) {
//                    // 1. HEARTBEAT: Giữ trạng thái ONLINE
//                    ConsensusService.getInstance().heartbeat();
//
//                    // 2. DASHBOARD STATUS: Cập nhật đèn báo Xanh/Đỏ
//                    updateUIStatus(session, lblModel, lblObj);
//
//                    // 3. COLLABORATION: Kiểm tra Bỏ phiếu/Đề xuất
//                    checkForConsensusEvents(mainWindow, session);
//                }
//            } catch (Exception e) {
//                // Log im lặng để không làm phiền người dùng khi mất mạng
//            }
//        }, 0, 5, TimeUnit.SECONDS);
    }



}