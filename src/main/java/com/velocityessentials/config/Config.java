package com.velocityessentials.config;

import com.velocityessentials.VelocityEssentials;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class Config {
    private final VelocityEssentials plugin;
    private final Path configPath;
    private CommentedConfigurationNode rootNode;
    
    // Database
    private String dbHost;
    private int dbPort;
    private String dbDatabase;
    private String dbUsername;
    private String dbPassword;
    
    // Server Memory
    private boolean serverMemoryEnabled;
    private String fallbackServer;
    private String firstJoinServer;
    private int rememberDays;
    private List<String> blacklistedServers;
    private String bypassPermission;
    
    // Discord
    private boolean discordEnabled;
    private String discordWebhookUrl;
    private String discordUsername;
    private String discordAvatarUrl;
    private boolean showFirstTime;
    private String firstTimeServer;
    
    // Messages
    private boolean customMessagesEnabled;
    private String messagePrefix;
    private String noPermissionMessage;
    private String sendingToLastServer;
    private String sendingToFallback;
    private String firstJoinMessage;
    
    // Other
    private boolean debug;
    
    public Config(VelocityEssentials plugin) {
        this.plugin = plugin;
        this.configPath = plugin.getDataDirectory().resolve("config.yml");
    }
    
    public boolean load() {
        try {
            // Create data directory if it doesn't exist
            if (!Files.exists(plugin.getDataDirectory())) {
                Files.createDirectories(plugin.getDataDirectory());
            }
            
            // Copy default config if it doesn't exist
            if (!Files.exists(configPath)) {
                try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
                    if (in != null) {
                        Files.copy(in, configPath);
                    } else {
                        plugin.getLogger().error("Default config.yml not found in resources!");
                        return false;
                    }
                }
            }
            
            // Load the configuration
            YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                .path(configPath)
                .build();
            
            rootNode = loader.load();
            
            // Database settings
            CommentedConfigurationNode dbNode = rootNode.node("database");
            dbHost = dbNode.node("host").getString("localhost");
            dbPort = dbNode.node("port").getInt(3306);
            dbDatabase = dbNode.node("database").getString("velocityessentials");
            dbUsername = dbNode.node("username").getString("root");
            dbPassword = dbNode.node("password").getString("password");
            
            // Server memory settings
            CommentedConfigurationNode memoryNode = rootNode.node("server-memory");
            serverMemoryEnabled = memoryNode.node("enabled").getBoolean(true);
            fallbackServer = memoryNode.node("fallback-server").getString("lobby");
            firstJoinServer = memoryNode.node("first-join-server").getString("lobby");
            rememberDays = memoryNode.node("remember-days").getInt(30);
            blacklistedServers = memoryNode.node("blacklisted-servers").getList(String.class, List.of());
            bypassPermission = memoryNode.node("bypass-permission").getString("velocityessentials.bypass");
            
            // Discord settings
            CommentedConfigurationNode discordNode = rootNode.node("discord");
            discordEnabled = discordNode.node("enabled").getBoolean(false);
            discordWebhookUrl = discordNode.node("webhook-url").getString("");
            discordUsername = discordNode.node("username").getString("VelocityEssentials");
            discordAvatarUrl = discordNode.node("avatar-url").getString("");
            showFirstTime = discordNode.node("show-first-time").getBoolean(true);
            firstTimeServer = discordNode.node("first-time-server").getString("lobby");
            
            // Message settings
            CommentedConfigurationNode messagesNode = rootNode.node("messages");
            customMessagesEnabled = messagesNode.node("custom-enabled").getBoolean(true);
            messagePrefix = messagesNode.node("prefix").getString("<gray>[<aqua>VE<gray>]</aqua>");
            noPermissionMessage = messagesNode.node("no-permission").getString("{prefix} <red>You don't have permission!");
            sendingToLastServer = messagesNode.node("sending-to-last-server").getString("{prefix} <green>Sending you to your last server: <yellow>{server}");
            sendingToFallback = messagesNode.node("sending-to-fallback").getString("{prefix} <yellow>Sending you to the fallback server");
            firstJoinMessage = messagesNode.node("first-join").getString("{prefix} <green>Welcome to the network!");
            
            // Debug
            debug = rootNode.node("debug").getBoolean(false);
            
            return true;
            
        } catch (ConfigurateException | IOException e) {
            plugin.getLogger().error("Failed to load configuration", e);
            return false;
        }
    }
    
    // Getters
    public String getDbHost() { return dbHost; }
    public int getDbPort() { return dbPort; }
    public String getDbDatabase() { return dbDatabase; }
    public String getDbUsername() { return dbUsername; }
    public String getDbPassword() { return dbPassword; }
    
    public boolean isServerMemoryEnabled() { return serverMemoryEnabled; }
    public String getFallbackServer() { return fallbackServer; }
    public String getFirstJoinServer() { return firstJoinServer; }
    public int getRememberDays() { return rememberDays; }
    public List<String> getBlacklistedServers() { return blacklistedServers; }
    public String getBypassPermission() { return bypassPermission; }
    
    public boolean isDiscordEnabled() { return discordEnabled; }
    public String getDiscordWebhookUrl() { return discordWebhookUrl; }
    public String getDiscordUsername() { return discordUsername; }
    public String getDiscordAvatarUrl() { return discordAvatarUrl; }
    public boolean isShowFirstTime() { return showFirstTime; }
    public String getFirstTimeServer() { return firstTimeServer; }
    
    public boolean isCustomMessagesEnabled() { return customMessagesEnabled; }
    public String getMessagePrefix() { return messagePrefix; }
    public String getNoPermissionMessage() { return noPermissionMessage; }
    public String getSendingToLastServer() { return sendingToLastServer; }
    public String getSendingToFallback() { return sendingToFallback; }
    public String getFirstJoinMessage() { return firstJoinMessage; }
    
    public boolean isDebug() { return debug; }
    
    public String getMessage(String key) {
        return getMessage(key, "");
    }
    
    public String getMessage(String key, String... replacements) {
        String message = switch (key) {
            case "no-permission" -> noPermissionMessage;
            case "sending-to-last-server" -> sendingToLastServer;
            case "sending-to-fallback" -> sendingToFallback;
            case "first-join" -> firstJoinMessage;
            default -> key;
        };
        
        message = message.replace("{prefix}", messagePrefix);
        
        // Apply replacements in pairs (key, value)
        for (int i = 0; i < replacements.length - 1; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }
        
        return message;
    }
}