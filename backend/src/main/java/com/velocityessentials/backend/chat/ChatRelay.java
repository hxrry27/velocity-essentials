package com.velocityessentials.backend.chat;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.velocityessentials.backend.VelocityEssentialsBackend;
import io.papermc.paper.event.player.AsyncChatEvent;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Map;

/**
 * sends chat messages to velocity for cross-server relay
 */
public class ChatRelay implements Listener {
    
    private final VelocityEssentialsBackend plugin;
    private final ChatConfig config;
    private final ChannelManager channelManager;
    private final MuteManager muteManager;
    private final MiniMessage miniMessage;
    
    public ChatRelay(VelocityEssentialsBackend plugin) {
        this.plugin = plugin;
        this.config = new ChatConfig(plugin);
        this.channelManager = new ChannelManager(plugin);
        this.muteManager = new MuteManager(plugin);
        this.miniMessage = MiniMessage.miniMessage();
    }
    
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onChat(AsyncChatEvent event) {
        if (!config.isEnabled()) {
            return;
        }
        
        Player player = event.getPlayer();
        
        // check if player is muted
        if (muteManager.isMuted(player)) {
            event.setCancelled(true);
            
            String mutedMessage = muteManager.getMutedMessage(player);
            Component message = miniMessage.deserialize(mutedMessage);
            player.sendMessage(message);
            
            if (plugin.debug) {
                plugin.getLogger().info(player.getName() + " tried to chat while muted");
            }
            
            return;
        }
        
        // get player's current channel
        Channel channel = channelManager.getPlayerChannel(player);
        
        if (channel == null) {
            plugin.getLogger().warning("player " + player.getName() + " has no valid channel");
            return;
        }
        
        // get raw message text
        String message = PlainTextComponentSerializer.plainText()
            .serialize(event.message());
        
        // apply permission-based formatting to message
        String formattedMessage = applyFormatting(player, message);
        
        // build display name using configurable format
        String displayName = buildDisplayName(player);
        
        // send to velocity for relay (if cross-server channel)
        if (channel.isCrossServer()) {
            sendToVelocity(player, message, formattedMessage, displayName, channel);
        } else {
            // local channel - handle locally only
            if (plugin.debug) {
                plugin.getLogger().info(
                    String.format("[ChatRelay] local channel message: [%s] %s: %s",
                        channel.getName(), displayName, formattedMessage)
                );
            }
            // TODO: format and display locally for local channels
        }
    }
    
    /**
     * build display name using configurable format and placeholders
     * example format: "{prefix}{name}{suffix}" with resets between components
     */
    private String buildDisplayName(Player player) {
        if (!config.isUsePlaceholderApi()) {
            return player.getName();
        }
        
        // check if placeholderapi is available
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return player.getName();
        }
        
        try {
            String format = config.getDisplayFormat();
            Map<String, String> placeholders = config.getPlaceholders();
            
            // resolve each placeholder
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                String key = entry.getKey();
                String placeholder = entry.getValue();
                
                // resolve using PlaceholderAPI
                String resolved = PlaceholderAPI.setPlaceholders(player, placeholder);
                
                // clean up if placeholder didn't resolve
                if (resolved == null || resolved.equals(placeholder)) {
                    resolved = "";
                }
                
                // add reset after non-empty placeholders to prevent format bleeding
                if (!resolved.isEmpty()) {
                    resolved = resolved + "<reset>";
                }
                
                // replace in format
                format = format.replace("{" + key + "}", resolved);
            }
            
            // clean up any remaining unreplaced placeholders
            format = format.replaceAll("\\{[^}]+\\}", "");

            // clean up multiple consecutive resets BUT KEEP THE SPACES
            format = format.replaceAll("(<reset>)+", "<reset>");

            // trim only leading/trailing whitespace (not internal spaces)
            format = format.trim();
            
            if (plugin.debug) {
                plugin.getLogger().info("[ChatRelay] display name for " + player.getName() + ": " + format);
            }
            
            return format;
            
        } catch (Exception e) {
            if (plugin.debug) {
                plugin.getLogger().warning("failed to build display name for " + player.getName() + ": " + e.getMessage());
            }
            return player.getName();
        }
    }
    
    /**
     * apply permission-based formatting to message
     * checks what formatting the player is allowed to use.
     */
    private String applyFormatting(Player player, String message) {
        // check permissions in order (most permissive to least)
        
        // full minimessage - all tags allowed
        if (hasPermission(player, config.getMinimessagePermission(), config.isMinimessageDefault())) {
            return message;
        }
        
        // rainbow allowed
        if (hasPermission(player, config.getRainbowPermission(), config.isRainbowDefault())) {
            return message;
        }
        
        // gradients allowed
        if (hasPermission(player, config.getGradientPermission(), config.isGradientDefault())) {
            return message;
        }
        
        // basic colors allowed
        if (hasPermission(player, config.getColorPermission(), config.isColorDefault())) {
            return message;
        }
        
        // no formatting allowed - strip all tags
        return stripFormatting(message);
    }
    
    /**
     * check if player has permission, considering defaults
     */
    private boolean hasPermission(Player player, String permission, boolean defaultValue) {
        if (permission == null || permission.isEmpty()) {
            return defaultValue;
        }
        
        // check explicit permission
        if (player.hasPermission(permission)) {
            return true;
        }
        
        // check if explicitly denied
        if (player.isPermissionSet(permission) && !player.hasPermission(permission)) {
            return false;
        }
        
        // use default
        return defaultValue;
    }
    
    /**
     * strip all minimessage formatting from message
     */
    private String stripFormatting(String message) {
        // remove minimessage tags
        message = message.replaceAll("<[^>]+>", "");
        // remove legacy color codes
        message = message.replaceAll("§[0-9a-fk-or]", "");
        message = message.replaceAll("&[0-9a-fk-or]", "");
        return message;
    }
    
    /**
     * send chat message to velocity via plugin messaging.
     */
    private void sendToVelocity(Player player, String message, String formattedMessage, 
                                String displayName, Channel channel) {
        if (!config.isSendToVelocity()) {
            return;
        }
        
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        
        // subchannel identifier
        out.writeUTF("chat");
        
        // player info
        out.writeUTF(player.getUniqueId().toString());
        out.writeUTF(player.getName());
        out.writeUTF(plugin.serverName);
        
        // message data
        out.writeUTF(message);                    // raw message
        out.writeUTF(formattedMessage);           // formatted message
        out.writeUTF(displayName);                // formatted display name
        out.writeUTF(channel.getId());            // channel id
        out.writeLong(System.currentTimeMillis()); // timestamp
        
        // send via plugin messaging
        player.sendPluginMessage(
            plugin, 
            VelocityEssentialsBackend.CHANNEL, 
            out.toByteArray()
        );
        
        if (plugin.debug) {
            plugin.getLogger().info(
                String.format("[ChatRelay] sent to velocity: [%s][%s] %s: %s",
                    plugin.serverName, channel.getName(), displayName, formattedMessage)
            );
        }
    }
    
    /**
     * get channel manager.
     */
    public ChannelManager getChannelManager() {
        return channelManager;
    }
    
    /**
     * get mute manager.
     */
    public MuteManager getMuteManager() {
        return muteManager;
    }
}