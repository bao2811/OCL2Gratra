package org.uet.dse.ocl2cypher.caseStudy;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.runtime.OclValue;
import org.uet.dse.ocl2cypher.source.model.SchemaModel;
import org.uet.dse.ocl2cypher.source.model.Snapshot;
import org.uet.dse.ocl2cypher.source.model.UmlAssociation;
import org.uet.dse.ocl2cypher.source.model.UmlAttribute;
import org.uet.dse.ocl2cypher.source.model.UmlClass;

/**
 * Loader for the Library case study corpus in {@code research/Case Study}.
 *
 * <p>The {@code .use} and {@code .soil} files are the human-readable normative
 * form; this loader builds the equivalent in-memory {@code SM}/{@code SN} so the
 * corpus can be replayed without a USE runtime. Every association is declared
 * source-end first: {@code UmlAssociation.binary(name, srcClass, srcRole,
 * tgtClass, tgtRole)} makes {@code tgtRole} the navigable to-many role reached
 * from {@code srcClass}, which is why {@code employment} declares
 * {@code Company[employer] -> Person[employees]}.
 */
public final class LibraryCorpus {

    private LibraryCorpus() {
    }

    /** Schema of {@code library.use}: 5 classes, 1 generalization, 5 associations. */
    public static SchemaModel schema() {
        return SchemaModel.builder("library")
                .clazz(UmlClass.of("Person"))
                .clazz(UmlClass.of("Staff", "Person"))
                .clazz(UmlClass.of("Company"))
                .clazz(UmlClass.of("Book"))
                .clazz(UmlClass.of("Loan"))
                .attribute(UmlAttribute.of("Person", "name", OclType.STRING))
                .attribute(UmlAttribute.of("Person", "age", OclType.INTEGER))
                .attribute(UmlAttribute.of("Person", "rate", OclType.REAL))
                .attribute(UmlAttribute.of("Staff", "grade", OclType.INTEGER))
                .attribute(UmlAttribute.of("Company", "budget", OclType.INTEGER))
                .attribute(UmlAttribute.of("Book", "title", OclType.STRING))
                .attribute(UmlAttribute.of("Loan", "fee", OclType.INTEGER))
                .association(UmlAssociation.binary("employment", "Company", "employer",
                        "Person", "employees"))
                .association(UmlAssociation.oneToOne("manages", "Company", "leads",
                        "Staff", "chief"))
                .association(UmlAssociation.binary("branchBook", "Company", "branch",
                        "Book", "catalog"))
                .association(UmlAssociation.oneToOne("loanCustomer", "Loan", "customer",
                        "Person", "member"))
                .association(UmlAssociation.oneToOne("loanItem", "Loan", "loanedBook",
                        "Book", "item"))
                .build();
    }

    /** The three snapshots keyed by the id used in {@code expected-violations.csv}. */
    public static Map<String, Snapshot> snapshots() {
        Map<String, Snapshot> m = new LinkedHashMap<>();
        m.put("SN1", main());
        m.put("SN2", emptyCollections());
        m.put("SN3", bottomCarriers());
        return m;
    }

    private static OclValue i(long v) {
        return new OclValue.IntegerValue(BigInteger.valueOf(v));
    }

    private static OclValue r(String v) {
        return new OclValue.RealValue(new BigDecimal(v));
    }

    private static OclValue s(String v) {
        return new OclValue.StringValue(v);
    }

    /** {@code snapshot-main.soil}. */
    public static Snapshot main() {
        return Snapshot.builder()
                .object("acme", "Company").attribute("acme", "budget", i(500))
                .object("mgr", "Staff")
                .attribute("mgr", "name", s("Mgr")).attribute("mgr", "age", i(40))
                .attribute("mgr", "rate", r("2.5")).attribute("mgr", "grade", i(3))
                .object("alice", "Person")
                .attribute("alice", "name", s("Alice")).attribute("alice", "age", i(30))
                .attribute("alice", "rate", r("1.5"))
                .object("bob", "Person")
                .attribute("bob", "name", s("Bob")).attribute("bob", "age", i(0))
                .attribute("bob", "rate", r("0.25"))
                .object("carol", "Person")
                .object("book1", "Book").attribute("book1", "title", s("OCL in Action"))
                .object("bookBlank", "Book").attribute("bookBlank", "title", s(""))
                .object("loan1", "Loan").attribute("loan1", "fee", i(0))
                .object("loan2", "Loan").attribute("loan2", "fee", i(-5))
                .link("employment", "acme", "alice")
                .link("employment", "acme", "bob")
                .link("employment", "acme", "carol")
                .link("employment", "acme", "mgr")
                .link("manages", "acme", "mgr")
                .link("branchBook", "acme", "book1")
                .link("branchBook", "acme", "bookBlank")
                .link("loanCustomer", "loan1", "alice")
                .link("loanItem", "loan1", "book1")
                .link("loanCustomer", "loan2", "bob")
                .link("loanItem", "loan2", "bookBlank")
                .build();
    }

