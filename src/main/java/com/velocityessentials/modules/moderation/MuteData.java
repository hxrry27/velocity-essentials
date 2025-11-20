package com.velocityessentials.modules.moderation;

import com.velocityessentials.VelocityEssentials;
import com.velocityessentials.database.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * manages mute data in database and syncs to paper servers
 */
public class MuteData {
    
    private final VelocityEssentials plugin;
    private final Database database;
    
    public MuteData(VelocityEssentials plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
        createTables();
    }
    
    /**
     * create mutes table
     */
    private void createTables() {
        String sql = """
            CREATE TABLE IF NOT EXISTS mutes (
                player_uuid TEXT PRIMARY KEY,
                player_name TEXT NOT NULL,
                reason TEXT,
                muted_by TEXT NOT NULL,
                muted_at BIGINT NOT NULL,
                expires_at BIGINT NOT NULL,
                active BOOLEAN NOT NULL DEFAULT 1
            )
        """;
        
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.execute();
        } catch (SQLException e) {
            plugin.getLogger().error("failed to create mutes table", e);
        }
    }
    
    /**
     * mute a player
     */
    public CompletableFuture<Boolean> mutePlayer(UUID uuid, String username, String reason, 
                                                  String mutedBy, long expiresAt) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                INSERT OR REPLACE INTO mutes 
                (player_uuid, player_name, reason, muted_by, muted_at, expires_at, active)
                VALUES (?, ?, ?, ?, ?, ?, 1)
            """;
            
            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, username);
                stmt.setString(3, reason);
                stmt.setString(4, mutedBy);
                stmt.setLong(5, System.currentTimeMillis());
                stmt.setLong(6, expiresAt);
                stmt.executeUpdate();
                
                // sync to all paper servers
                syncMuteToServers(uuid, reason, expiresAt);
                
                return true;
            } catch (SQLException e) {
                plugin.getLogger().error("failed to mute player: " + username, e);
                return false;
            }
        });
    }
    
    /**
     * unmute a player
     */
    public CompletableFuture<Boolean> unmutePlayer(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE mutes SET active = 0 WHERE player_uuid = ?";
            
            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                int updated = stmt.executeUpdate();
                
                if (updated > 0) {
                    // sync unmute to all paper servers
                    syncUnmuteToServers(uuid);
                    return true;
                }
                
                return false;
            } catch (SQLException e) {
                plugin.getLogger().error("failed to unmute player: " + uuid, e);
                return false;
            }
        });
    }
    
    /**
     * check if player is muted
     */
    public CompletableFuture<MuteInfo> getMute(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                SELECT player_name, reason, muted_by, muted_at, expires_at
                FROM mutes
                WHERE player_uuid = ? AND active = 1
            """;
            
            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        long expiresAt = rs.getLong("expires_at");
                        
                        // check if expired
                        if (expiresAt > 0 && System.currentTimeMillis() >= expiresAt) {
                            unmutePlayer(uuid);
                            return null;
                        }
                        
                        return new MuteInfo(
                            uuid,
                            rs.getString("player_name"),
                            rs.getString("reason"),
                            rs.getString("muted_by"),
                            rs.getLong("muted_at"),
                            expiresAt
                        );
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().error("failed to get mute for: " + uuid, e);
            }
            
            return null;
        });
    }
    
    /**
     * get all active mutes
     */
    public CompletableFuture<Map<UUID, MuteInfo>> getAllMutes() {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, MuteInfo> mutes = new HashMap<>();
            String sql = """
                SELECT player_uuid, player_name, reason, muted_by, muted_at, expires_at
                FROM mutes
                WHERE active = 1
            """;
            
            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                    long expiresAt = rs.getLong("expires_at");
                    
                    // skip expired
                    if (expiresAt > 0 && System.currentTimeMillis() >= expiresAt) {
                        continue;
                    }
                    
                    MuteInfo info = new MuteInfo(
                        uuid,
                        rs.getString("player_name"),
                        rs.getString("reason"),
                        rs.getString("muted_by"),
                        rs.getLong("muted_at"),
                        expiresAt
                    );
                    
                    mutes.put(uuid, info);
                }
            } catch (SQLException e) {
                plugin.getLogger().error("failed to get all mutes", e);
            }
            
            return mutes;
        });
    }
    
    /**
     * sync mute to all paper servers via plugin messaging
     */
    private void syncMuteToServers(UUID uuid, String reason, long expiresAt) {
        com.google.common.io.ByteArrayDataOutput out = 
            com.google.common.io.ByteStreams.newDataOutput();
        
        out.writeUTF("mute_player");
        out.writeUTF(uuid.toString());
        out.writeUTF(reason == null ? "" : reason);
        out.writeLong(expiresAt);
        
        byte[] data = out.toByteArray();
        
        // send to all servers
        for (com.velocitypowered.api.proxy.server.RegisteredServer server : 
             plugin.getServer().getAllServers()) {
            
            if (!server.getPlayersConnected().isEmpty()) {
                server.sendPluginMessage(VelocityEssentials.CHANNEL, data);
            }
        }
        
        if (plugin.getConfig().isDebug()) {
            plugin.getLogger().info("synced mute to all servers: " + uuid);
        }
    }
    
    /**
     * sync unmute to all paper servers via plugin messaging
     */
    private void syncUnmuteToServers(UUID uuid) {
        com.google.common.io.ByteArrayDataOutput out = 
            com.google.common.io.ByteStreams.newDataOutput();
        
        out.writeUTF("unmute_player");
        out.writeUTF(uuid.toString());
        
        byte[] data = out.toByteArray();
        
        // send to all servers
        for (com.velocitypowered.api.proxy.server.RegisteredServer server : 
             plugin.getServer().getAllServers()) {
            
            if (!server.getPlayersConnected().isEmpty()) {
                server.sendPluginMessage(VelocityEssentials.CHANNEL, data);
            }
        }
        
        if (plugin.getConfig().isDebug()) {
            plugin.getLogger().info("synced unmute to all servers: " + uuid);
        }
    }
    
    /**
     * represents mute information
     */
    public static class MuteInfo {
        private final UUID playerId;
        private final String playerName;
        private final String reason;
        private final String mutedBy;
        private final long mutedAt;
        private final long expiresAt;
        
        public MuteInfo(UUID playerId, String playerName, String reason, String mutedBy, 
                       long mutedAt, long expiresAt) {
            this.playerId = playerId;
            this.playerName = playerName;
            this.reason = reason;
            this.mutedBy = mutedBy;
            this.mutedAt = mutedAt;
            this.expiresAt = expiresAt;
        }
        
        public UUID getPlayerId() { return playerId; }
        public String getPlayerName() { return playerName; }
        public String getReason() { return reason; }
        public String getMutedBy() { return mutedBy; }
        public long getMutedAt() { return mutedAt; }
        public long getExpiresAt() { return expiresAt; }
        
        public boolean isPermanent() {
            return expiresAt == 0;
        }
        
        public long getRemainingTime() {
            if (isPermanent()) return -1;
            return Math.max(0, expiresAt - System.currentTimeMillis());
        }
    }
}