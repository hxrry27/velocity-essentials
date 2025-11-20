package com.velocityessentials.listeners;

import com.velocityessentials.VelocityEssentials;
import com.velocityessentials.utils.MessageUtil;
import com.velocityessentials.utils.PlayerTracker;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class PlayerListener {
    private final VelocityEssentials plugin;
    
    public PlayerListener(VelocityEssentials plugin) {
        this.plugin = plugin;
    }
    
    @Subscribe(order = PostOrder.EARLY)
    public void onPlayerChooseInitialServer(PlayerChooseInitialServerEvent event) {
        Player player = event.getPlayer();
        
        // check for server switch (player reconnecting quickly) - not ideal, but presumably a server swap will always be faster than someone timing out and logging back in
        PlayerTracker.DisconnectInfo switchInfo = plugin.getPlayerTracker().checkForSwitch(player);
        
        if (switchInfo != null) {
            // this is a server switch, not a real join
            if (plugin.getConfig().isDebug()) {
                plugin.getLogger().info(player.getUsername() + " is switching servers, not a real join");
            }
            // the ServerSwitchListener will handle this
            return;
        }
        
        // determine initial server
        RegisteredServer targetServer = determineInitialServer(player);
        if (targetServer != null) {
            event.setInitialServer(targetServer);
        }
    }
    
    @Subscribe
    public void onPlayerDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        
        // get their current server
        player.getCurrentServer().ifPresent(connection -> {
            RegisteredServer server = connection.getServer();
            
            // record the disconnect for switch detection
            plugin.getPlayerTracker().recordDisconnect(player, server);
            
            // schedule the actual leave notification after switch detection delay
            plugin.getServer().getScheduler()
                .buildTask(plugin, () -> {
                    // check if they reconnected (would have been removed by PlayerChooseInitialServerEvent)
                    PlayerTracker.DisconnectInfo info = plugin.getPlayerTracker().getAndRemoveDisconnect(player.getUniqueId());
                    
                    if (info != null) {
                        // real leave - send network_leave with minimessage format
                        String message = String.format(
                            "<dark_gray>[<red>-</red>]</dark_gray> <red>%s</red> <yellow>left the game</yellow>",
                            player.getUsername()
                        );
                        
                        if (plugin.getConfig().isDiscordEnabled()) {
                            plugin.getDiscordWebhook().sendLeaveMessage(player, server);
                        }
                        
                        plugin.getMessageHandler().sendNetworkMessage("network_leave", 
                            player.getUsername(), server.getServerInfo().getName(), message);
                    }
                })
                .delay(1100, TimeUnit.MILLISECONDS)
                .schedule();
        });
    }
    
    private RegisteredServer determineInitialServer(Player player) {
        // Check if server memory is enabled
        if (!plugin.getConfig().isServerMemoryEnabled()) {
            return getServer(plugin.getConfig().getFallbackServer());
        }
        
        // Check bypass permission
        if (player.hasPermission(plugin.getConfig().getBypassPermission())) {
            return getServer(plugin.getConfig().getFallbackServer());
        }
        
        // Try to get last server
        try {
            String lastServer = plugin.getPlayerData()
                .getLastServer(player.getUniqueId())
                .get(500, TimeUnit.MILLISECONDS);
            
            if (lastServer != null && !plugin.getConfig().getBlacklistedServers().contains(lastServer)) {
                Optional<RegisteredServer> server = plugin.getServer().getServer(lastServer);
                if (server.isPresent()) {
                    // Send message
                    player.sendMessage(MessageUtil.parse(
                        plugin.getConfig().getMessage("sending-to-last-server", "{server}", lastServer)
                    ));
                    return server.get();
                }
            }
        } catch (Exception e) {
            if (plugin.getConfig().isDebug()) {
                plugin.getLogger().warn("Failed to get last server for " + player.getUsername(), e);
            }
        }
        
        // Check if first join
        try {
            boolean isFirstJoin = plugin.getPlayerData()
                .isFirstJoin(player.getUniqueId())
                .get(500, TimeUnit.MILLISECONDS);
            
            if (isFirstJoin) {
                player.sendMessage(MessageUtil.parse(plugin.getConfig().getMessage("first-join")));
                return getServer(plugin.getConfig().getFirstJoinServer());
            }
        } catch (Exception e) {
            if (plugin.getConfig().isDebug()) {
                plugin.getLogger().warn("Failed to check first join for " + player.getUsername(), e);
            }
        }
        
        // Default to fallback
        player.sendMessage(MessageUtil.parse(plugin.getConfig().getMessage("sending-to-fallback")));
        return getServer(plugin.getConfig().getFallbackServer());
    }
    
    private RegisteredServer getServer(String name) {
        return plugin.getServer().getServer(name).orElse(null);
    }
}