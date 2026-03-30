package org.uet.dse.neo4j.ocl.value;

import org.tzi.use.uml.ocl.type.Type;
import org.tzi.use.uml.ocl.value.StringValue;
import org.tzi.use.uml.ocl.value.UndefinedValue;
import org.tzi.use.uml.ocl.value.Value;
import org.uet.dse.neo4j.mm.object.node.AttributeInstance;

public class NAttributeValue extends Value {
  public AttributeInstance attributeInstance;

  public NAttributeValue(Type t, AttributeInstance attributeInstance) {
    super(t);
    this.attributeInstance = attributeInstance;
  }

  public AttributeInstance getAttributeInstance() {
    return attributeInstance;
  }

  public void setAttributeInstance(AttributeInstance attributeInstance) {
    this.attributeInstance = attributeInstance;
  }

  @Override
  public StringBuilder toString(StringBuilder sb) {
    return null;
  }

  @Override
  public int hashCode() {
    return 0;
  }

  @Override
  public boolean equals(Object obj) {

    if (obj instanceof NAttributeValue) {
      return ((NAttributeValue) obj).attributeInstance.getName().equals(this.attributeInstance.getName())
          && ((NAttributeValue) obj).attributeInstance.getValue().equals(this.attributeInstance.getValue());
    } else if (obj instanceof UndefinedValue) {
      return this.attributeInstance.getValue() == null || this.attributeInstance.getValue().isEmpty();
    }
    return ((StringValue) obj).value().toString().equals(this.attributeInstance.getValue());
  }

  @Override
  public int compareTo(Value o) {
    return 0;
  }
}
