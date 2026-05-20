package org.uet.dse.neo4jtgg.ui;

import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.main.Session;
import org.uet.dse.neo4jtgg.model.TggWorkspaceContext;
import org.uet.dse.neo4jtgg.service.impl.DefaultTggWorkspaceService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;

public class TggWorkspaceDialog extends JDialog {
    private final DefaultTggWorkspaceService workspaceService;
    private final TggWorkspaceContext context;

    private final JTextField txtSource = new JTextField();
    private final JTextField txtTarget = new JTextField();
    private final JTextField txtTgg = new JTextField();
    private final JTextField txtCorr = new JTextField();
    private final JTextArea txtInfo = new JTextArea();

    public TggWorkspaceDialog(MainWindow parent, Session session) {
        super(parent, "Neo4j TGG Workspace", true);
        this.workspaceService = DefaultTggWorkspaceService.getInstance();
        this.context = workspaceService.createDefaultContext(new TggWorkspaceContext(session, parent));

        setLayout(new BorderLayout(10, 10));
        ((JComponent) getContentPane()).setBorder(new EmptyBorder(12, 12, 12, 12));
        setSize(820, 520);
        setMinimumSize(new Dimension(680, 420));

        add(buildForm(), BorderLayout.NORTH);
        add(buildInfoPanel(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        bindDefaults();
        setLocationRelativeTo(parent);
    }

    private JPanel buildForm() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        addRow(panel, gbc, 0, "Source .use", txtSource, e -> browse(txtSource, false));
        addRow(panel, gbc, 1, "Target .use", txtTarget, e -> browse(txtTarget, false));
        addRow(panel, gbc, 2, "TGG file", txtTgg, e -> browse(txtTgg, false));
        addRow(panel, gbc, 3, "Correspondence", txtCorr, e -> browse(txtCorr, true));

        return panel;
    }

    private JScrollPane buildInfoPanel() {
        txtInfo.setEditable(false);
        txtInfo.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        txtInfo.setLineWrap(true);
        txtInfo.setWrapStyleWord(true);
        txtInfo.setText("""
                Demo default:
                - rtl/examples/Families2Persons/Families.use
                - rtl/examples/Families2Persons/Persons.use
                - rtl/examples/Families2Persons/F2PForward.tgg
                - Source XMI import default: examples/families2person/src/models/Families.xmi
                - Target XMI import default: examples/families2person/src/models/Persons.xmi

                Run will:
                1. Require an active Neo4j connection from 'Neo4j Plugin phase 2 -> Open connection'
                2. Load source/target USE metadata, derive a correspondence model from the TGG file, and load rule metadata
                3. Reuse existing Neo4j metamodels when found; import only missing source/corr/target models (M2/M1)
                4. Read source/corr/target object snapshots directly from Neo4j for the graph views
                5. Open 3 graph windows: Source / Correspondence / Target
                6. Open neo4j-tgg runtime status window with Refresh USE Mirror

                If Neo4j is not connected, Run Workspace fails immediately.
                Run Workspace does not create M0 instances from .use files.
                Source and Target XMI/XML imports write M0 instances directly to Neo4j and link them to the imported metamodel.
                OCL validation runs against Neo4j.
                USE is only a reflected mirror rebuilt from Neo4j snapshots.
                """);
        JScrollPane scrollPane = new JScrollPane(txtInfo);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Workspace Information"));
        return scrollPane;
    }

    private JPanel buildButtons() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnRun = new JButton("Run Workspace");
        JButton btnClose = new JButton("Close");

        btnRun.addActionListener(e -> runWorkspace());
        btnClose.addActionListener(e -> dispose());

        panel.add(btnRun);
        panel.add(btnClose);
        return panel;
    }

    private void addRow(JPanel panel, GridBagConstraints gbc, int row, String label, JTextField textField,
                        java.awt.event.ActionListener browseAction) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        panel.add(new JLabel(label), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(textField, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        JButton btnBrowse = new JButton("Browse");
        btnBrowse.addActionListener(browseAction);
        panel.add(btnBrowse, gbc);
    }

    private void bindDefaults() {
        txtSource.setText(context.getSourceFile().getPath());
        txtTarget.setText(context.getTargetFile().getPath());
        txtTgg.setText(context.getTggFile().getPath());
    }

    private void browse(JTextField target, boolean optional) {
        JFileChooser chooser = new JFileChooser();
        if (!target.getText().isBlank()) {
            chooser.setSelectedFile(new File(target.getText()));
        }
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            target.setText(chooser.getSelectedFile().getAbsolutePath());
        } else if (optional && target == txtCorr) {
            target.setText("");
        }
    }

    private void runWorkspace() {
        try {
            context.setSourceFile(new File(txtSource.getText().trim()));
            context.setTargetFile(new File(txtTarget.getText().trim()));
            context.setTggFile(new File(txtTgg.getText().trim()));
            if (!txtCorr.getText().trim().isBlank()) {
                context.setCorrespondenceFile(new File(txtCorr.getText().trim()));
            }

            workspaceService.loadWorkspace(context);
            workspaceService.openGraphViews(context);
            dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Workspace Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
