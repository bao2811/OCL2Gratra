package org.uet.dse.neo4j.oclite;

import org.junit.jupiter.api.Test;
import org.uet.dse.neo4j.oclite.ast.ASTBinary;
import org.uet.dse.neo4j.oclite.ast.ASTCollectionLiteral;
import org.uet.dse.neo4j.oclite.ast.ASTContext;
import org.uet.dse.neo4j.oclite.ast.ASTExpression;
import org.uet.dse.neo4j.oclite.ast.ASTFile;
import org.uet.dse.neo4j.oclite.ast.ASTIterate;
import org.uet.dse.neo4j.oclite.ast.ASTStringLiteral;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OclAstParserConformanceTest {
    @Test
    void appliesOclLogicalAndArithmeticPrecedence() {
        ASTExpression expression = onlyExpression("1 + 2 * 3 = 7 or false and true");
        assertTrue(expression instanceof ASTBinary);
        ASTBinary or = (ASTBinary) expression;
        assertEquals("or", or.op);
        assertTrue(or.left instanceof ASTBinary);
        ASTBinary equality = (ASTBinary) or.left;
        assertEquals("=", equality.op);
        assertTrue(equality.left instanceof ASTBinary);
        ASTBinary plus = (ASTBinary) equality.left;
        assertEquals("+", plus.op);
        assertTrue(plus.right instanceof ASTBinary);
        assertEquals("*", ((ASTBinary) plus.right).op);
        assertTrue(or.right instanceof ASTBinary);
        assertEquals("and", ((ASTBinary) or.right).op);
    }

    @Test
    void preservesBroadCollectionLiteralAndSourceSpan() {
        ASTExpression expression = onlyExpression("Sequence{1, 2, 3}");
        assertTrue(expression instanceof ASTCollectionLiteral);
        ASTCollectionLiteral sequence = (ASTCollectionLiteral) expression;
        assertEquals("Sequence", sequence.kind);
        assertEquals(3, sequence.elements.size());
        assertTrue(sequence.sourceSpan().isKnown());
        assertEquals(0, sequence.sourceSpan().startOffset());
    }

    @Test
    void acceptsCommentsAndDecodesOclEscapedQuote() {
        ASTExpression expression = onlyExpression("-- comment\n'can''t'");
        assertTrue(expression instanceof ASTStringLiteral);
        assertEquals("can't", ((ASTStringLiteral) expression).value);
    }

    @Test
    void parsesQualifiedContextAndTypedOperationSignature() {
        ASTFile file = OclAstParser.parse(
                "context demo::Person::ageYears(x : Integer) : Integer pre valid: x > 0");
        assertEquals(1, file.operationConstraints().size());
        var operation = file.operationConstraints().get(0);
        assertEquals("demo::Person", operation.className);
        assertEquals("Integer", operation.parameterTypes.get(0));
        assertEquals("Integer", operation.returnTypeName);
    }

    @Test
    void reportsStableSyntaxCoordinates() {
        OclAstParser.SyntaxException error = assertThrows(OclAstParser.SyntaxException.class,
                () -> OclAstParser.parse("context Person inv broken: self.age >"));
        assertEquals(1, error.line());
        assertTrue(error.column() > 0);
    }

    @Test
    void parsesInvariantAsContextNode() {
        ASTFile file = OclAstParser.parse("context Person inv adult: self.age >= 18");
        ASTContext context = file.invariants().get(0);
        assertEquals("Person", context.className);
        assertEquals("adult", context.invName);
        assertTrue(context.sourceSpan().isKnown());
    }

    @Test
    void exposesVersionedFrontendAndDedicatedTypeEntryPoint() {
        assertEquals("OMG-OCL-2.4", OclFrontendMetadata.CURRENT.syntaxBaseline());
        assertEquals(2, OclFrontendMetadata.CURRENT.frontendVersion());
        assertEquals("Set(Person)", OclAstParser.parseType("Set(Person)").spelling);
    }

    @Test
    void preservesIterateAccumulatorInBroadSurfaceAst() {
        ASTExpression expression = onlyExpression(
                "Set{1, 2}->iterate(x; acc : Integer = 0 | acc + x)");
        assertTrue(expression instanceof ASTIterate);
        ASTIterate iterate = (ASTIterate) expression;
        assertEquals("acc", iterate.accumulator.name());
        assertEquals("Integer", iterate.accumulator.typeName());
    }

    private ASTExpression onlyExpression(String source) {
        ASTFile file = OclAstParser.parse(source);
        assertEquals(1, file.freeExpressions().size());
        return file.freeExpressions().get(0);
    }
}
