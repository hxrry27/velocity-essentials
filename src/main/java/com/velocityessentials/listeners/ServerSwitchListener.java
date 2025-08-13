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
        
        // Skip blacklisted servers (optional)
        if (plugin.getConfig().getBlacklistedServers().contains(serverName)) {
            return;
        }
        
        // Skip if join messages disabled (optional)
        if (!plugin.getConfig().isShowJoinMessages()) {
            return;
        }
        
        plugin.getPlayerData().isFirstJoin(player.getUniqueId()).thenAccept(isFirstTime -> {
            String message = isFirstTime ?
                "§8[§6★§8] §6" + player.getUsername() + " §ejoined for the first time!" :
                "§8[§a+§8] §a" + player.getUsername() + " §ejoined the game";
                
            // Send to Discord
            if (plugin.getConfig().isDiscordEnabled()) {
                plugin.getDiscordWebhook().sendJoinMessage(player, server, isFirstTime);
            }
            
            // Send network_join to ALL servers
            plugin.getMessageHandler().sendNetworkMessage("network_join", 
                player.getUsername(), serverName, message);
        });
    }
    
    private void handleServerSwitch(Player player, String fromServer, String toServer) {
        String message = "§8[§6↔§8] §6" + player.getUsername() + 
            " §eswitched servers: §f" + fromServer + " §7→ §f" + toServer;
            
        // Send to Discord  
        if (plugin.getConfig().isDiscordEnabled()) {
            plugin.getDiscordWebhook().sendSwitchMessage(player, fromServer, toServer);
        }
        
        // Send network_switch to ALL servers
        plugin.getMessageHandler().sendNetworkMessage("network_switch", 
            player.getUsername(), toServer, message);
    }
}