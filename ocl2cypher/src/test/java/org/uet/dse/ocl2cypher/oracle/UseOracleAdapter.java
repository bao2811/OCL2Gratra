package org.uet.dse.ocl2cypher.oracle;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.tzi.use.parser.shell.ShellCommandCompiler;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.expr.Evaluator;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.ocl.value.VarBindings;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.uml.sys.MSystemException;
import org.tzi.use.uml.sys.soil.MStatement;

/**
 * Test-only adapter to the USE reference implementation of UML/OCL.
 *
 * <p>The adapter deliberately shares no parser, AST, lowering, interpreter, or
 * graph code with {@code ocl2cypher}. It compiles a USE model, executes the
 * supplied SOIL snapshot, and evaluates each invariant body with USE's own
 * evaluator. Object names are returned as application-level stable IDs because
 * the fixtures create every object with an explicit, unique SOIL name.</p>
 *
 * <p>The validation observation matches the paper's contract: an object is a
 * violation when its invariant body is not {@code true}. Consequently USE's
 * undefined value is reported as a violation, alongside Boolean false.</p>
 */
public final class UseOracleAdapter {

    private UseOracleAdapter() {
    }

    /** Evaluate all invariants in {@code invariantFile} on the SOIL snapshot. */
    public static Result evaluate(Path modelFile, Path invariantFile, Path soilFile)
            throws Exception {
        String modelText = Files.readString(modelFile, StandardCharsets.UTF_8);
        String invariantText = Files.readString(invariantFile, StandardCharsets.UTF_8);
        // A second constraints section is legal USE syntax and also works when
        // the schema already ends with an empty constraints section.
        String completeSpecification = modelText + System.lineSeparator()
                + "constraints" + System.lineSeparator() + invariantText;

        MModel model = compileModel(completeSpecification, modelFile, invariantFile);
        MSystem system = new MSystem(model);
        replaySoil(system, soilFile);

        Map<String, Set<String>> violations = new LinkedHashMap<>();
        int objectEvaluations = 0;
        for (MClassInvariant invariant : model.classInvariants()) {
            Set<String> ids = new LinkedHashSet<>();
            Set<MObject> contextObjects = system.state()
                    .objectsOfClassAndSubClasses(invariant.cls());
            for (MObject object : contextObjects) {
                objectEvaluations++;
                VarBindings bindings = new VarBindings(system.varBindings());
                bindings.push("self", object.value());
                Value value = new Evaluator().eval(
                        invariant.bodyExpression(), system.state(), bindings);
                if (value.isUndefined()) {
                    ids.add(object.name());
                } else if (value instanceof BooleanValue booleanValue) {
                    if (!booleanValue.value()) {
                        ids.add(object.name());
                    }
                } else {
                    throw new IllegalStateException("USE invariant "
                            + invariant.qualifiedName() + " returned non-Boolean value "
                            + value + " : " + value.type());
                }
            }
            Set<String> previous = violations.put(invariant.qualifiedName(),
                    Collections.unmodifiableSet(ids));
            if (previous != null) {
                throw new IllegalStateException("duplicate USE invariant key: "
                        + invariant.qualifiedName());
            }
        }
        return new Result(Collections.unmodifiableMap(violations), objectEvaluations);
    }

    private static MModel compileModel(String source, Path modelFile, Path invariantFile) {
        StringWriter diagnostics = new StringWriter();
        MModel model = USECompiler.compileSpecification(source,
                modelFile + "+" + invariantFile,
                new PrintWriter(diagnostics, true), new ModelFactory());
        if (model == null) {
            throw new IllegalArgumentException("USE specification compilation failed:\n"
                    + diagnostics);
        }
        return model;
    }

    private static void replaySoil(MSystem system, Path soilFile) throws Exception {
        int lineNumber = 0;
        for (String rawLine : Files.readAllLines(soilFile, StandardCharsets.UTF_8)) {
            lineNumber++;
            String line = rawLine.strip();
            if (line.isEmpty() || line.startsWith("--")) {
                continue;
            }
            if (!line.startsWith("!")) {
                throw new IllegalArgumentException(soilFile + ":" + lineNumber
                        + ": expected a single-line SOIL command beginning with '!'");
            }
            String command = line.substring(line.startsWith("!!") ? 2 : 1).strip();
            StringWriter diagnostics = new StringWriter();
            MStatement statement = ShellCommandCompiler.compileShellCommand(
                    system.model(), system.state(), system.getVariableEnvironment(),
                    command, soilFile + ":" + lineNumber,
                    new PrintWriter(diagnostics, true), false);
            if (statement == null) {
                throw new IllegalArgumentException(soilFile + ":" + lineNumber
                        + ": USE rejected SOIL command `" + command + "`:\n"
                        + diagnostics);
            }
            try {
                system.execute(statement);
            } catch (MSystemException exception) {
                throw new IllegalArgumentException(soilFile + ":" + lineNumber
                        + ": USE failed to execute SOIL command `" + command + "`",
                        exception);
            }
        }
    }

    /** Immutable result from an independent USE evaluation run. */
    public record Result(Map<String, Set<String>> violations, int objectEvaluations) {
    }
}
