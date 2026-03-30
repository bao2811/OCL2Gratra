package org.uet.dse.neo4j.gui;

import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.main.Session;
import org.uet.dse.neo4j.collaboration.ConsensusService;
import org.uet.dse.neo4j.migration.ObjectMigrationManager;
import org.uet.dse.neo4j.sync.model.ModelSyncCoordinator;

import javax.swing.*;
import java.awt.*;

public class ProposalVotingDialog extends JDialog {

    private final String proposalId;
    private final Session useSession;
    private final MainWindow mainWindow;

    public ProposalVotingDialog(MainWindow parent, Session session, String proposalId, String proposer, String diffSummary) {
        super(parent, "Model Change Proposal", true);
        this.proposalId = proposalId;
        this.useSession = session;
        this.mainWindow = parent;

        setSize(600, 450);
        setLayout(new BorderLayout(15, 15));
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Nội dung đề xuất
        JTextArea txtDiff = new JTextArea(diffSummary);
        txtDiff.setFont(new Font("Monospaced", Font.PLAIN, 12));
        txtDiff.setEditable(false);

        JPanel pnlTop = new JPanel(new GridLayout(0, 1));
        pnlTop.add(new JLabel("<html><b>User " + proposer + "</b> requested a Model Synchronization.</html>"));
        pnlTop.add(new JLabel("Proposed Changes:"));
        add(pnlTop, BorderLayout.NORTH);
        add(new JScrollPane(txtDiff), BorderLayout.CENTER);

        // Nút bấm lựa chọn
        JPanel pnlBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnApprovePull = new JButton("Approve & Pull Now");
        JButton btnApproveLater = new JButton("Approve (Pull later)");
        JButton btnReject = new JButton("Reject");

        // --- LOGIC XỬ LÝ APPROVE & PULL ---
        btnApprovePull.addActionListener(e -> {
            // 1. Ghi nhận biểu quyết
            ConsensusService.getInstance().castVote(proposalId, ConsensusService.VoteType.APPROVE);

            // 2. THỰC HIỆN DI CƯ AN TOÀN
            ObjectMigrationManager migration = new ObjectMigrationManager();

            // A. Backup Object hiện có
            //migration.backupLocalState(useSession.system());

            // B. Pull Model mới (Sử dụng hàm không hiện popup confirm vì đã confirm ở đây rồi)
            new ModelSyncCoordinator(useSession, mainWindow).syncBackwardSilent();

            // C. Khôi phục Object
            migration.restoreState(useSession.system());

            JOptionPane.showMessageDialog(this, "Model updated. Objects have been migrated safely.");
            dispose();
        });

        // --- LOGIC APPROVE LATER ---
        btnApproveLater.addActionListener(e -> {
            ConsensusService.getInstance().castVote(proposalId, ConsensusService.VoteType.APPROVE);
            JOptionPane.showMessageDialog(this, "Vote casted. Please remember to PULL model later.");
            dispose();
        });

        // --- LOGIC REJECT ---
        btnReject.addActionListener(e -> {
            ConsensusService.getInstance().castVote(proposalId, ConsensusService.VoteType.REJECT);
            dispose();
        });

        pnlBtns.add(btnApprovePull);
        pnlBtns.add(btnApproveLater);
        pnlBtns.add(btnReject);
        add(pnlBtns, BorderLayout.SOUTH);

        setLocationRelativeTo(parent);
    }
}