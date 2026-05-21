package org.uet.dse.neo4jtgg.service.impl;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.uet.dse.neo4j.OCLLexer;
import org.uet.dse.neo4j.OCLParser;
import org.uet.dse.neo4j.oclite.ast.ASTFile;
import org.uet.dse.neo4j.oclite.ast.ASTNode;
import org.uet.dse.neo4j.oclite.ast.ASTVisitor;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclCompilationException;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclDiagnosticCode;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclDiagnosticPhase;

final class OclDocumentParser {
    private record UnsupportedRuleKindDetection(String ruleKind, Integer line, Integer column, String sourceSnippet) {
    }

    private record SyntaxIssue(Integer line, Integer column, String tokenText, String sourceSnippet) {
    }

    private static final class SyntaxIssueCollector extends BaseErrorListener {
        private SyntaxIssue firstIssue;
        private int errorCount;

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer,
                                Object offendingSymbol,
                                int line,
                                int charPositionInLine,
                                String msg,
                                RecognitionException ex) {
            errorCount++;
            if (firstIssue != null) {
                return;
            }
            Token token = offendingSymbol instanceof Token ? (Token) offendingSymbol : null;
            String tokenText = token != null ? token.getText() : null;
            firstIssue = new SyntaxIssue(line, charPositionInLine, tokenText, null);
        }

        int getErrorCount() {
            return errorCount;
        }

        SyntaxIssue getFirstIssue() {
            return firstIssue;
        }
    }

    private OclDocumentParser() {
    }

    static ASTFile parse(String oclText) {
        try {
            OCLLexer lexer = new OCLLexer(CharStreams.fromString(oclText));
            OCLParser parser = new OCLParser(new CommonTokenStream(lexer));
            SyntaxIssueCollector errorCollector = new SyntaxIssueCollector();
            lexer.removeErrorListeners();
            parser.removeErrorListeners();
            lexer.addErrorListener(errorCollector);
            parser.addErrorListener(errorCollector);
            ASTNode ast = new ASTVisitor().visit(parser.oclFile());
            if (parser.getNumberOfSyntaxErrors() > 0 || errorCollector.getErrorCount() > 0) {
                UnsupportedRuleKindDetection unsupportedRuleKind = detectUnsupportedRuleKind(oclText);
                if (unsupportedRuleKind != null) {
                    Integer line = unsupportedRuleKind.line();
                    Integer column = unsupportedRuleKind.column();
                    Integer endLine = line;
                    Integer endColumn = column != null ? column + unsupportedRuleKind.ruleKind().length() - 1 : null;
                    throw new OclCompilationException(OclDiagnosticPhase.PARSE, OclDiagnosticCode.UNSUPPORTED_RULE_KIND,
                            "Unsupported OCL rule kind `" + unsupportedRuleKind.ruleKind()
                                    + "`. Current parser/compiler path only supports `context <Class> inv ...` for now.",
                            line, column, endLine, endColumn, unsupportedRuleKind.ruleKind(), unsupportedRuleKind.sourceSnippet());
                }
                SyntaxIssue syntaxIssue = errorCollector.getFirstIssue();
                Token token = parser.getCurrentToken();
                Integer line = syntaxIssue != null ? syntaxIssue.line() : token != null ? token.getLine() : null;
                Integer column = syntaxIssue != null ? syntaxIssue.column() : token != null ? token.getCharPositionInLine() : null;
                Integer endLine = line;
                String tokenText = syntaxIssue != null && syntaxIssue.tokenText() != null
                        ? syntaxIssue.tokenText()
                        : token != null ? token.getText() : null;
                Integer endColumn = column != null
                        ? column + Math.max(tokenText != null ? tokenText.length() - 1 : 0, 0)
                        : null;
                String sourceSnippet = syntaxIssue != null && syntaxIssue.sourceSnippet() != null
                        ? syntaxIssue.sourceSnippet()
                        : extractSourceSnippet(oclText, line);
                throw new OclCompilationException(OclDiagnosticPhase.PARSE, OclDiagnosticCode.PARSE_ERROR,
                        "Failed to parse OCL input.", line, column, endLine, endColumn, tokenText, sourceSnippet);
            }
            if (ast instanceof ASTFile file) {
                return file;
            }
            ASTFile file = new ASTFile();
            file.addElement(ast);
            return file;
        } catch (OclCompilationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new OclCompilationException(OclDiagnosticPhase.PARSE,
                    ex.getMessage() != null ? ex.getMessage() : "Failed to parse OCL input.",
                    ex);
        }
    }

    private static UnsupportedRuleKindDetection detectUnsupportedRuleKind(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        String[] lines = source.split("\\R", -1);
        java.util.regex.Pattern operationRulePattern = java.util.regex.Pattern.compile("\\b(pre|post|body)\\b\\s*:?", java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Pattern attributeRulePattern = java.util.regex.Pattern.compile("\\b(init|derive)\\b\\s*:?", java.util.regex.Pattern.CASE_INSENSITIVE);
        boolean sawOperationContext = false;
        boolean sawAttributeLikeContext = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (trimmed.startsWith("context ") && trimmed.contains("::")) {
                sawOperationContext = true;
                sawAttributeLikeContext = true;
            }
            java.util.regex.Matcher operationMatcher = operationRulePattern.matcher(line);
            if (sawOperationContext && operationMatcher.find()) {
                return new UnsupportedRuleKindDetection(
                        operationMatcher.group(1).toLowerCase(),
                        i + 1,
                        operationMatcher.start(1),
                        trimmed.isBlank() ? null : trimmed);
            }
            java.util.regex.Matcher attributeMatcher = attributeRulePattern.matcher(line);
            if (sawAttributeLikeContext && attributeMatcher.find()) {
                return new UnsupportedRuleKindDetection(
                        attributeMatcher.group(1).toLowerCase(),
                        i + 1,
                        attributeMatcher.start(1),
                        trimmed.isBlank() ? null : trimmed);
            }
        }
        return null;
    }

    static String extractSourceSnippet(String source, Integer line) {
        if (source == null || line == null || line < 1) {
            return null;
        }
        String[] lines = source.split("\\R", -1);
        if (line > lines.length) {
            return null;
        }
        String snippet = lines[line - 1].trim();
        return snippet.isEmpty() ? null : snippet;
    }
}
