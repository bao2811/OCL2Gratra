package org.uet.dse.neo4j.manager;

import java.time.LocalDateTime;
import java.util.UUID;

public class SessionManager {
    private String sessionKey;
    private boolean isActive = false;
    private final LocalDateTime connectionDate;
    /**
     * Tạo ra một chuỗi định danh duy nhất cho người dùng hiện tại
     * Ví dụ: neo4j [Key: A1B2C3] (Admin-PC)
     */
    public String getUserDisplayName() {
        // 1. Lấy tên user đăng nhập vào Neo4j
        String dbUser = Neo4jDriverManager.getInstance().getUser();
        if (dbUser == null) dbUser = "unknown_user";

        // 2. Lấy Key của phiên làm việc hiện tại
        String key = (this.sessionKey != null) ? this.sessionKey : "NO_KEY";

        // 3. Lấy tên máy tính/người dùng từ hệ điều hành (Java build-in)
        String osUser = System.getProperty("user.name");

        return String.format("%s [Key: %s] (%s)", dbUser, key, osUser);
    }

    public SessionManager() {
        this.connectionDate = LocalDateTime.now();
    }

    public String createNewSession() {
        this.sessionKey = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.isActive = true;
        WorkLogManager.getInstance().log("SESSION_CREATE", "New session created with key: " + sessionKey);
        return sessionKey;
    }

    public void joinSession(String key) {
        this.sessionKey = key;
        this.isActive = true;
        WorkLogManager.getInstance().log("SESSION_JOIN", "Joined session with key: " + key);
    }

    public void setActive(boolean active) {
        this.isActive = active;
        WorkLogManager.getInstance().log("STATUS_CHANGE", active ? "Set to ACTIVE" : "Set to INACTIVE");
    }

    public boolean isActive() { return isActive; }
    public String getSessionKey() { return sessionKey; }
    public LocalDateTime getConnectionDate() { return connectionDate; }
}