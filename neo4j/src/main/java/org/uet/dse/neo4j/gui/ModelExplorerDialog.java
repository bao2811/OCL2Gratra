package org.uet.dse.neo4j.gui;

import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.neo4j.driver.Result;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ModelExplorerDialog extends JDialog {
    private JComboBox<String> cbModels;
    private String selectedModel = null;

    public ModelExplorerDialog(Frame parent) {
        super(parent, "Select Model from Neo4j", true);
        setSize(400, 200);
        setLayout(new BorderLayout(15, 15));
        ((JComponent)getContentPane()).setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        List<String> models = fetchModelNames();
        cbModels = new JComboBox<>(models.toArray(new String[0]));

        add(new JLabel("Available models in Repository:"), BorderLayout.NORTH);
        add(cbModels, BorderLayout.CENTER);

        JButton btnConfirm = new JButton("Confirm & Pull");
        btnConfirm.setBackground(new Color(0, 102, 204));
        btnConfirm.setForeground(Color.WHITE);
        btnConfirm.addActionListener(e -> {
            selectedModel = (String) cbModels.getSelectedItem();
            dispose();
        });

        JButton btnCancel = new JButton("Cancel");
        btnCancel.addActionListener(e -> {
            selectedModel = null;
            dispose();
        });

        JPanel pnlBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        pnlBtns.add(btnCancel);
        pnlBtns.add(btnConfirm);

        add(pnlBtns, BorderLayout.SOUTH);
        setLocationRelativeTo(parent);
    }

    private List<String> fetchModelNames() {
        List<String> list = new ArrayList<>();
        try (var session = Neo4jDriverManager.getInstance().openSession()) {
            Result res = session.run("MATCH (m:ManageModel) RETURN m.name as name ORDER BY m.name");
            while (res.hasNext()) list.add(res.next().get("name").asString());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public String getSelectedModelName() {
        return selectedModel;
    }
}