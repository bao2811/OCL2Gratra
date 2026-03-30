package org.uet.dse.neo4j.gui;

import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.neo4j.driver.Session;
import org.tzi.use.gui.main.MainWindow;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

public class Neo4jStatusDialog extends JDialog {

    // Các Label hiển thị thông tin
    private final JLabel lblStatusIcon;
    private final JLabel lblStatusText;
    private final JLabel lblPing;

    private final JLabel valUri;
    private final JLabel valUser;
    private final JLabel valDatabase;
    private final JLabel valVersion;
    private final JLabel valEdition;

    private final JLabel valNodeCount;
    private final JLabel valRelCount;
    private final JLabel valLabelCount;

    private final JButton btnRefresh;
    private final JButton btnClose;

    public Neo4jStatusDialog(MainWindow parent) {
        super(parent, "Neo4j Connection Status");
        setLayout(new BorderLayout(10, 10));

        // --- PANEL 1: TRẠNG THÁI TỔNG QUAN ---
        JPanel pnlHeader = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        pnlHeader.setBackground(new Color(240, 240, 240));
        pnlHeader.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));

        lblStatusIcon = new JLabel("●");
        lblStatusIcon.setFont(new Font("SansSerif", Font.BOLD, 24));
        lblStatusText = new JLabel("Checking...");
        lblStatusText.setFont(new Font("SansSerif", Font.BOLD, 16));
        lblPing = new JLabel("(Latency: -- ms)");
        lblPing.setForeground(Color.GRAY);

        pnlHeader.add(lblStatusIcon);
        pnlHeader.add(lblStatusText);
        pnlHeader.add(lblPing);

        // --- PANEL 2: CHI TIẾT KẾT NỐI (Grid Layout) ---
        JPanel pnlInfo = new JPanel(new GridLayout(5, 2, 10, 10));
        pnlInfo.setBorder(createTitledBorder("Connection & Server Info"));

        pnlInfo.add(new JLabel("URI:"));
        valUri = createValueLabel();
        pnlInfo.add(valUri);

        pnlInfo.add(new JLabel("User:"));
        valUser = createValueLabel();
        pnlInfo.add(valUser);

        pnlInfo.add(new JLabel("Active Database:"));
        valDatabase = createValueLabel();
        pnlInfo.add(valDatabase);

        pnlInfo.add(new JLabel("Neo4j Version:"));
        valVersion = createValueLabel();
        pnlInfo.add(valVersion);

        pnlInfo.add(new JLabel("Edition:"));
        valEdition = createValueLabel();
        pnlInfo.add(valEdition);

        // --- PANEL 3: THỐNG KÊ DỮ LIỆU (Data Stats) ---
        JPanel pnlStats = new JPanel(new GridLayout(3, 2, 10, 10));
        pnlStats.setBorder(createTitledBorder("Database Statistics"));

        pnlStats.add(new JLabel("Total Nodes:"));
        valNodeCount = createValueLabel();
        pnlStats.add(valNodeCount);

        pnlStats.add(new JLabel("Total Relationships:"));
        valRelCount = createValueLabel();
        pnlStats.add(valRelCount);

        pnlStats.add(new JLabel("Defined Labels:"));
        valLabelCount = createValueLabel();
        pnlStats.add(valLabelCount);

        // Gom nhóm Panel 2 và 3
        JPanel pnlBody = new JPanel();
        pnlBody.setLayout(new BoxLayout(pnlBody, BoxLayout.Y_AXIS));
        pnlBody.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        pnlBody.add(pnlInfo);
        pnlBody.add(Box.createVerticalStrut(15));
        pnlBody.add(pnlStats);

        // --- BUTTONS ---
        JPanel pnlBtn = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnRefresh = new JButton("Refresh");
        btnClose = new JButton("Close");
        pnlBtn.add(btnRefresh);
        pnlBtn.add(btnClose);

        // --- Add to Dialog ---
        add(pnlHeader, BorderLayout.NORTH);
        add(pnlBody, BorderLayout.CENTER);
        add(pnlBtn, BorderLayout.SOUTH);

        // --- Events ---
        btnRefresh.addActionListener(e -> refreshData());
        btnClose.addActionListener(e -> dispose());

        // Cấu hình Dialog
        setSize(400, 550);
        setLocationRelativeTo(parent);
        setResizable(false);

        // Tự động load dữ liệu khi mở
        refreshData();
    }

    private JLabel createValueLabel() {
        JLabel lbl = new JLabel("---");
        lbl.setFont(new Font("SansSerif", Font.BOLD, 12));
        lbl.setForeground(new Color(0, 102, 204)); // Màu xanh đậm
        return lbl;
    }

    private TitledBorder createTitledBorder(String title) {
        TitledBorder border = BorderFactory.createTitledBorder(title);
        border.setTitleFont(new Font("SansSerif", Font.BOLD, 12));
        return border;
    }

    /**
     * Hàm quan trọng nhất: Load dữ liệu từ Neo4j
     */
    private void refreshData() {
        btnRefresh.setEnabled(false);
        lblStatusText.setText("Refreshing...");
        lblStatusIcon.setForeground(Color.ORANGE);

        new SwingWorker<Void, Void>() {
            boolean isConnected = false;
            long pingTime = -1;
            String dbName = "Unknown";
            String version = "Unknown";
            String edition = "Unknown";
            String serverUri = "Unknown";
            String userName = "Unknown"; // Cần lưu lại user nếu manager hỗ trợ
            long nodes = 0;
            long edges = 0;
            long labels = 0;

            @Override
            protected Void doInBackground() {
                Neo4jDriverManager manager = Neo4jDriverManager.getInstance();

                // 1. Kiểm tra driver cơ bản
                if (manager == null || manager.getDriver() == null) {
                    return null; // Chưa khởi tạo
                }

                // Lấy thông tin tĩnh từ config (nếu có thể lưu trong manager thì tốt hơn)
                // Ở đây ta lấy tạm active DB từ manager
                try {
                    // Nếu bạn muốn hiển thị URI, cần getter trong Manager.
                    // Giả sử ta lấy từ config hoặc manager nếu có.
                    // serverUri = manager.getUri();
                } catch(Exception e) {}

                // 2. Kiểm tra kết nối & Đo Ping
                long start = System.currentTimeMillis();
                if (!manager.isConnected()) {
                    return null;
                }
                pingTime = System.currentTimeMillis() - start;
                isConnected = true;

                // 3. Lấy thông tin chi tiết qua Session
                try (Session session = manager.openSession()) {
                    // 3a. Lấy Server Info (Version, Edition)
                    session.executeRead(tx -> {

                        var res = tx.run("CALL dbms.components() YIELD name, versions, edition RETURN name, versions[0] as ver, edition");
                        if (res.hasNext()) {
                            var record = res.next(); // <--- Đổi single() thành next()
                            version = record.get("ver").asString();
                            edition = record.get("edition").asString();
                        }

                        // Lấy tên DB thật sự
                        var dbRes = tx.run("CALL db.info() YIELD name RETURN name");
                        if (dbRes.hasNext()) {
                            dbName = dbRes.next().get("name").asString(); // <--- Đổi single() thành next()
                        }

                        // Lấy User hiện tại
                        var userRes = tx.run("CALL dbms.showCurrentUser() YIELD username RETURN username");
                        if (userRes.hasNext()) {
                            userName = userRes.next().get("username").asString(); // <--- Đổi single() thành next()
                        }
                        return null;
                    });

                    // 3b. Lấy Thống kê (Statistics)
                    session.executeRead(tx -> {
                        // Đếm nodes
                        var r1 = tx.run("MATCH (n) RETURN count(n) as c");
                        if (r1.hasNext()) nodes = r1.next().get("c").asLong(); // <--- Đổi single() thành next() cho an toàn

                        // Đếm edges
                        var r2 = tx.run("MATCH ()-[r]->() RETURN count(r) as c");
                        if (r2.hasNext()) edges = r2.next().get("c").asLong(); // <--- Đổi single() thành next()

                        // Đếm labels (số loại node)
                        var r3 = tx.run("CALL db.labels() YIELD label RETURN count(label) as c");
                        if (r3.hasNext()) labels = r3.next().get("c").asLong(); // <--- Đổi single() thành next()

                        return null;
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    isConnected = false; // Nếu lỗi lúc query cũng coi như fail
                }
                return null;
            }

            @Override
            protected void done() {
                // Cập nhật giao diện
                btnRefresh.setEnabled(true);

                if (isConnected) {
                    // Update Header
                    lblStatusIcon.setForeground(Color.GREEN);
                    lblStatusText.setText("CONNECTED");
                    lblPing.setText("(Latency: " + pingTime + " ms)");

                    // Update Info
                    valUri.setText("Connected (via Driver)"); // Hoặc hiển thị URI thật nếu lưu
                    valUser.setText(userName);
                    valDatabase.setText(dbName);
                    valVersion.setText(version);
                    valEdition.setText(edition);

                    // Update Stats
                    valNodeCount.setText(String.format("%,d", nodes));
                    valRelCount.setText(String.format("%,d", edges));
                    valLabelCount.setText(String.format("%,d types", labels));
                } else {
                    // Update Header Offline
                    lblStatusIcon.setForeground(Color.RED);
                    lblStatusText.setText("DISCONNECTED");
                    lblPing.setText("(-- ms)");

                    // Reset Info
                    valUri.setText("---");
                    valUser.setText("---");
                    valDatabase.setText("---");
                    valVersion.setText("---");
                    valEdition.setText("---");

                    // Reset Stats
                    valNodeCount.setText("---");
                    valRelCount.setText("---");
                    valLabelCount.setText("---");

                    JOptionPane.showMessageDialog(Neo4jStatusDialog.this,
                            "Không thể kết nối tới Neo4j.\nHãy kiểm tra lại Connection Settings.",
                            "Connection Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
}