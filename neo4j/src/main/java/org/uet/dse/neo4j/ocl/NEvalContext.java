package org.uet.dse.neo4j.ocl;

import org.neo4j.driver.*;
import org.tzi.use.uml.ocl.expr.EvalContext;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uml.ocl.type.Type;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.ocl.value.VarBindings;
import org.tzi.use.uml.sys.MSystemState;
import org.tzi.use.util.Log;
import org.uet.dse.neo4j.mm.object.node.DomainObject;
import org.uet.dse.neo4j.ocl.expr.NExpression;
import org.uet.dse.neo4j.ocl.type.NObjectType;
import org.uet.dse.neo4j.ocl.value.NObjectValue;
import org.uet.dse.neo4j.repo.service.DomainObjectService;

import java.io.PrintWriter;
import java.util.*;

public class NEvalContext {

  //this version prioritize on invariant, not pre/post yet
  //private final MSystemState fPreState; // required for postconditions
  private final MSystemState currentState; // default state
  private final VarBindings fVarBindings;
  private int fNesting;   // for indentation during trace
  private DomainObjectService domainObjectService = new DomainObjectService();

  public NEvalContext(MSystemState currentState, VarBindings globalBindings) {
    this.currentState = currentState;
    fVarBindings = new VarBindings(globalBindings);
    fNesting = 0;
  }

  public NEvalContext(MSystemState currentState) {
    this.currentState = currentState;
    fVarBindings = null;
    fNesting = 0;
  }


  /**
   * Pushes a new variable binding onto the binding stack.
   */
  public void pushVarBinding(String varname, org.tzi.use.uml.ocl.value.Value value) {
    fVarBindings.push(varname, value);
  }

  /**
   * Pops the last added variable binding from the binding stack.
   */
  void popVarBinding() {
    fVarBindings.pop();
  }

  /**
   * Pops the last numToPop added variable bindings from the binding stack
   */
  void popVarBindings(int numToPop) {
    for (int i = 0; i < numToPop; ++i) {
      popVarBinding();
    }
  }

  /**
   * Returns current state of variable bindings (for debugging).
   */
  public VarBindings varBindings() {
    return fVarBindings;
  }

  /**
   * Search current bindings for variable name. Visibility is determined by the order of elements. Variable bindings may thus be hidden by bindings at earlier positions.
   *
   * @return value for name binding or null if not bound
   */
  public org.tzi.use.uml.ocl.value.Value getVarValue(String name) {
    DomainObject domainObject = domainObjectService.getObject(name);
    Type t = new NObjectType(domainObject.getClassName());
    return new NObjectValue(t, domainObjectService.getObject(name));
  }

  public void enter(NExpression expr) {
      ++fNesting;
      String ec = expr.getClass().getName();
      ec = ec.substring(ec.lastIndexOf(".") + 1);
      Log.trace(this, indent() + "enter " + ec + " \"" + expr + "\"");
  }

  public void exit(NExpression expr, Value result) {
      --fNesting;
      Log.trace(this, indent() + "exit  \"" + expr + "\" = " + result);
  }

  private String indent() {
    char[] indent = new char[fNesting];
    for (int i = 0; i < fNesting; i++)
      indent[i] = ' ';
    return new String(indent);
  }

}