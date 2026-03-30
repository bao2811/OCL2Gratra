package org.uet.dse.neo4j.gui;

import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.main.Session;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.uet.dse.neo4j.manager.SessionManager;
import org.uet.dse.neo4j.manager.WorkLogManager;
import org.uet.dse.neo4j.sync.ChangeTracker;
import org.uet.dse.neo4j.sync.model.ModelSyncCoordinator;
import org.uet.dse.neo4j.sync.object.ObjectSyncCoordinator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class Neo4jDashboardView extends JDialog {
    private static Neo4jDashboardView instance;
    private final Session session;
    private final MainWindow mainWindow;

    // UI Components - Metadata
    private JLabel lblUri, lblDb, lblUser, lblVersion, lblKey, lblDate;

    // UI Components - Status
    private JLabel lblModelStatus, lblObjectStatus, lblActiveStatus;
    private JButton btnToggleActive;

    public static Neo4jDashboardView getInstance(MainWindow parent, Session session) {
        if (instance == null) {
            instance = new Neo4jDashboardView(parent, session);
        }
        return instance;
    }

    private Neo4jDashboardView(MainWindow parent, Session session) {
        super(parent, "Neo4j Collaboration Dashboard", false); // Non-modal
        this.session = session;
        this.mainWindow = parent;

        // Cấu hình cửa sổ
        this.setSize(900, 650);
        this.setLayout(new BorderLayout());
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // --- BƯỚC 1: KHỞI TẠO UI (Cực kỳ quan trọng: Phải tạo Node UI trước khi nạp Data) ---
        initAllComponents();

        // --- BƯỚC 2: NẠP DỮ LIỆU BAN ĐẦU ---
        refreshData();

        // --- BƯỚC 3: KÍCH HOẠT THEO DÕI REAL-TIME ---
        ChangeTracker.startMonitoring(parent, session, lblModelStatus, lblObjectStatus);

        // Xử lý khi đóng cửa sổ
        this.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                instance = null; // Để lần sau mở lại sẽ tạo mới
            }
        });

        this.setLocationRelativeTo(parent);
    }

    private void initAllComponents() {
        // 1. Metadata Panel (Cố định ở phía trên)
        add(createMetadataPanel(), BorderLayout.NORTH);

        // 2. Hệ thống Tab ở giữa
        JTabbedPane tabbedPane = new JTabbedPane();

        // --- Tab 1: Synchronization ---
        JPanel pnlSync = new JPanel(new BorderLayout(10, 10));
        pnlSync.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        pnlSync.add(createStatusPanel(), BorderLayout.CENTER);
        pnlSync.add(createSyncControlPanel(), BorderLayout.SOUTH);

        tabbedPane.addTab("Sync & Management", pnlSync);

        // --- Tab 2: Collaborative Logs ---
        tabbedPane.addTab("Collaborative Logs", new Neo4jLogTab());

        add(tabbedPane, BorderLayout.CENTER);
    }

    private JPanel createMetadataPanel() {
        JPanel pnl = new JPanel(new GridLayout(3, 2, 10, 8));
        pnl.setBorder(BorderFactory.createTitledBorder("Database Information"));
        pnl.setBackground(new Color(240, 248, 255));

        lblUri = new JLabel("Server: -");
        lblDb = new JLabel("Database: -");
        lblUser = new JLabel("User: -");
        lblVersion = new JLabel("Neo4j Version: -");
        lblKey = new JLabel("Session Key: -");
        lblDate = new JLabel("Connected Date: -");

        pnl.add(lblUri); pnl.add(lblDb);
        pnl.add(lblUser); pnl.add(lblVersion);
        pnl.add(lblKey); pnl.add(lblDate);
        return pnl;
    }

    private JPanel createStatusPanel() {
        JPanel pnlMain = new JPanel(new GridLayout(3, 1, 10, 10));
        pnlMain.setBorder(BorderFactory.createTitledBorder("Live Synchronization Status"));

        // Row 1: Model
        JPanel pnlModel = new JPanel(new BorderLayout());
        lblModelStatus = new JLabel("Model Status: Initializing...");
        lblModelStatus.setFont(new Font("SansSerif", Font.BOLD, 13));
        JButton btnModelInspect = new JButton("Inspect & Selective Sync");
        btnModelInspect.addActionListener(e -> new SyncDetailDialog("Model",
                new ModelSyncCoordinator(session, mainWindow),
                new ObjectSyncCoordinator(session.system())).setVisible(true));
        pnlModel.add(lblModelStatus, BorderLayout.CENTER);
        pnlModel.add(btnModelInspect, BorderLayout.EAST);

        // Row 2: Object
        JPanel pnlObject = new JPanel(new BorderLayout());
        lblObjectStatus = new JLabel("Object Status: Initializing...");
        lblObjectStatus.setFont(new Font("SansSerif", Font.BOLD, 13));
        JButton btnObjectInspect = new JButton("Inspect & Selective Sync");
        btnObjectInspect.addActionListener(e -> new SyncDetailDialog("Object",
                new ModelSyncCoordinator(session, mainWindow),
                new ObjectSyncCoordinator(session.system())).setVisible(true));
        pnlObject.add(lblObjectStatus, BorderLayout.CENTER);
        pnlObject.add(btnObjectInspect, BorderLayout.EAST);

        // Row 3: Active Status
        JPanel pnlActive = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        lblActiveStatus = new JLabel("STATUS: UNKNOWN");
        lblActiveStatus.setFont(new Font("SansSerif", Font.BOLD, 14));
        btnToggleActive = new JButton("Go Inactive");
        btnToggleActive.addActionListener(e -> toggleActiveStatus());
        pnlActive.add(lblActiveStatus);
        pnlActive.add(btnToggleActive);

        pnlMain.add(pnlModel);
        pnlMain.add(pnlObject);
        pnlMain.add(pnlActive);

        return pnlMain;
    }

    private JPanel createSyncControlPanel() {
        JPanel pnlActions = new JPanel(new BorderLayout(10, 10));

        JPanel pnlButtons = new JPanel(new GridLayout(2, 2, 10, 10));
        pnlButtons.setBorder(BorderFactory.createTitledBorder("Manual Sync Controls"));

        JButton btnPushModel = new JButton("Push Model (Java -> DB)");
        JButton btnPullModel = new JButton("Pull Model (DB -> Java)");
        JButton btnPushObject = new JButton("Push Objects (Java -> DB)");
        JButton btnPullObject = new JButton("Pull Objects (DB -> Java)");

        btnPushModel.addActionListener(e -> new ModelSyncCoordinator(session, mainWindow).syncForward());
        btnPullModel.addActionListener(e -> new ModelSyncCoordinator(session, mainWindow).syncBackward());
        btnPushObject.addActionListener(e -> new ObjectSyncCoordinator(session.system()).syncObjectsForward(true));
        btnPullObject.addActionListener(e -> new ObjectSyncCoordinator(session.system()).syncObjectsBackward(true));

        pnlButtons.add(btnPushModel); pnlButtons.add(btnPullModel);
        pnlButtons.add(btnPushObject); pnlButtons.add(btnPullObject);

        JPanel pnlMisc = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnSettings = new JButton("Settings");
        btnSettings.addActionListener(e -> new SyncSettingsDialog(null).setVisible(true));
        pnlMisc.add(btnSettings);

        pnlActions.add(pnlButtons, BorderLayout.CENTER);
        pnlActions.add(pnlMisc, BorderLayout.SOUTH);
        return pnlActions;
    }

    public void refreshData() {
        Neo4jDriverManager manager = Neo4jDriverManager.getInstance();
        if (manager == null || !manager.isConnected()) return;

        Map<String, Object> stats = manager.getDatabaseStats();
        SessionManager sm = manager.getSessionManager();

        if (lblUri != null) lblUri.setText("Server: " + manager.getUri());
        if (lblDb != null) lblDb.setText("Database: " + manager.getActiveDatabase());
        if (lblUser != null) lblUser.setText("User: " + manager.getUser());
        if (lblVersion != null) lblVersion.setText("Neo4j Version: " + stats.getOrDefault("version", "N/A"));

        if (sm != null) {
            if (lblKey != null) lblKey.setText("Session Key: " + sm.getSessionKey());
            if (lblDate != null) lblDate.setText("Connected Date: " + sm.getConnectionDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
            updateStatusUI(sm.isActive());
        }
    }

    private void toggleActiveStatus() {
        SessionManager sm = Neo4jDriverManager.getInstance().getSessionManager();
        boolean newStatus = !sm.isActive();
        sm.setActive(newStatus);
        updateStatusUI(newStatus);
        WorkLogManager.getInstance().log("STATUS_UPDATE", "User set to " + (newStatus ? "ACTIVE" : "INACTIVE"));
    }

    private void updateStatusUI(boolean isActive) {
        if (lblActiveStatus != null) {
            lblActiveStatus.setText("STATUS: " + (isActive ? "ACTIVE" : "INACTIVE"));
            lblActiveStatus.setForeground(isActive ? new Color(0, 150, 0) : Color.RED);
        }
        if (btnToggleActive != null) {
            btnToggleActive.setText(isActive ? "Go Inactive" : "Go Active");
        }
    }
}