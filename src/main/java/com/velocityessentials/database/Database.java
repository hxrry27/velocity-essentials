package com.velocityessentials.database;

import com.velocityessentials.VelocityEssentials;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class Database {
    private final VelocityEssentials plugin;
    private HikariDataSource dataSource;
    
    private static final String CREATE_TABLE = """
        CREATE TABLE IF NOT EXISTS player_data (
            uuid VARCHAR(36) PRIMARY KEY,
            username VARCHAR(16) NOT NULL,
            last_server VARCHAR(50) NOT NULL,
            last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            first_joined TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            INDEX idx_last_seen (last_seen),
            INDEX idx_username (username)
        )
        """;
    
    public Database(VelocityEssentials plugin) {
        this.plugin = plugin;
    }
    
    public boolean connect() {
        try {
            HikariConfig config = new HikariConfig();
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            config.setJdbcUrl("jdbc:mysql://" + plugin.getConfig().getDbHost() + ":" + 
                plugin.getConfig().getDbPort() + "/" + plugin.getConfig().getDbDatabase() + 
                "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
            config.setUsername(plugin.getConfig().getDbUsername());
            config.setPassword(plugin.getConfig().getDbPassword());
            
            // Pool settings
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setMaxLifetime(1800000); // 30 minutes
            config.setConnectionTimeout(10000); // 10 seconds
            config.setLeakDetectionThreshold(60000); // 1 minute
            
            // Performance settings
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");
            config.addDataSourceProperty("useLocalSessionState", "true");
            config.addDataSourceProperty("rewriteBatchedStatements", "true");
            config.addDataSourceProperty("cacheResultSetMetadata", "true");
            config.addDataSourceProperty("cacheServerConfiguration", "true");
            config.addDataSourceProperty("elideSetAutoCommits", "true");
            config.addDataSourceProperty("maintainTimeStats", "false");
            
            this.dataSource = new HikariDataSource(config);
            
            // Create tables
            createTables();
            
            plugin.getLogger().info("Successfully connected to MySQL database");
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().error("Failed to connect to MySQL database", e);
            return false;
        }
    }
    
    private void createTables() {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(CREATE_TABLE)) {
            stmt.executeUpdate();
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