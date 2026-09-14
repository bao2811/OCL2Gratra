package org.uet.dse.ocl2cypher.cypher;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.uet.dse.ocl2cypher.cypher.CypherAst.*;
import org.uet.dse.ocl2cypher.diagnostics.SourceSpan;

import static org.junit.jupiter.api.Assertions.*;

/** Exhaustive executable-CypherAS serialization refinement for S-1. */
class S1SerializerClosureTest {

    @Test
    void everySealedClauseAndExpressionConstructorSerializesAndParsesAsCypher5() {
        GeneratedArtifact artifact = exhaustiveArtifact();
        CypherAstWellFormednessValidator.validate(artifact);

        Serializer.Serialized first = Serializer.serialize(artifact);
        Serializer.Serialized second = Serializer.serialize(artifact);
        assertEquals(first, second, "serialization must be deterministic");
        assertTrue(first.cypherText().startsWith("CYPHER 5\n"));
        assertTrue(first.cypherText().endsWith("\n"));
        assertFalse(first.cypherText().endsWith("\n\n"));
        assertFalse(first.cypherText().contains("\r"));

        Neo4jCypherParserGate.Parsed parsed = Neo4jCypherParserGate.parse(first.cypherText());
        assertEquals("CYPHER 5", parsed.dialectHeader());
        assertTrue(parsed.has("SingleQuery"));
        assertTrue(parsed.has("Match"));
        assertTrue(parsed.has("With"));
        assertTrue(parsed.has("Unwind"));
        assertTrue(parsed.has("Return"));
        assertFalse(parsed.hasUnboundedRelationship());
        assertFalse(parsed.hasDynamicLabelOrType());

        assertEquals(Set.of(MatchClause.class, WithClause.class,
                        UnwindClause.class, ReturnClause.class),
                Set.of(CypherClause.class.getPermittedSubclasses()));
        assertEquals(expectedExpressionClasses(),
                Set.of(CypherExpr.class.getPermittedSubclasses()));
    }

    @Test
    void operatorCataloguesAndEscapingAreAcceptedWithoutChangingTextBoundaries() {
        String text = Serializer.serialize(exhaustiveArtifact()).cypherText();
        assertTrue(text.contains(":`Odd``Label`"), text);
        assertTrue(text.contains("'quote\\' slash\\\\ newline\\n tab\\t'"), text);
        assertTrue(text.contains("1.0 AS `integralFloat`"), text);
        for (String token : List.of(" OR ", " XOR ", " AND ", " = ", " <> ",
                " < ", " <= ", " > ", " >= ", " IN ", " STARTS WITH ",
                " + ", " - ", " * ", " / ", " % ", " || ")) {
            assertTrue(text.contains(token), "missing binary token " + token + " in\n" + text);
        }
        assertTrue(text.contains("NOT "));
        assertTrue(text.contains(" IS NULL"));
        assertTrue(text.contains(" IS NOT NULL"));
        Neo4jCypherParserGate.assertParses(text);
    }

    @Test
    void malformedUnicodeIdentifierFailsClosed() {
        String unpaired = "bad" + '\uD800';
        CypherQuery query = new CypherQuery(List.of(new ReturnClause(false,
                List.of(new ProjectionItem(new IntegerLiteral(BigInteger.ONE), unpaired)))), true);
        GeneratedArtifact artifact = new GeneratedArtifact(Dialect.CYPHER_5, query,
                new ResultContract(ResultShape.SCALAR, unpaired, "Integer", false, null),
                List.of());
        assertThrows(IllegalArgumentException.class, () -> Serializer.serialize(artifact));
    }

