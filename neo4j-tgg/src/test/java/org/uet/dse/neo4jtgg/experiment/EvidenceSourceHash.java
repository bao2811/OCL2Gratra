package org.uet.dse.neo4jtgg.experiment;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/** Cross-platform digest for checked-in UTF-8 text evidence dependencies. */
final class EvidenceSourceHash {
    static final String MODE = "utf8-lf-v1";

    private EvidenceSourceHash() {
    }

    static String sha256(Path path) throws Exception {
        String canonical = Files.readString(path, StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8)))
                .toUpperCase(Locale.ROOT);
    }
}
