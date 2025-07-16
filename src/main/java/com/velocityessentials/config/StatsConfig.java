package com.velocityessentials.config;

import com.moandjiezana.toml.Toml;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class StatsConfig {
    private String dbHost = "localhost";
    private String dbName = "valestats";
    private String dbUser = "valestats";
    private String dbPassword = "changeme";
    private int dbPort = 5432;
    
    public static StatsConfig load(Path dataDirectory) {
        File configFile = dataDirectory.resolve("stats-config.toml").toFile();
        
        // Create default config if it doesn't exist
        if (!configFile.exists()) {
            try (InputStream in = StatsConfig.class.getResourceAsStream("/default-stats-config.toml")) {
                Files.createDirectories(dataDirectory);
                Files.copy(in, configFile.toPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        // Load config
        Toml toml = new Toml().read(configFile);
        StatsConfig config = new StatsConfig();
        
        config.dbHost = toml.getString("database.host", config.dbHost);
        config.dbName = toml.getString("database.name", config.dbName);
        config.dbUser = toml.getString("database.user", config.dbUser);
        config.dbPassword = toml.getString("database.password", config.dbPassword);
        config.dbPort = toml.getLong("database.port", (long) config.dbPort).intValue();
        
        return config;
    }
    
    // Getters
    public String getDbHost() { return dbHost; }
    public String getDbName() { return dbName; }
    public String getDbUser() { return dbUser; }
    public String getDbPassword() { return dbPassword; }
    public int getDbPort() { return dbPort; }
}