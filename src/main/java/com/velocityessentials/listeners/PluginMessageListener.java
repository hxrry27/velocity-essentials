package com.velocityessentials.listeners;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.velocityessentials.VelocityEssentials;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;

import java.util.UUID;

public class PluginMessageListener {
    private final VelocityEssentials plugin;
   
    public PluginMessageListener(VelocityEssentials plugin) {
        this.plugin = plugin;
    }
   
@Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(VelocityEssentials.CHANNEL)) return;
       
        ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
        String subchannel = in.readUTF();
       
        switch (subchannel) {
            case "chat" -> handleChat(in);
            case "afk_status" -> handleAFKStatus(in);
            default -> {
                if (plugin.getConfig().isDebug()) {
                    plugin.getLogger().info("Unknown subchannel: " + subchannel);
                }
            }
        }
        
        event.setResult(PluginMessageEvent.ForwardResult.handled());
    }
    
    private void handleChat(ByteArrayDataInput in) {
        String uuid = in.readUTF();
        String username = in.readUTF();
        String prefix = in.readUTF();
        String message = in.readUTF();
        String serverName = in.readUTF();
       
        // Get the player
        plugin.getServer().getPlayer(UUID.fromString(uuid)).ifPresent(player -> {
            // Send to Discord with the pre-formatted prefix from backend
            plugin.getDiscordWebhook().sendFormattedChatMessage(
                player, serverName, prefix, message
            );
        });
    }
    
    private void handleAFKStatus(ByteArrayDataInput in) {
        String uuid = in.readUTF();
        String playerName = in.readUTF();
        boolean isAfk = in.readBoolean();
        boolean manual = in.readBoolean();
        String sourceServer = in.readUTF();
        
        // Handle the AFK status change
        if (plugin.getAFKHandler() != null) {
            plugin.getAFKHandler().handleAFKStatus(uuid, playerName, isAfk, manual, sourceServer);
        }
        
        if (plugin.getConfig().isDebug()) {
            plugin.getLogger().info("Received AFK status: " + playerName + " = " + isAfk + " from " + sourceServer);
        }
    }
}