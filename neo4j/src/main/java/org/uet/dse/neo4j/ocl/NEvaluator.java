package org.uet.dse.neo4j.ocl;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.MSystemState;
import org.uet.dse.neo4j.ocl.expr.NExpression;

public final class NEvaluator implements IOCLEvaluator {
    private NEvalContext nEvalContext;
    public NEvaluator() {
    }

    public Value eval(MSystemState currentState, NExpression nExpression) {

        EvaluationResult res = evaluate(currentState, nExpression);
        System.out.println(res.toString());
        return res.fValue;

    }


    public EvaluationResult evaluate(MSystemState currentState, NExpression nExpression) {
        NEvalContext simpleEvalContext = new NSimpleEvalContext(currentState);

        Value fValue = nExpression.nEval(simpleEvalContext);
        return EvaluationResult.fValue(fValue);
    }


    @Override
    public EvaluationResult evaluate(String expression) {
        return null;
    }
}