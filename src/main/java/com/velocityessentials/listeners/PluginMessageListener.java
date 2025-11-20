package com.velocityessentials.listeners;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.velocityessentials.VelocityEssentials;
import com.velocityessentials.relay.ChatBroadcaster;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;

public class PluginMessageListener {
    private final VelocityEssentials plugin;
    private final ChatBroadcaster chatBroadcaster;
   
    public PluginMessageListener(VelocityEssentials plugin) {
        this.plugin = plugin;
        this.chatBroadcaster = new ChatBroadcaster(plugin);
    }
   
    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(VelocityEssentials.CHANNEL)) return;
       
        ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
        String subchannel = in.readUTF();
       
        switch (subchannel) {
            case "chat" -> chatBroadcaster.handleChatMessage(in); 
            case "afk_status" -> handleAFKStatus(in);
            case "afk_status_message" -> handleAFKStatusWithMessage(in);
            default -> {
                if (plugin.getConfig().isDebug()) {
                    plugin.getLogger().info("Unknown subchannel: " + subchannel);
                }
            }
        }
        
        event.setResult(PluginMessageEvent.ForwardResult.handled());
    }
    
    private void handleAFKStatus(ByteArrayDataInput in) {
        String uuid = in.readUTF();
        String playerName = in.readUTF();
        boolean isAfk = in.readBoolean();
        boolean manual = in.readBoolean();
        String sourceServer = in.readUTF();
        
        if (plugin.getAFKHandler() != null) {
            plugin.getAFKHandler().handleAFKStatus(uuid, playerName, isAfk, manual, null, sourceServer);
        }
        
        if (plugin.getConfig().isDebug()) {
            plugin.getLogger().info("Received AFK status: " + playerName + " = " + isAfk + " from " + sourceServer);
        }
    }

    private void handleAFKStatusWithMessage(ByteArrayDataInput in) {
        String uuid = in.readUTF();
        String playerName = in.readUTF();
        boolean isAfk = in.readBoolean();
        boolean manual = in.readBoolean();
        String message = in.readUTF();
        String sourceServer = in.readUTF();
        
        if (plugin.getAFKHandler() != null) {
            String afkMessage = message.isEmpty() ? null : message;
            plugin.getAFKHandler().handleAFKStatus(uuid, playerName, isAfk, manual, afkMessage, sourceServer);
        }
        
        if (plugin.getConfig().isDebug()) {
            plugin.getLogger().info("Received AFK status: " + playerName + " = " + isAfk + (message.isEmpty() ? "" : " (Message: " + message + ")") + " from " + sourceServer);
        }
    }
}