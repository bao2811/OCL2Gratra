package org.uet.dse.neo4j.oclite.expr;

import java.util.Objects;

public class BinaryExpression implements ExpressionNode {
    private final ExpressionNode left, right;
    private final String op;

    public BinaryExpression(ExpressionNode l, String op, ExpressionNode r) {
        this.left = l;
        this.op = op;
        this.right = r;
    }

    @Override
    public Object evaluate(ExecutionContext ctx) {
        Object lVal = left.evaluate(ctx);
        Object rVal = right.evaluate(ctx);

        if (op.equals("=") || op.equals("<>")) {
            boolean isEqual = compareEquals(lVal, rVal);
            return op.equals("=") ? isEqual : !isEqual;
        }

        if (op.equals(">") || op.equals("<") || op.equals(">=") || op.equals("<=")) {
            return compareNumbers(lVal, rVal, op);
        }

        if (op.equals("+") || op.equals("-") || op.equals("*") || op.equals("/")) {
            return calculateNumbers(lVal, rVal, op);
        }

        if (op.equals("and") || op.equals("or") || op.equals("implies")) {
            return evaluateLogic(lVal, rVal, op);
        }

        return null;
    }

    private boolean compareEquals(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;

        if (a instanceof Number && b instanceof Number) {
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue()) == 0;
        }
        return Objects.equals(a, b);
    }

    private Object compareNumbers(Object a, Object b, String operator) {
        if (!(a instanceof Number) || !(b instanceof Number)) return false;

        double n1 = ((Number) a).doubleValue();
        double n2 = ((Number) b).doubleValue();

        switch (operator) {
            case ">":  return n1 > n2;
            case "<":  return n1 < n2;
            case ">=": return n1 >= n2;
            case "<=": return n1 <= n2;
        }
        return false;
    }

    private Object calculateNumbers(Object a, Object b, String operator) {
        if (!(a instanceof Number) || !(b instanceof Number)) return null;

        double n1 = ((Number) a).doubleValue();
        double n2 = ((Number) b).doubleValue();

        switch (operator) {
            case "+": return n1 + n2;
            case "-": return n1 - n2;
            case "*": return n1 * n2;
            case "/":
                if (n2 == 0) throw new ArithmeticException("Lỗi chia cho 0");
                return n1 / n2;
        }
        return null;
    }

    private Object evaluateLogic(Object a, Object b, String operator) {
        Boolean b1 = (a instanceof Boolean) ? (Boolean) a : false;
        Boolean b2 = (b instanceof Boolean) ? (Boolean) b : false;

      return switch (op) {
        case "and" -> b1 && b2;
        case "or" -> b1 || b2;
        case "implies" ->
          //A implies B  <=>  !A or B
            !b1 || b2;
        default -> false;
      };

    }
}