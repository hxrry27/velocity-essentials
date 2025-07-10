package com.velocityessentials.database;

import com.velocityessentials.VelocityEssentials;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class Database {
    private final VelocityEssentials plugin;
    private HikariDataSource dataSource;
    
    // SQLite version - adjusted for SQLite syntax
    private static final String CREATE_TABLE = """
        CREATE TABLE IF NOT EXISTS last_server (
            uuid VARCHAR(36) PRIMARY KEY,
            username VARCHAR(16) NOT NULL,
            server_name VARCHAR(50) NOT NULL,
            last_seen DATETIME DEFAULT CURRENT_TIMESTAMP,
            first_joined DATETIME DEFAULT CURRENT_TIMESTAMP
        )
        """;
    
    // Create index separately for SQLite
    private static final String CREATE_INDEX_LAST_SEEN = 
        "CREATE INDEX IF NOT EXISTS idx_last_seen ON last_server(last_seen)";
    
    private static final String CREATE_INDEX_USERNAME = 
        "CREATE INDEX IF NOT EXISTS idx_username ON last_server(username)";
    
    public Database(VelocityEssentials plugin) {
        this.plugin = plugin;
    }
    
    public boolean connect() {
        try {
            // Create data directory if it doesn't exist
            Path dataDir = plugin.getDataDirectory();
            if (!Files.exists(dataDir)) {
                Files.createDirectories(dataDir);
            }
            
            // SQLite database file path
            Path dbFile = dataDir.resolve("playerdata.db");
            
            HikariConfig config = new HikariConfig();
            config.setDriverClassName("org.sqlite.JDBC");
            config.setJdbcUrl("jdbc:sqlite:" + dbFile.toAbsolutePath());
            
            // SQLite specific settings
            config.setMaximumPoolSize(1); // SQLite works best with single connection
            config.setMinimumIdle(1);
            config.setMaxLifetime(0); // Infinite lifetime
            config.setConnectionTimeout(10000); // 10 seconds
            
            // SQLite performance settings
            config.addDataSourceProperty("journal_mode", "WAL"); // Write-Ahead Logging
            config.addDataSourceProperty("synchronous", "NORMAL");
            config.addDataSourceProperty("cache_size", "-64000"); // 64MB cache
            config.addDataSourceProperty("temp_store", "MEMORY");
            
            this.dataSource = new HikariDataSource(config);
            
            // Create tables
            createTables();
            
            plugin.getLogger().info("Successfully connected to SQLite database at: " + dbFile);
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().error("Failed to connect to SQLite database", e);
            return false;
        }
    }
    
    private void createTables() {
        try (Connection conn = getConnection()) {
            // Create table
            try (PreparedStatement stmt = conn.prepareStatement(CREATE_TABLE)) {
                stmt.executeUpdate();
            }
            
            // Create indexes
            try (PreparedStatement stmt = conn.prepareStatement(CREATE_INDEX_LAST_SEEN)) {
                stmt.executeUpdate();
            }
            
            try (PreparedStatement stmt = conn.prepareStatement(CREATE_INDEX_USERNAME)) {
                stmt.executeUpdate();
            }
            
            // SQLite doesn't support ON UPDATE CURRENT_TIMESTAMP, so we'll handle it in the Java code
            
        } catch (SQLException e) {
            plugin.getLogger().error("Failed to create database tables", e);
        }
    }
    
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Database is not connected");
        }
        return dataSource.getConnection();
    }
    
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Database connection closed");
        }
    }
    
    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }
}