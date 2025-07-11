package com.velocityessentials.listeners;

import com.velocityessentials.VelocityEssentials;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;

public class ChatListener {
    private final VelocityEssentials plugin;
    
    public ChatListener(VelocityEssentials plugin) {
        this.plugin = plugin;
    }
    
    @Subscribe
    public void onPlayerChat(PlayerChatEvent event) {
        // Don't relay if Discord is disabled
        if (!plugin.getConfig().isDiscordEnabled()) return;
        
        // Don't relay if chat relay is disabled
        if (!plugin.getConfig().isChatRelayEnabled()) return;
        
        Player player = event.getPlayer();
        String message = event.getMessage();
        
        // Skip commands
        if (message.startsWith("/")) return;
        
        // Get current server
        player.getCurrentServer().ifPresent(connection -> {
            String serverName = connection.getServer().getServerInfo().getName();
            
            // Skip blacklisted servers
            if (plugin.getConfig().getBlacklistedServers().contains(serverName)) {
                return;
            }
            
            // Send to Discord
            plugin.getDiscordWebhook().sendChatMessage(player, serverName, message);
        });
    }
}