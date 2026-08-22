package org.uet.dse.neo4jtgg.ocl.ir;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RawCypherAstTotalityTest {
    private final RawCypherRenderer renderer = new RawCypherRenderer();
    private final RawCypherAst.AliasId x = new RawCypherAst.AliasId("x");
    private final RawCypherAst.AliasId y = new RawCypherAst.AliasId("y");
    private final RawCypherAst.Expr alias = new RawCypherAst.Alias(x);
    private final RawCypherAst.Expr truth = new RawCypherAst.BoolLiteral(true);
    private final RawCypherAst.Query returnX = new RawCypherAst.Seq(List.of(
            new RawCypherAst.Return(false, List.of(new RawCypherAst.Projection(alias, x)))));

    @Test
    void rendererCoversEverySealedRawAstConstructor() {
        List<RawCypherAst.Expr> expressions = List.of(
                alias,
                new RawCypherAst.Param(new RawCypherAst.ParamId("p1")),
                new RawCypherAst.NullLiteral(),
                truth,
                new RawCypherAst.IntLiteral(1),
                new RawCypherAst.Property(alias, RawCypherAst.PropertyKey.OBJECT_KEY),
                new RawCypherAst.ListExpr(List.of(alias, truth)),
                new RawCypherAst.Unary(RawCypherAst.UnaryOp.NOT, truth),
                new RawCypherAst.Binary(RawCypherAst.BinaryOp.EQ, alias, alias),
                new RawCypherAst.CaseExpr(List.of(new RawCypherAst.WhenThen(truth, alias)), new RawCypherAst.NullLiteral()),
                new RawCypherAst.Function(RawCypherAst.FunctionId.COALESCE, List.of(alias, truth)),
                new RawCypherAst.ListComp(x, new RawCypherAst.ListExpr(List.of(alias)), truth, alias),
                new RawCypherAst.ExistsExpr(returnX),
                new RawCypherAst.CountExpr(returnX));
        expressions.forEach(expression -> assertFalse(renderer.renderExpr(expression).isBlank()));
        assertEquals(permitted(RawCypherAst.Expr.class), classes(expressions));

        RawCypherAst.NodePattern left = node(x);
        RawCypherAst.NodePattern right = node(y);
        List<RawCypherAst.Pattern> patterns = List.of(left,
                new RawCypherAst.RelPattern(left, new RawCypherAst.AliasId("r"),
                        RawCypherAst.Direction.OUTGOING, RawCypherAst.RelType.OBJECT_INSTANCE_OF, right));
        patterns.forEach(pattern -> assertFalse(renderer.render(new RawCypherAst.Seq(List.of(
                new RawCypherAst.Match(false, List.of(pattern))))).isBlank()));
        assertEquals(permitted(RawCypherAst.Pattern.class), classes(patterns));

        List<RawCypherAst.Clause> clauses = List.of(
                new RawCypherAst.Match(false, patterns),
                new RawCypherAst.Where(truth),
                new RawCypherAst.Unwind(new RawCypherAst.ListExpr(List.of(alias)), y),
                new RawCypherAst.With(true, List.of(new RawCypherAst.Projection(alias, x))),
                new RawCypherAst.Return(true, List.of(new RawCypherAst.Projection(alias, x))),
                new RawCypherAst.Call(List.of(x), returnX));
        clauses.forEach(clause -> assertFalse(renderer.render(new RawCypherAst.Seq(List.of(clause))).isBlank()));
        assertEquals(permitted(RawCypherAst.Clause.class), classes(clauses));

        String productionText = "MATCH (self:Object)-[:ObjectInstanceOf]->(cls:UmlClass {classKey: $p1})\n"
                + "WHERE EXISTS { MATCH (self)-[r]->(target:Object) RETURN target AS target }\n"
                + "RETURN DISTINCT self.use_id AS useId";
        RawCypherAst.ProductionQuery productionQuery = new RawCypherParser().parse(productionText);
        assertEquals(productionText, renderer.render(productionQuery));
        List<RawCypherAst.Query> queries = List.of(returnX,
                new RawCypherAst.UnionAll(returnX, returnX), productionQuery);
        queries.forEach(query -> assertFalse(renderer.render(query).isBlank()));
        assertEquals(permitted(RawCypherAst.Query.class), classes(queries));
    }

    @Test
    void typedAtomsRejectInjectionAndModelValuesHaveNoTextLiteralConstructor() {
        assertThrows(IllegalArgumentException.class, () -> new RawCypherAst.AliasId("x) MATCH (n"));
        assertThrows(IllegalArgumentException.class, () -> new RawCypherAst.ParamId("p-1"));
        assertTrue(Arrays.stream(RawCypherAst.Expr.class.getPermittedSubclasses())
                .noneMatch(type -> type.getSimpleName().equals("StringLiteral")));
        assertEquals("$modelValue", renderer.renderExpr(
                new RawCypherAst.Param(new RawCypherAst.ParamId("modelValue"))));

        RawCypherParser productionParser = new RawCypherParser();
        assertThrows(IllegalArgumentException.class,
                () -> productionParser.parse("MATCH (n) // injected\nRETURN n AS value"));
        assertThrows(IllegalArgumentException.class,
                () -> productionParser.parse("RETURN <predicate> AS value"));
        assertThrows(IllegalArgumentException.class,
                () -> productionParser.parse("RETURN 1 AS value UNION RETURN 2 AS value"));
    }

    @Test
    void rawAstUsesTheCanonicalUmlClassLabel() {
        assertEquals("UmlClass", RawCypherAst.Label.UML_CLASS.text());
        assertTrue(Arrays.stream(RawCypherAst.Label.values())
                .noneMatch(label -> label.text().equals("Class")));
    }

    @Test
    void freshNamesAreDeterministicAndCannotCaptureReservedAliases() {
        RawCypherAst.FreshNames first = new RawCypherAst.FreshNames(Set.of(x));
        RawCypherAst.FreshNames second = new RawCypherAst.FreshNames(Set.of(x));
        List<RawCypherAst.AliasId> firstNames = List.of(first.fresh("x"), first.fresh("x"), first.fresh("item"));
        List<RawCypherAst.AliasId> secondNames = List.of(second.fresh("x"), second.fresh("x"), second.fresh("item"));
        assertEquals(firstNames, secondNames);
        assertEquals(List.of("x1", "x2", "item"), firstNames.stream().map(RawCypherAst.AliasId::value).toList());
    }

    @Test
    void callInjectsExplicitUniqueImportsAndUnionArmsRemainClosed() {
        RawCypherAst.Call call = new RawCypherAst.Call(List.of(x, y),
                new RawCypherAst.UnionAll(returnX, returnX));
        String rendered = renderer.render(new RawCypherAst.Seq(List.of(call)));
        assertTrue(rendered.startsWith("CALL {\nWITH x, y\n"), rendered);
        assertTrue(rendered.contains("\nUNION ALL\n"), rendered);
        assertEquals(2, rendered.split("WITH x, y", -1).length - 1, rendered);
        assertThrows(IllegalArgumentException.class, () -> new RawCypherAst.Call(List.of(x, x), returnX));
    }

    @Test
    void astCollectionsAreDefensivelyCopiedAndIncompleteNodesAreRejected() {
        java.util.ArrayList<RawCypherAst.Expr> mutable = new java.util.ArrayList<>(List.of(alias));
        RawCypherAst.ListExpr list = new RawCypherAst.ListExpr(mutable);
        mutable.add(truth);
        assertEquals(1, list.elements().size());
        assertThrows(UnsupportedOperationException.class, () -> list.elements().add(truth));
        assertThrows(IllegalArgumentException.class, () -> new RawCypherAst.Seq(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new RawCypherAst.Match(false, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new RawCypherAst.CaseExpr(List.of(), new RawCypherAst.NullLiteral()));
    }

    private RawCypherAst.NodePattern node(RawCypherAst.AliasId id) {
        return new RawCypherAst.NodePattern(id, RawCypherAst.Label.OBJECT,
                List.of(new RawCypherAst.PropertyEntry(RawCypherAst.PropertyKey.OBJECT_KEY,
                        new RawCypherAst.Param(new RawCypherAst.ParamId("objectKey")))));
    }

    private Set<Class<?>> permitted(Class<?> type) {
        return new LinkedHashSet<>(Arrays.asList(type.getPermittedSubclasses()));
    }

    private Set<Class<?>> classes(List<?> values) {
        return values.stream().map(Object::getClass).collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
