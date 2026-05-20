package org.uet.dse.neo4j.sync;

public final class LegacySyncGuard {
    private static volatile String disabledReason;

    private LegacySyncGuard() {
    }

    public static void disable(String reason) {
        disabledReason = reason == null || reason.isBlank()
                ? "Legacy Neo4j sync is disabled."
                : reason;
    }

    public static void enable() {
        disabledReason = null;
    }

    public static boolean isDisabled() {
        return disabledReason != null;
    }

    public static String getReason() {
        return disabledReason != null ? disabledReason : "Legacy Neo4j sync is enabled.";
    }
}
