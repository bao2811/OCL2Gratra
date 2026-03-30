package org.uet.dse.neo4j.gui;

import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Neo4jLogTab extends JPanel {
    private JTable logTable;
    private DefaultTableModel tableModel;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public Neo4jLogTab() {
        setLayout(new BorderLayout());

        // Định nghĩa các cột cho bảng Log
        String[] columns = {"Time", "User", "Category", "Action", "Target", "Details"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; } // Chỉ đọc
        };

        logTable = new JTable(tableModel);
        logTable.setFillsViewportHeight(true);
        logTable.setAutoCreateRowSorter(true);

        // Cấu hình độ rộng cột
        logTable.getColumnModel().getColumn(0).setPreferredWidth(130);
        logTable.getColumnModel().getColumn(5).setPreferredWidth(250);

        add(new JScrollPane(logTable), BorderLayout.CENTER);

        // Nút xóa log trên DB (Chỉ dành cho admin)
        JButton btnClear = new JButton("Clear DB Logs");
        btnClear.addActionListener(e -> clearLogs());

        JPanel pnlBottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        pnlBottom.add(new JLabel("Auto-refreshing every 5s..."));
        pnlBottom.add(btnClear);
        add(pnlBottom, BorderLayout.SOUTH);

        // Khởi tạo Timer tự động cập nhật mỗi 5 giây
        Timer timer = new Timer(3000, e -> refreshLogs()); // 3 giây cập nhật UI 1 lần
        timer.start();

        refreshLogs(); // Load dữ liệu lần đầu
    }

    public void refreshLogs() {
        if (Neo4jDriverManager.getInstance() == null || !Neo4jDriverManager.getInstance().isConnected()) return;

        try (var session = Neo4jDriverManager.getInstance().openSession()) {
            // Truy vấn lấy 100 log mới nhất từ thực thể ActionLog
            String cypher = "MATCH (l:ActionLog) RETURN l ORDER BY l.timestamp DESC LIMIT 100";
            Result result = session.run(cypher);

            // Tạm thời dừng cập nhật UI để render mượt hơn
            tableModel.setRowCount(0);

            while (result.hasNext()) {
                var node = result.next().get("l").asNode();
                tableModel.addRow(new Object[]{
                        dateFormat.format(new Date(node.get("timestamp").asLong())),
                        node.get("user").asString("Unknown"),
                        node.get("category").asString("General"),
                        node.get("actionType").asString("Unknown"),
                        node.get("target").asString("-"),
                        node.get("details").asString("")
                });
            }
        } catch (Exception e) {
            // Im lặng nếu mất mạng
        }
    }

    private void clearLogs() {
        int confirm = JOptionPane.showConfirmDialog(this, "Delete all ActionLogs in Database?", "Confirm Clear", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            try (var session = Neo4jDriverManager.getInstance().openSession()) {
                session.run("MATCH (l:ActionLog) DETACH DELETE l");
                refreshLogs();
            }
        }
    }
}