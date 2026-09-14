package org.uet.dse.ocl2cypher.qcyp;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.uet.dse.ocl2cypher.core.CoreInvariant;
import org.uet.dse.ocl2cypher.core.CoreQuery;
import org.uet.dse.ocl2cypher.core.CoreUnit;
import org.uet.dse.ocl2cypher.diagnostics.Diagnostic;
import org.uet.dse.ocl2cypher.diagnostics.Result;
import org.uet.dse.ocl2cypher.diagnostics.RuleId;
import org.uet.dse.ocl2cypher.diagnostics.Stage;

/** Ordered, all-or-nothing Core-unit to Q-query batch boundary ({@code T_model}). */
public final class QBatchTranslator {
    private QBatchTranslator() {
    }

    /** A source-owned unit identity; identity is distinct from an optional invariant name. */
    public record BatchUnit(String unitId, CoreUnit unit) {
        public BatchUnit {
            if (unitId == null || unitId.isBlank()) {
                throw new IllegalArgumentException("batch unit ID must be non-blank");
            }
            Objects.requireNonNull(unit, "batch Core unit");
        }
    }

    /** One successful query paired with the exact source unit that produced it. */
    public record BatchEntry(String unitId, QQuery query) {
        public BatchEntry {
            Objects.requireNonNull(unitId, "batch result unit ID");
            Objects.requireNonNull(query, "batch result query");
        }
    }

    /**
     * Translates in source-list order and publishes no query list unless every unit succeeds.
     * The mode map must have exactly the source unit IDs as its domain.
     */
    public static Result<List<BatchEntry>> translate(
            List<BatchUnit> units, Map<String, QQuery.QueryMode> modes) {
        if (units == null || modes == null) {
            return failure("T_MODE_MAP", "units and mode map must be present");
        }
        List<BatchUnit> checkedUnits = new ArrayList<>(units.size());
        for (BatchUnit unit : units) {
            if (unit == null) {
                return failure("T_BATCH_UNIT", "batch contains a null unit");
            }
            checkedUnits.add(unit);
        }
        List<BatchUnit> sourceUnits = List.copyOf(checkedUnits);
        Set<String> identities = new HashSet<>();
        for (BatchUnit unit : sourceUnits) {
            if (!identities.add(unit.unitId())) {
                return failure("T_DUPLICATE_UNIT_ID",
                        "duplicate batch unit ID: " + unit.unitId());
            }
        }
        if (!identities.equals(modes.keySet()) || modes.values().stream().anyMatch(Objects::isNull)) {
            return failure("T_MODE_MAP",
                    "mode map domain must equal batch unit IDs and contain no null modes");
        }

        List<BatchEntry> pending = new ArrayList<>(sourceUnits.size());
        for (BatchUnit source : sourceUnits) {
            QQuery.QueryMode mode = modes.get(source.unitId());
            Result<QQuery> translated = translateUnit(source.unit(), mode);
            if (translated.isFailure()) {
                return Result.failure(attachUnit(source.unitId(), translated.diagnostics()));
            }
            pending.add(new BatchEntry(source.unitId(), translated.value()));
        }
        return Result.success(List.copyOf(pending));
    }

    private static Result<QQuery> translateUnit(CoreUnit unit, QQuery.QueryMode mode) {
        if (mode == QQuery.QueryMode.VIOLATIONS && unit instanceof CoreInvariant invariant) {
            return QCypTranslator.translate(invariant);
        }
        if (mode == QQuery.QueryMode.VALUE && unit instanceof CoreQuery query) {
            return QCypTranslator.translate(query);
        }
        return Result.failure(Diagnostic.builder(
                        "T_MODE_INCOMPATIBLE",
                        Stage.T_G,
                        "mode " + mode + " is incompatible with unit kind "
                                + unit.getClass().getSimpleName())
                .rule(RuleId.T_MODEL_BATCH)
                .build());
    }

    private static List<Diagnostic> attachUnit(String unitId, List<Diagnostic> diagnostics) {
        List<Diagnostic> attached = new ArrayList<>(diagnostics.size());
        for (Diagnostic diagnostic : diagnostics) {
            Diagnostic.Builder copy = Diagnostic.builder(
                            diagnostic.code(), diagnostic.stage(), diagnostic.message())
                    .severity(diagnostic.severity())
                    .sourceId(unitId);
            diagnostic.ruleId().ifPresent(copy::rule);
            diagnostic.sourceSpan().ifPresent(copy::span);
            diagnostic.causeCode().ifPresent(copy::cause);
            attached.add(copy.build());
        }
        return List.copyOf(attached);
    }

    private static <T> Result<T> failure(String code, String message) {
        return Result.failure(Diagnostic.builder(code, Stage.T_G, message)
                .rule(RuleId.T_MODEL_BATCH)
                .build());
    }
}
