package org.uet.dse.neo4j.oclite;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.uet.dse.neo4j.OCLLexer;
import org.uet.dse.neo4j.OCLParser;
import org.uet.dse.neo4j.oclite.ast.ASTFile;
import org.uet.dse.neo4j.oclite.ast.ASTContext;
import org.uet.dse.neo4j.oclite.ast.ASTExpression;
import org.uet.dse.neo4j.oclite.ast.ASTNode;
import org.uet.dse.neo4j.oclite.ast.ASTTypeReference;
import org.uet.dse.neo4j.oclite.ast.ASTVisitor;
import org.uet.dse.neo4j.oclite.ast.SourceSpan;

import java.util.List;

/** The single syntax-to-surface-AST entry point used by the Neo4j plugin. */
public final class OclAstParser {
    private OclAstParser() {
    }

    public static ASTFile parse(String source) {
        String input = source == null ? "" : source;
        OCLLexer lexer = new OCLLexer(CharStreams.fromString(input));
        OCLParser parser = new OCLParser(new CommonTokenStream(lexer));
        FirstSyntaxError errors = new FirstSyntaxError(input);
        lexer.removeErrorListeners();
        parser.removeErrorListeners();
        lexer.addErrorListener(errors);
        parser.addErrorListener(errors);

        OCLParser.OclFileContext tree = parser.oclFile();
        if (errors.count > 0 || parser.getNumberOfSyntaxErrors() > 0) {
            throw errors.asException();
        }
        ASTNode ast = new ASTVisitor().visit(tree);
        if (!(ast instanceof ASTFile file)) {
            throw new IllegalStateException("OCL document parser did not produce ASTFile.");
        }
        return file;
    }

    public static ASTFile parseDocument(String source) {
        return parse(source);
    }

    public static List<ASTNode> parseConstraintDocument(String source) {
        ASTFile file = parse(source);
        if (file.elements().isEmpty() || !file.freeExpressions().isEmpty()) {
            throw new IllegalArgumentException("Constraint document requires one or more OCL constraints.");
        }
        return file.elements();
    }

    public static ASTContext parseInvariant(String source) {
        ASTFile file = parse(source);
        if (file.elements().size() != 1 || file.invariants().size() != 1) {
            throw new IllegalArgumentException("Expected exactly one context invariant.");
        }
        return file.invariants().get(0);
    }

    public static ASTExpression parseExpression(String source) {
        ASTFile file = parse(source);
        if (file.elements().size() != 1 || file.freeExpressions().size() != 1) {
            throw new IllegalArgumentException("Expected exactly one free OCL expression.");
        }
        return file.freeExpressions().get(0);
    }

    public static ASTTypeReference parseType(String source) {
        String input = source == null ? "" : source;
        OCLLexer lexer = new OCLLexer(CharStreams.fromString(input));
        OCLParser parser = new OCLParser(new CommonTokenStream(lexer));
        FirstSyntaxError errors = new FirstSyntaxError(input);
        lexer.removeErrorListeners();
        parser.removeErrorListeners();
        lexer.addErrorListener(errors);
        parser.addErrorListener(errors);
        OCLParser.TypeOnlyContext tree = parser.typeOnly();
        if (errors.count > 0 || parser.getNumberOfSyntaxErrors() > 0) {
            throw errors.asException();
        }
        ASTTypeReference type = new ASTTypeReference(tree.typeRef().getText());
        Token start = tree.typeRef().getStart();
        Token stop = tree.typeRef().getStop();
        type.setSourceSpan(new SourceSpan(
                start.getStartIndex(), stop.getStopIndex(),
                start.getLine(), start.getCharPositionInLine(),
                stop.getLine(), stop.getCharPositionInLine() + stop.getText().length() - 1));
        return type;
    }

    public static final class SyntaxException extends IllegalArgumentException {
        private final Integer line;
        private final Integer column;
        private final String tokenText;
        private final String sourceSnippet;

        private SyntaxException(String message, Integer line, Integer column,
                                String tokenText, String sourceSnippet) {
            super(message);
            this.line = line;
            this.column = column;
            this.tokenText = tokenText;
            this.sourceSnippet = sourceSnippet;
        }

        public Integer line() {
            return line;
        }

        public Integer column() {
            return column;
        }

        public String tokenText() {
            return tokenText;
        }

        public String sourceSnippet() {
            return sourceSnippet;
        }
    }

    private static final class FirstSyntaxError extends BaseErrorListener {
        private final String source;
        private int count;
        private Integer line;
        private Integer column;
        private String tokenText;
        private String message;

        private FirstSyntaxError(String source) {
            this.source = source;
        }

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                int line, int charPositionInLine, String msg,
                                RecognitionException exception) {
            count++;
            if (this.line != null) {
                return;
            }
            this.line = line;
            this.column = charPositionInLine;
            this.tokenText = offendingSymbol instanceof Token token ? token.getText() : null;
            this.message = msg;
        }

        private SyntaxException asException() {
            Integer errorLine = line == null ? 1 : line;
            Integer errorColumn = column == null ? 0 : column;
            return new SyntaxException(
                    "Failed to parse OCL input at " + errorLine + ":" + errorColumn
                            + (message == null ? "." : ": " + message),
                    errorLine,
                    errorColumn,
                    tokenText,
                    sourceLine(source, errorLine));
        }

        private static String sourceLine(String source, int line) {
            String[] lines = source.split("\\R", -1);
            if (line < 1 || line > lines.length) {
                return null;
            }
            String result = lines[line - 1].trim();
            return result.isEmpty() ? null : result;
        }
    }
}
