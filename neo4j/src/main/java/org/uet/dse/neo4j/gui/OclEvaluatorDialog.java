package org.uet.dse.neo4j.gui;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.neo4j.driver.types.Node;
import org.tzi.use.gui.util.TextComponentWriter;
import org.tzi.use.main.Session;
import org.tzi.use.parser.ocl.OCLCompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.MSystemState;
import org.tzi.use.util.TeeWriter;
import org.uet.dse.neo4j.OCLLexer;
import org.uet.dse.neo4j.OCLParser;
import org.uet.dse.neo4j.ocl.IOCLEvaluator;
import org.uet.dse.neo4j.ocl.NEvaluator;
import org.uet.dse.neo4j.ocl.mapper.NExpressionRewriter;
import org.uet.dse.neo4j.ocl.expr.NExpression;
import org.uet.dse.neo4j.oclite.Neo4jRepository;
import org.uet.dse.neo4j.oclite.ast.ASTContext;
import org.uet.dse.neo4j.oclite.ast.ASTNode;
import org.uet.dse.neo4j.oclite.ast.ASTVisitor;
import org.uet.dse.neo4j.oclite.expr.ExecutionContext;
import org.uet.dse.neo4j.oclite.expr.ExpressionBinder;
import org.uet.dse.neo4j.oclite.expr.ExpressionNode;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OclEvaluatorDialog extends JDialog {
  private final JTextArea txtExpression;
  private final JTextArea txtResult;
  private final Session useSession;
  private final IOCLEvaluator oclEvaluator;
  private final MSystemState currentState;

  public OclEvaluatorDialog(Frame parent, Session session, IOCLEvaluator oclEvaluator) {
    super(parent, "Evaluate OCL expression", false);
    this.useSession = session;
    this.oclEvaluator = oclEvaluator;
    this.currentState = session.system().state();

    setSize(600, 300);
    setLayout(new BorderLayout(10, 10));
    ((JComponent) getContentPane()).setBorder(new EmptyBorder(10, 10, 10, 10));

    JPanel pnlLeft = new JPanel(new GridLayout(2, 1, 5, 10));

    JPanel pnlInput = new JPanel(new BorderLayout(5, 5));
    pnlInput.add(new JLabel("Enter OCL expression:"), BorderLayout.NORTH);
    txtExpression = new JTextArea();
    txtExpression.setLineWrap(true);
    pnlInput.add(new JScrollPane(txtExpression), BorderLayout.CENTER);

    JPanel pnlOutput = new JPanel(new BorderLayout(5, 5));
    pnlOutput.add(new JLabel("Result:"), BorderLayout.NORTH);
    txtResult = new JTextArea();
    txtResult.setEditable(false);
    txtResult.setBackground(new Color(245, 245, 245));
    pnlOutput.add(new JScrollPane(txtResult), BorderLayout.CENTER);

    pnlLeft.add(pnlInput);
    pnlLeft.add(pnlOutput);

    JPanel pnlRight = new JPanel();
    pnlRight.setLayout(new BoxLayout(pnlRight, BoxLayout.Y_AXIS));

    JButton btnEvaluate = createStyledButton("Evaluate", new Color(0, 120, 215));
    JButton btnBrowser = createStyledButton("Browser", Color.WHITE);
    JButton btnClear = createStyledButton("Clear", Color.WHITE);
    JButton btnClose = createStyledButton("Close", Color.WHITE);

    pnlRight.add(btnEvaluate);
    pnlRight.add(Box.createVerticalStrut(10));
    pnlRight.add(btnBrowser);
    pnlRight.add(Box.createVerticalStrut(10));
    pnlRight.add(btnClear);
    pnlRight.add(Box.createVerticalStrut(10));
    pnlRight.add(btnClose);

    btnEvaluate.addActionListener(e -> doEvaluate());
    btnClear.addActionListener(e -> {
      txtExpression.setText("");
      txtResult.setText("");
    });
    btnClose.addActionListener(e -> dispose());
    btnBrowser.addActionListener(e -> {
      JOptionPane.showMessageDialog(this, "Endpoint Browser feature is under development.");
    });

    add(pnlLeft, BorderLayout.CENTER);
    add(pnlRight, BorderLayout.EAST);

    setLocationRelativeTo(parent);
  }

  private void doEvaluate() {
    String expression = txtExpression.getText().trim();
    if (expression.isEmpty())
      return;

    MModel model = useSession.system().model();

    StringWriter msgWriter = new StringWriter();
    PrintWriter out = new PrintWriter(new TeeWriter(new TextComponentWriter(txtResult), msgWriter), true);

    //      //core version
    //      Expression expr = OCLCompiler.compileExpression(
    //          model,
    //          useSession.system().state(),
    //          expression,
    //          "Neo4jPlugin",
    //          out,
    //          useSession.system().varBindings()
    //      );
    //
    //      if (expr == null) {
    //        System.out.println("Error parsing ocl to expr");
    //      }
    //
    //      // Rewrite tree -> neo4j version
    //      NExpression neo4jExpr = NExpressionRewriter.rewrite(expr);
    //      Value result = null;
    //        NEvaluator evaluator = new NEvaluator();
    //        // eval như bình thường - nhưng chạy trên neo4j
    //        result = evaluator.eval(currentState, neo4jExpr);

    CharStream stream = CharStreams.fromString(expression);
    OCLLexer lexer = new OCLLexer(stream);
    CommonTokenStream tokens = new CommonTokenStream(lexer);
    OCLParser parser = new OCLParser(tokens);
    //OCLParser.ExpressionContext parseTree = parser.expression();
    OCLParser.OclFileContext parseTree = parser.oclFile();

    ASTVisitor astBuilder = new ASTVisitor();
    ASTNode astTree = astBuilder.visit(parseTree);

    String modelName = this.useSession.system().model().name();
    if (modelName == null) {
      throw new RuntimeException("Dont know which model is being worked on");
    }

    ExpressionBinder binder = new ExpressionBinder(modelName);
    //      ExpressionNode execTree = binder.bind(astTree);
    //
    //
    //      ExecutionContext context = new ExecutionContext(modelName);

    Object result;
    boolean isValidation = astTree instanceof ASTContext;
    if (astTree instanceof ASTContext) {

      result = handleValidation((ASTContext) astTree, modelName, binder);
    } else {

      result = handleNormalEvaluation(astTree, modelName, binder);
    }
    //Object result = execTree.evaluate(context);

    System.out.println("--- 3. Kết quả thực thi OCL ---");
    System.out.println("Result: " + result);

    if (result != null) {
      String resStr = result.toString();
      txtResult.setText(resStr);

      if (isValidation && resStr.startsWith("FAILURE")) {
        txtResult.setForeground(Color.RED);
      } else {
        txtResult.setForeground(new Color(0, 100, 0));
      }
    } else {
      txtResult.setText("Could not evaluate");
      txtResult.setForeground(Color.RED);
    }
//    if (result != null) {
//      txtResult.setText(result.toString());
//      txtResult.setForeground(new Color(0, 100, 0));
//    } else {
//      txtResult.setText("Could not evaluate");
//      txtResult.setForeground(Color.RED);
//    }
  }

  private Object handleNormalEvaluation(ASTNode astTree, String modelName, ExpressionBinder binder) {
    ExpressionNode execTree = binder.bind(astTree);
    ExecutionContext context = new ExecutionContext(modelName);
    return execTree.evaluate(context);

  }

  private Object handleValidation(ASTContext astContext, String modelName, ExpressionBinder binder) {
    ExpressionNode constraintExecTree = binder.bind(astContext.expression);
    Neo4jRepository repo = new Neo4jRepository(modelName);

    List<Node> instances = repo.findAllInstancesOfClass(astContext.className);

    int total = instances.size();
    int failed = 0;
    StringBuilder report = new StringBuilder();
    report.append("--- Invariant: ").append(astContext.invName).append(" ---\n");

    if (total == 0) {
      return "No instances of class '" + astContext.className + "' found to validate.";
    }

    for (Node node : instances) {
      ExecutionContext ctx = new ExecutionContext(modelName);
      ctx.pushScope("self", node);
      ctx.setVariable("self", node);

      Object isOk = constraintExecTree.evaluate(ctx);

      if (isOk instanceof Boolean && !((Boolean) isOk)) {
        failed++;
        String objId = node.get("use_id").asString();
        report.append("  [!] Violation: ").append(objId).append("\n");
      }
    }

    if (failed == 0) {
      return "SUCCESS: All " + total + " instances of '" + astContext.className + "' satisfy the invariant.";
    } else {
      return "FAILURE: " + failed + "/" + total + " violations found.\n" + report.toString();
    }
  }

    private JButton createStyledButton(String text, Color bgColor) {
        JButton btn = new JButton(text);
        btn.setMaximumSize(new Dimension(120, 35));
        btn.setPreferredSize(new Dimension(120, 35));
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        if (bgColor != Color.WHITE) {
            btn.setBackground(bgColor);
            btn.setForeground(Color.WHITE);
            btn.setOpaque(true);
            btn.setBorderPainted(false);
        }
        return btn;
    }
}