package com.velocityessentials.backend.chat;

import com.velocityessentials.backend.VelocityEssentialsBackend;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * manages chat channels and player channel memberships.
 */
public class ChannelManager {
    
    private final VelocityEssentialsBackend plugin;
    private final Map<String, Channel> channels;
    private final Map<UUID, String> playerChannels; // player uuid -> channel id
    private String defaultChannel;
    
    public ChannelManager(VelocityEssentialsBackend plugin) {
        this.plugin = plugin;
        this.channels = new LinkedHashMap<>();
        this.playerChannels = new HashMap<>();
        loadChannels();
    }
    
    /**
     * load channels from config.
     */
    private void loadChannels() {
        ConfigurationSection chatSection = plugin.getConfig().getConfigurationSection("chat");
        if (chatSection == null) {
            plugin.getLogger().warning("chat section not found in config");
            createDefaultChannels();
            return;
        }
        
        // get default channel
        defaultChannel = chatSection.getString("default-channel", "global");
        
        // load channels
        ConfigurationSection channelsSection = chatSection.getConfigurationSection("channels");
        if (channelsSection == null) {
            plugin.getLogger().warning("no channels configured, using defaults");
            createDefaultChannels();
            return;
        }
        
        for (String channelId : channelsSection.getKeys(false)) {
            ConfigurationSection channelConfig = channelsSection.getConfigurationSection(channelId);
            if (channelConfig == null) continue;
            
            Channel channel = Channel.builder(channelId)
                .name(channelConfig.getString("name", channelId))
                .enabled(channelConfig.getBoolean("enabled", true))
                .permission(channelConfig.getString("permission", ""))
                .permissionRequired(channelConfig.getBoolean("permission-required", false))
                .crossServer(channelConfig.getBoolean("cross-server", true))
                .format(channelConfig.getString("format", "{display}: {message}"))
                .receivePermission(channelConfig.getString("receive-permission", ""))
                .build();
            
            if (channel.isEnabled()) {
                channels.put(channelId, channel);
                if (plugin.debug) {
                    plugin.getLogger().info("loaded channel: " + channel);
                }
            }
        }
        
        plugin.getLogger().info("loaded " + channels.size() + " chat channel(s)");
    }
    
    /**
     * create default channels if config is missing.
     */
    private void createDefaultChannels() {
        Channel global = Channel.builder("global")
            .name("Global")
            .crossServer(true)
            .format("{display}: {message}")
            .build();
        
        channels.put("global", global);
        defaultChannel = "global";
        
        plugin.getLogger().info("created default global channel");
    }
    
    /**
     * get channel by id.
     */
    public Channel getChannel(String channelId) {
        return channels.get(channelId);
    }
    
    /**
     * get all channels.
     */
    public Collection<Channel> getChannels() {
        return channels.values();
    }
    
    /**
     * get channels a player can use.
     */
    public List<Channel> getAvailableChannels(Player player) {
        List<Channel> available = new ArrayList<>();
        
        for (Channel channel : channels.values()) {
            if (canUseChannel(player, channel)) {
                available.add(channel);
            }
        }
        
        return available;
    }
    
    /**
     * check if player can use a channel.
     */
    public boolean canUseChannel(Player player, Channel channel) {
        if (!channel.isEnabled()) {
            return false;
        }
        
        if (!channel.isPermissionRequired()) {
            return true;
        }
        
        String permission = channel.getPermission();
        return permission == null || permission.isEmpty() || player.hasPermission(permission);
    }
    
    /**
     * check if player can see messages in a channel.
     */
    public boolean canSeeChannel(Player player, Channel channel) {
        if (!canUseChannel(player, channel)) {
            return false;
        }
        
        if (!channel.hasReceivePermission()) {
            return true;
        }
        
        return player.hasPermission(channel.getReceivePermission());
    }
    
    /**
     * get player's current channel.
     */
    public Channel getPlayerChannel(Player player) {
        String channelId = playerChannels.getOrDefault(player.getUniqueId(), defaultChannel);
        Channel channel = getChannel(channelId);
        
        // fallback to default if channel doesn't exist or player can't use it
        if (channel == null || !canUseChannel(player, channel)) {
            channel = getChannel(defaultChannel);
        }
        
        return channel;
    }
    
    /**
     * set player's channel.
     */
    public boolean setPlayerChannel(Player player, String channelId) {
        Channel channel = getChannel(channelId);
        
        if (channel == null) {
            return false;
        }
        
        if (!canUseChannel(player, channel)) {
            return false;
        }
        
        playerChannels.put(player.getUniqueId(), channelId);
        
        if (plugin.debug) {
            plugin.getLogger().info(player.getName() + " switched to channel: " + channel.getName());
        }
        
        return true;
    }
    
    /**
     * reset player to default channel.
     */
    public void resetPlayerChannel(Player player) {
        playerChannels.remove(player.getUniqueId());
    }
    
    /**
     * get default channel.
     */
    public Channel getDefaultChannel() {
        return getChannel(defaultChannel);
    }
    
    /**
     * reload channels from config.
     */
    public void reload() {
        channels.clear();
        loadChannels();
    }
}