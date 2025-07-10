package com.velocityessentials.listeners;

import com.velocityessentials.VelocityEssentials;
import com.velocityessentials.utils.PlayerTracker;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;

public class ServerSwitchListener {
    private final VelocityEssentials plugin;
    
    public ServerSwitchListener(VelocityEssentials plugin) {
        this.plugin = plugin;
    }
    
    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        RegisteredServer currentServer = player.getCurrentServer()
            .map(connection -> connection.getServer())
            .orElse(null);
        
        if (currentServer == null) return;
        
        RegisteredServer previousServer = event.getPreviousServer();
        String currentServerName = currentServer.getServerInfo().getName();
        
        // Save player's current server
        plugin.getPlayerData().savePlayerData(
            player.getUniqueId(),
            player.getUsername(),
            currentServerName
        );
        
        // Check permissions for silent mode
        if (player.hasPermission("velocityessentials.silent")) {
            return;
        }
        
        // Determine the type of connection
        if (previousServer == null) {
            // No previous server - check if this is a switch or real join
            PlayerTracker.DisconnectInfo disconnectInfo = plugin.getPlayerTracker().checkForSwitch(player);
            
            if (disconnectInfo != null && !disconnectInfo.serverName.equals(currentServerName)) {
                // This is a server switch
                handleServerSwitch(player, disconnectInfo.serverName, currentServerName);
            } else {
                // This is a real join
                handlePlayerJoin(player, currentServer);
            }
        } else {
            // Has previous server - internal switch
            String previousServerName = previousServer.getServerInfo().getName();
            if (!previousServerName.equals(currentServerName)) {
                handleServerSwitch(player, previousServerName, currentServerName);
            }
        }
    }
    
    private void handlePlayerJoin(Player player, RegisteredServer server) {
        String serverName = server.getServerInfo().getName();
        
        // Skip blacklisted servers
        if (plugin.getConfig().getBlacklistedServers().contains(serverName)) {
            return;
        }
        
        // Check if first time
        plugin.getPlayerData().isFirstJoin(player.getUniqueId()).thenAccept(isFirstTime -> {
            boolean showFirstTime = isFirstTime && 
                plugin.getConfig().isShowFirstTime() && 
                serverName.equals(plugin.getConfig().getFirstTimeServer());
            
            if (plugin.getConfig().isDebug()) {
                plugin.getLogger().info(player.getUsername() + " joined the network on " + serverName + 
                    (showFirstTime ? " (first time)" : ""));
            }
            
            // Send Discord notification
            if (plugin.getConfig().isDiscordEnabled()) {
                plugin.getDiscordWebhook().sendJoinMessage(player, server, showFirstTime);
            }
            
            // Send to backends
            if (plugin.getConfig().isCustomMessagesEnabled()) {
                plugin.getMessageHandler().sendJoinMessage(player, server, showFirstTime);
            }
        });
    }
    
    private void handleServerSwitch(Player player, String fromServer, String toServer) {
        // Skip if either server is blacklisted
        if (plugin.getConfig().getBlacklistedServers().contains(fromServer) ||
            plugin.getConfig().getBlacklistedServers().contains(toServer)) {
            return;
        }
        
        // Check cooldown
        if (plugin.getPlayerTracker().shouldSuppressSwitch(player.getUniqueId())) {
            return;
        }
        
        plugin.getPlayerTracker().recordSwitch(player.getUniqueId());
        
        if (plugin.getConfig().isDebug()) {
            plugin.getLogger().info(player.getUsername() + " switched from " + fromServer + " to " + toServer);
        }
        
        // Send Discord notification
        if (plugin.getConfig().isDiscordEnabled()) {
            plugin.getDiscordWebhook().sendSwitchMessage(player, fromServer, toServer);
        }
        
        // Send to backends
        if (plugin.getConfig().isCustomMessagesEnabled()) {
            // Suppress vanilla messages
            plugin.getPlayerTracker().suppressPlayer(player.getUsername());
            plugin.getMessageHandler().sendSwitchMessage(player, fromServer, toServer);
        }
    }
}