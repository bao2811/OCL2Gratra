package org.uet.dse.neo4jtgg.ocl.ir;

import org.junit.jupiter.api.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.uet.dse.neo4j.OCLLexer;
import org.uet.dse.neo4j.OCLParser;
import org.uet.dse.neo4j.oclite.ast.ASTFile;
import org.uet.dse.neo4j.oclite.ast.ASTContext;
import org.uet.dse.neo4j.oclite.ast.ASTNode;
import org.uet.dse.neo4j.oclite.ast.ASTVisitor;
import org.uet.dse.neo4jtgg.ocl.OclBottomToken;
import org.uet.dse.neo4jtgg.ocl.OclMetamodelIndex;
import org.uet.dse.neo4jtgg.ocl.OclSemanticBinder;
import org.uet.dse.neo4jtgg.service.impl.DefaultOclToCypherCompiler;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OclCypherRendererTest {
    @Test
    void rendersCanonicalBottomEqualityInsteadOfCypherNullEquality() {
        var bottom = new OclCypherPlan.LiteralPlan(
                null, org.uet.dse.neo4jtgg.ocl.OclTypeBinding.scalar("Void"));
        var value = new OclCypherPlan.LiteralPlan(
                "defined", org.uet.dse.neo4jtgg.ocl.OclTypeBinding.scalar("String"));
        var booleanType = org.uet.dse.neo4jtgg.ocl.OclTypeBinding.scalar("Boolean");

        var bottomEqualsBottom = new OclCypherRenderer().renderTopLevelExpression(
                new OclCypherPlan.BinaryPlan("=", bottom, bottom, booleanType));
        var valueNotEqualsBottom = new OclCypherRenderer().renderTopLevelExpression(
                new OclCypherPlan.BinaryPlan("<>", value, bottom, booleanType));

        assertTrue(bottomEqualsBottom.cypher().contains("null IS NULL OR coalesce(null = $"));
        assertTrue(bottomEqualsBottom.cypher().contains("THEN true"));
        assertTrue(valueNotEqualsBottom.cypher().contains("THEN false"));
        assertFalse(bottomEqualsBottom.cypher().contains("RETURN (null = null)"));
    }

    @Test
    void normalizesCollectionValuedBottomBeforeASetObservation() {
        var booleanType = org.uet.dse.neo4jtgg.ocl.OclTypeBinding.scalar("Boolean");
        var integerType = org.uet.dse.neo4jtgg.ocl.OclTypeBinding.scalar("Integer");
        var integerSetType = org.uet.dse.neo4jtgg.ocl.OclTypeBinding.scalarCollection("Integer");
        var voidType = org.uet.dse.neo4jtgg.ocl.OclTypeBinding.scalar("Void");
        var source = new OclCypherPlan.IfPlan(
                new OclCypherPlan.LiteralPlan(true, booleanType),
                new OclCypherPlan.LiteralPlan(null, voidType),
                new OclCypherPlan.SetLiteralPlan(
                        java.util.List.of(new OclCypherPlan.LiteralPlan(1L, integerType)),
                        integerSetType),
                integerSetType);
        var isEmpty = new OclCypherPlan.CollectionOperationPlan(
                source, integerSetType, "isEmpty", java.util.List.of(), booleanType);

        var rendered = new OclCypherRenderer().renderTopLevelExpression(isEmpty);

        String bottomParam = rendered.parameters().entrySet().stream()
                .filter(entry -> OclBottomToken.isToken(entry.getValue()))
                .map(java.util.Map.Entry::getKey)
                .findFirst()
                .orElseThrow();
        assertTrue(rendered.cypher().contains("size(head(COLLECT { WITH"), rendered.cypher());
        assertTrue(rendered.cypher().matches("(?s).* AS finiteSet[0-9]+ RETURN \\(CASE WHEN finiteSet[0-9]+ IS NULL.*"),
                rendered.cypher());
        assertTrue(rendered.cypher().contains(" = $" + bottomParam + ", false) THEN []"), rendered.cypher());
        assertTrue(rendered.cypher().endsWith("AS value })) = 0 AS value"), rendered.cypher());
    }

    @Test
    void normalizesNullAndTokenBottomOnEveryCollectionRhsPlan() {
        for (String operation : java.util.List.of(
                "includesAll", "excludesAll", "union", "intersection")) {
            String nullRhs = "(if self.age >= 0 then null else Set{1} endif)";
            OclCypherRenderer.RenderedInvariant nullRendered = renderDirectPersonInvariant(
                    invariantUsingCollectionRhs("NullRhs" + operation, operation, nullRhs));
            assertCollectionBottomBoundary(nullRendered);
            assertTrue(nullRendered.cypher().contains("THEN null ELSE"), nullRendered.cypher());

            // This direct bound/plan path deliberately bypasses OCL_val
            // admission: collection-valued collect and any are outside the
            // frozen theorem fragment. It verifies defensive renderer totality
            // for a token-shaped collection cell, not certified admission.
            String tokenRhs = "Person.allInstances()->collect(p | "
                    + "if p.age >= 0 then null else Set{1} endif)->any(xs | true)";
            OclCypherRenderer.RenderedInvariant tokenRendered = renderDirectPersonInvariant(
                    invariantUsingCollectionRhs("TokenRhs" + operation, operation, tokenRhs));
            String bottomParam = assertCollectionBottomBoundary(tokenRendered);
            long tokenizedFiniteSetItems = java.util.regex.Pattern.compile(
                            "\\+ coalesce\\(item[0-9]+, \\$" + bottomParam + "\\)")
                    .matcher(tokenRendered.cypher()).results().count();
            assertTrue(tokenizedFiniteSetItems >= 2, tokenRendered.cypher());
        }
    }

    @Test
    void certifiedCompilerAdmitsAndNormalizesANullCollectionRhs() {
        MModel model = personCollectionBottomModel();
        var compiled = new DefaultOclToCypherCompiler(model).compileInvariantInstrumented(
                "context Person inv CertifiedNullRhs: "
                        + "Set{1}->includesAll(if self.age >= 0 then null else Set{1} endif)");

        assertCollectionBottomBoundary(new OclCypherRenderer.RenderedInvariant(
                compiled.cypher(), compiled.parameters()));
    }

    @Test
    void certifiedScalarOperatorsRecognizeBottomElementsMaterializedAsTokens() {
        MModel model = personCollectionBottomModel();
        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);

        for (String expression : java.util.List.of(
                "Set{null,1}->exists(x | x = null)",
                "Set{null,1}->forAll(x | x > 0)",
                "Set{null,1}->exists(x | x + 1 = 2)")) {
            var compiled = compiler.compileInvariantInstrumented(
                    "context Person inv BottomScalarOperator: " + expression);
            String bottomParam = compiled.parameters().entrySet().stream()
                    .filter(entry -> OclBottomToken.isToken(entry.getValue()))
                    .map(java.util.Map.Entry::getKey)
                    .findFirst()
                    .orElseThrow();

            assertTrue(compiled.cypher().contains(" IS NULL OR coalesce("), compiled.cypher());
            assertTrue(compiled.cypher().contains(" = $" + bottomParam), compiled.cypher());
        }
    }

    @Test
    void certifiedVoidSetEqualityUsesTheExtensionalEmptySetBoundary() {
        MModel model = personCollectionBottomModel();
        var compiled = new DefaultOclToCypherCompiler(model).compileInvariantInstrumented(
                "context Person inv VoidEqualsEmptySet: "
                        + "null = Set{1}->select(x | false)");

        assertTrue(compiled.cypher().contains("all(setLeft"), compiled.cypher());
        assertTrue(compiled.cypher().contains("all(setRight"), compiled.cypher());
        assertCollectionBottomBoundary(new OclCypherRenderer.RenderedInvariant(
                compiled.cypher(), compiled.parameters()));
    }

    @Test
    void flattenTreatsACollectionBottomTokenAsAnEmptyInnerCollection() {
        OclCypherRenderer.RenderedInvariant rendered = renderDirectPersonInvariant(
                "context Person inv FlattenBottom: "
                        + "Person.allInstances()->collect(p | "
                        + "if p.age >= 0 then null else Set{1} endif)->flatten()->isEmpty()");

        String bottomParam = assertCollectionBottomBoundary(rendered);
        assertTrue(rendered.cypher().matches("(?s).*acc[0-9]+ \\+ \\(CASE WHEN item[0-9]+ IS NULL OR "
                + "coalesce\\(item[0-9]+ = \\$" + bottomParam
                + ", false\\) THEN \\[] ELSE item[0-9]+ END\\).*"), rendered.cypher());
    }

    @Test
    void normalizesACollectionBottomTokenBeforeFiniteSetEquality() {
        OclCypherRenderer.RenderedInvariant rendered = renderDirectPersonInvariant(
                "context Person inv BottomSetEquality: ("
                        + collectionBottomTokenPlanExpression()
                        + ") = Set{1}");

        String bottomParam = assertCollectionBottomBoundary(rendered);
        assertTrue(rendered.cypher().contains("all(setLeft"), rendered.cypher());
        assertTrue(rendered.cypher().contains("AS setComparisonLeft"), rendered.cypher());
        assertTrue(rendered.cypher().contains(" = $" + bottomParam
                + ", false) THEN [] ELSE finiteSet"), rendered.cypher());
    }

    @Test
    void normalizesACollectionBottomTokenBeforeDefinitionChecks() {
        for (String method : java.util.List.of("isDefined", "isUndefined")) {
            OclCypherRenderer.RenderedInvariant rendered = renderDirectPersonInvariant(
                    "context Person inv BottomCollection" + method + ": ("
                            + collectionBottomTokenPlanExpression() + ")." + method + "()");

            String bottomParam = assertCollectionBottomBoundary(rendered);
            assertTrue(rendered.cypher().contains("size(head(COLLECT { WITH head(["), rendered.cypher());
            assertTrue(rendered.cypher().contains(" = $" + bottomParam
                    + ", false) THEN [] ELSE finiteSet"), rendered.cypher());
        }
    }

    @Test
    void normalizesAStaticCollectionTokenBeforeEntityUnwindAndAttributeProjection() {
        String spec = """
                model RendererCollectionSources
                class Family end
                class Person
                attributes
                    age : Integer
                end
                association FamilyChildren between
                    Family[*] role family
                    Person[*] role children
                end
                """;
        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(
                spec, "renderer-collection-sources.use",
                new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());
        OclMetamodelIndex index = new OclMetamodelIndex(model);
        var familySet = org.uet.dse.neo4jtgg.ocl.OclTypeBinding.nodeCollection(
                "Family", org.uet.dse.neo4jtgg.ocl.OclTypeBinding.CollectionKind.SET);
        var personSet = org.uet.dse.neo4jtgg.ocl.OclTypeBinding.nodeCollection(
                "Person", org.uet.dse.neo4jtgg.ocl.OclTypeBinding.CollectionKind.SET);

        var navigation = new OclCypherPlan.NavigationAccessPlan(
                new OclCypherPlan.LiteralPlan(OclBottomToken.value(), familySet),
                index.resolveNavigation("Family", "children"), java.util.List.of(), personSet);
        var renderedNavigation = new OclCypherRenderer(model.name()).renderTopLevelExpression(navigation);
        assertTrue(renderedNavigation.cypher().matches(
                "(?s).*UNWIND \\(CASE WHEN \\$p[0-9]+ IS NULL OR coalesce\\(\\$p[0-9]+ = \\$p[0-9]+, false\\) "
                        + "THEN \\[] ELSE \\$p[0-9]+ END\\) AS sourceCandidate.*"),
                renderedNavigation.cypher());

        var age = model.getClass("Person").attribute("age", true);
        assertNotNull(age);
        var attribute = new OclCypherPlan.AttributeAccessPlan(
                new OclCypherPlan.LiteralPlan(OclBottomToken.value(), personSet),
                "age", age.type(), org.uet.dse.neo4jtgg.ocl.OclTypeBinding.scalar("Integer"), age);
        var renderedAttribute = new OclCypherRenderer(model.name()).renderTopLevelExpression(attribute);
        assertTrue(renderedAttribute.cypher().matches(
                "(?s).* IN \\(CASE WHEN \\$p[0-9]+ IS NULL OR coalesce\\(\\$p[0-9]+ = \\$p[0-9]+, false\\) "
                        + "THEN \\[] ELSE \\$p[0-9]+ END\\) \\| .*"),
                renderedAttribute.cypher());
    }

    @Test
    void validationSemanticsTreatsOnlyTrueAsSatisfied() {
        assertEquals("coalesce((p) = true, false)", OclValidationSemantics.validationTruth("p"));
        assertEquals("NOT coalesce((p) = true, false)", OclValidationSemantics.violationPredicate("p"));
        assertEquals("NOT coalesce((null) = true, false)", OclValidationSemantics.violationPredicate("null"));
        assertEquals("(NOT coalesce((p) = true, false))", OclValidationSemantics.not("p"));
        assertEquals("(coalesce((a) = true, false) AND coalesce((b) = true, false))",
                OclValidationSemantics.and("a", "b"));
        assertEquals("(coalesce((a) = true, false) OR coalesce((b) = true, false))",
                OclValidationSemantics.or("a", "b"));
        assertEquals("((NOT coalesce((a) = true, false)) OR coalesce((b) = true, false))",
                OclValidationSemantics.implies("a", "b"));
    }

    @Test
    void rendersUndefinedInvariantPredicateAsValidationViolation() {
        OclCypherPlan.InvariantPlan plan = new OclCypherPlan.InvariantPlan(
                "Person",
                "UndefinedIsViolation",
                new OclCypherPlan.LiteralPlan(null, org.uet.dse.neo4jtgg.ocl.OclTypeBinding.scalar("Boolean")));

        OclCypherRenderer.RenderedInvariant rendered = new OclCypherRenderer().renderInvariant(plan);

        assertTrue(rendered.cypher().contains("WHERE NOT coalesce((null) = true, false)"));
        assertTrue(rendered.cypher().contains("RETURN DISTINCT self.use_id AS useId"));
    }

    @Test
    void rendersNavigationExistsUsingBoundDirectionInsteadOfLooseUndirectedMatch() {
        String spec = """
                model Demo
                class Family
                end
                class Person
                attributes
                    age : Integer
                end
                association FamilyChildren between
                    Family[*] role family
                    Person[*] role children
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        ASTNode ast = new ASTVisitor().visit(new OCLParser(new org.antlr.v4.runtime.CommonTokenStream(
                new OCLLexer(org.antlr.v4.runtime.CharStreams.fromString(
                        "context Family inv HasAdultChild: self.children->exists(c | c.age >= 18)")))).oclFile());

        OclSemanticBinder binder = new OclSemanticBinder(new OclMetamodelIndex(model));
        OclSemanticBinder.BoundContextInvariant bound = binder.bindContext(firstContext(ast));
        OclIr.InvariantQuery invariantQuery = new OclIrOptimizer().optimizeInvariant(
                new OclIrBuilder().buildInvariant(bound));
        OclCypherPlan.InvariantPlan plan = new OclCypherPlanner().planInvariant(invariantQuery);

        OclCypherRenderer.RenderedInvariant rendered = new OclCypherRenderer().renderInvariant(plan);
        assertTrue(rendered.cypher().contains("EXISTS { UNWIND COLLECT {"));
        assertTrue(rendered.cypher().contains("MATCH (navOwner"));
        assertTrue(rendered.cypher().contains("-[r]->(c:Object)"));
        assertTrue(rendered.cypher().contains("AND coalesce("));
        assertFalse(rendered.cypher().contains("-[r]-(c:Object)"));
    }

    @Test
    void rendersIfExpressionAsCaseWhen() {
        String spec = """
                model Demo
                class Person
                attributes
                    age : Integer
                    name : String
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        ASTNode ast = new ASTVisitor().visit(new OCLParser(new org.antlr.v4.runtime.CommonTokenStream(
                new OCLLexer(org.antlr.v4.runtime.CharStreams.fromString(
                        "context Person inv AdultNamed: if self.age >= 18 then self.name else 'minor' endif <> ''")))).oclFile());

        OclSemanticBinder binder = new OclSemanticBinder(new OclMetamodelIndex(model));
        OclSemanticBinder.BoundContextInvariant bound = binder.bindContext(firstContext(ast));
        OclIr.InvariantQuery invariantQuery = new OclIrOptimizer().optimizeInvariant(
                new OclIrBuilder().buildInvariant(bound));
        OclCypherPlan.InvariantPlan plan = new OclCypherPlanner().planInvariant(invariantQuery);

        OclCypherRenderer.RenderedInvariant rendered = new OclCypherRenderer().renderInvariant(plan);
        assertTrue(rendered.cypher().contains("CASE WHEN coalesce("));
        assertTrue(rendered.cypher().contains("THEN"));
        assertTrue(rendered.cypher().contains("ELSE"));
    }

    @Test
    void rendersLetExpressionWithoutLeakingSourceVariableName() {
        String spec = """
                model Demo
                class Person
                attributes
                    age : Integer
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        ASTNode ast = new ASTVisitor().visit(new OCLParser(new org.antlr.v4.runtime.CommonTokenStream(
                new OCLLexer(org.antlr.v4.runtime.CharStreams.fromString(
                        "context Person inv AdultByLet: let measuredAge = self.age in measuredAge >= 18")))).oclFile());

        OclSemanticBinder binder = new OclSemanticBinder(new OclMetamodelIndex(model));
        OclSemanticBinder.BoundContextInvariant bound = binder.bindContext(firstContext(ast));
        OclIr.InvariantQuery invariantQuery = new OclIrOptimizer().optimizeInvariant(
                new OclIrBuilder().buildInvariant(bound));
        OclCypherPlan.InvariantPlan plan = new OclCypherPlanner().planInvariant(invariantQuery);

        OclCypherRenderer.RenderedInvariant rendered = new OclCypherRenderer().renderInvariant(plan);
        assertFalse(rendered.cypher().contains("measuredAge"));
        assertTrue(rendered.cypher().contains("head([_let"));
        assertFalse(rendered.cypher().contains("reduce(_let"));
        assertTrue(rendered.parameters().containsValue(18L));
    }

    @Test
    void rendersOptimizedIteratorChainWithScopedTargetAlias() {
        String spec = """
                model Demo
                class Family
                end
                class Person
                attributes
                    age : Integer
                    name : String
                end
                association FamilyChildren between
                    Family[*] role family
                    Person[*] role children
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        ASTNode ast = new ASTVisitor().visit(new OCLParser(new org.antlr.v4.runtime.CommonTokenStream(
                new OCLLexer(org.antlr.v4.runtime.CharStreams.fromString(
                        "context Family inv ScopedIteratorAlias: self.children->select(a | a.age >= 18)->exists(b | b.name = 'Lisa')")))).oclFile());

        OclSemanticBinder binder = new OclSemanticBinder(new OclMetamodelIndex(model));
        OclSemanticBinder.BoundContextInvariant bound = binder.bindContext(firstContext(ast));
        OclIr.InvariantQuery invariantQuery = new OclIrOptimizer().optimizeInvariant(new OclIrBuilder().buildInvariant(bound));
        OclCypherPlan.InvariantPlan plan = new OclCypherPlanner().planInvariant(invariantQuery);

        OclCypherRenderer.RenderedInvariant rendered = new OclCypherRenderer().renderInvariant(plan);
        assertTrue(rendered.cypher().contains("EXISTS { UNWIND COLLECT {"), rendered.cypher());
        assertTrue(rendered.cypher().contains("MATCH (navOwner"), rendered.cypher());
        assertTrue(rendered.cypher().contains("-[r]->(b:Object)"), rendered.cypher());
        assertTrue(rendered.cypher().contains("WITH b.objectKey AS attrOwner"), rendered.cypher());
        assertTrue(rendered.cypher().contains("(attrOwner"), rendered.cypher());
        assertFalse(rendered.cypher().contains("WITH a.objectKey AS attrOwner"), rendered.cypher());
    }

    @Test
    void rendersNestedNavigationAttributeAccessWithoutInliningNodeExpressionIntoPattern() {
        String spec = """
                model Demo
                class Family
                attributes
                    name : String
                end
                class Person
                attributes
                    name : String
                end
                association FamilyFather between
                    Family[*] role family
                    Person[0..1] role father
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        ASTNode ast = new ASTVisitor().visit(new OCLParser(new org.antlr.v4.runtime.CommonTokenStream(
                new OCLLexer(org.antlr.v4.runtime.CharStreams.fromString(
                        "context Family inv NamedFather: self.father.name <> ''")))).oclFile());

        OclSemanticBinder binder = new OclSemanticBinder(new OclMetamodelIndex(model));
        OclSemanticBinder.BoundContextInvariant bound = binder.bindContext(firstContext(ast));
        OclIr.InvariantQuery invariantQuery = new OclIrOptimizer().optimizeInvariant(
                new OclIrBuilder().buildInvariant(bound));
        OclCypherPlan.InvariantPlan plan = new OclCypherPlanner().planInvariant(invariantQuery);

        OclCypherRenderer.RenderedInvariant rendered = new OclCypherRenderer().renderInvariant(plan);
        assertFalse(rendered.cypher().contains("head([(head(["));
        assertTrue(rendered.cypher().contains(" AS rawAttr"));
        assertTrue(rendered.cypher().contains("RETURN CASE WHEN rawAttr"));
    }

    @Test
    void rendersNavigationSizeComparisonFromNestedCollectionAsListSize() {
        String spec = """
                model Demo
                class Family
                attributes
                    name : String
                end
                class Person
                end
                association FamilyChildren between
                    Family[*] role family
                    Person[*] role sons
                end
                """;

        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        ASTNode ast = new ASTVisitor().visit(new OCLParser(new org.antlr.v4.runtime.CommonTokenStream(
                new OCLLexer(org.antlr.v4.runtime.CharStreams.fromString(
                        "context Family inv HasTwoSons: self.name = 'Flanders' implies self.sons->size() = 2")))).oclFile());

        OclSemanticBinder binder = new OclSemanticBinder(new OclMetamodelIndex(model));
        OclSemanticBinder.BoundContextInvariant bound = binder.bindContext(firstContext(ast));
        OclIr.InvariantQuery invariantQuery = new OclIrOptimizer().optimizeInvariant(
                new OclIrBuilder().buildInvariant(bound));
        OclCypherPlan.InvariantPlan plan = new OclCypherPlanner().planInvariant(invariantQuery);

        OclCypherRenderer.RenderedInvariant rendered = new OclCypherRenderer().renderInvariant(plan);
        assertTrue(rendered.cypher().contains("size("));
        assertFalse(rendered.cypher().contains("head([(head(["));
    }

    @Test
    void allocatesRelationshipAliasFreshFromIteratorNamedR() {
        String cypher = renderOptimizedFamilyInvariant(
                "context Family inv AliasR: self.children->exists(r | r.age >= 18)");

        assertTrue(cypher.contains("->(r:Object"), cypher);
        assertFalse(cypher.contains("-[r]->(r:Object"), cypher);
        assertTrue(cypher.matches("(?s).*-\\[r_v[0-9]+\\]->\\(r:Object.*"), cypher);
    }

    @Test
    void rendersOptimizedNestedNavigationOwnerBeforeShadowingItsTargetName() {
        String spec = """
                model RecursiveRendererSafety
                class Node end
                association Tree between
                    Node[0..1] role parent
                    Node[*] role children
                end
                """;
        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(
                spec, "recursive-renderer-safety.use",
                new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());
        String invariant = "context Node inv NestedShadow: "
                + "self.children->exists(x | x.children->exists(x | true))";
        ASTNode ast = new ASTVisitor().visit(new OCLParser(new org.antlr.v4.runtime.CommonTokenStream(
                new OCLLexer(org.antlr.v4.runtime.CharStreams.fromString(invariant)))).oclFile());
        OclSemanticBinder.BoundContextInvariant bound =
                new OclSemanticBinder(new OclMetamodelIndex(model)).bindContext(firstContext(ast));
        OclIr.InvariantQuery optimized = new OclIrOptimizer().optimizeInvariant(
                new OclIrBuilder().buildInvariant(bound));

        String cypher = new OclCypherRenderer(model.name()).renderInvariant(
                new OclCypherPlanner().planInvariant(optimized)).cypher();

        assertTrue(cypher.contains("UNWIND [x] AS sourceCandidate"), cypher);
        assertFalse(cypher.matches("(?s).*UNWIND \\[x_v[0-9]+] AS sourceCandidate.*"), cypher);
    }

    @Test
    void filtersBottomEntityValuesBeforeNavigationMatch() {
        String cypher = renderOptimizedFamilyInvariant(
                "context Family inv BottomSafe: Set{self, null}->exists(x | x.children->notEmpty())");

        assertTrue(cypher.contains(".objectKey AS sourceKey"), cypher);
        assertTrue(cypher.contains("WHERE sourceKey"), cypher);
        assertTrue(cypher.contains("RETURN DISTINCT sourceNode"), cypher);
    }

    @Test
    void countsDistinctNavigationTargetsRatherThanRelationshipPaths() {
        String cypher = renderOptimizedFamilyInvariant(
                "context Family inv OneChild: self.children->size() = 1");

        assertTrue(cypher.contains("size(COLLECT {"), cypher);
        assertTrue(cypher.contains("RETURN DISTINCT"), cypher);
        assertFalse(cypher.contains("COUNT {"), cypher);
    }

    @Test
    void serializesRealQualifierWithCanonicalUnsignedZeroText() {
        String spec = """
                model RealQualifier
                class Library
                attributes
                    shelf : Real
                end
                class Book end
                association Catalog between
                    Library[1] role library qualifier (position : Real)
                    Book[*] role book
                end
                """;
        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(
                spec, "real-qualifier.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());
        ASTNode ast = new ASTVisitor().visit(new OCLParser(new org.antlr.v4.runtime.CommonTokenStream(
                new OCLLexer(org.antlr.v4.runtime.CharStreams.fromString(
                        "context Library inv CanonicalZero: self.book[self.shelf]->notEmpty()")))).oclFile());
        OclSemanticBinder.BoundContextInvariant bound =
                new OclSemanticBinder(new OclMetamodelIndex(model)).bindContext(firstContext(ast));
        OclIr.InvariantQuery optimized = new OclIrOptimizer().optimizeInvariant(
                new OclIrBuilder().buildInvariant(bound));

        String cypher = new OclCypherRenderer(model.name()).renderInvariant(
                new OclCypherPlanner().planInvariant(optimized)).cypher();

        assertTrue(cypher.contains("sourceQualifiers[0]"), cypher);
        assertTrue(cypher.contains("= 0.0 THEN '0.0' ELSE toString(toFloat("), cypher);
    }

    @Test
    void certifiedQualifierGuardRecognizesABottomSetElementToken() {
        String spec = """
                model BottomQualifier
                class Library end
                class Book end
                association Catalog between
                    Library[1] role library qualifier (position : Integer)
                    Book[*] role book
                end
                """;
        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(
                spec, "bottom-qualifier.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());
        var compiled = new DefaultOclToCypherCompiler(model).compileInvariantInstrumented(
                "context Library inv BottomQualifierElement: "
                        + "Set{null,1}->forAll(q | self.book[q]->isEmpty())");
        String bottomParam = compiled.parameters().entrySet().stream()
                .filter(entry -> OclBottomToken.isToken(entry.getValue()))
                .map(java.util.Map.Entry::getKey)
                .findFirst()
                .orElseThrow();

        assertTrue(compiled.cypher().contains("sourceQualifiers[0]"), compiled.cypher());
        assertTrue(compiled.cypher().contains("AND NOT (q IS NULL OR coalesce(q = $"
                + bottomParam), compiled.cypher());
        assertTrue(compiled.cypher().contains("THEN 'v1|V'"), compiled.cypher());
    }

    private String invariantUsingCollectionRhs(String name, String operation, String rhs) {
        String expression = switch (operation) {
            case "includesAll", "excludesAll" -> "Set{1}->" + operation + "(" + rhs + ")";
            case "union", "intersection" -> "Set{1}->" + operation + "(" + rhs + ")->size() >= 0";
            default -> throw new IllegalArgumentException("Unexpected collection operation: " + operation);
        };
        return "context Person inv " + name + ": " + expression;
    }

    private String collectionBottomTokenPlanExpression() {
        return "Person.allInstances()->collect(p | "
                + "if p.age >= 0 then null else Set{1} endif)->any(xs | true)";
    }

    private String assertCollectionBottomBoundary(OclCypherRenderer.RenderedInvariant rendered) {
        String bottomParam = rendered.parameters().entrySet().stream()
                .filter(entry -> OclBottomToken.isToken(entry.getValue()))
                .map(java.util.Map.Entry::getKey)
                .findFirst()
                .orElseThrow();
        String boundarySuffix = " = $" + bottomParam + ", false) THEN []";
        int boundaryCount = rendered.cypher().split(
                java.util.regex.Pattern.quote(boundarySuffix), -1).length - 1;
        assertTrue(boundaryCount >= 2,
                "Both receiver and RHS must cross a bottom-safe collection boundary: " + rendered.cypher());
        return bottomParam;
    }

    /** Builds the bound/optimized plan directly; this helper does not run OCL_val admission. */
    private OclCypherRenderer.RenderedInvariant renderDirectPersonInvariant(String invariant) {
        MModel model = personCollectionBottomModel();
        ASTNode ast = new ASTVisitor().visit(new OCLParser(new org.antlr.v4.runtime.CommonTokenStream(
                new OCLLexer(org.antlr.v4.runtime.CharStreams.fromString(invariant)))).oclFile());
        OclSemanticBinder.BoundContextInvariant bound =
                new OclSemanticBinder(new OclMetamodelIndex(model)).bindContext(firstContext(ast));
        OclIr.InvariantQuery optimized = new OclIrOptimizer().optimizeInvariant(
                new OclIrBuilder().buildInvariant(bound));
        return new OclCypherRenderer(model.name()).renderInvariant(
                new OclCypherPlanner().planInvariant(optimized));
    }

    private MModel personCollectionBottomModel() {
        String spec = """
                model RendererCollectionBottom
                class Person
                attributes
                    age : Integer
                end
                """;
        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(
                spec, "renderer-collection-bottom.use",
                new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());
        return model;
    }

    private String renderOptimizedFamilyInvariant(String invariant) {
        String spec = """
                model RendererSafety
                class Family end
                class Person
                attributes
                    age : Integer
                end
                association FamilyChildren between
                    Family[*] role family
                    Person[*] role children
                end
                """;
        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(
                spec, "renderer-safety.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());
        ASTNode ast = new ASTVisitor().visit(new OCLParser(new org.antlr.v4.runtime.CommonTokenStream(
                new OCLLexer(org.antlr.v4.runtime.CharStreams.fromString(invariant)))).oclFile());
        OclSemanticBinder.BoundContextInvariant bound =
                new OclSemanticBinder(new OclMetamodelIndex(model)).bindContext(firstContext(ast));
        OclIr.InvariantQuery optimized = new OclIrOptimizer().optimizeInvariant(
                new OclIrBuilder().buildInvariant(bound));
        return new OclCypherRenderer(model.name()).renderInvariant(
                new OclCypherPlanner().planInvariant(optimized)).cypher();
    }

    private ASTContext firstContext(ASTNode ast) {
        assertTrue(ast instanceof ASTFile);
        ASTFile file = (ASTFile) ast;
        assertEquals(1, file.invariants().size());
        return file.invariants().get(0);
    }
}
