package com.velocityessentials.utils;

import com.velocityessentials.VelocityEssentials;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class PlayerTracker {
    private final VelocityEssentials plugin;
    
    // Track recent disconnects to detect switches
    private final Map<UUID, DisconnectInfo> recentDisconnects = new ConcurrentHashMap<>();
    
    // Track recent switches to prevent spam
    private final Map<UUID, Long> recentSwitches = new ConcurrentHashMap<>();
    
    // Track suppress messages for backend
    private final Map<String, Long> suppressedPlayers = new ConcurrentHashMap<>();
    
    private static final long SWITCH_DETECTION_DELAY = 1000; // 1 second
    private static final long SWITCH_COOLDOWN = 3000; // 3 seconds
    private static final long SUPPRESS_DURATION = 5000; // 5 seconds
    
    public PlayerTracker(VelocityEssentials plugin) {
        this.plugin = plugin;
        
        // Schedule cleanup task
        plugin.getServer().getScheduler()
            .buildTask(plugin, this::cleanup)
            .repeat(30, TimeUnit.SECONDS)
            .schedule();
    }
    
    /**
     * Record a player disconnect
     */
    public void recordDisconnect(Player player, RegisteredServer server) {
        if (server == null) return;
        
        DisconnectInfo info = new DisconnectInfo(
            player.getUniqueId(),
            player.getUsername(),
            server.getServerInfo().getName(),
            System.currentTimeMillis()
        );
        
        recentDisconnects.put(player.getUniqueId(), info);
        
        if (plugin.getConfig().isDebug()) {
            plugin.getLogger().info("Recorded disconnect for " + player.getUsername() + " from " + server.getServerInfo().getName());
        }
    }
    
    /**
     * Check if a join is actually a server switch
     */
    public DisconnectInfo checkForSwitch(Player player) {
        DisconnectInfo info = recentDisconnects.get(player.getUniqueId());
        
        if (info != null) {
            long timeSinceDisconnect = System.currentTimeMillis() - info.disconnectTime;
            
            if (timeSinceDisconnect <= SWITCH_DETECTION_DELAY) {
                // This is likely a switch
                recentDisconnects.remove(player.getUniqueId());
                return info;
            }
        }
        
        return null;
    }
    
    /**
     * Check if we should suppress switch spam
     */
    public boolean shouldSuppressSwitch(UUID uuid) {
        Long lastSwitch = recentSwitches.get(uuid);
        if (lastSwitch == null) return false;
        
        return System.currentTimeMillis() - lastSwitch < SWITCH_COOLDOWN;
    }
    
    /**
     * Record a switch to prevent spam
     */
    public void recordSwitch(UUID uuid) {
        recentSwitches.put(uuid, System.currentTimeMillis());
    }
    
    /**
     * Mark a player for message suppression on backend
     */
    public void suppressPlayer(String username) {
        suppressedPlayers.put(username.toLowerCase(), System.currentTimeMillis());
    }
    
    /**
     * Check if a player should be suppressed
     */
    public boolean isSuppressed(String username) {
        Long suppressTime = suppressedPlayers.get(username.toLowerCase());
        if (suppressTime == null) return false;
        
        return System.currentTimeMillis() - suppressTime < SUPPRESS_DURATION;
    }
    
    /**
     * Get disconnect info and remove it
     */
    public DisconnectInfo getAndRemoveDisconnect(UUID uuid) {
        return recentDisconnects.remove(uuid);
    }
    
    /**
     * Clean up old entries
     */
    private void cleanup() {
        long now = System.currentTimeMillis();
        
        // Clean up disconnects older than switch detection delay
        recentDisconnects.entrySet().removeIf(entry -> 
            now - entry.getValue().disconnectTime > SWITCH_DETECTION_DELAY * 2
        );
        
        // Clean up switch cooldowns
        recentSwitches.entrySet().removeIf(entry -> 
            now - entry.getValue() > SWITCH_COOLDOWN
        );
        
        // Clean up suppressed players
        suppressedPlayers.entrySet().removeIf(entry -> 
            now - entry.getValue() > SUPPRESS_DURATION
        );
    }
    
    public static class DisconnectInfo {
        public final UUID uuid;
        public final String username;
        public final String serverName;
        public final long disconnectTime;
        
        public DisconnectInfo(UUID uuid, String username, String serverName, long disconnectTime) {
            this.uuid = uuid;
            this.username = username;
            this.serverName = serverName;
            this.disconnectTime = disconnectTime;
        }
    }
}