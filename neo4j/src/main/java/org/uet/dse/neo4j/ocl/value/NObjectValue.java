package org.uet.dse.neo4j.ocl.value;

import org.tzi.use.uml.ocl.type.Type;
import org.tzi.use.uml.ocl.type.TypeFactory;
import org.tzi.use.uml.ocl.value.UndefinedValue;
import org.tzi.use.uml.ocl.value.Value;
import org.uet.dse.neo4j.mm.object.node.DomainObject;

public class NObjectValue extends Value {

  public DomainObject domainObject;
  public NObjectValue(Type t) {
    super(t);
  }


  private Type fType;

  @Override
  public Type type() {
    return fType;
  }

  public NObjectValue(Type t, DomainObject domainObject) {
    super(t);
    this.fType = t;
    this.domainObject = domainObject;

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
    String fir_id = this.domainObject.getId();

    if (obj instanceof NObjectValue) {
      return ((NObjectValue) obj).domainObject.getId().equals(this.domainObject.getId());
    } else if (obj instanceof UndefinedValue) {
      return false;
    }
    return ((NObjectValue) obj).domainObject.getId().equals(this.domainObject.getId());
  }

  @Override
  public int compareTo(Value o) {
    return 0;
  }
}
