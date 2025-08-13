package com.velocityessentials.modules.messages;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.velocityessentials.VelocityEssentials;
import com.velocitypowered.api.proxy.server.RegisteredServer;

public class MessageHandler {
    private final VelocityEssentials plugin;
    
    public MessageHandler(VelocityEssentials plugin) {
        this.plugin = plugin;
    }
    
    public void sendNetworkMessage(String type, String playerName, String serverName, String message) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(type);           // network_join, network_leave, network_switch
        out.writeUTF(playerName);     
        out.writeUTF(serverName);     
        out.writeUTF(message);        // The formatted message to display
        
        // Send to ALL servers
        for (RegisteredServer server : plugin.getServer().getAllServers()) {
            if (!server.getPlayersConnected().isEmpty()) {
                server.sendPluginMessage(VelocityEssentials.CHANNEL, out.toByteArray());
            }
        }
        
        if (plugin.getConfig().isDebug()) {
            plugin.getLogger().info("Sent " + type + " to all servers: " + message);
        }
    }
        
    public void sendTestMessage(RegisteredServer server) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("test");
        out.writeUTF("VelocityEssentials Backend Test");
        
        server.sendPluginMessage(VelocityEssentials.CHANNEL, out.toByteArray());
    }
}