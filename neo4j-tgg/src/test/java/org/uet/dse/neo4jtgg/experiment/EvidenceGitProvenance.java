package org.uet.dse.neo4jtgg.experiment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

/** Shared provenance checks for checked-in evidence captured by an external runtime. */
final class EvidenceGitProvenance {
    private static final String REQUIRE_CLEAN_PROPERTY = "verification.requireCleanEvidence";

    private EvidenceGitProvenance() {
    }

    static void validate(Map<String, String> metadata, Path workspace) throws IOException, InterruptedException {
        String commit = metadata.get("gitCommit");
        if (commit == null || !commit.matches("[0-9a-f]{40}")) {
            throw new IllegalStateException("gitCommit is not a full lowercase Git object ID: " + commit);
        }
        String dirty = metadata.get("gitDirtyAtCapture");
        if (!"true".equals(dirty) && !"false".equals(dirty)) {
            throw new IllegalStateException("gitDirtyAtCapture must be true or false: " + dirty);
        }
        if (Boolean.getBoolean(REQUIRE_CLEAN_PROPERTY) && !"false".equals(dirty)) {
            throw new IllegalStateException("Clean CI requires gitDirtyAtCapture=false");
        }

        Process process = new ProcessBuilder("git", "merge-base", "--is-ancestor", commit, "HEAD")
                .directory(workspace.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("Evidence gitCommit is not reachable from HEAD: "
                    + commit + (output.isBlank() ? "" : " (" + output + ")"));
        }
    }
}
