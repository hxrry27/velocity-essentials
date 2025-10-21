package com.velocityessentials.config;

import com.velocityessentials.VelocityEssentials;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.velocityessentials.modules.restart.data.RestartSchedule;
import com.velocityessentials.modules.restart.RestartUtil;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.Set;


public class Config {
    private final VelocityEssentials plugin;
    private final Path configPath;
    private CommentedConfigurationNode rootNode;
    
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
    private boolean showJoinMessages;
    private boolean showLeaveMessages;
    private boolean showSwitchMessages;
    private boolean showFirstTimeMessages;
    private boolean suppressVanillaMessages;
    private String messagePrefix;
    private String noPermissionMessage;
    private String sendingToLastServer;
    private String sendingToFallback;
    private String firstJoinMessage;

    // Discord Hook
    private boolean chatRelayEnabled;
    private String chatFormat;
    private boolean usePlayerHeadForChat;
    private Map<String, String> chatPrefixes;
    private boolean chatShowServerPrefix;
    private String chatServerFormat;
    private boolean discordAFKEnabled;

    // Stats
    private boolean statsEnabled;
    private int statsUpdateInterval;
    private Map<String, String> statsServerPaths;
    private boolean statsApiEnabled;
    private int statsApiPort;
    private String statsApiKey;
    private String statsApiBind;

    // Restart
    private boolean autoRestartEnabled;
    private String autoRestartTimezone;
    private int autoRestartDelayCheckInterval;
    private Map<String, RestartSchedule> restartSchedules;

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
            chatRelayEnabled = discordNode.node("chat-relay").getBoolean(false);
            chatFormat = discordNode.node("chat-format").getString("[{server}] **{player}**: {message}");
            usePlayerHeadForChat = discordNode.node("use-player-head-for-chat").getBoolean(false);
            discordAFKEnabled = discordNode.node("show-afk").getBoolean(true);
            
            CommentedConfigurationNode prefixNode = discordNode.node("chat-prefixes");
            chatPrefixes = new LinkedHashMap<>(); // LinkedHashMap preserves insertion order
            if (!prefixNode.virtual()) {
                // Get all children in the order they appear in config
                prefixNode.childrenMap().forEach((key, value) -> {
                    chatPrefixes.put(key.toString(), value.getString(""));
                });
            }

            // Message settings
            CommentedConfigurationNode messagesNode = rootNode.node("messages");
            customMessagesEnabled = messagesNode.node("custom-enabled").getBoolean(true);
            showJoinMessages = messagesNode.node("show-join").getBoolean(true);
            showLeaveMessages = messagesNode.node("show-leave").getBoolean(true);
            showSwitchMessages = messagesNode.node("show-switch").getBoolean(true);
            showFirstTimeMessages = messagesNode.node("show-first-time").getBoolean(true);
            suppressVanillaMessages = messagesNode.node("suppress-vanilla").getBoolean(true);
            messagePrefix = messagesNode.node("prefix").getString("<gray>[<aqua>VE<gray>]</aqua>");
            noPermissionMessage = messagesNode.node("no-permission").getString("{prefix} <red>You don't have permission!");
            sendingToLastServer = messagesNode.node("sending-to-last-server").getString("{prefix} <green>Sending you to your last server: <yellow>{server}");
            sendingToFallback = messagesNode.node("sending-to-fallback").getString("{prefix} <yellow>Sending you to the fallback server");
            firstJoinMessage = messagesNode.node("first-join").getString("{prefix} <green>Welcome to the network!");
            chatShowServerPrefix = discordNode.node("chat-show-server").getBoolean(false);
            chatServerFormat = discordNode.node("chat-server-format").getString("[{server}]");
            
            // Debug
            debug = rootNode.node("debug").getBoolean(false);
            
            // Stats settings
            CommentedConfigurationNode statsNode = rootNode.node("stats");
            statsEnabled = statsNode.node("enabled").getBoolean(false);
            statsUpdateInterval = statsNode.node("update-interval").getInt(10);

            // Auto Restart settings
            CommentedConfigurationNode restartNode = rootNode.node("auto-restart");
            autoRestartEnabled = restartNode.node("enabled").getBoolean(false);
            autoRestartTimezone = restartNode.node("timezone").getString("UTC");
            autoRestartDelayCheckInterval = restartNode.node("delay-check-interval").getInt(60);
            
