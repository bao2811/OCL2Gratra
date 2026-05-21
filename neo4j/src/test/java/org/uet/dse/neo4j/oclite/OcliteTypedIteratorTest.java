package org.uet.dse.neo4j.oclite;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.RecognitionException;
import org.junit.jupiter.api.Test;
import org.uet.dse.neo4j.OCLLexer;
import org.uet.dse.neo4j.OCLParser;
import org.uet.dse.neo4j.oclite.ast.ASTIterator;
import org.uet.dse.neo4j.oclite.ast.ASTIf;
import org.uet.dse.neo4j.oclite.ast.ASTLet;
import org.uet.dse.neo4j.oclite.ast.ASTMethodCall;
import org.uet.dse.neo4j.oclite.ast.ASTNode;
import org.uet.dse.neo4j.oclite.ast.ASTFile;
import org.uet.dse.neo4j.oclite.ast.ASTVisitor;
import org.uet.dse.neo4j.oclite.expr.ExecutionContext;
import org.uet.dse.neo4j.oclite.expr.ExpressionBinder;
import org.uet.dse.neo4j.oclite.expr.ExpressionNode;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OcliteTypedIteratorTest {
    @Test
    void parsesTypedExistsIterator() {
        ASTNode ast = parse("self->exists(e:Integer | e > 1)");
        assertTrue(ast instanceof ASTIterator);
        ASTIterator iterator = (ASTIterator) ast;
        assertEquals("exists", iterator.operation);
        assertEquals("e", iterator.iteratorName);
        assertEquals("Integer", iterator.iteratorTypeName);
    }

    @Test
    void evaluatesTypedExistsIteratorOnScalarCollection() {
        Object value = evaluate("self->exists(e:Integer | e > 1)", List.of(1L, 2L, 3L));
        assertEquals(Boolean.TRUE, value);
    }

    @Test
    void evaluatesTypedForAllIteratorOnScalarCollection() {
        Object value = evaluate("self->forAll(e:Integer | e >= 1)", List.of(1L, 2L, 3L));
        assertEquals(Boolean.TRUE, value);
    }

    @Test
    void evaluatesTypedSelectIteratorWithCollectionOperation() {
        Object value = evaluate("self->select(e:Integer | e > 1)->size()", List.of(1L, 2L, 3L));
        assertEquals(2.0d, value);
    }

    @Test
    void evaluatesTypedCollectIteratorWithAtOperation() {
        Object value = evaluate("self->collect(e:Integer | e + 1)->at(2)", List.of(1L, 2L, 3L));
        assertEquals(3.0d, value);
    }

    @Test
    void rejectIteratorParsesButIsStillUnsupportedInEvaluatorBinder() {
        ASTNode ast = parse("self->reject(e:Integer | e > 1)");
        assertTrue(ast instanceof ASTIterator);

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> new ExpressionBinder("test").bind(ast));
        assertTrue(error.getMessage().contains("reject"));
    }

    @Test
    void multiIteratorSyntaxIsStillRejectedByGrammar() {
        assertThrows(IllegalArgumentException.class,
                () -> parse("self->forAll(e1:Integer, e2:Integer | e1 <> e2)"));
    }

    @Test
    void parsesIfExpression() {
        ASTNode ast = parse("if self->exists(e:Integer | e > 1) then 'adult' else 'minor' endif");
        assertTrue(ast instanceof ASTIf);
    }

    @Test
    void parsesMethodCallOnIfWithoutParentheses() {
        ASTNode ast = parse("if self->exists(e:Integer | e > 1) then 'adult' else 'minor' endif.isDefined()");
        assertTrue(ast instanceof ASTMethodCall);
        ASTMethodCall methodCall = (ASTMethodCall) ast;
        assertEquals("isDefined", methodCall.methodName);
        assertTrue(methodCall.source instanceof ASTIf);
    }

    @Test
    void parsesLetExpression() {
        ASTNode ast = parse("let threshold = 1 in self->exists(e:Integer | e > threshold)");
        assertTrue(ast instanceof ASTLet);
    }

    @Test
    void parsesNestedLetExpression() {
        ASTNode ast = parse("let threshold = 1 in let bonus = threshold + 1 in self->exists(e:Integer | e > bonus)");
        assertTrue(ast instanceof ASTLet);
        ASTLet outer = (ASTLet) ast;
        assertTrue(outer.body instanceof ASTLet);
    }

    private static Object evaluate(String expression, Object selfValue) {
        ASTNode ast = parse(expression);
        ExpressionNode bound = new ExpressionBinder("test").bind(ast);
        ExecutionContext context = new ExecutionContext("test");
        context.setVariable("self", selfValue);
        return bound.evaluate(context);
    }

    private static ASTNode parse(String expression) {
        OCLLexer lexer = new OCLLexer(CharStreams.fromString(expression));
        OCLParser parser = new OCLParser(new CommonTokenStream(lexer));
        CollectingErrorListener errors = new CollectingErrorListener();
        lexer.removeErrorListeners();
        parser.removeErrorListeners();
        lexer.addErrorListener(errors);
        parser.addErrorListener(errors);

        OCLParser.OclFileContext tree = parser.oclFile();
        if (!errors.messages.isEmpty()) {
            throw new IllegalArgumentException(String.join(System.lineSeparator(), errors.messages));
        }
        ASTNode ast = new ASTVisitor().visit(tree);
        assertNotNull(ast);
        if (ast instanceof ASTFile file && file.freeExpressions().size() == 1) {
            return file.freeExpressions().get(0);
        }
        return ast;
    }

    private static final class CollectingErrorListener extends BaseErrorListener {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                                int charPositionInLine, String msg, RecognitionException e) {
            messages.add("line " + line + ":" + charPositionInLine + " " + msg);
        }
    }
}
