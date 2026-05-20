package org.uet.dse.neo4jtgg.service.impl;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.neo4j.driver.Record;
import org.neo4j.driver.types.Node;
import org.tzi.use.uml.mm.MModel;
import org.uet.dse.neo4j.OCLLexer;
import org.uet.dse.neo4j.OCLParser;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.uet.dse.neo4j.oclite.Neo4jRepository;
import org.uet.dse.neo4j.oclite.ast.ASTContext;
import org.uet.dse.neo4j.oclite.ast.ASTNode;
import org.uet.dse.neo4j.oclite.ast.ASTVisitor;
import org.uet.dse.neo4j.oclite.expr.ExecutionContext;
import org.uet.dse.neo4j.oclite.expr.ExpressionBinder;
import org.uet.dse.neo4j.oclite.expr.ExpressionNode;
import org.uet.dse.neo4jtgg.model.CypherCompilationResult;
import org.uet.dse.neo4jtgg.model.OclValidationResult;
import org.uet.dse.neo4jtgg.model.TggWorkspaceContext;
import org.uet.dse.neo4jtgg.model.WorkspaceSide;
import org.uet.dse.neo4jtgg.service.OclValidationService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DefaultOclValidationService implements OclValidationService {

    @Override
    public OclValidationResult validate(TggWorkspaceContext context, WorkspaceSide side, String oclExpression) {
        if (Neo4jDriverManager.getInstance() == null || !Neo4jDriverManager.getInstance().isConnected()) {
            return new OclValidationResult(false, "Neo4j is not connected.", "", false, Map.of());
        }

        MModel model = context.getWorkspaceDefinition() != null ? context.getWorkspaceDefinition().getModel(side) : null;
        if (model == null) {
            return new OclValidationResult(false,
                    "No Neo4j-backed model metadata is available for " + side.getDisplayName() + ".",
                    "", false, Map.of());
        }
        DefaultOclToCypherCompiler compiler = new DefaultOclToCypherCompiler(model);
        CypherCompilationResult compilation = compiler.compile(oclExpression);

        try {
            ASTNode ast = new ASTVisitor().visit(new OCLParser(new CommonTokenStream(new OCLLexer(CharStreams.fromString(oclExpression)))).oclFile());
            if (ast instanceof ASTContext astContext && compilation.isSupported()) {
                return validateContextWithCypher(model, astContext, compilation);
            }
            return fallbackEvaluation(model, ast, compilation.getCypher(), compilation.isSupported());
        } catch (Exception ex) {
            return new OclValidationResult(false, "Validation failed: " + ex.getMessage(),
                    compilation.getCypher(), !compilation.isSupported(), Map.of());
        }
    }

    private OclValidationResult validateContextWithCypher(MModel model, ASTContext astContext, CypherCompilationResult compilation) {
        Map<String, String> violations = new LinkedHashMap<>();
        try (org.neo4j.driver.Session neo4jSession = Neo4jDriverManager.getInstance().openSession()) {
            List<Record> records = neo4jSession.run(compilation.getCypher(), compilation.getParameters()).list();
            for (Record record : records) {
                String useId = record.get("useId").asString();
                violations.put(useId, "Invariant `" + astContext.invName + "` violated");
            }
        }

        String summary = violations.isEmpty()
                ? "SUCCESS: invariant `" + astContext.invName + "` passed on Neo4j."
                : "FAILURE: invariant `" + astContext.invName + "` violated for " + violations.size() + " object(s).";
        return new OclValidationResult(violations.isEmpty(), summary, compilation.getCypher(), false, violations);
    }

    private OclValidationResult fallbackEvaluation(MModel model, ASTNode ast, String generatedCypher, boolean compilerSupported) {
        String modelName = model.name();
        ExpressionBinder binder = new ExpressionBinder(modelName);

        if (ast instanceof ASTContext astContext) {
            Map<String, String> violations = new LinkedHashMap<>();
            ExpressionNode expressionNode = binder.bind(astContext.expression);
            Neo4jRepository repository = new Neo4jRepository(modelName);
            for (Node node : repository.findAllInstancesOfClass(astContext.className)) {
                ExecutionContext executionContext = new ExecutionContext(modelName);
                executionContext.pushScope("self", node);
                executionContext.setVariable("self", node);
                Object result = expressionNode.evaluate(executionContext);
                if (!(result instanceof Boolean ok) || !ok) {
                    String useId = node.containsKey("use_id") ? node.get("use_id").asString() : String.valueOf(node.id());
                    violations.put(useId, "Fallback evaluation reported false");
                }
            }

            String summary = violations.isEmpty()
                    ? "SUCCESS: fallback evaluator passed on Neo4j."
                    : "FAILURE: fallback evaluator found " + violations.size() + " violation(s).";
            return new OclValidationResult(violations.isEmpty(), summary, generatedCypher, true, violations);
        }

        ExpressionNode expressionNode = binder.bind(ast);
        Object result = expressionNode.evaluate(new ExecutionContext(modelName));
        String summary = "Evaluation result: " + result;
        boolean success = result instanceof Boolean ok ? ok : result != null;
        return new OclValidationResult(success, summary, generatedCypher, !compilerSupported, Map.of());
    }
}
