package org.uet.dse.neo4j.ocl.type;

import org.tzi.use.uml.ocl.type.Type;

import java.util.Set;

public class NObjectType implements Type {
  public String fName;

  public NObjectType(String name) {
    this.fName = name;
    //should resolve conforms here
  }

  @Override
  public String shortName() {
    return this.fName;
  }

  //is having parent class
  @Override
  public boolean conformsTo(Type other) {
    if (other instanceof NObjectType) {
      return this.fName.equals(((NObjectType) other).fName);
    }
    if (this.fName.equals(other.shortName())) {
      return true;
    }
    return false;
  }

  @Override
  public Set<? extends Type> allSupertypes() {
    return Set.of();
  }

  @Override
  public Type getLeastCommonSupertype(Type other) {
    return null;
  }

  @Override
  public boolean isVoidOrElementTypeIsVoid() {
    return false;
  }

  @Override
  public boolean isKindOfNumber(VoidHandling h) {
    return false;
  }

  @Override
  public boolean isTypeOfInteger() {
    return false;
  }

  @Override
  public boolean isKindOfInteger(VoidHandling h) {
    return false;
  }

  @Override
  public boolean isTypeOfUnlimitedNatural() {
    return false;
  }

  @Override
  public boolean isKindOfUnlimitedNatural(VoidHandling h) {
    return false;
  }

  @Override
  public boolean isKindOfReal(VoidHandling h) {
    return false;
  }

  @Override
  public boolean isTypeOfReal() {
    return false;
  }

  @Override
  public boolean isKindOfString(VoidHandling h) {
    return false;
  }

  @Override
  public boolean isTypeOfString() {
    return false;
  }

  @Override
  public boolean isKindOfBoolean(VoidHandling h) {
    return false;
  }

  @Override
  public boolean isTypeOfBoolean() {
    return false;
  }

  @Override
  public boolean isKindOfEnum(VoidHandling h) {
    return false;
  }

  @Override
  public boolean isTypeOfEnum() {
    return false;
  }

  @Override
  public boolean isKindOfCollection(VoidHandling h) {
    return false;
  }

  @Override
  public boolean isTypeOfCollection() {
    return false;
  }

  @Override
  public boolean isKindOfSet(VoidHandling h) {
    return false;
  }

  @Override
  public boolean isTypeOfSet() {
    return false;
  }

  @Override
  public boolean isKindOfSequence(VoidHandling h) {
    return false;
  }

  @Override
  public boolean isTypeOfSequence() {
    return false;
  }

  @Override
  public boolean isKindOfOrderedSet(VoidHandling h) {
    return false;
  }

  @Override
  public boolean isTypeOfOrderedSet() {
    return false;
  }

  @Override
  public boolean isKindOfBag(VoidHandling h) {
    return false;
  }

  @Override
  public boolean isTypeOfBag() {
    return false;
  }

  @Override
  public boolean isKindOfClassifier(VoidHandling h) {
    return false;
  }

  @Override
  public boolean isTypeOfClassifier() {
    return false;
  }

  @Override
  public boolean isKindOfClass(VoidHandling h) {
    return true;
  }

  @Override
  public boolean isTypeOfClass() {
    return false;
  }

  @Override
  public boolean isKindOfDataType(VoidHandling h) {
    return false;
  }

  @Override
  public boolean isTypeOfDataType() {
    return false;
  }

  @Override
  public boolean isKindOfAssociation(VoidHandling h) {
    return false;
  }

  @Override
  public boolean isTypeOfAssociation() {
    return false;
  }

  @Override
  public boolean isKindOfOclAny(VoidHandling h) {
    return false;
  }

  @Override
  public boolean isTypeOfOclAny() {
    return false;
  }

  @Override
  public boolean isKindOfTupleType(VoidHandling h) {
    return false;
  }

  @Override
  public boolean isTypeOfTupleType() {
    return false;
  }

  @Override
  public boolean isTypeOfVoidType() {
    return false;
  }

  @Override
  public boolean isInstantiableCollection() {
    return false;
  }

  @Override
  public StringBuilder toString(StringBuilder sb) {
    return null;
  }
}
