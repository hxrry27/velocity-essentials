package com.velocityessentials.backend.chat;

import com.velocityessentials.backend.VelocityEssentialsBackend;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * manages muted players and mute checking
 */
public class MuteManager {
    
    private final VelocityEssentialsBackend plugin;
    private final Map<UUID, MuteInfo> mutedPlayers;
    
    private boolean enabled;
    private String mutedMessage;
    private boolean showReason;
    private boolean syncAcrossServers;
    
    public MuteManager(VelocityEssentialsBackend plugin) {
        this.plugin = plugin;
        this.mutedPlayers = new ConcurrentHashMap<>();
        loadConfig();
    }
    
    /**
     * load mute config from main config
     */
    private void loadConfig() {
        ConfigurationSection chatSection = plugin.getConfig().getConfigurationSection("chat");
        if (chatSection == null) {
            setDefaults();
            return;
        }
        
        ConfigurationSection modSection = chatSection.getConfigurationSection("moderation");
        if (modSection == null) {
            setDefaults();
            return;
        }
        
        ConfigurationSection mutesSection = modSection.getConfigurationSection("mutes");
        if (mutesSection == null) {
            setDefaults();
            return;
        }
        
        enabled = mutesSection.getBoolean("enabled", true);
        mutedMessage = mutesSection.getString("muted-message", 
            "<red>You are muted and cannot send chat messages.</red>");
        showReason = mutesSection.getBoolean("show-reason", true);
        syncAcrossServers = mutesSection.getBoolean("sync-across-servers", true);
        
        if (plugin.debug) {
            plugin.getLogger().info("mute system enabled: " + enabled);
        }
    }
    
    private void setDefaults() {
        enabled = true;
        mutedMessage = "<red>You are muted and cannot send chat messages.</red>";
        showReason = true;
        syncAcrossServers = true;
    }
    
    /**
     * check if player is muted
     */
    public boolean isMuted(Player player) {
        if (!enabled) {
            return false;
        }
        
        MuteInfo info = mutedPlayers.get(player.getUniqueId());
        
        if (info == null) {
            return false;
        }
        
        // check if temporary mute has expired
        if (info.isTemporary() && info.hasExpired()) {
            unmute(player.getUniqueId());
            return false;
        }
        
        return true;
    }
    
    /**
     * check if player is muted by uuid
     */
    public boolean isMuted(UUID uuid) {
        if (!enabled) {
            return false;
        }
        
        MuteInfo info = mutedPlayers.get(uuid);
        
        if (info == null) {
            return false;
        }
        
        if (info.isTemporary() && info.hasExpired()) {
            unmute(uuid);
            return false;
        }
        
        return true;
    }
    
    /**
     * get mute info for player
     */
    public MuteInfo getMuteInfo(Player player) {
        return mutedPlayers.get(player.getUniqueId());
    }
    
    /**
     * mute a player (called when receiving mute from velocity)
     */
    public void mute(UUID uuid, String reason, long expiresAt) {
        MuteInfo info = new MuteInfo(uuid, reason, expiresAt);
        mutedPlayers.put(uuid, info);
        
        if (plugin.debug) {
            plugin.getLogger().info("muted player: " + uuid + 
                (info.isTemporary() ? " (temporary)" : " (permanent)"));
        }
    }
    
    /**
     * unmute a player
     */
    public void unmute(UUID uuid) {
        mutedPlayers.remove(uuid);
        
        if (plugin.debug) {
            plugin.getLogger().info("unmuted player: " + uuid);
        }
    }
    
    /**
     * get muted message to send to player
     */
    public String getMutedMessage(Player player) {
        MuteInfo info = getMuteInfo(player);
        
        if (info == null) {
            return mutedMessage;
        }
        
        if (!showReason || info.getReason() == null || info.getReason().isEmpty()) {
            return mutedMessage;
        }
        
        // include reason
        return mutedMessage + "\n<gray>Reason: <white>" + info.getReason() + "</white></gray>";
    }
    
    /**
     * check if mutes are enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * check if mutes sync across servers
     */
    public boolean isSyncAcrossServers() {
        return syncAcrossServers;
    }
    
    /**
     * reload config
     */
    public void reload() {
        loadConfig();
    }
    
    /**
     * represents mute information for a player
     */
    public static class MuteInfo {
        private final UUID playerId;
        private final String reason;
        private final long expiresAt; // 0 = permanent
        private final long mutedAt;
        
        public MuteInfo(UUID playerId, String reason, long expiresAt) {
            this.playerId = playerId;
            this.reason = reason;
            this.expiresAt = expiresAt;
            this.mutedAt = System.currentTimeMillis();
        }
        
        public UUID getPlayerId() { return playerId; }
        public String getReason() { return reason; }
        public long getExpiresAt() { return expiresAt; }
        public long getMutedAt() { return mutedAt; }
        
        public boolean isTemporary() {
            return expiresAt > 0;
        }
        
        public boolean hasExpired() {
            return isTemporary() && System.currentTimeMillis() >= expiresAt;
        }
        
        public long getRemainingTime() {
            if (!isTemporary()) {
                return -1; // permanent
            }
            
            long remaining = expiresAt - System.currentTimeMillis();
            return Math.max(0, remaining);
        }
    }
}