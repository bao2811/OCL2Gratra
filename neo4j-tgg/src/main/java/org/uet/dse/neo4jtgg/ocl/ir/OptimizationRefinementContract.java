package org.uet.dse.neo4jtgg.ocl.ir;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Optional, non-normative contract for comparing Java {@code T_OPT} with the
 * reference {@code T_NORM} result. It is intentionally absent from the main
 * specification theorem.
 */
public final class OptimizationRefinementContract {
    private OptimizationRefinementContract() {
    }

    public enum EvidenceStatus {
        EMPIRICAL,
        PARTIAL,
        PROVED
    }

    public record Observation(String caseId, Object normalizedValue, Object optimizedValue) {
        public Observation {
            if (caseId == null || caseId.isBlank()) throw new IllegalArgumentException("caseId is required");
        }

        public boolean agrees() {
            return Objects.equals(normalizedValue, optimizedValue);
        }
    }

    public record Report(EvidenceStatus status, List<Observation> observations, List<String> failures) {
        public Report {
            observations = List.copyOf(observations);
            failures = List.copyOf(failures);
        }

        /** Finite agreement is useful evidence but never promotes itself to a proof. */
        public boolean finiteAgreement() {
            return failures.isEmpty() && !observations.isEmpty();
        }
    }

    public static Report empirical(List<Observation> observations) {
        List<String> failures = new ArrayList<>();
        for (Observation observation : observations) {
            if (!observation.agrees()) failures.add(observation.caseId());
        }
        return new Report(EvidenceStatus.EMPIRICAL, observations, failures);
    }
}
