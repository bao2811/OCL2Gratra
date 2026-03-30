package org.uet.dse.neo4j.incrementalUpdate;

import org.antlr.runtime.ANTLRInputStream;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.RecognitionException;
import org.tzi.use.parser.Context;
import org.tzi.use.parser.ParseErrorHandler;
import org.tzi.use.parser.use.ASTModel;
import org.tzi.use.parser.use.USELexer;
import org.tzi.use.parser.use.USEParser;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.uet.dse.neo4j.sync.model.sukunaDomainExpansion.FukumaMizushi;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;

public class FakeUSECompiler {
  public static MModel compileSpecification(String in,
      String inName,
      PrintWriter err,
      ModelFactory factory,
      FukumaMizushi allClassInNeo4j) {

    InputStream inStream = new ByteArrayInputStream(in.getBytes());
    return FakeUSECompiler.compileSpecification(inStream, inName, err, factory, allClassInNeo4j);
  }

  public static MModel compileSpecification(InputStream in,
      String inName,
      PrintWriter err,
      ModelFactory factory,
      FukumaMizushi allClassInNeo4j) {
    MModel model = null;
    ParseErrorHandler errHandler = new ParseErrorHandler(inName, err);

    ANTLRInputStream aInput;
    try {
      aInput = new ANTLRInputStream(in);
      aInput.name = inName;
    } catch (IOException e1) {
      err.println(e1.getMessage());
      return model;
    }

    USELexer lexer = new USELexer(aInput);
    CommonTokenStream tokenStream = new CommonTokenStream(lexer);
    USEParser parser = new USEParser(tokenStream);

    lexer.init(errHandler);
    parser.init(errHandler);

    try {
      // Parse the specification
      ASTModel astModel = parser.model();
      FakeASTModel fakeASTModel = new FakeASTModel(astModel);
      if (errHandler.errorCount() == 0 ) {

        // Generate code
        Context ctx = new Context(inName, err, null, factory);
        model = fakeASTModel.gen(ctx, allClassInNeo4j);
//        if (ctx.errorCount() > 0 )
//          model = null;
      }
    } catch (RecognitionException e) {
      err.println(parser.getSourceName() +":" +
          e.line + ":" +
          e.charPositionInLine + ": " +
          e.getMessage());
    }

    err.flush();
    return model;
  }
}
