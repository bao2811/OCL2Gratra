package org.uet.dse.neo4jtgg.ocl;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
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

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OclSemanticBinderTest {
    @Test
    void infersAllInstancesAsSet() {
        OclSemanticBinder.BoundContextInvariant bound = bind("""
                model Demo
                class Person
                end
                """, "context Person inv AllPeopleVisible: Person.allInstances()->size() >= 0");

        OclSemanticBinder.BoundBinary comparison = as(bound.expression(), OclSemanticBinder.BoundBinary.class);
        OclSemanticBinder.BoundMethodCall allInstances = as(
                as(comparison.left(), OclSemanticBinder.BoundCollectionOperation.class).source(),
                OclSemanticBinder.BoundMethodCall.class);
        assertEquals(OclTypeBinding.CollectionKind.SET, allInstances.type().collectionKind());
    }

    @Test
    void infersSplitAsSequenceAndAsSetAsSet() {
        OclSemanticBinder.BoundContextInvariant bound = bind("""
                model Demo
                class Family
                attributes
                    name : String
                end
                """, "context Family inv UniqueNames: self.name.split(',')->asSet()->size() >= 1");

        OclSemanticBinder.BoundBinary comparison = as(bound.expression(), OclSemanticBinder.BoundBinary.class);
        OclSemanticBinder.BoundCollectionOperation size = as(comparison.left(), OclSemanticBinder.BoundCollectionOperation.class);
        OclSemanticBinder.BoundCollectionOperation asSet = as(size.source(), OclSemanticBinder.BoundCollectionOperation.class);
        OclSemanticBinder.BoundMethodCall split = as(asSet.source(), OclSemanticBinder.BoundMethodCall.class);

        assertEquals(OclTypeBinding.CollectionKind.SEQUENCE, split.type().collectionKind());
        assertEquals(OclTypeBinding.CollectionKind.SET, asSet.type().collectionKind());
    }

    @Test
    void infersAsOrderedSetAsOrderedSet() {
        OclSemanticBinder.BoundContextInvariant bound = bind("""
                model Demo
                class Family
                attributes
                    name : String
                end
                """, "context Family inv OrderedUniqueNames: self.name.split(',')->asOrderedSet()->first().isDefined()");

        OclSemanticBinder.BoundMethodCall isDefined = as(bound.expression(), OclSemanticBinder.BoundMethodCall.class);
        OclSemanticBinder.BoundCollectionOperation first = as(isDefined.source(), OclSemanticBinder.BoundCollectionOperation.class);
        OclSemanticBinder.BoundCollectionOperation asOrderedSet = as(first.source(), OclSemanticBinder.BoundCollectionOperation.class);

        assertEquals(OclTypeBinding.CollectionKind.ORDERED_SET, asOrderedSet.type().collectionKind());
        assertEquals("String", first.type().typeName());
    }

    @Test
    void infersIfExpressionBranchType() {
        OclSemanticBinder.BoundContextInvariant bound = bind("""
                model Demo
                class Person
                attributes
                    age : Integer
                    name : String
                end
                """, "context Person inv AdultLabelDefined: if self.age >= 18 then self.name else 'minor' endif <> ''");

        OclSemanticBinder.BoundBinary comparison = as(bound.expression(), OclSemanticBinder.BoundBinary.class);
        OclSemanticBinder.BoundIf ifExpression = as(comparison.left(), OclSemanticBinder.BoundIf.class);
        assertEquals("Boolean", ifExpression.condition().type().typeName());
        assertEquals("String", ifExpression.type().typeName());
    }

    @Test
    void infersIfExpressionTypeFromNonVoidElseBranch() {
        OclSemanticBinder.BoundContextInvariant bound = bind("""
                model Demo
                class Person
                attributes
                    age : Integer
                    name : String
                end
                """, "context Person inv NullableAdultLabel: (if self.age >= 18 then null else self.name endif).isUndefined()");

        OclSemanticBinder.BoundMethodCall isUndefined = as(bound.expression(), OclSemanticBinder.BoundMethodCall.class);
        OclSemanticBinder.BoundIf ifExpression = as(isUndefined.source(), OclSemanticBinder.BoundIf.class);
        assertEquals("String", ifExpression.type().typeName());
        assertEquals("Void", ifExpression.thenBranch().type().typeName());
        assertEquals("String", ifExpression.elseBranch().type().typeName());
    }

    @Test
    void infersIfExpressionTypeFromNonVoidThenBranch() {
        OclSemanticBinder.BoundContextInvariant bound = bind("""
                model Demo
                class Person
                attributes
                    age : Integer
                    name : String
                end
                """, "context Person inv NullableMinorLabel: (if self.age >= 18 then self.name else null endif).isDefined()");

        OclSemanticBinder.BoundMethodCall isDefined = as(bound.expression(), OclSemanticBinder.BoundMethodCall.class);
        OclSemanticBinder.BoundIf ifExpression = as(isDefined.source(), OclSemanticBinder.BoundIf.class);
        assertEquals("String", ifExpression.type().typeName());
        assertEquals("String", ifExpression.thenBranch().type().typeName());
        assertEquals("Void", ifExpression.elseBranch().type().typeName());
    }

    @Test
    void bindsLetExpressionIntoScopedVariable() {
        OclSemanticBinder.BoundContextInvariant bound = bind("""
                model Demo
                class Person
                attributes
                    age : Integer
                end
                """, "context Person inv AdultByLet: let threshold = 18 in self.age >= threshold");

        OclSemanticBinder.BoundLet letExpression = as(bound.expression(), OclSemanticBinder.BoundLet.class);
        assertEquals("Integer", letExpression.value().type().typeName());
        OclSemanticBinder.BoundBinary comparison = as(letExpression.body(), OclSemanticBinder.BoundBinary.class);
        OclSemanticBinder.BoundVariable threshold = as(comparison.right(), OclSemanticBinder.BoundVariable.class);
        assertEquals("threshold", threshold.ast().name);
        assertEquals("Boolean", letExpression.type().typeName());
    }

    @Test
    void bindsNestedLetWithShadowing() {
        OclSemanticBinder.BoundContextInvariant bound = bind("""
                model Demo
                class Person
                attributes
                    age : Integer
                end
                """, "context Person inv NestedLet: let threshold = 18 in let threshold = threshold + 1 in self.age >= threshold");

        OclSemanticBinder.BoundLet outerLet = as(bound.expression(), OclSemanticBinder.BoundLet.class);
        OclSemanticBinder.BoundLet innerLet = as(outerLet.body(), OclSemanticBinder.BoundLet.class);
        OclSemanticBinder.BoundBinary innerValue = as(innerLet.value(), OclSemanticBinder.BoundBinary.class);
        OclSemanticBinder.BoundVariable outerThresholdRef = as(innerValue.left(), OclSemanticBinder.BoundVariable.class);
        OclSemanticBinder.BoundBinary comparison = as(innerLet.body(), OclSemanticBinder.BoundBinary.class);
        OclSemanticBinder.BoundVariable innerThresholdRef = as(comparison.right(), OclSemanticBinder.BoundVariable.class);

        assertEquals("threshold", outerThresholdRef.ast().name);
        assertEquals("threshold", innerThresholdRef.ast().name);
        assertEquals("Boolean", innerLet.type().typeName());
    }

    @Test
    void infersCollectFromSetSourceAsBag() {
        OclSemanticBinder.BoundContextInvariant bound = bind("""
                model Demo
                class Family
                end
                class Person
                attributes
                    name : String
                end
                association FamilyChildren between
                    Family[*] role family
                    Person[*] role children
                end
                """, "context Family inv HasBart: self.children->collect(c | c.name)->includes('Bart')");

        OclSemanticBinder.BoundCollectionOperation includes = as(bound.expression(), OclSemanticBinder.BoundCollectionOperation.class);
        OclSemanticBinder.BoundIterator collect = as(includes.source(), OclSemanticBinder.BoundIterator.class);

        assertEquals(OclTypeBinding.CollectionKind.SET, collect.source().type().collectionKind());
        assertEquals(OclTypeBinding.CollectionKind.BAG, collect.type().collectionKind());
    }

    @Test
    void infersCollectFromOrderedSourceAsSequence() {
        OclSemanticBinder.BoundContextInvariant bound = bind("""
                model Demo
                class Family
                attributes
                    name : String
                end
                """, "context Family inv FirstTokenDefined: self.name.split(',')->collect(token | token)->first().isDefined()");

        OclSemanticBinder.BoundMethodCall isDefined = as(bound.expression(), OclSemanticBinder.BoundMethodCall.class);
        OclSemanticBinder.BoundCollectionOperation first = as(isDefined.source(), OclSemanticBinder.BoundCollectionOperation.class);
        OclSemanticBinder.BoundIterator collect = as(first.source(), OclSemanticBinder.BoundIterator.class);

        assertEquals(OclTypeBinding.CollectionKind.SEQUENCE, collect.source().type().collectionKind());
        assertEquals(OclTypeBinding.CollectionKind.SEQUENCE, collect.type().collectionKind());
    }

    @Test
    void infersUnionResultKinds() {
        OclSemanticBinder.BoundContextInvariant setUnionSet = bind("""
                model Demo
                class Family
                end
                class Person
                attributes
                    name : String
                end
                association FamilyChildren between
                    Family[*] role family
                    Person[*] role children
                end
                """, "context Family inv UnionChildren: self.children->union(self.children)->notEmpty()");
        OclSemanticBinder.BoundContextInvariant setUnionBag = bind("""
                model Demo
                class Family
                end
                class Person
                attributes
                    name : String
                end
                association FamilyChildren between
                    Family[*] role family
                    Person[*] role children
                end
                """, "context Family inv UnionNames: self.children->union(self.children->collect(c | c))->notEmpty()");
        OclSemanticBinder.BoundContextInvariant seqUnionSeq = bind("""
                model Demo
                class Family
                attributes
                    name : String
                end
                """, "context Family inv UnionSplit: self.name.split(',')->union(self.name.split(';'))->notEmpty()");

        assertEquals(OclTypeBinding.CollectionKind.SET,
                as(as(setUnionSet.expression(), OclSemanticBinder.BoundCollectionOperation.class).source(),
                        OclSemanticBinder.BoundCollectionOperation.class).type().collectionKind());
        assertEquals(OclTypeBinding.CollectionKind.BAG,
                as(as(setUnionBag.expression(), OclSemanticBinder.BoundCollectionOperation.class).source(),
                        OclSemanticBinder.BoundCollectionOperation.class).type().collectionKind());
        assertEquals(OclTypeBinding.CollectionKind.SEQUENCE,
                as(as(seqUnionSeq.expression(), OclSemanticBinder.BoundCollectionOperation.class).source(),
                        OclSemanticBinder.BoundCollectionOperation.class).type().collectionKind());
    }

    @Test
    void infersIntersectionResultKinds() {
        OclSemanticBinder.BoundContextInvariant setIntersectionBag = bind("""
                model Demo
                class Family
                end
                class Person
                attributes
                    name : String
                end
                association FamilyChildren between
                    Family[*] role family
                    Person[*] role children
                end
                """, "context Family inv SharedChildren: self.children->intersection(self.children->collect(c | c))->notEmpty()");
        OclSemanticBinder.BoundContextInvariant bagIntersectionBag = bind("""
                model Demo
                class Family
                end
                class Person
                attributes
                    name : String
                end
                association FamilyChildren between
                    Family[*] role family
                    Person[*] role children
                end
                """, "context Family inv SharedNames: self.children->collect(c | c.name)->intersection(self.children->collect(c | c.name))->notEmpty()");

        assertEquals(OclTypeBinding.CollectionKind.SET,
                as(as(setIntersectionBag.expression(), OclSemanticBinder.BoundCollectionOperation.class).source(),
                        OclSemanticBinder.BoundCollectionOperation.class).type().collectionKind());
        assertEquals(OclTypeBinding.CollectionKind.BAG,
                as(as(bagIntersectionBag.expression(), OclSemanticBinder.BoundCollectionOperation.class).source(),
                        OclSemanticBinder.BoundCollectionOperation.class).type().collectionKind());
    }

    @Test
    void infersOrderedSetUnionAndIntersectionKinds() {
        OclSemanticBinder.BoundContextInvariant orderedUnion = bind("""
                model Demo
                class Invoice
                end
                class LineItem
                attributes
                    price : Integer
                end
                association InvoiceLineItems between
                    Invoice[1] role invoice
                    LineItem[*] role lineItem ordered
                end
                """, "context Invoice inv OrderedUnion: self.lineItem->union(self.lineItem)->first().price.isDefined()");
        OclSemanticBinder.BoundContextInvariant orderedIntersection = bind("""
                model Demo
                class Invoice
                end
                class LineItem
                attributes
                    price : Integer
                end
                association InvoiceLineItems between
                    Invoice[1] role invoice
                    LineItem[*] role lineItem ordered
                end
                """, "context Invoice inv OrderedIntersection: self.lineItem->intersection(self.lineItem)->last().price.isDefined()");

        OclSemanticBinder.BoundMethodCall unionDefined = as(orderedUnion.expression(), OclSemanticBinder.BoundMethodCall.class);
        OclSemanticBinder.BoundProperty unionPrice = as(unionDefined.source(), OclSemanticBinder.BoundProperty.class);
        OclSemanticBinder.BoundCollectionOperation unionFirst = as(unionPrice.source(), OclSemanticBinder.BoundCollectionOperation.class);
        OclSemanticBinder.BoundCollectionOperation union = as(unionFirst.source(), OclSemanticBinder.BoundCollectionOperation.class);

        OclSemanticBinder.BoundMethodCall intersectionDefined = as(orderedIntersection.expression(), OclSemanticBinder.BoundMethodCall.class);
        OclSemanticBinder.BoundProperty intersectionPrice = as(intersectionDefined.source(), OclSemanticBinder.BoundProperty.class);
        OclSemanticBinder.BoundCollectionOperation intersectionLast = as(intersectionPrice.source(), OclSemanticBinder.BoundCollectionOperation.class);
        OclSemanticBinder.BoundCollectionOperation intersection = as(intersectionLast.source(), OclSemanticBinder.BoundCollectionOperation.class);

        assertEquals(OclTypeBinding.CollectionKind.ORDERED_SET, union.type().collectionKind());
        assertEquals("LineItem", unionFirst.type().typeName());
        assertEquals(OclTypeBinding.CollectionKind.ORDERED_SET, intersection.type().collectionKind());
        assertEquals("LineItem", intersectionLast.type().typeName());
    }

    @Test
    void preservesCollectionKindAcrossFlatten() {
        OclSemanticBinder.BoundContextInvariant sequenceFlatten = bind("""
                model Demo
                class Family
                attributes
                    aliases : String
                end
                """, "context Family inv FlatAliases: self.aliases.split(';')->collect(a | a.split(','))->flatten()->notEmpty()");
        OclSemanticBinder.BoundContextInvariant bagFlatten = bind("""
                model Demo
                class Family
                end
                class Person
                end
                association FamilyChildren between
                    Family[*] role family
                    Person[*] role children
                end
                """, "context Family inv FlatFamilies: self.children->collect(c | c.family)->flatten()->notEmpty()");

        assertEquals(OclTypeBinding.CollectionKind.SEQUENCE,
                as(as(sequenceFlatten.expression(), OclSemanticBinder.BoundCollectionOperation.class).source(),
                        OclSemanticBinder.BoundCollectionOperation.class).type().collectionKind());
        assertEquals(OclTypeBinding.CollectionKind.BAG,
                as(as(bagFlatten.expression(), OclSemanticBinder.BoundCollectionOperation.class).source(),
                        OclSemanticBinder.BoundCollectionOperation.class).type().collectionKind());
    }

    @Test
    void preservesOrderedSetAcrossFlatten() {
        OclSemanticBinder.BoundContextInvariant orderedSetFlatten = bind("""
                model Demo
                class Family
                attributes
                    aliases : String
                end
                """, "context Family inv FlatOrderedAliases: self.aliases.split(';')->collect(a | a.split(','))->asOrderedSet()->flatten()->first().isDefined()");

        OclSemanticBinder.BoundMethodCall isDefined = as(orderedSetFlatten.expression(), OclSemanticBinder.BoundMethodCall.class);
        OclSemanticBinder.BoundCollectionOperation first = as(isDefined.source(), OclSemanticBinder.BoundCollectionOperation.class);
        OclSemanticBinder.BoundCollectionOperation flatten = as(first.source(), OclSemanticBinder.BoundCollectionOperation.class);

        assertEquals(OclTypeBinding.CollectionKind.ORDERED_SET, flatten.type().collectionKind());
        assertEquals("String", first.type().typeName());
    }

    @Test
    void normalizesCollectionPropertyProjectionIntoCollect() {
        OclSemanticBinder.BoundContextInvariant bound = bind("""
                model Demo
                class Family
                end
                class Person
                attributes
                    name : String
                end
                association FamilyChildren between
                    Family[*] role family
                    Person[*] role children
                end
                """, "context Family inv ChildNamesPresent: self.children.name->includes('Bart')");

        OclSemanticBinder.BoundCollectionOperation includes = as(bound.expression(), OclSemanticBinder.BoundCollectionOperation.class);
        OclSemanticBinder.BoundIterator collect = as(includes.source(), OclSemanticBinder.BoundIterator.class);
        OclSemanticBinder.BoundProperty name = as(collect.body(), OclSemanticBinder.BoundProperty.class);
        OclSemanticBinder.BoundVariable iteratorVar = as(name.source(), OclSemanticBinder.BoundVariable.class);

        assertEquals("collect", collect.ast().operation);
        assertEquals("String", name.type().typeName());
        assertEquals("Person", iteratorVar.type().typeName());
        assertEquals(OclTypeBinding.CollectionKind.BAG, collect.type().collectionKind());
    }

    @Test
    void keepsPositionalAccessPolicyExplicitForOrderedCollections() {
        OclSemanticBinder.BoundContextInvariant orderedFirst = bind("""
                model Demo
                class Family
                attributes
                    name : String
                end
                """, "context Family inv OrderedFirst: self.name.split(',')->first().isDefined()");

        OclSemanticBinder.BoundMethodCall orderedIsDefined = as(orderedFirst.expression(), OclSemanticBinder.BoundMethodCall.class);
        OclSemanticBinder.BoundCollectionOperation first = as(orderedIsDefined.source(), OclSemanticBinder.BoundCollectionOperation.class);
        assertEquals("String", first.type().typeName());
    }

    @Test
    void allowsPositionalAccessOnOrderedNavigationCollections() {
        OclSemanticBinder.BoundContextInvariant orderedFirst = bind("""
                model Demo
                class Invoice
                end
                class LineItem
                attributes
                    price : Integer
                end
                association InvoiceLineItems between
                    Invoice[1] role invoice
                    LineItem[*] role lineItem ordered
                end
                """, "context Invoice inv FirstLineItemPriceDefined: self.lineItem->first().price.isDefined()");

        OclSemanticBinder.BoundMethodCall isDefined = as(orderedFirst.expression(), OclSemanticBinder.BoundMethodCall.class);
        OclSemanticBinder.BoundProperty price = as(isDefined.source(), OclSemanticBinder.BoundProperty.class);
        OclSemanticBinder.BoundCollectionOperation first = as(price.source(), OclSemanticBinder.BoundCollectionOperation.class);
        OclSemanticBinder.BoundProperty lineItem = as(first.source(), OclSemanticBinder.BoundProperty.class);

        assertEquals(OclTypeBinding.CollectionKind.ORDERED_SET, lineItem.type().collectionKind());
        assertEquals("LineItem", first.type().typeName());
        assertEquals("Integer", price.type().typeName());
    }

    @Test
    void rejectsPositionalAccessOnUnorderedCollections() {
        UnsupportedOperationException atFailure = assertThrows(UnsupportedOperationException.class, () -> bind("""
                model Demo
                class Family
                end
                class Person
                end
                association FamilyChildren between
                    Family[*] role family
                    Person[*] role children
                end
                """, "context Family inv UnorderedAt: self.children->at(1).isDefined()"));
        UnsupportedOperationException lastFailure = assertThrows(UnsupportedOperationException.class, () -> bind("""
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
                """, "context Family inv UnorderedLast: self.children->last().age.isDefined()"));

        assertTrue(atFailure.getMessage().contains("ordered collections"));
        assertTrue(lastFailure.getMessage().contains("ordered collections"));
    }

    private OclSemanticBinder.BoundContextInvariant bind(String spec, String ocl) {
        StringWriter buffer = new StringWriter();
        MModel model = USECompiler.compileSpecification(spec, "demo.use", new PrintWriter(buffer, true), new ModelFactory());
        assertNotNull(model, buffer.toString());

        ASTNode ast = new ASTVisitor().visit(new OCLParser(
                new CommonTokenStream(new OCLLexer(CharStreams.fromString(ocl)))).oclFile());
        return new OclSemanticBinder(new OclMetamodelIndex(model)).bindContext(firstContext(ast));
    }

    private ASTContext firstContext(ASTNode ast) {
        assertTrue(ast instanceof ASTFile);
        ASTFile file = (ASTFile) ast;
        assertEquals(1, file.invariants().size());
        return file.invariants().get(0);
    }

    @SuppressWarnings("unchecked")
    private <T> T as(Object value, Class<T> type) {
        assertTrue(type.isInstance(value), "Expected " + type.getSimpleName() + " but got " +
                (value == null ? "null" : value.getClass().getSimpleName()));
        return (T) value;
    }
}
