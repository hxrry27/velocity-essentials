package com.velocityessentials.database;

import com.velocityessentials.VelocityEssentials;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PlayerData {
    private final VelocityEssentials plugin;
    
    // SQLite compatible queries
    private static final String GET_LAST_SERVER = """
        SELECT server_name FROM last_server 
        WHERE uuid = ? AND last_seen > datetime('now', '-' || ? || ' days')
        """;
    
    private static final String SAVE_PLAYER_DATA = """
        INSERT INTO last_server (uuid, username, server_name) 
        VALUES (?, ?, ?)
        ON CONFLICT(uuid) DO UPDATE SET 
            username = excluded.username, 
            server_name = excluded.server_name, 
            last_seen = CURRENT_TIMESTAMP
        """;
    
    private static final String CHECK_FIRST_JOIN = """
        SELECT COUNT(*) as count FROM last_server WHERE uuid = ?
        """;
    
    private static final String DELETE_OLD_ENTRIES = """
        DELETE FROM last_server 
        WHERE last_seen < datetime('now', '-' || ? || ' days')
        """;
    
    private static final String GET_PLAYER_INFO = """
        SELECT username, server_name as last_server, last_seen, first_joined 
        FROM last_server WHERE username = ?
        """;
    
    public PlayerData(VelocityEssentials plugin) {
        this.plugin = plugin;
    }
    
    public CompletableFuture<String> getLastServer(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = plugin.getDatabase().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(GET_LAST_SERVER)) {
                
                stmt.setString(1, uuid.toString());
                stmt.setInt(2, plugin.getConfig().getRememberDays());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("server_name");
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().error("Failed to get last server for UUID: " + uuid, e);
            }
            return null;
        });
    }
    
    public CompletableFuture<Void> savePlayerData(UUID uuid, String username, String server) {
        return CompletableFuture.runAsync(() -> {
            // Don't save blacklisted servers
            if (plugin.getConfig().getBlacklistedServers().contains(server)) {
                return;
            }
            
            try (Connection conn = plugin.getDatabase().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SAVE_PLAYER_DATA)) {
                
                stmt.setString(1, uuid.toString());
                stmt.setString(2, username);
                stmt.setString(3, server);
                
                stmt.executeUpdate();
                
                if (plugin.getConfig().isDebug()) {
                    plugin.getLogger().info("Saved player data for " + username + " on server " + server);
                }
                
            } catch (SQLException e) {
                plugin.getLogger().error("Failed to save player data for UUID: " + uuid, e);
            }
        });
    }
    
    public CompletableFuture<Boolean> isFirstJoin(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = plugin.getDatabase().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(CHECK_FIRST_JOIN)) {
                
                stmt.setString(1, uuid.toString());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("count") == 0;
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().error("Failed to check first join for UUID: " + uuid, e);
            }
            return false;
        });
    }
    
    public CompletableFuture<Integer> cleanupOldEntries() {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = plugin.getDatabase().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(DELETE_OLD_ENTRIES)) {
                
                stmt.setInt(1, plugin.getConfig().getRememberDays() * 2); // Keep data for double the remember time
                
                int deleted = stmt.executeUpdate();
                if (deleted > 0 && plugin.getConfig().isDebug()) {
                    plugin.getLogger().info("Cleaned up " + deleted + " old player entries");
                }
                return deleted;
                
            } catch (SQLException e) {
                plugin.getLogger().error("Failed to cleanup old entries", e);
                return 0;
            }
        });
    }
    
    public CompletableFuture<PlayerInfo> getPlayerInfo(String username) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = plugin.getDatabase().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(GET_PLAYER_INFO)) {
                
                stmt.setString(1, username);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return new PlayerInfo(
                            rs.getString("username"),
                            rs.getString("last_server"),
                            rs.getTimestamp("last_seen"),
                            rs.getTimestamp("first_joined")
                        );
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().error("Failed to get player info for: " + username, e);
            }
            return null;
        });
    }
    
    public static class PlayerInfo {
        public final String username;
        public final String lastServer;
        public final java.sql.Timestamp lastSeen;
        public final java.sql.Timestamp firstJoined;
        
        public PlayerInfo(String username, String lastServer, java.sql.Timestamp lastSeen, java.sql.Timestamp firstJoined) {
            this.username = username;
            this.lastServer = lastServer;
            this.lastSeen = lastSeen;
            this.firstJoined = firstJoined;
        }
    }
}