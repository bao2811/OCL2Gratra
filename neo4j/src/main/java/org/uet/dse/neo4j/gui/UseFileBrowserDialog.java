package org.uet.dse.neo4j.gui;

import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.main.Session;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.uet.dse.neo4j.sync.model.ModelSyncCoordinator;
import org.uet.dse.neo4j.sync.model.sukunaDomainExpansion.FukumaMizushi;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;

public class UseFileBrowserDialog extends JDialog {
    private final JTextField txtFilePath;
    private final JTextArea txtContent;
    private File selectedFile;
    private final PrintWriter logWriter;
    private final Session session;
    private final MainWindow mainWindow;
    public UseFileBrowserDialog(MainWindow parent, PrintWriter logWriter, Session session) {
        super(parent, "USE File Browser & Compiler", true);
      this.session = session;
      this.logWriter = logWriter;
      this.mainWindow = parent;

        setSize(800, 600);
        setLayout(new BorderLayout(10, 10));
        ((JComponent) getContentPane()).setBorder(new EmptyBorder(10, 10, 10, 10));

        // --- Top Panel: File Selection ---
        JPanel pnlTop = new JPanel(new BorderLayout(5, 5));
        txtFilePath = new JTextField();
        txtFilePath.setEditable(false);
        txtFilePath.setBackground(Color.WHITE);
        
        JButton btnBrowse = new JButton("Browse...");
        btnBrowse.addActionListener(e -> doBrowse());

        pnlTop.add(new JLabel("Select .use file:"), BorderLayout.WEST);
        pnlTop.add(txtFilePath, BorderLayout.CENTER);
        pnlTop.add(btnBrowse, BorderLayout.EAST);

        // --- Center Panel: Content Display ---
        txtContent = new JTextArea();
        txtContent.setFont(new Font("Monospaced", Font.PLAIN, 13));
        txtContent.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(txtContent);
        scrollPane.setBorder(BorderFactory.createTitledBorder("File Content Output"));

        // --- Bottom Panel: Actions ---
        JPanel pnlBottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnCompile = new JButton("Compile");
        JButton btnClose = new JButton("Close");

        btnCompile.setBackground(new Color(0, 153, 76));
        btnCompile.setForeground(Color.WHITE);
        btnCompile.setOpaque(true);
        btnCompile.setBorderPainted(false);

        btnCompile.addActionListener(e -> doCompile());
        btnClose.addActionListener(e -> dispose());

        pnlBottom.add(btnCompile);
        pnlBottom.add(btnClose);

        add(pnlTop, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(pnlBottom, BorderLayout.SOUTH);

        setLocationRelativeTo(parent);
    }

    private void doBrowse() {
        JFileChooser fileChooser = new JFileChooser();
        // Chỉ lọc các file có đuôi .use
        FileNameExtensionFilter filter = new FileNameExtensionFilter("USE Specification Files (*.use)", "use");
        fileChooser.setFileFilter(filter);

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            selectedFile = fileChooser.getSelectedFile();
            txtFilePath.setText(selectedFile.getAbsolutePath());
        }
    }

    private void doCompile() {
        if (selectedFile == null) {
            JOptionPane.showMessageDialog(this, "Please select a file first!");
            return;
        }

        try {
            String content = Files.readString(selectedFile.toPath());


            MModel incrementalModel = null;
            MModel model = session.system().model();
          FukumaMizushi fukumaMizushi = new FukumaMizushi();

          try (org.neo4j.driver.Session session1 =
              Neo4jDriverManager.getInstance().openSession()) {

            String query = """
        MATCH (n)-[:InstanceOf]->(c:MetaNode)
        WHERE c.name IN [
          'NodeConcreteClass',
          'NodeAbstractClass',
          'NodeEnumeration',
          'NodeAssociationClass'
        ]
        RETURN n.name AS name, c.name AS type
        """;

            fukumaMizushi.allEnumNames = new ArrayList<>();
            fukumaMizushi.allConcreteClassName = new ArrayList<>();
            fukumaMizushi.allAbstractClassName = new ArrayList<>();
            fukumaMizushi.allAssociationClassName = new ArrayList<>();

            session1.readTransaction(tx -> {
              var result = tx.run(query);

              while (result.hasNext()) {
                var record = result.next();
                String name = record.get("name").asString();
                String type = record.get("type").asString();

                switch (type) {
                  case "NodeConcreteClass":
                    fukumaMizushi.allConcreteClassName.add(name);
                    break;
                  case "NodeAbstractClass":
                    fukumaMizushi.allAbstractClassName.add(name);
                    break;

                  case "NodeEnumeration":
                    fukumaMizushi.allEnumNames.add(name);
                    break;

                  case "NodeAssociationClass":
                    fukumaMizushi.allAssociationClassName.add(name);
                    break;
                }
              }
              return null;
            });

          } catch (Exception e) {
            System.out.println("oh shit");
            e.printStackTrace();
          }

            try {
                incrementalModel = USECompiler.compileSpecification(content, model.name(), logWriter, new ModelFactory());

              ModelSyncCoordinator modelSyncCoordinator = new ModelSyncCoordinator(session, mainWindow);
              modelSyncCoordinator.syncForwardIncrementalCase(incrementalModel);

            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Failed");
                return;
            }
            JOptionPane.showMessageDialog(this, "File loaded and 'compiled' successfully as String.");
            
        } catch (Exception e) {
            txtContent.setText("Error reading file: " + e.getMessage());
            txtContent.setForeground(Color.RED);
        }
    }
}
