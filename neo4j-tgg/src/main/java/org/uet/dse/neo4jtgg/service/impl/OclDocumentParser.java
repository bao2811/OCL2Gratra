package org.uet.dse.neo4jtgg.service.impl;

import org.tzi.use.uml.mm.MModel;
import org.uet.dse.neo4j.oclite.OclAstParser;
import org.uet.dse.neo4j.oclite.ast.ASTFile;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclCompilationException;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclDiagnosticCode;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclDiagnosticPhase;

/** Production front-end bridge from OCL text to the unresolved plugin AST. */
final class OclDocumentParser {
    private OclDocumentParser() {
    }

    /**
     * Parses syntax only. Metamodel resolution intentionally remains in
     * OclSemanticBinder so parsing has one deterministic implementation.
     */
    static ASTFile parse(MModel model, String oclText) {
        return parse(oclText);
    }

    static ASTFile parse(String oclText) {
        try {
            return OclAstParser.parse(oclText);
        } catch (OclAstParser.SyntaxException ex) {
            Integer endColumn = ex.column() == null
                    ? null
                    : ex.column() + Math.max(ex.tokenText() == null ? 0 : ex.tokenText().length() - 1, 0);
            throw new OclCompilationException(
                    OclDiagnosticPhase.PARSE,
                    OclDiagnosticCode.PARSE_ERROR,
                    ex.getMessage(),
                    ex.line(),
                    ex.column(),
                    ex.line(),
                    endColumn,
                    ex.tokenText(),
                    ex.sourceSnippet());
        } catch (OclCompilationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new OclCompilationException(
                    OclDiagnosticPhase.PARSE,
                    ex.getMessage() == null ? "Failed to parse OCL input." : ex.getMessage(),
                    ex);
        }
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
