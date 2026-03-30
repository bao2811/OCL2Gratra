package org.uet.dse.neo4j.manager;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class WorkLogManager {
    private static final String LOG_FILE = "neo4j_plugin_worklog.log";
    private static WorkLogManager instance;

    private WorkLogManager() {}

    public static synchronized WorkLogManager getInstance() {
        if (instance == null) instance = new WorkLogManager();
        return instance;
    }

    public synchronized void log(String action, String details) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(LOG_FILE, true))) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            writer.write(String.format("[%s] ACTION: %s | DETAILS: %s%n", timestamp, action, details));
        } catch (IOException e) {
            System.err.println("Could not write to log file: " + e.getMessage());
        }
    }
}