    @Test
    void negativeLiteralAndUnaryNegationHaveDistinctCanonicalSyntax() {
        String literal = Serializer.CypherText.expr(
                new IntegerLiteral(BigInteger.valueOf(-1)));
        String negation = Serializer.CypherText.expr(
                new UnaryExpr(UnaryOp.NEGATE, new IntegerLiteral(BigInteger.ONE)));

        assertEquals("-1", literal);
        assertEquals("-(1)", negation);
        assertNotEquals(literal, negation,
                "S-1 requires the serializer image to distinguish literal and operator nodes");

        assertEquals("NOT (false)", Serializer.CypherText.expr(
                new UnaryExpr(UnaryOp.NOT, new BooleanLiteral(false))));
        assertEquals("(1) IS NULL", Serializer.CypherText.expr(
                new UnaryExpr(UnaryOp.IS_NULL, new IntegerLiteral(BigInteger.ONE))));
        assertEquals("(1) IS NOT NULL", Serializer.CypherText.expr(
                new UnaryExpr(UnaryOp.IS_NOT_NULL, new IntegerLiteral(BigInteger.ONE))));
    }

    private static GeneratedArtifact exhaustiveArtifact() {
        ParameterExpr parameter = new ParameterExpr("publicText");
        NodePattern left = new NodePattern("left", List.of("Odd`Label"),
                List.of(new PropertyMapEntry("key`part", parameter)));
        NodePattern right = new NodePattern("right", List.of("Object"), List.of());
        RelPattern relationship = new RelPattern("edge", "RELATED_TO",
                RelDirection.OUTGOING, List.of(new PropertyMapEntry("rank",
                        new IntegerLiteral(BigInteger.ONE))), 0, 2);
        MatchClause match = new MatchClause(new Pattern(List.of(new PathPattern(
                List.of(left, right), List.of(relationship)))), new BooleanLiteral(true));
        WithClause with = new WithClause(true, List.of(
                new ProjectionItem(new VariableExpr("left"), "left"),
                new ProjectionItem(new VariableExpr("right"), "right")),
                new BooleanLiteral(true));
        UnwindClause unwind = new UnwindClause(new ListExpr(List.of(
                new IntegerLiteral(BigInteger.ONE),
                new IntegerLiteral(BigInteger.TWO))), "item");

        List<CypherExpr> expressions = new ArrayList<>();
        expressions.add(new VariableExpr("item"));
        expressions.add(parameter);
        expressions.add(new NullLiteral());
        expressions.add(new BooleanLiteral(true));
        expressions.add(new IntegerLiteral(BigInteger.valueOf(-7)));
        expressions.add(new FloatLiteral(new BigDecimal("0.5")));
        expressions.add(new FloatLiteral(new BigDecimal("1")));
        expressions.add(new StringLiteral("quote' slash\\ newline\n tab\t"));
        expressions.add(new ListExpr(List.of(new IntegerLiteral(BigInteger.ONE))));
        MapExpr map = new MapExpr(List.of(new MapEntry("k", new IntegerLiteral(BigInteger.ONE))));
        expressions.add(map);
        expressions.add(new PropertyAccess(map, "k"));
        expressions.add(new UnaryExpr(UnaryOp.NOT, new BooleanLiteral(false)));
        expressions.add(new BinaryExpr(BinaryOp.ADD, new IntegerLiteral(BigInteger.ONE),
                new IntegerLiteral(BigInteger.TWO)));
        expressions.add(new FunctionCall("size", false,
                List.of(new ListExpr(List.of(new IntegerLiteral(BigInteger.ONE))))));
        expressions.add(new CaseExpr(List.of(new WhenThen(new BooleanLiteral(true),
                new IntegerLiteral(BigInteger.ONE))), new IntegerLiteral(BigInteger.ZERO)));
        expressions.add(new ListComprehension("mapped",
                new ListExpr(List.of(new IntegerLiteral(BigInteger.ONE))), null,
                new VariableExpr("mapped")));
        expressions.add(new QuantifiedPredicateExpression(QuantifierKind.ANY, "candidate",
                new ListExpr(List.of(new IntegerLiteral(BigInteger.ONE))),
                new BinaryExpr(BinaryOp.GREATER_THAN, new VariableExpr("candidate"),
                        new IntegerLiteral(BigInteger.ZERO))));
        expressions.add(new ReduceExpr("acc", new IntegerLiteral(BigInteger.ZERO), "term",
                new ListExpr(List.of(new IntegerLiteral(BigInteger.ONE))),
                new BinaryExpr(BinaryOp.ADD, new VariableExpr("acc"),
                        new VariableExpr("term"))));
        expressions.add(new ExistsSubquery(new CypherQuery(List.of(
                new MatchClause(new Pattern(List.of(new PathPattern(
                        List.of(new NodePattern("nested", List.of("Object"), List.of())),
                        List.of()))), null)), false)));
        expressions.add(new CollectSubquery(new CypherQuery(List.of(
                new ReturnClause(false, List.of(new ProjectionItem(
                        new IntegerLiteral(BigInteger.ONE), "nestedResult")))), true)));
        expressions.add(new LocatedExpr(new IntegerLiteral(BigInteger.ONE),
                new SourceSpan(1, 2, 1, 1)));

        for (UnaryOp operator : UnaryOp.values()) {
            CypherExpr operand = operator == UnaryOp.NOT
                    ? new BooleanLiteral(false) : new IntegerLiteral(BigInteger.ONE);
            expressions.add(new UnaryExpr(operator, operand));
        }
        for (BinaryOp operator : BinaryOp.values()) {
            expressions.add(binary(operator));
        }

        List<ProjectionItem> projections = new ArrayList<>();
        projections.add(new ProjectionItem(new VariableExpr("item"), "result"));
        for (int i = 0; i < expressions.size(); i++) {
            String alias = expressions.get(i) instanceof FloatLiteral f
                    && f.value().compareTo(BigDecimal.ONE) == 0
                    ? "integralFloat" : "case" + i;
            projections.add(new ProjectionItem(expressions.get(i), alias));
        }
        ReturnClause result = new ReturnClause(false, projections);
        CypherQuery query = new CypherQuery(List.of(match, with, unwind, result), true);
        return new GeneratedArtifact(Dialect.CYPHER_5, query,
                new ResultContract(ResultShape.SCALAR, "result", "Integer", false, null),
                List.of(
                        new QueryParameter("publicText", "String",
                                QueryParameter.Origin.PUBLIC, null),
                        new QueryParameter("__oclGenerated", "Physical:String",
                                QueryParameter.Origin.GENERATED, "fixed")));
    }

