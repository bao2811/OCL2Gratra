package org.uet.dse.neo4j.ocl.expr;

import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.ocl.expr.ExpAttrOp;
import org.tzi.use.uml.ocl.expr.ExpressionVisitor;
import org.tzi.use.uml.ocl.type.Type;
import org.tzi.use.uml.ocl.value.InstanceValue;
import org.tzi.use.uml.ocl.value.UndefinedValue;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.*;
import org.tzi.use.util.collections.CollectionUtil;
import org.uet.dse.neo4j.mm.object.node.AttributeInstance;
import org.uet.dse.neo4j.mm.object.node.DomainObject;
import org.uet.dse.neo4j.ocl.NEvalContext;
import org.uet.dse.neo4j.ocl.type.NAttributeType;
import org.uet.dse.neo4j.ocl.type.NObjectType;
import org.uet.dse.neo4j.ocl.value.NAttributeValue;
import org.uet.dse.neo4j.ocl.value.NObjectValue;

import java.util.List;

/**
 * Attribute operation on objects.
 *
 * @author Mark Richters
 * @author Lars Hamann
 */
public final class NExpAttrOp extends NExpression {
  private final ExpAttrOp wrapped;
  private MAttribute fAttr;

  //business
  private AttributeInstance nAttr;

  //owner object exp: b2
  public NExpression fObjExp;

  public NExpAttrOp(ExpAttrOp wrapped) {
    super();
    this.wrapped = wrapped;
    this.fAttr = wrapped.attr();

    //not yet needed
    //this.nAttr = new AttributeInstance();

  }

  public NExpAttrOp(MAttribute a, NExpression objExp, ExpAttrOp wrapped) {
    super();
    this.wrapped = wrapped;
    fAttr = a;
    fObjExp = objExp;
    if (!(objExp.type().isTypeOfClass() || objExp.type().isTypeOfDataType()))
      throw new IllegalArgumentException("Target expression of attribute operation must have " + "object type, found `" + objExp.type() + "'.");
  }

  public MAttribute attr() {
    return fAttr;
  }

  public NExpression objExp() {
    return fObjExp;
  }

  /**
   * Evaluates expression and returns result value.
   */
  public Value nEval(NEvalContext ctx) {
    ctx.enter(this);
    Value res = UndefinedValue.instance;

    //retrive actual object
    Value val = fObjExp.nEval(ctx);

    // if we don't have an object we can't deliver an attribute value
    //if object != null
    if (!val.isUndefined()) {
      NObjectValue objState = (NObjectValue) val;
      DomainObject domainObject = objState.domainObject;
      List<AttributeInstance> attrList = domainObject.getAttributes();

      if (fAttr.isDerived()) {
        System.out.println("derived attribute not yet implemented");
      } else {
        //should have pre/post features here, but not yet for this phase

        // if the object is dead the result is undefined
        if (attrList != null && !attrList.isEmpty())
          for (AttributeInstance a : attrList) {
            if (a.getName().equals(fAttr.name())) {
              NAttributeValue attributeValue = new NAttributeValue(fAttr.type(), a);
              res = attributeValue;
              return res;
            }
          }
      }
    }
    ctx.exit(this, res);
    return res;
  }

  @Override
  public StringBuilder toString(StringBuilder sb) {
//    fObjExp.toString(sb);
//    sb.append(".");
//    sb.append(fAttr.name());
//    return sb.append(atPre());
    return new StringBuilder(sb.toString());
  }

  @Override
  public void processWithVisitor(ExpressionVisitor visitor) {

  }
}