            // Load server paths
            statsServerPaths = new HashMap<>();
            CommentedConfigurationNode serversNode = statsNode.node("servers");
            if (!serversNode.virtual()) {
                serversNode.childrenMap().forEach((key, value) -> {
                    statsServerPaths.put(key.toString(), value.getString(""));
                });
            }
            
            // API settings
            CommentedConfigurationNode apiNode = statsNode.node("api");
            statsApiEnabled = apiNode.node("enabled").getBoolean(false);
            statsApiPort = apiNode.node("port").getInt(8080);
            statsApiKey = apiNode.node("auth-key").getString("change-me");
            statsApiBind = apiNode.node("bind").getString("0.0.0.0");

            loadRestartConfig();

            return true;
            
        } catch (IOException e) {
            plugin.getLogger().error("Failed to load configuration", e);
            return false;
        }
    }
    
    // Getters - removed all MySQL-related getters
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
    public boolean isChatRelayEnabled() { return chatRelayEnabled; }
    public String getChatFormat() { return chatFormat; }
    public boolean isUsePlayerHeadForChat() { return usePlayerHeadForChat; }
    
    public boolean isCustomMessagesEnabled() { return customMessagesEnabled; }
    public String getMessagePrefix() { return messagePrefix; }
    public String getNoPermissionMessage() { return noPermissionMessage; }
    public String getSendingToLastServer() { return sendingToLastServer; }
    public String getSendingToFallback() { return sendingToFallback; }
    public String getFirstJoinMessage() { return firstJoinMessage; }
    public Map<String, String> getChatPrefixes() { return chatPrefixes; }
    public boolean isChatShowServerPrefix() { return chatShowServerPrefix; }
    public String getChatServerFormat() { return chatServerFormat; }

    public boolean isShowJoinMessages() { return showJoinMessages; }
    public boolean isShowLeaveMessages() { return showLeaveMessages; }
    public boolean isShowSwitchMessages() { return showSwitchMessages; }
    public boolean isShowFirstTimeMessages() { return showFirstTimeMessages; }
    public boolean isSuppressVanillaMessages() { return suppressVanillaMessages; }
    
    // Stats Getters
    public boolean isStatsEnabled() { return statsEnabled; }
    public int getStatsUpdateInterval() { return statsUpdateInterval; }
    public Map<String, String> getStatsServerPaths() { return statsServerPaths; }
    public boolean isStatsApiEnabled() { return statsApiEnabled; }
    public int getStatsApiPort() { return statsApiPort; }
    public String getStatsApiKey() { return statsApiKey; }
    public String getStatsApiBind() { return statsApiBind; }
    public boolean isDiscordAFKEnabled() { return discordAFKEnabled; }
    
    // Restart getters
    public boolean isAutoRestartEnabled() { return autoRestartEnabled; }
    public String getAutoRestartTimezone() { return autoRestartTimezone; }
    public int getAutoRestartDelayCheckInterval() { return autoRestartDelayCheckInterval; }
    public Map<String, RestartSchedule> getRestartSchedules() { return restartSchedules; }

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

    private void loadRestartConfig() {
        try {
            CommentedConfigurationNode restartNode = rootNode.node("auto-restart");
            autoRestartEnabled = restartNode.node("enabled").getBoolean(false);
            autoRestartTimezone = restartNode.node("timezone").getString("UTC");
            autoRestartDelayCheckInterval = restartNode.node("delay-check-interval").getInt(60);
            
            restartSchedules = new HashMap<>();
            
            if (!autoRestartEnabled) {
                plugin.getLogger().info("Auto-restart system is disabled");
                return;
            }
            
            CommentedConfigurationNode schedulesNode = restartNode.node("schedules");
            
            if (schedulesNode.virtual() || schedulesNode.empty()) {
                plugin.getLogger().warn("No restart schedules configured!");
                return;
            }
            
            Map<Object, ? extends CommentedConfigurationNode> scheduleMap = schedulesNode.childrenMap();
            
            for (Map.Entry<Object, ? extends CommentedConfigurationNode> entry : scheduleMap.entrySet()) {
                String scheduleName = entry.getKey().toString();
                CommentedConfigurationNode scheduleConfig = entry.getValue();
                
                try {
                    boolean enabled = scheduleConfig.node("enabled").getBoolean(true);
                    List<String> servers = scheduleConfig.node("servers").getList(String.class, new ArrayList<>());
                    String time = scheduleConfig.node("time").getString("04:00");
                    List<String> dayStrings = scheduleConfig.node("days").getList(String.class, List.of("*"));
                    int minPlayers = scheduleConfig.node("min-players-delay").getInt(0);
                    
                    CommentedConfigurationNode warningsNode = scheduleConfig.node("warnings");
                    List<Integer> warningIntervals = warningsNode.node("intervals")
                        .getList(Integer.class, List.of(300, 180, 60, 30, 10));
                    String warningSound = warningsNode.node("sound")
                        .getString("ENTITY_EXPERIENCE_ORB_PICKUP");
                    
                    Set<DayOfWeek> days = RestartUtil.parseDays(dayStrings);
                    ZoneId timezone = ZoneId.of(autoRestartTimezone);
                    
                    RestartSchedule.Builder builder = RestartSchedule.builder(scheduleName)
                        .enabled(enabled)
                        .servers(servers)
                        .time(time, days, timezone)
                        .warningIntervals(warningIntervals)
                        .warningSound(warningSound)
                        .minPlayersDelay(minPlayers);
                    
                    CommentedConfigurationNode commandsNode = scheduleConfig.node("commands");
                    
                    parseCommands(
                        commandsNode.node("before"),
                        builder,
                        RestartSchedule.CommandTiming.BEFORE
                    );
                    
                    parseCommands(
                        commandsNode.node("after"),
                        builder,
                        RestartSchedule.CommandTiming.AFTER
                    );
                    
                    RestartSchedule schedule = builder.build();
                    List<String> errors = schedule.validate();
                    
                    if (!errors.isEmpty()) {
                        plugin.getLogger().error("Schedule '" + scheduleName + "' has validation errors:");
                        errors.forEach(error -> plugin.getLogger().error("  - " + error));
                        continue;
                    }
                    
                    restartSchedules.put(scheduleName, schedule);
                    plugin.getLogger().info("Loaded restart schedule: " + scheduleName + 
                        " (" + servers.size() + " servers, " + 
                        (enabled ? "enabled" : "disabled") + ")");
                    
                } catch (Exception e) {
                    plugin.getLogger().error("Failed to load restart schedule: " + scheduleName, e);
                }
            }
            
            plugin.getLogger().info("Loaded " + restartSchedules.size() + " restart schedule(s)");
            
        } catch (Exception e) {
            plugin.getLogger().error("Failed to load restart configuration", e);
            autoRestartEnabled = false;
        }
    }

    private void parseCommands(
        CommentedConfigurationNode commandsNode,
        RestartSchedule.Builder builder,
        RestartSchedule.CommandTiming timing
    ) {
        if (commandsNode.virtual() || !commandsNode.isList()) {
            return;
        }
        
        try {
            List<?> commandsList = commandsNode.getList(Object.class, new ArrayList<>());
            
            for (Object cmdObj : commandsList) {
                if (!(cmdObj instanceof Map)) {
                    continue;
                }
                
                @SuppressWarnings("unchecked")
                Map<String, Object> cmdMap = (Map<String, Object>) cmdObj;
                
                Object delayObj = cmdMap.get("delay");
                if (delayObj == null) {
                    plugin.getLogger().warn("Command missing 'delay' field, skipping");
                    continue;
                }
                int delay = ((Number) delayObj).intValue();
                
                String command = (String) cmdMap.get("command");
                if (command == null || command.isEmpty()) {
                    plugin.getLogger().warn("Command missing 'command' field, skipping");
                    continue;
                }
                
                String targetStr = (String) cmdMap.getOrDefault("target", "server");
                RestartSchedule.CommandTarget target = 
                    targetStr.equalsIgnoreCase("proxy") ?
                    RestartSchedule.CommandTarget.PROXY :
                    RestartSchedule.CommandTarget.SERVER;
                
                builder.addCommand(new RestartSchedule.ScheduledCommand(
                    command, delay, timing, target
                ));
            }
        } catch (Exception e) {
            plugin.getLogger().error("Failed to parse commands", e);
        }
    }
}