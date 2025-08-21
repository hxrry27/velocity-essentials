package com.velocityessentials.modules.afk;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.velocityessentials.VelocityEssentials;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AFKHandler {
    private final VelocityEssentials plugin;
    private final Set<UUID> afkPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> afkMessages = new ConcurrentHashMap<>();
    
    public AFKHandler(VelocityEssentials plugin) {
        this.plugin = plugin;
    }
    
    /**
     * handle AFK status change from a backend server
     */
    public void handleAFKStatus(String uuidString, String playerName, boolean isAfk, boolean manual, String message, String sourceServer) {
        UUID uuid = UUID.fromString(uuidString);
        
        // update our tracking
        if (isAfk) {
            afkPlayers.add(uuid);
            if (message != null && !message.isEmpty()) {
                afkMessages.put(uuid, message);
            }
        } else {
            afkPlayers.remove(uuid);
            afkMessages.remove(uuid);
        }
        
        if (plugin.getConfig().isDebug()) {
            plugin.getLogger().info("AFK status change: " + playerName + " is " + 
                (isAfk ? "now" : "no longer") + " AFK" +
                (message != null && !message.isEmpty() ? " (Message: " + message + ")" : "") +
                " (from " + sourceServer + ")");
        }
        
        // send to Discord if enabled
        if (plugin.getConfig().isDiscordEnabled()) {
            plugin.getDiscordWebhook().sendAFKMessage(playerName, isAfk, manual, message);
        }
        
        // broadcast
        broadcastAFKMessage(playerName, isAfk, manual, message);
    }
    
    /**
     * broadcast AFK message to all servers TODO: add whitelist / blacklist
     */
    private void broadcastAFKMessage(String playerName, boolean isAfk, boolean manual, String message) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("network_afk_message");
        out.writeUTF(playerName);
        out.writeBoolean(isAfk);
        out.writeBoolean(manual);
        out.writeUTF(message != null ? message : "");
        
        // send to all connected servers
        for (RegisteredServer server : plugin.getServer().getAllServers()) {
            if (!server.getPlayersConnected().isEmpty()) {
                server.sendPluginMessage(VelocityEssentials.CHANNEL, out.toByteArray());
            }
        }
    }
    
    /**
     * check if a UUID is AFK 
     */
    public boolean isAFK(UUID uuid) {
        return afkPlayers.contains(uuid);
    }
    
    /**
     * check if a player is AFK
     */
    public boolean isAFK(Player player) {
        return isAFK(player.getUniqueId());
    }
    
    /**
     * get AFK message for a player UUID
     */
    public String getAFKMessage(UUID uuid) {
        return afkMessages.get(uuid);
    }
    
    /**
     * get AFK message for a player
     */
    public String getAFKMessage(Player player) {
        return getAFKMessage(player.getUniqueId());
    }
    
    /**
     * remove player from AFK list (used when they onPlayerDisconnect)
     */
    public void removePlayer(UUID uuid) {
        afkPlayers.remove(uuid);
        afkMessages.remove(uuid);
    }
    
    /**
     * get all AFK players UUID
     */
    public Set<UUID> getAFKPlayers() {
        return new HashSet<>(afkPlayers);
    }
    
    /**
     * get count of AFK players
     */
    public int getAFKCount() {
        return afkPlayers.size();
    }
}