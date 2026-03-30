package org.uet.dse.neo4j.mm.object.node;

import org.uet.dse.neo4j.mm.core.common.NCollectionType;
import org.uet.dse.neo4j.mm.core.common.NType;
import org.uet.dse.neo4j.mm.object.common.AttributeType;

import java.util.List;

public final class AttributeInstance {

  private final String name;
  private final NType type;
  private final boolean isCollection;
  private final boolean isNestedCollection;
  private final NCollectionType collectionType;
  private final String value;
  private final List<NestedNode> nestedStructure;

  public AttributeInstance(String name, NType type, boolean isCollection, boolean isNestedCollection, NCollectionType collectionType, String value, List<NestedNode> nestedStructure) {
    this.name = name;
    this.type = type;
    this.isCollection = isCollection;
    this.isNestedCollection = isNestedCollection;
    this.collectionType = collectionType;
    this.value = value;
    this.nestedStructure = nestedStructure;
  }

  public Object getResolvedValue() {
    if (value == null) return null;

    switch (type) {
      case Boolean:
        return Boolean.parseBoolean(value);

      case Int:
        return Integer.parseInt(value);

      case Float:
        return Float.parseFloat(value);

      case Double:
        return Double.parseDouble(value);

      case String:
        return value;

      case Void:
      case None:
        return null;

      case Object:
      default:
        return value; // hoặc resolve entity nếu có context
    }
  }


  public String getValue() {
    return this.value;
  }

  public String getName() {
    if (name == null) return null;

    int idx = name.lastIndexOf("_");
    if (idx == -1) return name;

    return name.substring(idx + 1);
  }


  public NType getType() {
    return type;
  }

  public boolean isCollection() {
    return isCollection;
  }

  public boolean isNestedCollection() {
    return isNestedCollection;
  }

  public NCollectionType getCollectionType() {
    return collectionType;
  }

  public List<NestedNode> getNestedStructure() {
    return nestedStructure;
  }
}
