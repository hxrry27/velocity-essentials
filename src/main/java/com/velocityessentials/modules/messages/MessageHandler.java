package com.velocityessentials.modules.messages;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.velocityessentials.VelocityEssentials;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;

public class MessageHandler {
    private final VelocityEssentials plugin;
    
    public MessageHandler(VelocityEssentials plugin) {
        this.plugin = plugin;
    }
    
    public void sendJoinMessage(Player player, RegisteredServer server, boolean isFirstTime) {
        if (!plugin.getConfig().isCustomMessagesEnabled()) return;
        
        String messageType = isFirstTime ? "firstjoin" : "join";
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(messageType);
        out.writeUTF(player.getUsername());
        out.writeUTF(server.getServerInfo().getName());
        
        broadcastToAllServers(out.toByteArray());
    }
    
    public void sendLeaveMessage(Player player, RegisteredServer server) {
        if (!plugin.getConfig().isCustomMessagesEnabled()) return;
        
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("leave");
        out.writeUTF(player.getUsername());
        out.writeUTF(server.getServerInfo().getName());
        
        broadcastToAllServers(out.toByteArray());
    }
    
    public void sendSwitchMessage(Player player, String fromServer, String toServer) {
        if (!plugin.getConfig().isCustomMessagesEnabled()) return;
        
        // First, suppress vanilla messages on both servers
        suppressPlayerMessages(player.getUsername(), fromServer);
        suppressPlayerMessages(player.getUsername(), toServer);
        
        // Then send the switch message
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("switch");
        out.writeUTF(player.getUsername());
        out.writeUTF(fromServer);
        out.writeUTF(toServer);
        
        broadcastToAllServers(out.toByteArray());
    }
    
    public void suppressPlayerMessages(String playerName, String serverName) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("suppress");
        out.writeUTF(playerName);
        
        // Send to specific server
        plugin.getServer().getServer(serverName).ifPresent(server -> {
            if (!server.getPlayersConnected().isEmpty()) {
                server.sendPluginMessage(VelocityEssentials.CHANNEL, out.toByteArray());
                
                if (plugin.getConfig().isDebug()) {
                    plugin.getLogger().info("Sent suppress message for " + playerName + " to " + serverName);
                }
            }
        });
    }
    
    private void broadcastToAllServers(byte[] data) {
        for (RegisteredServer server : plugin.getServer().getAllServers()) {
            // Only send if server has players
            if (!server.getPlayersConnected().isEmpty()) {
                server.sendPluginMessage(VelocityEssentials.CHANNEL, data);
                
                if (plugin.getConfig().isDebug()) {
                    plugin.getLogger().info("Sent message to server: " + server.getServerInfo().getName());
                }
            }
        }
    }
    
    public void sendTestMessage(RegisteredServer server) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("test");
        out.writeUTF("VelocityEssentials Backend Test");
        
        server.sendPluginMessage(VelocityEssentials.CHANNEL, out.toByteArray());
    }
}