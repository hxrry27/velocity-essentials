package com.velocityessentials.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

public class StatsDatabase {
    private final HikariDataSource dataSource;
    
    public StatsDatabase(String host, String database, String user, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://" + host + "/" + database);
        config.setUsername(user);
        config.setPassword(password);
        config.setMaximumPoolSize(10);
        
        this.dataSource = new HikariDataSource(config);
    }
    
    public CompletableFuture<Boolean> createEvent(String name, String stat, 
            LocalDateTime start, LocalDateTime end, String creator) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO events (name, display_name, stat_key, start_time, end_time, created_by) " +
                     "VALUES (?, ?, ?, ?, ?, ?)"
                 )) {
                ps.setString(1, name.toLowerCase().replace(" ", "_"));
                ps.setString(2, name);
                ps.setString(3, stat);
                ps.setTimestamp(4, Timestamp.valueOf(start));
                ps.setTimestamp(5, Timestamp.valueOf(end));
                ps.setString(6, creator);
                
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        });
    }
    
    // ... other database methods
}