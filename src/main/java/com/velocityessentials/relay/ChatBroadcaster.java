package com.velocityessentials.relay;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.velocityessentials.VelocityEssentials;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.util.UUID;

/**
 * receives chat messages from paper servers and broadcasts to all other servers.
 * 
 * phase 1: relays raw messages
 * phase 2: will handle formatted messages with prefixes
 * phase 3: will add channel routing and moderation
 */
public class ChatBroadcaster {
    
    private final VelocityEssentials plugin;
    
    public ChatBroadcaster(VelocityEssentials plugin) {
        this.plugin = plugin;
    }
    
    /**
     * handle incoming chat message from paper server.
     */
    public void handleChatMessage(ByteArrayDataInput in) {
        try {
            // read message data
            String uuidStr = in.readUTF();
            String username = in.readUTF();
            String serverName = in.readUTF();
            String message = in.readUTF();             // raw message
            String formattedMessage = in.readUTF();    // formatted message
            String displayName = in.readUTF();         // formatted display name
            String channelId = in.readUTF();           // channel id (phase 3)
            long timestamp = in.readLong();
            
            UUID uuid = UUID.fromString(uuidStr);
            
            if (plugin.getConfig().isDebug()) {
                plugin.getLogger().info(
                    String.format("[ChatBroadcaster] received from %s [%s]: %s: %s",
                        serverName, channelId, displayName, formattedMessage)
                );
            }
            
            // broadcast to servers based on channel
            broadcastToServers(username, serverName, formattedMessage, displayName, channelId, uuid);
            
            // phase 5: send to discord if enabled (only for global channel)
            if (channelId.equals("global") && 
                plugin.getConfig().isDiscordEnabled() && 
                plugin.getConfig().isChatRelayEnabled()) {
                sendToDiscord(username, serverName, message, displayName);
            }
            
        } catch (Exception e) {
            plugin.getLogger().error("error handling chat message: " + e.getMessage());
            if (plugin.getConfig().isDebug()) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * broadcast message to all servers except origin
     * sends via plugin messaging so paper can display it
     */
    private void broadcastToServers(String username, String originServer, String formattedMessage, String displayName, String channelId, UUID uuid) {
    
        // get display name for server tag
        String serverDisplayName = plugin.getConfig().getServerDisplayName(originServer);
        
        // build server tag if enabled
        String serverTag = "";
        if (plugin.getConfig().isShowServerTag()) {
            serverTag = plugin.getConfig().getServerTagFormat()
                .replace("{server}", serverDisplayName);
        }
        
        // build complete message with reset tags between each component
        StringBuilder displayMessage = new StringBuilder();
        
        // add server tag with reset
        if (!serverTag.isEmpty()) {
            displayMessage.append(serverTag).append("<reset> ");
        }
        
        // add formatted display name (already includes prefix, name, suffix with resets from paper)
        displayMessage.append(displayName).append("<reset>");
        
        // add colon separator with reset
        displayMessage.append("<gray>:</gray><reset> ");
        
        // add formatted message
        displayMessage.append(formattedMessage);
        
        // prepare plugin message with channel info
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("network_chat");
        out.writeUTF(displayMessage.toString());
        out.writeUTF(channelId);  // send channel id so paper can check permissions
        
        byte[] data = out.toByteArray();
        int broadcasted = 0;
        
        // route based on channel type
        // note: we don't have channel config on velocity, so we rely on paper's channel system
        // local channels won't be sent here (they're handled on paper side)
        // all messages that reach velocity are cross-server
        
        // send to all servers except origin
        for (RegisteredServer server : plugin.getServer().getAllServers()) {
            // skip origin server (they already have it)
            if (server.getServerInfo().getName().equals(originServer)) {
                continue;
            }
            
            // skip if no players online
            if (server.getPlayersConnected().isEmpty()) {
                continue;
            }
            
            // send to this server
            server.sendPluginMessage(VelocityEssentials.CHANNEL, data);
            broadcasted++;
            
            if (plugin.getConfig().isDebug()) {
                plugin.getLogger().info(
                    String.format("[ChatBroadcaster] broadcasted [%s] to %s", 
                        channelId, server.getServerInfo().getName())
                );
            }
        }
        
        if (plugin.getConfig().isDebug()) {
            plugin.getLogger().info(
                String.format("[ChatBroadcaster] broadcasted [%s] to %d servers", channelId, broadcasted)
            );
        }
    }
    
    /**
     * send chat message to discord webhook.
     */
    private void sendToDiscord(String username, String serverName, String message, String displayName) {
        try {
            plugin.getServer().getPlayer(username).ifPresent(player -> {
                plugin.getDiscordWebhook().sendFormattedChatMessage(
                    player,
                    serverName,
                    displayName,  // send full display name to discord
                    message
                );
            });
        } catch (Exception e) {
            if (plugin.getConfig().isDebug()) {
                plugin.getLogger().error("failed to send chat to discord: " + e.getMessage());
            }
        }
    }
}