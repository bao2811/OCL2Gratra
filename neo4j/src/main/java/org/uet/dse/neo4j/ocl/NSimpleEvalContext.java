package org.uet.dse.neo4j.ocl;

import org.neo4j.driver.Session;
import org.tzi.use.uml.sys.MSystemState;

public final class NSimpleEvalContext extends NEvalContext {
  public NSimpleEvalContext(MSystemState currentState) {
    super(currentState);
  }
}
