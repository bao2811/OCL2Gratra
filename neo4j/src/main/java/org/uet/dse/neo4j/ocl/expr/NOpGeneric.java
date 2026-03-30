package org.uet.dse.neo4j.ocl.expr;

import com.google.common.collect.Multimap;
import org.tzi.use.uml.ocl.type.Type;
import org.tzi.use.uml.ocl.value.Value;
import org.uet.dse.neo4j.ocl.NEvalContext;

public abstract class NOpGeneric {
  // These constants define different groups of operations. The
  // groups mainly differ wrt their behavior in case of undefined
  // arguments. The effects of passing any undefined argument to an
  // operation are as follows:
  //
  // OPERATION -> UndefinedValue(T) with T being the result type
  // of the operation
  // PREDICATE -> BooleanValue(false)
  // SPECIAL -> operation needs special treatment of undefined arguments
  public static final int OPERATION = 0;

  public static final int SPECIAL = 3;

  public abstract String name();

  public boolean isBooleanOperation() {
    return false;
  }

  public abstract int kind();

  public abstract boolean isInfixOrPrefix();

  public abstract Type matches(Type params[]);

  public String checkWarningUnrelatedTypes(NExpression args[]) {
    return null;
  }

  public abstract Value nEval(NEvalContext ctx, Value args[], Type resultType);

  public String stringRep(NExpression args[], String atPre) {
    //        String res;
    //        if (isInfixOrPrefix()) {
    //            if (args.length == 1) {
    //                // e.g. `not true', -2, +3
    //                // insert blank between operator and expression to
    //                // avoid `--' which would be interpreted as comment
    //                res = name() + " " + args[0];
    //            } else
    //                // e.g. `3 + 4'
    //                res = "(" + StringUtil.fmtSeq(args, " " + name() + " ") + ")";
    //        } else {
    //            // translate into dot notation, e.g. foo->union(bla)
    //            res = name() + atPre;
    //            if (args.length > 0) {
    //                if (args[0].type().isKindOfCollection(VoidHandling.EXCLUDE_VOID))
    //                    res = args[0] + "->" + res;
    //                else
    //                    res = args[0] + "." + res;
    //                if (args.length > 1)
    //                    res += "(" + StringUtil.fmtSeq(args, 1, ",") + ")";
    //            }
    //        }
    //        return res;
    return null;
  }

  public static void registerOperations(Multimap<String, NOpGeneric> opmap) {
    //		// Basic operations
    //		StandardOperationsAny.registerTypeOperations(opmap);
    //		StandardOperationsObject.registerTypeOperations(opmap);
    //
    //		StandardOperationsEnum.registerTypeOperations(opmap);
    //
    //		// Basic types
    //		StandardOperationsNumber.registerTypeOperations(opmap);
    //		StandardOperationsString.registerTypeOperations(opmap);
    //		StandardOperationsBoolean.registerTypeOperations(opmap);
    //
    //		// Collections
    //		StandardOperationsCollection.registerTypeOperations(opmap);
    //		StandardOperationsSet.registerTypeOperations(opmap);
    //		StandardOperationsBag.registerTypeOperations(opmap);
    //		StandardOperationsSequence.registerTypeOperations(opmap);
    //		StandardOperationsOrderedSet.registerTypeOperations(opmap);
  }

  /**
   * Puts an operation into the given MultiMap
   *
   * @param op    The operation to register
   * @param opmap The multi map holding the operations
   */
  public static void registerOperation(NOpGeneric op, Multimap<String, NOpGeneric> opmap) {
    opmap.put(op.name(), op);
  }

  /**
   * Puts an operation into the given MultiMap under the given name
   *
   * @param name  The name under which the operation is referred to
   * @param op    The operation to register
   * @param opmap The multi map holding the operations
   */
  public static void registerOperation(String name, NOpGeneric op, Multimap<String, NOpGeneric> opmap) {
    opmap.put(name, op);
  }
}