package com.velocityessentials.modules.afk;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.velocityessentials.VelocityEssentials;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AFKHandler {
    private final VelocityEssentials plugin;
    private final Set<UUID> afkPlayers = ConcurrentHashMap.newKeySet();
    
    public AFKHandler(VelocityEssentials plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Handle AFK status change from a backend server
     */
    public void handleAFKStatus(String uuidString, String playerName, boolean isAfk, boolean manual, String sourceServer) {
        UUID uuid = UUID.fromString(uuidString);
        
        // Update our tracking
        if (isAfk) {
            afkPlayers.add(uuid);
        } else {
            afkPlayers.remove(uuid);
        }
        
        if (plugin.getConfig().isDebug()) {
            plugin.getLogger().info("AFK status change: " + playerName + " is " + 
                (isAfk ? "now" : "no longer") + " AFK (from " + sourceServer + ")");
        }
        
        // Send to Discord if enabled
        if (plugin.getConfig().isDiscordEnabled()) {
            plugin.getDiscordWebhook().sendAFKMessage(playerName, isAfk, manual);
        }
        
        // Broadcast to all servers
        broadcastAFKMessage(playerName, isAfk, manual);
    }
    
    /**
     * Broadcast AFK message to all servers
     */
    private void broadcastAFKMessage(String playerName, boolean isAfk, boolean manual) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("network_afk");
        out.writeUTF(playerName);
        out.writeBoolean(isAfk);
        out.writeBoolean(manual);
        
        // Send to all connected servers
        for (RegisteredServer server : plugin.getServer().getAllServers()) {
            if (!server.getPlayersConnected().isEmpty()) {
                server.sendPluginMessage(VelocityEssentials.CHANNEL, out.toByteArray());
            }
        }
    }
    
    /**
     * Check if a player is AFK
     */
    public boolean isAFK(UUID uuid) {
        return afkPlayers.contains(uuid);
    }
    
    /**
     * Check if a player is AFK
     */
    public boolean isAFK(Player player) {
        return isAFK(player.getUniqueId());
    }
    
    /**
     * Remove player from AFK list (used when they disconnect)
     */
    public void removePlayer(UUID uuid) {
        afkPlayers.remove(uuid);
    }
    
    /**
     * Get all AFK players
     */
    public Set<UUID> getAFKPlayers() {
        return new HashSet<>(afkPlayers);
    }
    
    /**
     * Get count of AFK players
     */
    public int getAFKCount() {
        return afkPlayers.size();
    }
}