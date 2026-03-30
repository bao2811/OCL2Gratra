package org.tzi.use;

import org.tzi.use.parser.ocl.OCLCompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uml.sys.MSystem;

import java.io.PrintWriter;
import java.io.StringWriter;

public class TestParserMain {
    // Source - https://stackoverflow.com/a
// Posted by jf_, modified by community. See post 'Timeline' for change history
// Retrieved 2026-01-25, License - CC BY-SA 4.0

    public static void main(String args[]) {

        ModelFactory mFactory = new ModelFactory();
        MModel mModel = mFactory.createModel("unnamed");
        MSystem system = new MSystem(mModel);

        String input = "Set{1,2,3} ->collect(i|i*2)";

        PrintWriter errorPrinter = new PrintWriter(new StringWriter(), true);

        Expression expr = OCLCompiler.compileExpression(mModel, input,
                "USE Api", errorPrinter, system.varBindings());

        System.out.println(expr);
    }

}
