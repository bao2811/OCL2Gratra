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
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclCodedUnsupportedOperationException;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclDiagnosticCode;
import org.uet.dse.neo4jtgg.ocl.ir.OclIr;
import org.uet.dse.neo4jtgg.ocl.ir.OclIrBuilder;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OclSemanticBinderTest {
    @Test
    void preservesMedicalNestedIteratorTypesThroughBoundAndSemanticIr() {
        String spec = """
                model MedicalTypes
                class Medication
                end
                class Doctor
                attributes
                    shiftSchedule : Sequence(Set(Integer))
                end
                class Patient
                attributes
                    treatmentHistory : Set(Sequence(String))
                    prescriptionHistory : Sequence(Sequence(Medication))
                end
                """;

        assertNestedIteratorTypes(bind(spec,
                        "context Doctor inv Rooms: self.shiftSchedule->forAll(shift | shift->size() <= 3)"),
                OclTypeBinding.CollectionKind.SEQUENCE,
                OclTypeBinding.CollectionKind.SET,
                "Integer", false);
        assertNestedIteratorTypes(bind(spec,
                        "context Patient inv Treatments: self.treatmentHistory->forAll(batch | batch->notEmpty())"),
                OclTypeBinding.CollectionKind.SET,
                OclTypeBinding.CollectionKind.SEQUENCE,
                "String", false);
        assertNestedIteratorTypes(bind(spec,
                        "context Patient inv Prescriptions: self.prescriptionHistory->forAll(batch | batch->notEmpty())"),
                OclTypeBinding.CollectionKind.SEQUENCE,
                OclTypeBinding.CollectionKind.SEQUENCE,
                "Medication", true);
    }

    private void assertNestedIteratorTypes(OclSemanticBinder.BoundContextInvariant bound,
                                           OclTypeBinding.CollectionKind outerKind,
                                           OclTypeBinding.CollectionKind innerKind,
                                           String leafType,
                                           boolean nodeLeaf) {
        OclSemanticBinder.BoundIterator iterator = as(
                bound.expression(), OclSemanticBinder.BoundIterator.class);
        assertNestedIteratorType(iterator.sourceCollectionType(), iterator.iteratorVariableType(),
                outerKind, innerKind, leafType, nodeLeaf);

        OclIr.IteratorOperation semantic = as(
                new OclIrBuilder().buildInvariant(bound).predicate(), OclIr.IteratorOperation.class);
        assertNestedIteratorType(semantic.sourceCollectionType(), semantic.iteratorVariableType(),
                outerKind, innerKind, leafType, nodeLeaf);
    }

    private static void assertNestedIteratorType(OclTypeBinding sourceType,
                                                 OclTypeBinding iteratorType,
                                                 OclTypeBinding.CollectionKind outerKind,
                                                 OclTypeBinding.CollectionKind innerKind,
                                                 String leafType,
                                                 boolean nodeLeaf) {
        assertEquals(outerKind, sourceType.collectionKind());
        assertEquals(iteratorType, sourceType.elementType());
        assertTrue(iteratorType.isCollection());
        assertEquals(innerKind, iteratorType.collectionKind());
        assertEquals(leafType, iteratorType.elementType().typeName());
        assertEquals(nodeLeaf, iteratorType.elementType().isNode());
    }

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
    void distinguishesSelfIndependentGlobalBodyFromSelfDependentBody() {
        String spec = """
                model SelfDependency
                class CarGroup
                end
                """;
        OclSemanticBinder.BoundContextInvariant global = bind(spec,
                "context CarGroup inv ExactlyOne: CarGroup.allInstances()->size() = 1");
        OclSemanticBinder.BoundContextInvariant local = bind(spec,
                "context CarGroup inv HasSelf: self.oclIsKindOf(CarGroup)");

        assertTrue(global.isSelfIndependent());
        assertEquals(java.util.Set.of(), global.freeVariables());
        assertFalse(local.isSelfIndependent());
        assertTrue(local.freeVariables().contains("self"));
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
    void infersAggregateCollectionOperationTypes() {
        OclSemanticBinder.BoundContextInvariant integerBound = bind("""
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
                """, "context Family inv TotalAgePositive: self.children->collect(c | c.age)->sum() >= 0");
        OclSemanticBinder.BoundContextInvariant realBound = bind("""
                model Demo
                class Invoice
                end
                class LineItem
                attributes
                    amount : Real
                end
                association InvoiceLineItems between
                    Invoice[1] role invoice
                    LineItem[*] role lineItem ordered
                end
                """, "context Invoice inv MaxAmountDefined: self.lineItem->collect(li | li.amount)->max().isDefined()");

        OclSemanticBinder.BoundBinary integerComparison = as(integerBound.expression(), OclSemanticBinder.BoundBinary.class);
        OclSemanticBinder.BoundCollectionOperation sum = as(integerComparison.left(), OclSemanticBinder.BoundCollectionOperation.class);
        OclSemanticBinder.BoundMethodCall maxIsDefined = as(realBound.expression(), OclSemanticBinder.BoundMethodCall.class);
        OclSemanticBinder.BoundCollectionOperation max = as(maxIsDefined.source(), OclSemanticBinder.BoundCollectionOperation.class);

        assertEquals("Integer", sum.type().typeName());
        assertEquals("Real", max.type().typeName());
    }

    @Test
    void rejectsAggregateCollectionOperationsOnNonNumericElements() {
        UnsupportedOperationException failure = assertThrows(UnsupportedOperationException.class, () -> bind("""
                model Demo
                class Family
                attributes
                    name : String
                end
                """, "context Family inv BadSum: self.name.split(',')->sum() >= 0"));

        assertTrue(failure.getMessage().contains("Integer or Real"));
    }

    @Test
    void infersIsUniqueIteratorAsBoolean() {
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
                """, "context Family inv UniqueChildNames: self.children->isUnique(c | c.name)");

        OclSemanticBinder.BoundIterator isUnique = as(bound.expression(), OclSemanticBinder.BoundIterator.class);
        OclSemanticBinder.BoundProperty name = as(isUnique.body(), OclSemanticBinder.BoundProperty.class);

        assertEquals("isUnique", isUnique.ast().operation);
        assertEquals("Boolean", isUnique.type().typeName());
        assertEquals("name", name.ast().name);
    }

    @Test
    void infersCollectionValuedPrimitiveAttributeAsCollectionBinding() {
        OclSemanticBinder.BoundContextInvariant bound = bind("""
                model Demo
                class Person
                attributes
                    aliases : Sequence(String)
                end
                """, "context Person inv HasAliases: self.aliases->first().isDefined()");

        OclSemanticBinder.BoundMethodCall isDefined = as(bound.expression(), OclSemanticBinder.BoundMethodCall.class);
        OclSemanticBinder.BoundCollectionOperation first = as(isDefined.source(), OclSemanticBinder.BoundCollectionOperation.class);
        OclSemanticBinder.BoundProperty aliases = as(first.source(), OclSemanticBinder.BoundProperty.class);

        assertEquals(OclTypeBinding.CollectionKind.SEQUENCE, aliases.type().collectionKind());
        assertEquals("String", aliases.type().elementType().typeName());
        assertEquals("String", first.type().typeName());
    }

    @Test
    void infersCollectionValuedObjectReferenceAttributeAsNodeCollectionBinding() {
        OclSemanticBinder.BoundContextInvariant bound = bind("""
                model Demo
                class Person
                attributes
                    friends : Sequence(Person)
                end
                """, "context Person inv HasSelfOrEmpty: self.friends->includes(self) or self.friends->isEmpty()");

        OclSemanticBinder.BoundBinary or = as(bound.expression(), OclSemanticBinder.BoundBinary.class);
        OclSemanticBinder.BoundCollectionOperation includes = as(or.left(), OclSemanticBinder.BoundCollectionOperation.class);
        OclSemanticBinder.BoundProperty friends = as(includes.source(), OclSemanticBinder.BoundProperty.class);

        assertEquals(OclTypeBinding.CollectionKind.SEQUENCE, friends.type().collectionKind());
        assertTrue(friends.type().elementType().isNode());
        assertEquals("Person", friends.type().elementType().typeName());
    }

    @Test
    void infersNestedCollectionValuedPrimitiveAttributeAsNestedCollectionBinding() {
        OclSemanticBinder.BoundContextInvariant bound = bind("""
                model Demo
                class Person
                attributes
                    aliases2d : Sequence(Sequence(String))
                end
                """, "context Person inv HasBartSomewhere: self.aliases2d->flatten()->count('Bart') >= 0");

        OclSemanticBinder.BoundBinary comparison = as(bound.expression(), OclSemanticBinder.BoundBinary.class);
        OclSemanticBinder.BoundCollectionOperation count = as(comparison.left(), OclSemanticBinder.BoundCollectionOperation.class);
        OclSemanticBinder.BoundCollectionOperation flatten = as(count.source(), OclSemanticBinder.BoundCollectionOperation.class);
        OclSemanticBinder.BoundProperty aliases2d = as(flatten.source(), OclSemanticBinder.BoundProperty.class);

        assertEquals(OclTypeBinding.CollectionKind.SEQUENCE, aliases2d.type().collectionKind());
        assertTrue(aliases2d.type().elementType().isCollection());
        assertEquals("String", aliases2d.type().elementType().elementType().typeName());
        assertEquals("String", flatten.type().elementType().typeName());
    }

    @Test
    void infersDeepNestedCollectionValuedPrimitiveAttributeAcrossMultipleFlattenLevels() {
        OclSemanticBinder.BoundContextInvariant bound = bind("""
                model Demo
                class Person
                attributes
                    aliases3d : Sequence(Sequence(Sequence(String)))
                end
                """, "context Person inv HasBartSomewhere: self.aliases3d->flatten()->flatten()->count('Bart') >= 0");

        OclSemanticBinder.BoundBinary comparison = as(bound.expression(), OclSemanticBinder.BoundBinary.class);
        OclSemanticBinder.BoundCollectionOperation count = as(comparison.left(), OclSemanticBinder.BoundCollectionOperation.class);
        OclSemanticBinder.BoundCollectionOperation flatten2 = as(count.source(), OclSemanticBinder.BoundCollectionOperation.class);
        OclSemanticBinder.BoundCollectionOperation flatten1 = as(flatten2.source(), OclSemanticBinder.BoundCollectionOperation.class);
        OclSemanticBinder.BoundProperty aliases3d = as(flatten1.source(), OclSemanticBinder.BoundProperty.class);

        assertTrue(aliases3d.type().elementType().isCollection());
        assertTrue(aliases3d.type().elementType().elementType().isCollection());
        assertEquals("String", flatten2.type().elementType().typeName());
    }

    @Test
    void preservesOrderedAndUniqueKindsAcrossNestedFlatten() {
        OclSemanticBinder.BoundContextInvariant orderedBound = bind("""
                model Demo
                class Person
                attributes
                    aliases2d : Sequence(Sequence(String))
                end
                """, "context Person inv OrderedAliases: self.aliases2d->asOrderedSet()->flatten()->first().isDefined()");
        OclSemanticBinder.BoundContextInvariant uniqueBound = bind("""
                model Demo
                class Person
                attributes
                    aliases2d : Sequence(Sequence(String))
                end
                """, "context Person inv UniqueAliases: self.aliases2d->asSet()->flatten()->count('Bart') >= 0");

        OclSemanticBinder.BoundMethodCall orderedDefined = as(orderedBound.expression(), OclSemanticBinder.BoundMethodCall.class);
        OclSemanticBinder.BoundCollectionOperation orderedFirst = as(orderedDefined.source(), OclSemanticBinder.BoundCollectionOperation.class);
        OclSemanticBinder.BoundCollectionOperation orderedFlatten = as(orderedFirst.source(), OclSemanticBinder.BoundCollectionOperation.class);

        OclSemanticBinder.BoundBinary uniqueComparison = as(uniqueBound.expression(), OclSemanticBinder.BoundBinary.class);
        OclSemanticBinder.BoundCollectionOperation uniqueCount = as(uniqueComparison.left(), OclSemanticBinder.BoundCollectionOperation.class);
        OclSemanticBinder.BoundCollectionOperation uniqueFlatten = as(uniqueCount.source(), OclSemanticBinder.BoundCollectionOperation.class);

        assertEquals(OclTypeBinding.CollectionKind.ORDERED_SET, orderedFlatten.type().collectionKind());
        assertEquals(OclTypeBinding.CollectionKind.SET, uniqueFlatten.type().collectionKind());
    }

    @Test
    void infersNestedUnionAndIntersectionKinds() {
        OclSemanticBinder.BoundContextInvariant uniqueUnion = bind("""
                model Demo
                class Person
                attributes
                    aliases2d : Sequence(Sequence(String))
                end
                """, "context Person inv UniqueNestedAliases: self.aliases2d->flatten()->asSet()->union(self.aliases2d->flatten()->asSet())->count('Bart') >= 0");
        OclSemanticBinder.BoundContextInvariant orderedIntersection = bind("""
                model Demo
                class Person
                attributes
                    aliases2d : Sequence(Sequence(String))
                end
                """, "context Person inv OrderedNestedAliases: self.aliases2d->flatten()->asOrderedSet()->intersection(self.aliases2d->flatten()->asOrderedSet())->first().isDefined()");

        OclSemanticBinder.BoundBinary uniqueComparison = as(uniqueUnion.expression(), OclSemanticBinder.BoundBinary.class);
        OclSemanticBinder.BoundCollectionOperation uniqueCount = as(uniqueComparison.left(), OclSemanticBinder.BoundCollectionOperation.class);
        OclSemanticBinder.BoundCollectionOperation uniqueUnionOp = as(uniqueCount.source(), OclSemanticBinder.BoundCollectionOperation.class);

        OclSemanticBinder.BoundMethodCall orderedDefined = as(orderedIntersection.expression(), OclSemanticBinder.BoundMethodCall.class);
        OclSemanticBinder.BoundCollectionOperation orderedFirst = as(orderedDefined.source(), OclSemanticBinder.BoundCollectionOperation.class);
        OclSemanticBinder.BoundCollectionOperation orderedIntersectionOp = as(orderedFirst.source(), OclSemanticBinder.BoundCollectionOperation.class);

        assertEquals(OclTypeBinding.CollectionKind.SET, uniqueUnionOp.type().collectionKind());
        assertEquals(OclTypeBinding.CollectionKind.ORDERED_SET, orderedIntersectionOp.type().collectionKind());
    }

    @Test
    void infersNestedObjectReferenceUnionAndIntersectionKinds() {
        OclSemanticBinder.BoundContextInvariant uniqueUnion = bind("""
                model Demo
                class Person
                attributes
                    friendGroups2d : Sequence(Sequence(Person))
                end
                """, "context Person inv UniqueNestedFriends: self.friendGroups2d->flatten()->asSet()->union(self.friendGroups2d->flatten()->asSet())->count(self) >= 0");
        OclSemanticBinder.BoundContextInvariant orderedIntersection = bind("""
                model Demo
                class Person
                attributes
                    friendGroups2d : Sequence(Sequence(Person))
                end
                """, "context Person inv OrderedNestedFriends: self.friendGroups2d->flatten()->asOrderedSet()->intersection(self.friendGroups2d->flatten()->asOrderedSet())->first().isDefined() or self.friendGroups2d->flatten()->isEmpty()");

        OclSemanticBinder.BoundBinary uniqueComparison = as(uniqueUnion.expression(), OclSemanticBinder.BoundBinary.class);
        OclSemanticBinder.BoundCollectionOperation uniqueCount = as(uniqueComparison.left(), OclSemanticBinder.BoundCollectionOperation.class);
        OclSemanticBinder.BoundCollectionOperation uniqueUnionOp = as(uniqueCount.source(), OclSemanticBinder.BoundCollectionOperation.class);

        OclSemanticBinder.BoundBinary orderedOr = as(orderedIntersection.expression(), OclSemanticBinder.BoundBinary.class);
        OclSemanticBinder.BoundMethodCall orderedDefined = as(orderedOr.left(), OclSemanticBinder.BoundMethodCall.class);
        OclSemanticBinder.BoundCollectionOperation orderedFirst = as(orderedDefined.source(), OclSemanticBinder.BoundCollectionOperation.class);
        OclSemanticBinder.BoundCollectionOperation orderedIntersectionOp = as(orderedFirst.source(), OclSemanticBinder.BoundCollectionOperation.class);

        assertEquals(OclTypeBinding.CollectionKind.SET, uniqueUnionOp.type().collectionKind());
        assertEquals(OclTypeBinding.CollectionKind.ORDERED_SET, orderedIntersectionOp.type().collectionKind());
    }

    @Test
    void infersNestedBagUnionAndIntersectionKinds() {
        OclSemanticBinder.BoundContextInvariant bagUnion = bind("""
                model Demo
                class Person
                attributes
                    aliases2d : Sequence(Sequence(String))
                end
                """, "context Person inv BagNestedAliases: self.aliases2d->flatten()->asBag()->union(self.aliases2d->flatten()->asBag())->count('Bart') >= 2");
        OclSemanticBinder.BoundContextInvariant bagIntersection = bind("""
                model Demo
                class Person
                attributes
                    aliases2d : Sequence(Sequence(String))
                end
                """, "context Person inv SharedNestedAliases: self.aliases2d->flatten()->asBag()->intersection(self.aliases2d->flatten()->asBag())->count('Bart') >= 1");

        OclSemanticBinder.BoundBinary unionComparison = as(bagUnion.expression(), OclSemanticBinder.BoundBinary.class);
        OclSemanticBinder.BoundCollectionOperation unionCount = as(unionComparison.left(), OclSemanticBinder.BoundCollectionOperation.class);
        OclSemanticBinder.BoundCollectionOperation unionOp = as(unionCount.source(), OclSemanticBinder.BoundCollectionOperation.class);

        OclSemanticBinder.BoundBinary intersectionComparison = as(bagIntersection.expression(), OclSemanticBinder.BoundBinary.class);
        OclSemanticBinder.BoundCollectionOperation intersectionCount = as(intersectionComparison.left(), OclSemanticBinder.BoundCollectionOperation.class);
        OclSemanticBinder.BoundCollectionOperation intersectionOp = as(intersectionCount.source(), OclSemanticBinder.BoundCollectionOperation.class);

        assertEquals(OclTypeBinding.CollectionKind.BAG, unionOp.type().collectionKind());
        assertEquals(OclTypeBinding.CollectionKind.BAG, intersectionOp.type().collectionKind());
    }

    @Test
    void infersIncludingAndExcludingCollectionKinds() {
        OclSemanticBinder.BoundContextInvariant includingBound = bind("""
                model Demo
                class Family
                attributes
                    name : String
                end
                """, "context Family inv AddedBartStillOrdered: self.name.split(',')->including('Bart')->last().isDefined()");
        OclSemanticBinder.BoundContextInvariant excludingBound = bind("""
                model Demo
                class Family
                attributes
                    name : String
                end
                """, "context Family inv RemovedBartCount: self.name.split(',')->asSet()->excluding('Bart')->count('Bart') = 0");

        OclSemanticBinder.BoundMethodCall includingDefined = as(includingBound.expression(), OclSemanticBinder.BoundMethodCall.class);
        OclSemanticBinder.BoundCollectionOperation includingLast = as(includingDefined.source(), OclSemanticBinder.BoundCollectionOperation.class);
        OclSemanticBinder.BoundCollectionOperation including = as(includingLast.source(), OclSemanticBinder.BoundCollectionOperation.class);

        OclSemanticBinder.BoundBinary excludingComparison = as(excludingBound.expression(), OclSemanticBinder.BoundBinary.class);
        OclSemanticBinder.BoundCollectionOperation excludingCount = as(excludingComparison.left(), OclSemanticBinder.BoundCollectionOperation.class);
        OclSemanticBinder.BoundCollectionOperation excluding = as(excludingCount.source(), OclSemanticBinder.BoundCollectionOperation.class);

        assertEquals(OclTypeBinding.CollectionKind.SEQUENCE, including.type().collectionKind());
        assertEquals(OclTypeBinding.CollectionKind.SET, excluding.type().collectionKind());
    }

    @Test
    void infersAppendAndPrependCollectionKinds() {
        OclSemanticBinder.BoundContextInvariant appendBound = bind("""
                model Demo
                class Family
                attributes
                    name : String
                end
                """, "context Family inv AppendedBartStillOrdered: self.name.split(',')->append('Bart')->last().isDefined()");
        OclSemanticBinder.BoundContextInvariant prependBound = bind("""
                model Demo
                class Family
                attributes
                    name : String
                end
                """, "context Family inv PrependedBartMovesToFront: self.name.split(',')->asOrderedSet()->prepend('Bart')->first().isDefined()");

        OclSemanticBinder.BoundMethodCall appendDefined = as(appendBound.expression(), OclSemanticBinder.BoundMethodCall.class);
        OclSemanticBinder.BoundCollectionOperation appendLast = as(appendDefined.source(), OclSemanticBinder.BoundCollectionOperation.class);
        OclSemanticBinder.BoundCollectionOperation append = as(appendLast.source(), OclSemanticBinder.BoundCollectionOperation.class);

        OclSemanticBinder.BoundMethodCall prependDefined = as(prependBound.expression(), OclSemanticBinder.BoundMethodCall.class);
        OclSemanticBinder.BoundCollectionOperation prependFirst = as(prependDefined.source(), OclSemanticBinder.BoundCollectionOperation.class);
        OclSemanticBinder.BoundCollectionOperation prepend = as(prependFirst.source(), OclSemanticBinder.BoundCollectionOperation.class);

        assertEquals(OclTypeBinding.CollectionKind.SEQUENCE, append.type().collectionKind());
        assertEquals(OclTypeBinding.CollectionKind.ORDERED_SET, prepend.type().collectionKind());
    }

    @Test
    void infersSubSequenceCollectionKinds() {
        OclSemanticBinder.BoundContextInvariant sequenceBound = bind("""
                model Demo
                class Family
                attributes
                    name : String
                end
                """, "context Family inv MiddleNamesStayOrdered: self.name.split(',')->subSequence(1, 2)->last().isDefined()");
        OclSemanticBinder.BoundContextInvariant orderedSetBound = bind("""
                model Demo
                class Family
                attributes
                    name : String
                end
                """, "context Family inv MiddleUniqueNamesStayOrdered: self.name.split(',')->asOrderedSet()->subSequence(1, 2)->first().isDefined()");

        OclSemanticBinder.BoundMethodCall sequenceDefined = as(sequenceBound.expression(), OclSemanticBinder.BoundMethodCall.class);
        OclSemanticBinder.BoundCollectionOperation sequenceLast = as(sequenceDefined.source(), OclSemanticBinder.BoundCollectionOperation.class);
        OclSemanticBinder.BoundCollectionOperation sequenceSubSequence = as(sequenceLast.source(), OclSemanticBinder.BoundCollectionOperation.class);

        OclSemanticBinder.BoundMethodCall orderedSetDefined = as(orderedSetBound.expression(), OclSemanticBinder.BoundMethodCall.class);
        OclSemanticBinder.BoundCollectionOperation orderedSetFirst = as(orderedSetDefined.source(), OclSemanticBinder.BoundCollectionOperation.class);
        OclSemanticBinder.BoundCollectionOperation orderedSetSubSequence = as(orderedSetFirst.source(), OclSemanticBinder.BoundCollectionOperation.class);

        assertEquals(OclTypeBinding.CollectionKind.SEQUENCE, sequenceSubSequence.type().collectionKind());
        assertEquals(OclTypeBinding.CollectionKind.ORDERED_SET, orderedSetSubSequence.type().collectionKind());
    }

    @Test
    void infersSortedByCollectionKinds() {
        OclSemanticBinder.BoundContextInvariant sequenceBound = bind("""
                model Demo
                class Family
                attributes
                    name : String
                end
                """, "context Family inv SortedNamesStayOrdered: self.name.split(',')->sortedBy(token | token)->first().isDefined()");
        OclSemanticBinder.BoundContextInvariant orderedSetBound = bind("""
                model Demo
                class Family
                attributes
                    name : String
                end
                """, "context Family inv SortedUniqueNamesStayUnique: self.name.split(',')->asSet()->sortedBy(token | token)->first().isDefined()");

        OclSemanticBinder.BoundMethodCall sequenceDefined = as(sequenceBound.expression(), OclSemanticBinder.BoundMethodCall.class);
        OclSemanticBinder.BoundCollectionOperation sequenceFirst = as(sequenceDefined.source(), OclSemanticBinder.BoundCollectionOperation.class);
        OclSemanticBinder.BoundIterator sequenceSortedBy = as(sequenceFirst.source(), OclSemanticBinder.BoundIterator.class);

        OclSemanticBinder.BoundMethodCall orderedSetDefined = as(orderedSetBound.expression(), OclSemanticBinder.BoundMethodCall.class);
        OclSemanticBinder.BoundCollectionOperation orderedSetFirst = as(orderedSetDefined.source(), OclSemanticBinder.BoundCollectionOperation.class);
        OclSemanticBinder.BoundIterator orderedSetSortedBy = as(orderedSetFirst.source(), OclSemanticBinder.BoundIterator.class);

        assertEquals(OclTypeBinding.CollectionKind.SEQUENCE, sequenceSortedBy.type().collectionKind());
        assertEquals(OclTypeBinding.CollectionKind.ORDERED_SET, orderedSetSortedBy.type().collectionKind());
    }

    @Test
    void infersOclAsTypeTargetNodeType() {
        OclSemanticBinder.BoundContextInvariant bound = bind("""
                model Demo
                class Person
                end
                class Employee < Person
                attributes
                    salary : Integer
                end
                """, "context Employee inv CastKeepsEmployeeType: self.oclAsType(Employee).salary >= 0");

        OclSemanticBinder.BoundBinary comparison = as(bound.expression(), OclSemanticBinder.BoundBinary.class);
        OclSemanticBinder.BoundProperty salary = as(comparison.left(), OclSemanticBinder.BoundProperty.class);
        OclSemanticBinder.BoundMethodCall cast = as(salary.source(), OclSemanticBinder.BoundMethodCall.class);

        assertEquals("Employee", cast.type().typeName());
        assertTrue(cast.type().isNode());
        assertEquals("salary", salary.ast().name);
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
    void acceptsNullAsBottomIfCondition() {
        OclSemanticBinder.BoundContextInvariant bound = bind("""
                model Demo
                class Person
                end
                """, "context Person inv BottomCondition: if null then true else false endif");

        OclSemanticBinder.BoundIf ifExpression = as(bound.expression(), OclSemanticBinder.BoundIf.class);
        assertEquals("Void", ifExpression.condition().type().typeName());
        assertEquals("Boolean", ifExpression.type().typeName());
    }

    @Test
    void keepsVoidInternalWhileAllowingNullToConformToDeclaredType() {
        OclSemanticBinder.BoundContextInvariant bound = bind("""
                model Demo
                class Person
                end
                """, "context Person inv TypedNull: let value : Integer = null in value = null");

        OclSemanticBinder.BoundLet let = as(bound.expression(), OclSemanticBinder.BoundLet.class);
        assertEquals("Integer", let.variableType().typeName());
        assertEquals("Void", let.value().type().typeName());

        UnsupportedOperationException failure = assertThrows(UnsupportedOperationException.class, () -> bind("""
                model Demo
                class Person
                end
                """, "context Person inv ExplicitVoid: let value : Void = null in value.isUndefined()"));
        assertTrue(failure.getMessage().contains("internal to null-bottom inference"));
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
    void rejectsIfExpressionWithNonBooleanCondition() {
        UnsupportedOperationException failure = assertThrows(UnsupportedOperationException.class, () -> bind("""
                model Demo
                class Person
                attributes
                    age : Integer
                    name : String
                end
                """, "context Person inv BadIfCondition: if self.name then self.age else 0 endif > 0"));

        assertTrue(failure.getMessage().contains("if condition must be Boolean"));
    }

    @Test
    void rejectsIfExpressionWithIncompatibleBranches() {
        UnsupportedOperationException failure = assertThrows(UnsupportedOperationException.class, () -> bind("""
                model Demo
                class Person
                attributes
                    age : Integer
                    name : String
                end
                """, "context Person inv BadIfBranches: (if self.age >= 18 then self.name else self.age endif).isDefined()"));

        assertTrue(failure.getMessage().contains("if branches must have compatible types"));
    }

    @Test
    void rejectsNotWithNonBooleanOperand() {
        UnsupportedOperationException failure = assertThrows(UnsupportedOperationException.class, () -> bind("""
                model Demo
                class Person
                attributes
                    name : String
                end
                """, "context Person inv BadNot: not self.name"));

        assertTrue(failure.getMessage().contains("requires Boolean scalar"));
    }

    @Test
    void rejectsBooleanBinaryOperatorWithNonBooleanOperand() {
        UnsupportedOperationException failure = assertThrows(UnsupportedOperationException.class, () -> bind("""
                model Demo
                class Person
                attributes
                    age : Integer
                    name : String
                end
                """, "context Person inv BadAnd: self.name = 'Lisa' and self.age"));

        assertTrue(failure.getMessage().contains("requires Boolean scalar"));
    }

    @Test
    void rejectsArithmeticOperatorWithNonNumericOperand() {
        UnsupportedOperationException failure = assertThrows(UnsupportedOperationException.class, () -> bind("""
                model Demo
                class Person
                attributes
                    name : String
                end
                """, "context Person inv BadPlus: self.name + 1 > 0"));

        assertTrue(failure.getMessage().contains("requires Integer or Real scalar"));
    }

    @Test
    void rejectsOrderingOperatorWithNonNumericOperand() {
        UnsupportedOperationException failure = assertThrows(UnsupportedOperationException.class, () -> bind("""
                model Demo
                class Person
                attributes
                    name : String
                end
                """, "context Person inv BadOrdering: self.name > 'A'"));

        assertTrue(failure.getMessage().contains("requires Integer or Real scalar"));
    }

    @Test
    void rejectsEqualityOperatorWithIncompatibleOperands() {
        UnsupportedOperationException failure = assertThrows(UnsupportedOperationException.class, () -> bind("""
                model Demo
                class Person
                attributes
                    age : Integer
                    name : String
                end
                """, "context Person inv BadEquality: self.name = self.age"));

        assertTrue(failure.getMessage().contains("requires compatible operand types"));
    }

    @Test
    void rejectsPredicateIteratorWithNonBooleanBody() {
        UnsupportedOperationException failure = assertThrows(UnsupportedOperationException.class, () -> bind("""
                model Demo
                class Family
                end
                class Person
                attributes
                    name : String
                end
                association FamilyChildren between
                    Family[1] role family
                    Person[*] role children
                end
                """, "context Family inv BadExistsBody: self.children->exists(c | c.name)"));

        assertTrue(failure.getMessage().contains("requires Boolean scalar"));
    }

    @Test
    void rejectsSelectIteratorWithNonBooleanBody() {
        UnsupportedOperationException failure = assertThrows(UnsupportedOperationException.class, () -> bind("""
                model Demo
                class Family
                end
                class Person
                attributes
                    name : String
                end
                association FamilyChildren between
                    Family[1] role family
                    Person[*] role children
                end
                """, "context Family inv BadSelectBody: self.children->select(c | c.name)->notEmpty()"));

        assertTrue(failure.getMessage().contains("requires Boolean scalar"));
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
    void bindsTypedLetUsingDeclaredTypeAndNumericConformance() {
        OclSemanticBinder.BoundContextInvariant bound = bind("""
                model Demo
                class Person
                attributes
                    age : Integer
                end
                """, "context Person inv TypedLet: let threshold : Real = self.age in threshold >= 0.0");

        OclSemanticBinder.BoundLet letExpression = as(bound.expression(), OclSemanticBinder.BoundLet.class);
        assertEquals("Integer", letExpression.value().type().typeName());
        assertEquals("Real", letExpression.variableType().typeName());
        OclSemanticBinder.BoundVariable threshold = as(
                as(letExpression.body(), OclSemanticBinder.BoundBinary.class).left(),
                OclSemanticBinder.BoundVariable.class);
        assertEquals("Real", threshold.type().typeName());
    }

    @Test
    void rejectsTypedLetWhenInitializerDoesNotConform() {
        OclCodedUnsupportedOperationException failure = assertThrows(
                OclCodedUnsupportedOperationException.class,
                () -> bind("""
                        model Demo
                        class Person
                        attributes
                            age : Integer
                        end
                        """, "context Person inv BadTypedLet: let x : String = self.age in x = x"));

        assertEquals(OclDiagnosticCode.LET_TYPE_MISMATCH, failure.code());
    }

    @Test
    void bindsTypedIteratorWithElementSupertype() {
        OclSemanticBinder.BoundContextInvariant bound = bind("""
                model Demo
                class Person
                attributes
                    age : Integer
                end
                class Employee < Person
                end
                """, "context Person inv TypedIterator: Employee.allInstances()->forAll(e : Person | e.age >= 0)");

        OclSemanticBinder.BoundIterator iterator = as(bound.expression(), OclSemanticBinder.BoundIterator.class);
        assertEquals("Employee", iterator.sourceCollectionType().elementType().typeName());
        assertEquals("Person", iterator.iteratorVariableType().typeName());
        OclSemanticBinder.BoundProperty age = as(
                as(iterator.body(), OclSemanticBinder.BoundBinary.class).left(),
                OclSemanticBinder.BoundProperty.class);
        assertEquals("Person", age.source().type().typeName());
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

        OclSemanticBinder.BoundCollectionOperation sequenceNotEmpty =
                as(sequenceFlatten.expression(), OclSemanticBinder.BoundCollectionOperation.class);
        OclSemanticBinder.BoundCollectionOperation sequenceFlattenOp =
                as(sequenceNotEmpty.source(), OclSemanticBinder.BoundCollectionOperation.class);
        OclSemanticBinder.BoundIterator sequenceCollect =
                as(sequenceFlattenOp.source(), OclSemanticBinder.BoundIterator.class);
        OclSemanticBinder.BoundCollectionOperation bagNotEmpty =
                as(bagFlatten.expression(), OclSemanticBinder.BoundCollectionOperation.class);
        OclSemanticBinder.BoundCollectionOperation bagFlattenOp =
                as(bagNotEmpty.source(), OclSemanticBinder.BoundCollectionOperation.class);
        OclSemanticBinder.BoundIterator bagCollect =
                as(bagFlattenOp.source(), OclSemanticBinder.BoundIterator.class);

        assertEquals(OclTypeBinding.CollectionKind.SEQUENCE, sequenceCollect.type().collectionKind());
        assertTrue(sequenceCollect.type().elementType().isCollection());
        assertEquals(OclTypeBinding.CollectionKind.BAG, bagCollect.type().collectionKind());
        assertTrue(bagCollect.type().elementType().isCollection());
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
    void normalizesCollectionValuedPropertyProjectionIntoCollectThenFlatten() {
        OclSemanticBinder.BoundContextInvariant bound = bind("""
                model Demo
                class Family
                end
                class Person
                end
                association FamilyChildren between
                    Family[*] role family
                    Person[*] role children
                end
                """, "context Family inv ChildFamiliesIncludeSelf: self.children.family->includes(self)");

        OclSemanticBinder.BoundCollectionOperation includes = as(bound.expression(), OclSemanticBinder.BoundCollectionOperation.class);
        OclSemanticBinder.BoundCollectionOperation flatten = as(includes.source(), OclSemanticBinder.BoundCollectionOperation.class);
        OclSemanticBinder.BoundIterator collect = as(flatten.source(), OclSemanticBinder.BoundIterator.class);
        OclSemanticBinder.BoundProperty family = as(collect.body(), OclSemanticBinder.BoundProperty.class);

        assertEquals("flatten", flatten.ast().opName);
        assertEquals("collect", collect.ast().operation);
        assertTrue(collect.type().elementType().isCollection());
        assertEquals(OclTypeBinding.CollectionKind.BAG, flatten.type().collectionKind());
        assertEquals("Family", flatten.type().elementType().typeName());
        assertEquals("family", family.ast().name);
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

    @Test
    void bindsLiteralQualifiedNavigation() {
        OclSemanticBinder.BoundContextInvariant bound = bind("""
                model Demo
                class Library
                end
                class Book
                end
                association Catalog between
                    Library[1] role library qualifier (shelf : String)
                    Book[*] role book
                end
                """, "context Library inv ShelfLookup: self.book['A1']->notEmpty()");

        OclSemanticBinder.BoundCollectionOperation notEmpty = as(bound.expression(), OclSemanticBinder.BoundCollectionOperation.class);
        OclSemanticBinder.BoundProperty book = as(notEmpty.source(), OclSemanticBinder.BoundProperty.class);

        assertEquals(1, book.qualifiers().size());
        assertEquals("String", book.qualifiers().get(0).type().typeName());
    }

    @Test
    void bindsVariableQualifiedNavigation() {
        OclSemanticBinder.BoundContextInvariant bound = bind("""
                model Demo
                class Library
                attributes
                    defaultShelf : String
                end
                class Book
                end
                association Catalog between
                    Library[1] role library qualifier (shelf : String)
                    Book[*] role book
                end
                """, "context Library inv ShelfLookup: let shelf = self.defaultShelf in self.book[shelf]->notEmpty()");

        OclSemanticBinder.BoundLet letExpression = as(bound.expression(), OclSemanticBinder.BoundLet.class);
        OclSemanticBinder.BoundCollectionOperation notEmpty = as(letExpression.body(), OclSemanticBinder.BoundCollectionOperation.class);
        OclSemanticBinder.BoundProperty book = as(notEmpty.source(), OclSemanticBinder.BoundProperty.class);

        assertEquals(1, book.qualifiers().size());
        assertEquals("String", book.qualifiers().get(0).type().typeName());
    }

    @Test
    void bindsComputedQualifiedNavigation() {
        OclSemanticBinder.BoundContextInvariant bound = bind("""
                model Demo
                class Library
                attributes
                    defaultShelf : String
                end
                class Book
                end
                association Catalog between
                    Library[1] role library qualifier (shelf : String)
                    Book[*] role book
                end
                """, "context Library inv ShelfLookup: self.book[self.defaultShelf.concat('')]->notEmpty()");

        OclSemanticBinder.BoundCollectionOperation notEmpty = as(bound.expression(), OclSemanticBinder.BoundCollectionOperation.class);
        OclSemanticBinder.BoundProperty book = as(notEmpty.source(), OclSemanticBinder.BoundProperty.class);

        assertEquals(1, book.qualifiers().size());
        assertEquals("String", book.qualifiers().get(0).type().typeName());
        assertTrue(book.qualifiers().get(0) instanceof OclSemanticBinder.BoundMethodCall);
    }

    @Test
    void bindsEnumLiteralQualifiedNavigation() {
        OclSemanticBinder.BoundContextInvariant bound = bind("""
                model Demo
                enum Shelf { A1, B2 }
                class Library
                end
                class Book
                end
                association Catalog between
                    Library[1] role library qualifier (shelf : Shelf)
                    Book[*] role book
                end
                """, "context Library inv ShelfLookup: self.book[Shelf::A1]->notEmpty()");

        OclSemanticBinder.BoundCollectionOperation notEmpty = as(bound.expression(), OclSemanticBinder.BoundCollectionOperation.class);
        OclSemanticBinder.BoundProperty book = as(notEmpty.source(), OclSemanticBinder.BoundProperty.class);

        assertEquals(1, book.qualifiers().size());
        assertEquals("Shelf", book.qualifiers().get(0).type().typeName());
    }

    @Test
    void bindsEnumAttributeQualifiedNavigation() {
        OclSemanticBinder.BoundContextInvariant bound = bind("""
                model Demo
                enum Shelf { A1, B2 }
                class Library
                attributes
                    defaultShelf : Shelf
                end
                class Book
                end
                association Catalog between
                    Library[1] role library qualifier (shelf : Shelf)
                    Book[*] role book
                end
                """, "context Library inv ShelfLookup: self.book[self.defaultShelf]->notEmpty()");

        OclSemanticBinder.BoundCollectionOperation notEmpty = as(bound.expression(), OclSemanticBinder.BoundCollectionOperation.class);
        OclSemanticBinder.BoundProperty book = as(notEmpty.source(), OclSemanticBinder.BoundProperty.class);

        assertEquals(1, book.qualifiers().size());
        assertEquals("Shelf", book.qualifiers().get(0).type().typeName());
        assertTrue(book.qualifiers().get(0) instanceof OclSemanticBinder.BoundProperty);
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
