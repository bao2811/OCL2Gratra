package org.uet.dse.neo4j.manager;

import org.neo4j.driver.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Neo4jDriverManager implements AutoCloseable {
    private static Neo4jDriverManager instance;
    private final Driver driver;
    private final String uri;
    private final String user;
    private final String activeDatabase;
    private final boolean deleteOnExit;

    private SessionManager sessionManager;

    // Constructor private để ép buộc khởi tạo qua phương thức static connect
    private Neo4jDriverManager(String uri, String user, String password, String database, boolean deleteOnExit) {
        this.uri = uri;
        this.user = user;
        this.activeDatabase = (database == null || database.isEmpty()) ? "neo4j" : database;
        this.deleteOnExit = deleteOnExit;

        Config config = Config.builder()
                .withConnectionTimeout(30, TimeUnit.SECONDS)
                .withMaxConnectionPoolSize(50)
                .build();

        this.driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password), config);

    }

    /**
     * Phương thức chính để thiết lập kết nối.
     * Xử lý cả việc tạo database mới thông qua database 'system'.
     */
    public static synchronized void connect(String uri, String user, String password, String database, boolean createNew, boolean deleteOnExit) throws Exception {
        // 1. Đóng instance cũ nếu đang tồn tại
        if (instance != null) {
            instance.close();
            instance = null;
        }

        // 2. Nếu yêu cầu tạo mới DB, phải can thiệp qua database 'system'
        if (createNew && !database.equalsIgnoreCase("neo4j") && !database.equalsIgnoreCase("system")) {
            try (Driver adminDriver = GraphDatabase.driver(uri, AuthTokens.basic(user, password))) {
                try (Session sysSession = adminDriver.session(SessionConfig.forDatabase("system"))) {
                    sysSession.executeWrite(tx -> {
                        tx.run("CREATE DATABASE `" + database + "` IF NOT EXISTS WAIT");
                        return null;
                    });
                }
            } catch (Exception e) {
                throw new Exception("Không thể tạo Database mới. Lỗi quyền hạn hoặc kết nối: " + e.getMessage());
            }
        }

        // 3. Khởi tạo instance chính thức
        instance = new Neo4jDriverManager(uri, user, password, database, deleteOnExit);

        // Kiểm tra kết nối ngay lập tức
        instance.driver.verifyConnectivity();
        // Server connectivity alone does not prove that the selected database
        // exists. Verify it before dependent UI operations are unlocked.
        try (Session targetSession = instance.openSession()) {
            targetSession.run("RETURN 1 AS connectionCheck").consume();
        } catch (Exception exception) {
            instance.driver.close();
            instance = null;
            throw new Exception("Cannot access target database `" + database + "`: "
                    + exception.getMessage(), exception);
        }
        WorkLogManager.getInstance().log("CONNECTION", "Connected to " + uri + " [DB: " + database + "]");
    }

    public static Neo4jDriverManager getInstance() {
        return instance;
    }

    /**
     * Mở session làm việc với database đã chọn
     */
    public Session openSession() {
        return driver.session(SessionConfig.forDatabase(activeDatabase));
    }

    public boolean isConnected() {
        try {
            if (driver != null) {
                driver.verifyConnectivity();
                return true;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    /**
     * Lấy các thông số thống kê của Database (Metadata)
     */
    public Map<String, Object> getDatabaseStats() {
        Map<String, Object> stats = new HashMap<>();
        if (!isConnected()) return stats;

        try (Session session = openSession()) {
            // 1. Lấy Version của Neo4j
            Result resVer = session.run("CALL dbms.components() YIELD versions RETURN versions[0] AS version");
            if (resVer.hasNext()) stats.put("version", resVer.single().get("version").asString());

            // 2. Thống kê số lượng Node và Edge hiện có
            Result resCount = session.run("MATCH (n) WITH count(n) as nodes MATCH ()-[r]->() RETURN nodes, count(r) as edges");
            if (resCount.hasNext()) {
                org.neo4j.driver.Record rec = resCount.single();
                stats.put("nodeCount", rec.get("nodes").asLong());
                stats.put("edgeCount", rec.get("edges").asLong());
            }
        } catch (Exception e) {
            stats.put("error", e.getMessage());
        }
        return stats;
    }

    // --- Getter & Setter cho SessionManager (Quản lý Collaboration Key) ---
    public void setSessionManager(SessionManager sm) {
        this.sessionManager = sm;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public String getActiveDatabase() {
        return activeDatabase;
    }

    public String getUri() {
        return uri;
    }

    public String getUser() {
        return user;
    }

    /**
     * Đóng driver và thực hiện cleanup (Xóa DB nếu cờ deleteOnExit được bật)
     */
    @Override
    public void close() throws Exception {
        if (driver != null) {
            if (deleteOnExit && !activeDatabase.equals("neo4j") && !activeDatabase.equals("system")) {
                WorkLogManager.getInstance().log("CLEANUP", "Deleting temporary database: " + activeDatabase);
                try (Session sysSession = driver.session(SessionConfig.forDatabase("system"))) {
                    sysSession.executeWrite(tx -> {
                        tx.run("STOP DATABASE `" + activeDatabase + "` WAIT");
                        tx.run("DROP DATABASE `" + activeDatabase + "` WAIT");
                        return null;
                    });
                } catch (Exception e) {
                    System.err.println("Cleanup error: " + e.getMessage());
                }
            }
            driver.close();
        }
    }

    public Driver getDriver() {
        return driver;
    }
}