    private static CypherExpr binary(BinaryOp operator) {
        return switch (operator) {
            case OR, XOR, AND -> new BinaryExpr(operator,
                    new BooleanLiteral(true), new BooleanLiteral(false));
            case IN -> new BinaryExpr(operator, new IntegerLiteral(BigInteger.ONE),
                    new ListExpr(List.of(new IntegerLiteral(BigInteger.ONE))));
            case STARTS_WITH -> new BinaryExpr(operator,
                    new StringLiteral("prefix"), new StringLiteral("pre"));
            case LIST_CONCAT -> new BinaryExpr(operator,
                    new ListExpr(List.of(new IntegerLiteral(BigInteger.ONE))),
                    new ListExpr(List.of(new IntegerLiteral(BigInteger.TWO))));
            default -> new BinaryExpr(operator, new IntegerLiteral(BigInteger.ONE),
                    new IntegerLiteral(BigInteger.TWO));
        };
    }

    private static Set<Class<?>> expectedExpressionClasses() {
        return new LinkedHashSet<>(Arrays.asList(
                VariableExpr.class, ParameterExpr.class, NullLiteral.class,
                BooleanLiteral.class, IntegerLiteral.class, FloatLiteral.class,
                StringLiteral.class, ListExpr.class, MapExpr.class,
                PropertyAccess.class, UnaryExpr.class, BinaryExpr.class,
                FunctionCall.class, CaseExpr.class, ListComprehension.class,
                QuantifiedPredicateExpression.class, ReduceExpr.class,
                ExistsSubquery.class, CollectSubquery.class, LocatedExpr.class));
    }
}