    /** {@code snapshot-empty.soil} — every to-many navigation is the empty Set. */
    public static Snapshot emptyCollections() {
        return Snapshot.builder()
                .object("shell", "Company").attribute("shell", "budget", i(0))
                .object("solo", "Staff")
                .attribute("solo", "name", s("Solo")).attribute("solo", "age", i(50))
                .attribute("solo", "rate", r("3.0")).attribute("solo", "grade", i(9))
                .build();
    }

    /** {@code snapshot-bottom.soil} — absent slots and dangling to-one links. */
    public static Snapshot bottomCarriers() {
        return Snapshot.builder()
                .object("ghostCo", "Company")
                .object("void1", "Person")
                .object("void2", "Staff")
                .object("bookNoTitle", "Book")
                .object("loanNoLinks", "Loan")
                .object("loanNoFee", "Loan")
                .link("employment", "ghostCo", "void1")
                .link("employment", "ghostCo", "void2")
                .link("loanCustomer", "loanNoFee", "void1")
                .build();
    }

    /** One admitted invariant of {@code invariants.ocl}. */
    public record Invariant(String key, String oclText) {
    }

    /**
     * Parse {@code invariants.ocl}. An entry is {@code context C inv N: body}
     * where the body runs to the next {@code context} or EOF; the returned key is
     * {@code C::N}, matching the {@code invariant} column of the expected CSV.
     */
    public static List<Invariant> invariants(java.nio.file.Path oclFile)
            throws java.io.IOException {
        List<Invariant> out = new ArrayList<>();
        String ctx = null;
        String name = null;
        StringBuilder body = null;
        for (String raw : java.nio.file.Files.readAllLines(oclFile)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("--")) {
                continue;
            }
            if (line.startsWith("context ")) {
                if (ctx != null && name != null) {
                    out.add(entry(ctx, name, body));
                }
                // `context C inv N: body` may be on one line, or `context C` alone.
                String rest = line.substring("context ".length()).trim();
                int invAt = rest.indexOf(" inv ");
                if (invAt < 0) {
                    ctx = rest;
                    name = null;
                    body = null;
                    continue;
                }
                ctx = rest.substring(0, invAt).trim();
                String after = rest.substring(invAt + " inv ".length()).trim();
                int colon = after.indexOf(':');
                name = colon < 0 ? after : after.substring(0, colon).trim();
                body = new StringBuilder(colon < 0 ? "" : after.substring(colon + 1).trim());
                continue;
            }
            if (line.startsWith("inv ")) {
                if (ctx != null && name != null) {
                    out.add(entry(ctx, name, body));
                }
                String after = line.substring("inv ".length()).trim();
                int colon = after.indexOf(':');
                name = colon < 0 ? after : after.substring(0, colon).trim();
                body = new StringBuilder(colon < 0 ? "" : after.substring(colon + 1).trim());
                continue;
            }
            if (body != null) {
                if (body.length() > 0) {
                    body.append(' ');
                }
                body.append(line);
            }
        }
        if (ctx != null && name != null) {
            out.add(entry(ctx, name, body));
        }
        return List.copyOf(out);
    }

    private static Invariant entry(String ctx, String name, StringBuilder body) {
        String text = "context " + ctx + " inv " + name + ": " + body.toString().trim();
        return new Invariant(ctx + "::" + name, text);
    }

    /**
     * Parse {@code expected-violations.csv} into
     * {@code (snapshotId, invariantKey) -> violated stable ids}. Ids are
     * pipe-separated because a comma is the column separator.
     */
    public static Map<String, java.util.Set<String>> expected(java.nio.file.Path csv)
            throws java.io.IOException {
        Map<String, java.util.Set<String>> out = new LinkedHashMap<>();
        for (String raw : java.nio.file.Files.readAllLines(csv)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("snapshot,")) {
                continue;
            }
            String[] parts = line.split(",", 3);
            if (parts.length < 2) {
                continue;
            }
            String ids = parts.length < 3 ? "" : parts[2].trim();
            java.util.Set<String> set = new java.util.TreeSet<>();
            for (String id : ids.split("\\|")) {
                if (!id.isBlank()) {
                    set.add(id.trim());
                }
            }
            out.put(parts[0].trim() + "\t" + parts[1].trim(), set);
        }
        return out;
    }

    /** Locate {@code research/Case Study} from either the module or the repo root. */
    public static java.nio.file.Path corpusDir() {
        java.nio.file.Path fromModule = java.nio.file.Path.of("..", "research", "Case Study");
        if (java.nio.file.Files.isDirectory(fromModule)) {
            return fromModule;
        }
        return java.nio.file.Path.of("research", "Case Study");
    }
}
