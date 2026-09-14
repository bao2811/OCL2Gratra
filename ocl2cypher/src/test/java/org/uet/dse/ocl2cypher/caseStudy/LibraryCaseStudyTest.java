package org.uet.dse.ocl2cypher.caseStudy;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.uet.dse.ocl2cypher.api.CoreOracle;
import org.uet.dse.ocl2cypher.api.FrontendCompiler;
import org.uet.dse.ocl2cypher.core.CoreInvariant;
import org.uet.dse.ocl2cypher.core.CoreLowering;
import org.uet.dse.ocl2cypher.cypher.CypherAst;
import org.uet.dse.ocl2cypher.cypher.Realization;
import org.uet.dse.ocl2cypher.cypher.Serializer;
import org.uet.dse.ocl2cypher.graph.GraphBuilder;
import org.uet.dse.ocl2cypher.graph.GraphModel;
import org.uet.dse.ocl2cypher.qcyp.QCypTranslator;
import org.uet.dse.ocl2cypher.qcyp.QInterpreter;
import org.uet.dse.ocl2cypher.qcyp.QQuery;
import org.uet.dse.ocl2cypher.source.model.SchemaModel;
import org.uet.dse.ocl2cypher.source.model.Snapshot;

/**
 * The complete-chain case study: {@code research/Case Study} replayed through
 * every boundary of {@code E_SM → N_SM → T_MM/F_G → T_G → R → S}.
 *
 * <p>
 * Three assertions carry the weight. First, the expected violation sets in
 * {@code expected-violations.csv} are matched by the Core oracle on
 * {@code (SM,SN)} — the corpus is hand-auditable, so a wrong row is a wrong
 * claim about OCL, not about the graph. Second, the Q oracle recomputes the
 * same sets over {@code G} through PGMM observers only, which makes the
 * agreement a simultaneous check of {@code Theorem T}, {@code F_G} fidelity and
 * the observer contract: a lost link, a mislabelled role or a bottom collapsed
 * into an empty set would split the two answers. Third, each invariant has an
 * explicit target expectation: read-only text or a specific stage-R certificate
 * rejection. Numeric rejection is not evidence of successful end-to-end
 * execution.
 */
class LibraryCaseStudyTest {

    private static final CypherAst.Dialect DIALECT = CypherAst.Dialect.CYPHER_5;

    /**
     * Read-only profile: these must never appear in generated text.
     */
    private static final List<String> FORBIDDEN_CLAUSES = List.of(
            "CREATE", "MERGE", " SET ", "REMOVE", "DELETE", "DETACH",
            "LOAD CSV", "FOREACH", "OPTIONAL MATCH", "UNION", "ORDER BY",
            "SKIP", "LIMIT", "apoc.");

    private record Compiled(CoreInvariant unit, QQuery query, GraphModel graph) {

    }

    private static Compiled compile(String ocl, SchemaModel sm, Snapshot sn) {
        var fe = FrontendCompiler.compile(ocl, sm);
        assertTrue(fe.isSuccess(), () -> "E_SM: " + fe.diagnostics() + " for " + ocl);
        var doc = fe.value().get(0);
        assertEquals(1, doc.constraints.size(), "one invariant per corpus entry");

        var low = CoreLowering.lower(sm, doc, doc.constraints.get(0));
        assertTrue(low.isSuccess(), () -> "N_SM: " + low.diagnostics() + " for " + ocl);

        var gb = GraphBuilder.build(sm, sn);
        assertTrue(gb.isSuccess(), () -> "F_G: " + gb.diagnostics());

        var q = QCypTranslator.translate(low.value());
        assertTrue(q.isSuccess(), () -> "T_G: " + q.diagnostics() + " for " + ocl);

        return new Compiled(low.value(), q.value(), gb.value().graph());
    }

    @Test
    void expectedViolationsMatchCoreAndQOverGraphOnEverySnapshot() throws Exception {
        Path dir = LibraryCorpus.corpusDir();
        var invariants = LibraryCorpus.invariants(dir.resolve("invariants.ocl"));
        var expected = LibraryCorpus.expected(dir.resolve("expected-violations.csv"));
        SchemaModel sm = LibraryCorpus.schema();
        Map<String, Snapshot> snapshots = LibraryCorpus.snapshots();

        assertEquals(26, invariants.size(), "corpus size is fixed by the case study");
        assertEquals(invariants.size() * snapshots.size(), expected.size(),
                "every (snapshot, invariant) pair has an expected row");

        for (var snapshot : snapshots.entrySet()) {
            String snId = snapshot.getKey();
            Snapshot sn = snapshot.getValue();
            for (var inv : invariants) {
                String row = snId + "\t" + inv.key();
                Set<String> want = expected.get(row);
                assertTrue(want != null, () -> "no expected row for " + row);

                Compiled c = compile(inv.oclText(), sm, sn);

                Set<String> core = new TreeSet<>(
                        CoreOracle.violationsOclEq(inv.oclText(), sm, sn));
                assertEquals(want, core, () -> "Core vs expected CSV for " + row);

                Set<String> overGraph = new TreeSet<>(
                        QInterpreter.violations(sm, c.graph(), c.unit(), c.query()));
                assertEquals(core, overGraph,
                        () -> "Q-over-G vs Core for " + row + " (bottom included)");
            }
        }
    }

