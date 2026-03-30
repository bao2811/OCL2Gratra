package org.uet.dse.neo4j.incrementalUpdate;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class UseFileService {
    public String readFileContent(File file) throws Exception {
        if (file == null || !file.exists()) {
            throw new Exception("File does not exist.");
        }
        byte[] encoded = Files.readAllBytes(file.toPath());
        return new String(encoded, StandardCharsets.UTF_8);
    }
}