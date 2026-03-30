package org.uet.dse.neo4j.ocl.phase5;

import org.antlr.runtime.ANTLRInputStream;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.RecognitionException;
import org.tzi.use.parser.Context;
import org.tzi.use.parser.ParseErrorHandler;
import org.tzi.use.parser.SemanticException;
import org.tzi.use.parser.Symtable;
import org.tzi.use.parser.ocl.ASTExpression;
import org.tzi.use.parser.ocl.OCLLexer;
import org.tzi.use.parser.ocl.OCLParser;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uml.ocl.value.VarBindings;
import org.tzi.use.uml.sys.MSystemState;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;

public class FakeOCLCompiler {

  private static Expression compileExpression(MModel model,
      MSystemState state,
      InputStream in,
      String inName,
      PrintWriter err,
      VarBindings globalBindings,
      Symtable varTable) {
    Expression expr = null;
    ParseErrorHandler errHandler = new ParseErrorHandler(inName, err);

    ANTLRInputStream aInput;
    try {
      aInput = new ANTLRInputStream(in);
      aInput.name = inName;
    } catch (IOException e1) {
      err.println(e1.getMessage());
      return expr;
    }

    OCLLexer lexer = new OCLLexer(aInput);

    CommonTokenStream tokenStream = new CommonTokenStream(lexer);
    OCLParser parser = new OCLParser(tokenStream);

    lexer.init(errHandler);
    parser.init(errHandler);



    try {
      // Parse the input expression
      ASTExpression astExpr = parser.expressionOnly();

      if (errHandler.errorCount() == 0 ) {

//        // Generate code
//        expr = astExpr.gen();
//
//        // check for semantic errors
//        if (ctx.errorCount() > 0 )
//          expr = null;
      }
    } catch (RecognitionException e) {
      err.println(parser.getSourceName() +":" +
          e.line + ":" +
          e.charPositionInLine + ": " +
          e.getMessage());
    } catch (NullPointerException e) {
      // Only throw if not handled before
      if (errHandler.errorCount() == 0)
        throw e;
    }

    err.flush();
    return expr;
  }
}