    @Test
    void invariantsMeetExplicitRealizationExpectations() throws Exception {
        Path dir = LibraryCorpus.corpusDir();
        var invariants = LibraryCorpus.invariants(dir.resolve("invariants.ocl"));
        SchemaModel sm = LibraryCorpus.schema();
        Snapshot sn = LibraryCorpus.main();

        for (var inv : invariants) {
            Compiled c = compile(inv.oclText(), sm, sn);

            var r = Realization.realize(c.query(), c.graph(), DIALECT);
            String rejection = switch (inv.key()) {
                case "Person::GradedBonus" -> "R_NUMERIC_CAPABILITY";
                case "Person::NameNotBlank", "Person::KindOfStaff", "Person::ExactlyPerson", "Company::StaffedNotEmpty", "Company::CatalogNotEmpty", "Company::CatalogNotEmptyViaNot", "Loan::ItemHasTitle", "Person::AdultPerson", "Person::NotMinor", "Company::AllSalariedPositive", "Company::HasEmployees", "Company::AllAdultEmployees", "Company::AdultsYoungEnough", "Company::NoMinorsAtAll", "Company::HasSeniorEmployee", "Company::SafeChiefGrade", "Company::SafeChiefAge", "Company::CompanyExists", "Company::MaximalEmployee", "Company::GovernanceChain", "Loan::NonNegativeFee", "Loan::BorrowerIsAdult", "Person::SetDedup", "Person::BagKeepsOccurrences", "Person::RateAboveHalf" ->
                    null;
                default ->
                    throw new AssertionError("Unreviewed invariant: " + inv.key());
            };
            if (rejection != null) {
                assertTrue(r.isFailure(), "Expected rejection for " + inv.key());
                assertEquals(org.uet.dse.ocl2cypher.diagnostics.Stage.R,
                        r.primaryDiagnostic().stage(), inv.key());
                assertEquals(rejection, r.primaryDiagnostic().code(), inv.key());
                continue;
            }
            assertTrue(r.isSuccess(), () -> "R: " + r.diagnostics() + " for " + inv.key());
            assertEquals(CypherAst.ResultShape.IDS, r.value().contract().shape(),
                    "VIOLATIONS mode projects stable ids");

            String text = Serializer.cypherText(r.value());
            assertTrue(text.contains("MATCH"), () -> "no MATCH in text for " + inv.key());
            assertTrue(text.contains("RETURN"), () -> "no RETURN in text for " + inv.key());

            String upper = " " + text.toUpperCase(java.util.Locale.ROOT) + " ";
            for (String forbidden : FORBIDDEN_CLAUSES) {
                assertFalse(upper.contains(forbidden.toUpperCase(java.util.Locale.ROOT)),
                        () -> "read-only profile violated by `" + forbidden.trim()
                        + "` in " + inv.key());
            }
        }
    }

    /**
     * Out-of-scope surface constructs must be refused by {@code E_SM} or
     * {@code N_SM}. A refusal is a {@code Failure} carrying diagnostics; it is
     * never turned into a typed bottom that would later surface as a violation.
     */
    @Test
    void rejectedCorpusFailsAtTheFrontendOrLowering() throws Exception {
        Path dir = LibraryCorpus.corpusDir();
        var rejected = LibraryCorpus.invariants(dir.resolve("rejected-corpus.ocl"));
        SchemaModel sm = LibraryCorpus.schema();
        assertEquals(15, rejected.size(), "rejection corpus size is fixed");

        List<String> admitted = new ArrayList<>();
        for (var inv : rejected) {
            var fe = FrontendCompiler.compile(inv.oclText(), sm);
            if (fe.isFailure()) {
                assertFalse(fe.diagnostics().isEmpty(),
                        () -> "E_SM refusal must carry a diagnostic: " + inv.key());
                continue;
            }
            var doc = fe.value().get(0);
            var low = CoreLowering.lower(sm, doc, doc.constraints.get(0));
            if (low.isFailure()) {
                assertFalse(low.diagnostics().isEmpty(),
                        () -> "N_SM refusal must carry a diagnostic: " + inv.key());
                continue;
            }
            admitted.add(inv.key());
        }
        assertEquals(List.of(), admitted,
                "out-of-scope constructs must not reach the Core IR");
    }

    /**
     * The three snapshots are what make {@code bottom}, {@code empty} and
     * {@code false} pairwise distinguishable rather than three spellings of the
     * same answer: on SN2 every to-many navigation is the empty Set, so
     * {@code forAll} is vacuously true while {@code notEmpty()} is false; on
     * SN3 the same navigation is non-empty but every element attribute is
     * bottom, so {@code forAll} is bottom — and a bottom body is a violation.
     */
    @Test
    void bottomEmptyAndFalseStayDistinct() throws Exception {
        Path dir = LibraryCorpus.corpusDir();
        var expected = LibraryCorpus.expected(dir.resolve("expected-violations.csv"));

        // empty Set: vacuous forAll passes, notEmpty fails.
        assertEquals(Set.of(), expected.get("SN2\tCompany::AllAdultEmployees"));
        assertEquals(Set.of("shell"), expected.get("SN2\tCompany::StaffedNotEmpty"));

        // element bottom in a non-empty Set: forAll is bottom, so it is a violation,
        // while notEmpty stays true — the two cannot be the same carrier.
        assertEquals(Set.of("ghostCo"), expected.get("SN3\tCompany::AllAdultEmployees"));
        assertEquals(Set.of(), expected.get("SN3\tCompany::StaffedNotEmpty"));

        // Total equality never yields bottom: a bottom slot is unequal to 18 and
        // therefore `<> ''` on an absent String slot is TRUE, not a violation.
        assertEquals(Set.of(), expected.get("SN3\tPerson::NameNotBlank"));
        assertEquals(Set.of("void1", "void2"), expected.get("SN3\tPerson::AdultPerson"));
    }
}
