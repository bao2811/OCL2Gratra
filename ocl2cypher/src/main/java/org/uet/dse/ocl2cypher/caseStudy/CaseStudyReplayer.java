package org.uet.dse.ocl2cypher.caseStudy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.runtime.OclValue;
import org.uet.dse.ocl2cypher.api.CoreOracle;
import org.uet.dse.ocl2cypher.api.FrontendCompiler;
import org.uet.dse.ocl2cypher.source.model.SchemaModel;
import org.uet.dse.ocl2cypher.source.model.Snapshot;

/**
 * Minimum replayer for the project case studies whose constraints live in the
 * examples tree (invariants OCL files) and whose snapshots live in the matching
 * soil files.
 *
 * <p>The corpus there is more than a tag layout: a single invariant whose body
 * is a predicate-bottom must remain indistinguishable from a missing source,
 * and the short-circuit for a Boolean connective has to know where on the row
 * its alias became whole bottom.
 */
public final class CaseStudyReplayer {

    private CaseStudyReplayer() {
    }

    record Study(SchemaModel schema, Snapshot snapshot, List<Entry> entries) {
    }

    record Entry(String name, String oclText, Set<String> expected,
                 Set<String> actual, String rejectionCode) {
        boolean admitted() { return rejectionCode == null; }
    }

    static Study loadCarRental(Path soil, Path oclFile) throws IOException {
        var fixture = CarRentalFixture.read(soil.resolveSibling("carrentalmodel.use"), soil);
        var executable = fixture.toExecutable();
        List<Spec> specs = loadInvariants(oclFile);
        Map<String, Set<String>> expected = loadExpected(
                oclFile.resolveSibling("expected-violations-extended.csv"));
        List<Entry> entries = new ArrayList<>();
        for (Spec spec : specs) {
            String key = spec.context() + "::" + spec.name();
            Set<String> expectedIds = expected.get(key);
            if (expectedIds == null) {
                throw new IOException("Missing expected violation row: " + key);
            }
            var frontend = FrontendCompiler.compile(spec.source(), executable.schema());
            if (frontend.isFailure()) {
                entries.add(new Entry(key, spec.source(), expectedIds, Set.of(),
                        frontend.primaryDiagnostic().code()));
                continue;
            }
            try {
                Set<String> actual = Set.copyOf(CoreOracle.violationsOclEq(
                        spec.source(), executable.schema(), executable.snapshot()));
                entries.add(new Entry(key, spec.source(), expectedIds, actual, null));
            } catch (RuntimeException error) {
                throw new IOException("Replay failed for " + key + ": " + error.getMessage(), error);
            }
        }
        if (entries.size() != expected.size()) {
            throw new IOException("Invariant/expectation cardinality mismatch: "
                    + entries.size() + " vs " + expected.size());
        }
        return new Study(executable.schema(), executable.snapshot(), entries);
    }

    private static Map<String, Set<String>> loadExpected(Path csv) throws IOException {
        List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !lines.get(0).equals("invariant,ids,classification")) {
            throw new IOException("Invalid expected-violations header: " + csv);
        }
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] fields = line.split(",", -1);
            if (fields.length != 3 || fields[0].isBlank()) {
                throw new IOException("Invalid expected-violations row: " + line);
            }
            Set<String> ids = fields[1].isBlank()
                    ? Set.of() : Set.of(fields[1].split(";"));
            if (result.putIfAbsent(fields[0], ids) != null) {
                throw new IOException("Duplicate expected-violations row: " + fields[0]);
            }
        }
        return result;
    }

    // ---- OCL file ------------------------------------------------------

    record Spec(String context, String name, String body) {
        String source() { return "context " + context + " inv " + name + ": " + body; }
    }

    static List<Spec> loadInvariants(Path p) throws IOException {
        String text = Files.readString(p, StandardCharsets.UTF_8);
        // `context C inv Name: body` where body is up to the next context or EOF.
        List<Spec> out = new ArrayList<>();
        String[] lines = text.split("\\R");
        String curCtx = null;
        String curName = null;
        StringBuilder curBody = null;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("--")) {
                continue;
            }
            if (line.startsWith("context ")) {
                if (curBody != null) {
                    out.add(new Spec(curCtx, curName, curBody.toString().trim()));
                    curBody = null;
                }
                Matcher header = Pattern.compile("context\\s+(\\w+)(?:\\s+(inv\\s+.*))?").matcher(line);
                if (!header.matches()) throw new IOException("Unsupported context header: " + line);
                curCtx = header.group(1);
                if (header.group(2) == null) continue;
                line = header.group(2);
            }
            if (line.startsWith("inv ")) {
                if (curBody != null) {
                    out.add(new Spec(curCtx, curName, curBody.toString().trim()));
                }
                int colon = line.indexOf(':');
                if (curCtx == null || colon < 0)
                    throw new IOException("Invariant requires context and colon: " + line);
                String after = colon == -1 ? "" : line.substring(colon + 1).trim();
                String invPart = line.substring(0, colon == -1 ? line.length() : colon).trim();
                curName = invPart.replaceFirst("inv\\s*", "")
                        .replace(":", "").trim();
                curBody = new StringBuilder(after);
                continue;
            }
            if (curBody != null) {
                if (curBody.length() > 0) {
                    curBody.append(' ');
                }
                curBody.append(line);
            }
        }
        if (curBody != null) {
            out.add(new Spec(curCtx, curName, curBody.toString().trim()));
        }
        Set<String> keys = new java.util.HashSet<>();
        for (Spec spec : out) {
            if (spec.name.isBlank() || spec.body.isBlank()
                    || !keys.add(spec.context + "::" + spec.name))
                throw new IOException("Empty or duplicate invariant: " + spec.context + "::" + spec.name);
        }
        return List.copyOf(out);
    }

    /** Strict scalar literal subset; unsupported SOIL values are never fabricated. */
    static OclValue decodeScalar(String raw, OclType type) throws IOException {
        String token = raw.trim();
        if (type.equals(OclType.BOOLEAN)) {
            if (token.equals("true")) return org.uet.dse.ocl2cypher.runtime.Boolean3.TRUE;
            if (token.equals("false")) return org.uet.dse.ocl2cypher.runtime.Boolean3.FALSE;
        } else if (type.equals(OclType.INTEGER) && token.matches("[+-]?[0-9]+")) {
            return new OclValue.IntegerValue(new java.math.BigInteger(token));
        } else if (type.equals(OclType.REAL) && token.matches("[+-]?[0-9]+(?:\\.[0-9]+)?")) {
            return new OclValue.RealValue(new java.math.BigDecimal(token));
        } else if (type.equals(OclType.STRING) && token.length() >= 2
                && token.startsWith("'") && token.endsWith("'")) {
            String contents = token.substring(1, token.length() - 1);
            // Escapes need a separate SOIL lexer rule; do not guess their meaning.
            if (contents.indexOf('\'') < 0 && contents.indexOf('\\') < 0)
                return new OclValue.StringValue(contents);
        }
        throw new IOException("Unsupported or malformed SOIL scalar literal for " + type);
    }

}
