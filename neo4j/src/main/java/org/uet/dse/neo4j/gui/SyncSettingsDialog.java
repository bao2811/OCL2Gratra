package org.uet.dse.neo4j.gui;

import org.uet.dse.neo4j.config.SyncConfig;
import org.uet.dse.neo4j.realtime.Neo4jRealTimeService;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

public class SyncSettingsDialog extends JDialog {
    private JCheckBox chkPushModel, chkPushObject, chkPullModel, chkPullObject;
    private JComboBox<String> comboFrequency;

    public SyncSettingsDialog(Frame parent) {
        super(parent, "Sync & Frequency Settings", true);
        this.setLayout(new BorderLayout());

        // Panel chính chứa tất cả các mục
        JPanel pnlMain = new JPanel();
        pnlMain.setLayout(new BoxLayout(pnlMain, BoxLayout.Y_AXIS));
        pnlMain.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        SyncConfig config = SyncConfig.getInstance();

        // --- Group 1: Outgoing Sync (USE to Neo4j) ---
        JPanel pnlPush = new JPanel(new GridLayout(0, 1, 5, 5));
        pnlPush.setBorder(BorderFactory.createTitledBorder("Outgoing (USE -> Neo4j)"));
        chkPushModel = new JCheckBox("Immediate Sync Model on Import", config.autoPushModelOnImport);
        chkPushObject = new JCheckBox("Immediate Sync Object on Change", config.autoPushObjectOnChange);
        pnlPush.add(chkPushModel);
        pnlPush.add(chkPushObject);
        pnlMain.add(pnlPush);

        pnlMain.add(Box.createVerticalStrut(10)); // Khoảng cách giữa các group

        // --- Group 2: Incoming Sync (Neo4j to USE) ---
        JPanel pnlPull = new JPanel(new GridLayout(0, 1, 5, 5));
        pnlPull.setBorder(BorderFactory.createTitledBorder("Incoming (Neo4j -> USE)"));
        chkPullModel = new JCheckBox("Immediate Receive Model changes from DB", config.autoPullModelOnChange);
        chkPullObject = new JCheckBox("Immediate Receive Object changes from DB", config.autoPullObjectOnChange);
        pnlPull.add(chkPullModel);
        pnlPull.add(chkPullObject);
        pnlMain.add(pnlPull);

        pnlMain.add(Box.createVerticalStrut(10));

        // ============================================================
        // CHỖ CẦN SỬA: GỌI HÀM HIỂN THỊ TẦN SUẤT TẠI ĐÂY
        // ============================================================
        addFrequencyPanel(pnlMain);
        // ============================================================

        // --- Buttons Panel ---
        JPanel pnlBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnSave = new JButton("Save & Apply Settings");
        btnSave.setBackground(new Color(0, 120, 215)); // Màu xanh Windows
        btnSave.setForeground(Color.WHITE);
        btnSave.setFocusPainted(false);

        JButton btnCancel = new JButton("Cancel");

        btnSave.addActionListener(e -> {
            // 1. Lưu cấu hình các nút gạt (Checkbox)
            config.autoPushModelOnImport = chkPushModel.isSelected();
            config.autoPushObjectOnChange = chkPushObject.isSelected();
            config.autoPullModelOnChange = chkPullModel.isSelected();
            config.autoPullObjectOnChange = chkPullObject.isSelected();

            // 2. Lưu cấu hình tần suất quét (Frequency)
            int index = comboFrequency.getSelectedIndex();
            int[] intervals = { 500, 1000, 2000, 5000 };
            config.syncInterval = intervals[index];

            config.save(); // Ghi file .properties

            // 3. Kích hoạt lại Service để áp dụng tần suất mới ngay lập tức
            Neo4jRealTimeService.restart();

            JOptionPane.showMessageDialog(this, "Settings saved and applied successfully!");
            dispose();
        });

        btnCancel.addActionListener(e -> dispose());

        pnlBtns.add(btnSave);
        pnlBtns.add(btnCancel);

        // Thêm các panel vào Dialog
        this.add(pnlMain, BorderLayout.CENTER);
        this.add(pnlBtns, BorderLayout.SOUTH);

        this.pack();
        this.setResizable(false);
        this.setLocationRelativeTo(parent);
    }

    /**
     * Hàm xây dựng giao diện phần chọn tần suất
     */
    private void addFrequencyPanel(JPanel mainPanel) {
        JPanel pnlFreq = new JPanel(new FlowLayout(FlowLayout.LEFT));
        pnlFreq.setBorder(BorderFactory.createTitledBorder("Sync Frequency (Polling Rate)"));

        String[] modes = {
                "Super Real-time (0.5s)",
                "Fast (1s)",
                "Balanced (2s) - Recommended",
                "Stable (5s)"
        };
        comboFrequency = new JComboBox<>(modes);

        // Đọc giá trị hiện tại từ config để set mặc định cho ComboBox
        int currentInterval = SyncConfig.getInstance().syncInterval;
        if (currentInterval <= 500) comboFrequency.setSelectedIndex(0);
        else if (currentInterval <= 1000) comboFrequency.setSelectedIndex(1);
        else if (currentInterval <= 2000) comboFrequency.setSelectedIndex(2);
        else comboFrequency.setSelectedIndex(3);

        pnlFreq.add(new JLabel("Refresh Mode: "));
        pnlFreq.add(comboFrequency);

        // Đảm bảo panel giãn hết chiều ngang
        pnlFreq.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

        mainPanel.add(pnlFreq);
    }
}