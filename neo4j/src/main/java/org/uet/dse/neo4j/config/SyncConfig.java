package org.uet.dse.neo4j.config;

import java.io.*;
import java.util.Properties;

public class SyncConfig {
    // Tần suất quét mặc định là 2000ms (2 giây)
    public int syncInterval = 2000;
    private static SyncConfig instance;
    private static final String CONFIG_FILE = "neo4j_sync_settings.properties";

    // Options: Outgoing (USE -> Neo4j)
    public boolean autoPushModelOnImport = false; // Mặc định là Manual (false)
    public boolean autoPushObjectOnChange = false;

    // Options: Incoming (Neo4j -> USE)
    public boolean autoPullModelOnChange = false;
    public boolean autoPullObjectOnChange = false;

    private SyncConfig() { load(); }

    public static synchronized SyncConfig getInstance() {
        if (instance == null) instance = new SyncConfig();
        return instance;
    }

    public void save() {
        Properties props = new Properties();
        props.setProperty("autoPushModelOnImport", String.valueOf(autoPushModelOnImport));
        props.setProperty("autoPushObjectOnChange", String.valueOf(autoPushObjectOnChange));
        props.setProperty("autoPullModelOnChange", String.valueOf(autoPullModelOnChange));
        props.setProperty("autoPullObjectOnChange", String.valueOf(autoPullObjectOnChange));

        props.setProperty("syncInterval", String.valueOf(syncInterval));
        try (OutputStream out = new FileOutputStream(CONFIG_FILE)) {
            props.store(out, "Neo4j Sync Settings");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void load() {
        Properties props = new Properties();
        try (InputStream in = new FileInputStream(CONFIG_FILE)) {
            props.load(in);
            autoPushModelOnImport = Boolean.parseBoolean(props.getProperty("autoPushModelOnImport", "false"));
            autoPushObjectOnChange = Boolean.parseBoolean(props.getProperty("autoPushObjectOnChange", "false"));
            autoPullModelOnChange = Boolean.parseBoolean(props.getProperty("autoPullModelOnChange", "false"));
            autoPullObjectOnChange = Boolean.parseBoolean(props.getProperty("autoPullObjectOnChange", "false"));

            syncInterval = Integer.parseInt(props.getProperty("syncInterval", "2000"));
        } catch (IOException e) {
            // Nếu chưa có file thì dùng default (false)
        }
    }
}