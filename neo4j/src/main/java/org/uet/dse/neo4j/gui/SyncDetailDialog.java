package org.uet.dse.neo4j.gui;

import org.tzi.use.uml.mm.MClass;
import org.uet.dse.neo4j.sync.model.ModelDiff;
import org.uet.dse.neo4j.sync.model.CoreModelPushService;
import org.uet.dse.neo4j.sync.model.ModelSyncCoordinator;
import org.uet.dse.neo4j.sync.object.ObjectDiff;
import org.uet.dse.neo4j.sync.object.ObjectSyncCoordinator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public class SyncDetailDialog extends JDialog {
    private JList<CheckListItem> list;
    private DefaultListModel<CheckListItem> listModel;

    private final ObjectSyncCoordinator objCoord;
    private final ModelSyncCoordinator modelCoord;

    public SyncDetailDialog(String type, ModelSyncCoordinator modelCoord, ObjectSyncCoordinator objCoord) {
        this.setTitle("Deep Sync Check: " + type + " Level");
        this.setSize(650, 550);
        this.setLayout(new BorderLayout(10, 10));
        this.setModal(true);
        this.modelCoord = modelCoord;
        this.objCoord = objCoord;

        listModel = new DefaultListModel<>();

        // 1. THỐNG KÊ CHI TIẾT (Deep Check)
        if (type.equals("Model")) {
            // Lấy Diff từ Model Coordinator
            ModelDiff diff = modelCoord.getModelSnapshotsAnalyzer().compareWithNeo4j("null");

            // Những thứ chỉ có ở Java (Cần đẩy lên DB)
            diff.javaOnlyClasses.forEach(c ->
                    listModel.addElement(new CheckListItem("[JAVA ONLY] Class: " + c + " (Missing in DB)")));

            // Những thứ chỉ có ở DB (Cần kéo về Java)
            diff.neo4jOnlyClasses.forEach(c ->
                    listModel.addElement(new CheckListItem("[DB ONLY] Class: " + c + " (Missing in Java)")));

            // Những thứ bị lệch cấu trúc
            diff.mismatchedClasses.forEach(c ->
                    listModel.addElement(new CheckListItem("[MISMATCH] Class: " + c + " (Schema differs)")));

        }else if (type.equals("Object")) {
            // 1. Lấy Snapshot từ cả 2 phía và thực hiện so sánh khách quan
            ObjectDiff diff = objCoord.compareObjects();

            // 2. Thống kê những đối tượng chỉ có ở máy Java (Cần Push)
            diff.javaOnlyObjects.forEach(o ->
                    listModel.addElement(new CheckListItem("[JAVA ONLY] Object: " + o + " (Missing in DB)")));

            // 3. Thống kê những đối tượng chỉ có ở dưới Database (Cần Pull)
            diff.neo4jOnlyObjects.forEach(o ->
                    listModel.addElement(new CheckListItem("[DB ONLY] Object: " + o + " (Missing in USE)")));

            // 4. Thống kê những đối tượng bị lệch giá trị hoặc tham chiếu
            diff.mismatchedObjects.forEach(o -> {
                String reason = "";
                // Nếu có thông tin chi tiết về sự sai lệch (ví dụ: sai giá trị age)
                if (diff.mismatchDetails.containsKey(o) && !diff.mismatchDetails.get(o).isEmpty()) {
                    reason = " (" + diff.mismatchDetails.get(o).get(0) + ")"; // Hiển thị lỗi đầu tiên để làm ví dụ
                }
                listModel.addElement(new CheckListItem("[MISMATCH] Object: " + o + reason));
            });
        }

        // Kiểm tra nếu không có gì thay đổi
        if (listModel.isEmpty()) {
            listModel.addElement(new CheckListItem("--- All elements are fully synchronized ---"));
        }

        // 2. Cấu hình giao diện JList hỗ trợ CheckBox
        list = new JList<>(listModel);
        list.setCellRenderer(new CheckListRenderer());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Lắng nghe click chuột để Toggle checkbox
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                int index = list.locationToIndex(event.getPoint());
                if (index >= 0) {
                    CheckListItem item = list.getModel().getElementAt(index);
                    if (!item.getLabel().startsWith("---")) { // Không cho click vào dòng thông báo "Fully Synced"
                        item.setSelected(!item.isSelected());
                        list.repaint(list.getCellBounds(index, index));
                    }
                }
            }
        });

        // 3. Header thông tin
        JLabel lblHeader = new JLabel("<html><b>Detected Inconsistencies:</b> Select items to synchronize selectively.</html>");
        lblHeader.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        this.add(lblHeader, BorderLayout.NORTH);

        this.add(new JScrollPane(list), BorderLayout.CENTER);

        // 4. Panel nút bấm (Selective Sync)
        JPanel pnlButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnSyncSelected = new JButton("Sync Selected Components");
        JButton btnCancel = new JButton("Close");

        btnSyncSelected.addActionListener(e -> {
            List<String> selectedLabels = new ArrayList<>();
            for (int i = 0; i < listModel.size(); i++) {
                CheckListItem item = listModel.getElementAt(i);
                if (item.isSelected()) {
                    selectedLabels.add(item.getLabel());
                }
            }

            if (selectedLabels.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please select at least one component to sync.");
                return;
            }

            // Xử lý Sync chọn lọc (Extra Task)
            handleSelectiveSync(selectedLabels, type);
        });

        btnCancel.addActionListener(e -> dispose());

        pnlButtons.add(btnSyncSelected);
        pnlButtons.add(btnCancel);
        this.add(pnlButtons, BorderLayout.SOUTH);

        this.setLocationRelativeTo(null);
    }

    /**
     * Logic xử lý đồng bộ chọn lọc (Extra Feature)
     */
    private void handleSelectiveSync(List<String> selectedLabels, String syncType) {
        try {
            for (String label : selectedLabels) {
                // Lấy tên thực thể từ label (vd: "[JAVA ONLY] Class: Person" -> "Person")
                String name = extractNameFromLabel(label);

                if (syncType.equals("Model")) {
                    CoreModelPushService coreModelPushService = new CoreModelPushService(modelCoord.getModelApi());
                    if (label.contains("[JAVA ONLY]")) {
                        // Java có, DB thiếu -> Push Class
                        MClass mClass = modelCoord.getUseModel().getClass(name);
                        coreModelPushService.pushSingleClass(mClass);
                    } else if (label.contains("[DB ONLY]")) {
                        // DB có, Java thiếu -> Pull Class
                        coreModelPushService.pullSingleClass(name);
                    }
                } else if (syncType.equals("Object")) {
                    if (label.contains("[JAVA ONLY]")) {
                        // Push Object đơn lẻ
                        objCoord.pushSingleObjectByName(name);
                    }
                    // Tương tự pullSingleObjectByName nếu cần
                }
            }

            JOptionPane.showMessageDialog(this, "Selected components synchronized successfully!");
            this.dispose();

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error: " + e.getMessage());
        }
    }

    private String extractNameFromLabel(String label) {
        // Logic tách chuỗi: "[JAVA ONLY] Class: Person (Missing in DB)" -> lấy "Person"
        try {
            String[] parts = label.split(": ");
            String namePart = parts[1];
            if (namePart.contains(" ")) {
                return namePart.split(" ")[0];
            }
            return namePart;
        } catch (Exception e) {
            return label;
        }
    }
}