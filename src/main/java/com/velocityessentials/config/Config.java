package com.velocityessentials.config;

import com.velocityessentials.VelocityEssentials;
import com.velocityessentials.modules.restart.data.RestartSchedule;
import com.velocityessentials.modules.restart.RestartUtil;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.*;

public class Config {
    private final VelocityEssentials plugin;
    private final Path configPath;
    private CommentedConfigurationNode rootNode;
    
    // server memory settings
    private boolean serverMemoryEnabled;
    private String fallbackServer;
    private String firstJoinServer;
    private int rememberDays;
    private List<String> blacklistedServers;
    private String bypassPermission;
    
    // discord settings
    private boolean discordEnabled;
    private String discordWebhookUrl;
    private String discordUsername;
    private String discordAvatarUrl;
    private boolean showFirstTime;
    private String firstTimeServer;
    
    // message settings
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

    // discord chat relay
    private boolean chatRelayEnabled;
    private String chatFormat;
    private boolean usePlayerHeadForChat;
    private Map<String, String> chatPrefixes;
    private boolean chatShowServerPrefix;
    private String chatServerFormat;
    private boolean discordAFKEnabled;

    // stats system
    private boolean statsEnabled;
    private int statsUpdateInterval;
    private Map<String, String> statsServerPaths;
    private boolean statsApiEnabled;
    private int statsApiPort;
    private String statsApiKey;
    private String statsApiBind;
    
    // auto-restart system
    private boolean autoRestartEnabled;
    private String autoRestartTimezone;
    private int autoRestartDelayCheckInterval;
    private Map<String, RestartSchedule> restartSchedules;

    // debug mode
    private boolean debug;
    
    public Config(VelocityEssentials plugin) {
        this.plugin = plugin;
        this.configPath = plugin.getDataDirectory().resolve("config.yml");
    }
    
    public boolean load() {
        try {
            // create data directory if needed
            if (!Files.exists(plugin.getDataDirectory())) {
                Files.createDirectories(plugin.getDataDirectory());
            }
            
            // copy default config if it doesn't exist
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
            
            // load the configuration
            YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                .path(configPath)
                .build();
            
            rootNode = loader.load();
            
            // load all config sections
            loadServerMemory();
            loadDiscord();
            loadMessages();
            loadStats();
            loadRestartConfig();
            
            // debug mode
            debug = rootNode.node("debug").getBoolean(false);
            
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().error("Failed to load configuration", e);
            return false;
        }
    }
    
    private void loadServerMemory() {
        try {
            CommentedConfigurationNode memoryNode = rootNode.node("server-memory");
            serverMemoryEnabled = memoryNode.node("enabled").getBoolean(true);
            fallbackServer = memoryNode.node("fallback-server").getString("lobby");
            firstJoinServer = memoryNode.node("first-join-server").getString("lobby");
            rememberDays = memoryNode.node("remember-days").getInt(30);
            blacklistedServers = memoryNode.node("blacklisted-servers").getList(String.class, List.of());
            bypassPermission = memoryNode.node("bypass-permission").getString("velocityessentials.bypass");
        } catch (Exception e) {
            plugin.getLogger().error("Failed to load server memory config", e);
        }
    }
    
    private void loadDiscord() {
        try {
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
            
            // chat prefixes - preserve insertion order
            CommentedConfigurationNode prefixNode = discordNode.node("chat-prefixes");
            chatPrefixes = new LinkedHashMap<>();
            if (!prefixNode.virtual()) {
                prefixNode.childrenMap().forEach((key, value) -> {
                    chatPrefixes.put(key.toString(), value.getString(""));
                });
            }
            
            chatShowServerPrefix = discordNode.node("chat-show-server").getBoolean(false);
            chatServerFormat = discordNode.node("chat-server-format").getString("[{server}]");
        } catch (Exception e) {
            plugin.getLogger().error("Failed to load discord config", e);
        }
    }
    
    private void loadMessages() {
        try {
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
        } catch (Exception e) {
            plugin.getLogger().error("Failed to load messages config", e);
        }
    }
    
    private void loadStats() {
        try {
            CommentedConfigurationNode statsNode = rootNode.node("stats");
            statsEnabled = statsNode.node("enabled").getBoolean(false);
            statsUpdateInterval = statsNode.node("update-interval").getInt(10);
            
            // load server paths
            statsServerPaths = new HashMap<>();
            CommentedConfigurationNode serversNode = statsNode.node("servers");
            if (!serversNode.virtual()) {
                serversNode.childrenMap().forEach((key, value) -> {
                    statsServerPaths.put(key.toString(), value.getString(""));
                });
            }
            
            // api settings
            CommentedConfigurationNode apiNode = statsNode.node("api");
            statsApiEnabled = apiNode.node("enabled").getBoolean(false);
            statsApiPort = apiNode.node("port").getInt(8080);
            statsApiKey = apiNode.node("auth-key").getString("change-me");
            statsApiBind = apiNode.node("bind").getString("0.0.0.0");
        } catch (Exception e) {
            plugin.getLogger().error("Failed to load stats config", e);
        }
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
                    // basic settings
                    boolean enabled = scheduleConfig.node("enabled").getBoolean(true);
                    List<String> servers = scheduleConfig.node("servers").getList(String.class, new ArrayList<>());
                    String time = scheduleConfig.node("time").getString("04:00");
                    List<String> dayStrings = scheduleConfig.node("days").getList(String.class, List.of("*"));
                    int minPlayers = scheduleConfig.node("min-players-delay").getInt(0);
                    
                    // warning settings
                    CommentedConfigurationNode warningsNode = scheduleConfig.node("warnings");
                    List<Integer> warningIntervals = warningsNode.node("intervals")
                        .getList(Integer.class, List.of(300, 180, 60, 30, 10));
                    String warningSound = warningsNode.node("sound")
                        .getString("ENTITY_EXPERIENCE_ORB_PICKUP");
                    
                    // parse time settings
                    Set<DayOfWeek> days = RestartUtil.parseDays(dayStrings);
                    ZoneId timezone = ZoneId.of(autoRestartTimezone);
                    
                    // create schedule builder
                    RestartSchedule.Builder builder = RestartSchedule.builder(scheduleName)
                        .enabled(enabled)
                        .servers(servers)
                        .time(time, days, timezone)
                        .warningIntervals(warningIntervals)
                        .warningSound(warningSound)
                        .minPlayersDelay(minPlayers);
                    
                    // parse commands
                    CommentedConfigurationNode commandsNode = scheduleConfig.node("commands");
                    
                    // before commands
                    parseCommands(
                        commandsNode.node("before"),
                        builder,
                        RestartSchedule.CommandTiming.BEFORE
                    );
                    
                    // after commands
                    parseCommands(
                        commandsNode.node("after"),
                        builder,
                        RestartSchedule.CommandTiming.AFTER
                    );
                    
                    // build and validate
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
    
    // helper method for parsing commands from config
    // uses childrenList() instead of getList() to avoid configurate serialization issues
    private void parseCommands(
        CommentedConfigurationNode commandsNode,
        RestartSchedule.Builder builder,
        RestartSchedule.CommandTiming timing
    ) {
        if (commandsNode.virtual() || !commandsNode.isList()) {
            return;
        }
        
        try {
            // iterate through child nodes directly instead of using getList()
            for (CommentedConfigurationNode cmdNode : commandsNode.childrenList()) {
                try {
                    // get delay
                    int delay = cmdNode.node("delay").getInt(0);
                    if (delay == 0) {
                        plugin.getLogger().warn("Command missing 'delay' field, skipping");
                        continue;
                    }
                    
                    // get command
                    String command = cmdNode.node("command").getString();
                    if (command == null || command.isEmpty()) {
                        plugin.getLogger().warn("Command missing 'command' field, skipping");
                        continue;
                    }
                    
                    // get target
                    String targetStr = cmdNode.node("target").getString("server");
                    RestartSchedule.CommandTarget target = 
                        targetStr.equalsIgnoreCase("proxy") ?
                        RestartSchedule.CommandTarget.PROXY :
                        RestartSchedule.CommandTarget.SERVER;
                    
                    // add command to builder
                    builder.addCommand(new RestartSchedule.ScheduledCommand(
                        command, delay, timing, target
                    ));
                    
                } catch (Exception e) {
                    plugin.getLogger().warn("Failed to parse command entry: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().error("Failed to parse commands", e);
        }
    }
    
    // getters for server memory
    public boolean isServerMemoryEnabled() { return serverMemoryEnabled; }
    public String getFallbackServer() { return fallbackServer; }
    public String getFirstJoinServer() { return firstJoinServer; }
    public int getRememberDays() { return rememberDays; }
    public List<String> getBlacklistedServers() { return blacklistedServers; }
    public String getBypassPermission() { return bypassPermission; }
    
    // getters for discord
    public boolean isDiscordEnabled() { return discordEnabled; }
    public String getDiscordWebhookUrl() { return discordWebhookUrl; }
    public String getDiscordUsername() { return discordUsername; }
    public String getDiscordAvatarUrl() { return discordAvatarUrl; }
    public boolean isShowFirstTime() { return showFirstTime; }
    public String getFirstTimeServer() { return firstTimeServer; }
    public boolean isChatRelayEnabled() { return chatRelayEnabled; }
    public String getChatFormat() { return chatFormat; }
    public boolean isUsePlayerHeadForChat() { return usePlayerHeadForChat; }
    public Map<String, String> getChatPrefixes() { return chatPrefixes; }
    public boolean isChatShowServerPrefix() { return chatShowServerPrefix; }
    public String getChatServerFormat() { return chatServerFormat; }
    public boolean isDiscordAFKEnabled() { return discordAFKEnabled; }
    
    // getters for messages
    public boolean isCustomMessagesEnabled() { return customMessagesEnabled; }
    public boolean isShowJoinMessages() { return showJoinMessages; }
    public boolean isShowLeaveMessages() { return showLeaveMessages; }
    public boolean isShowSwitchMessages() { return showSwitchMessages; }
    public boolean isShowFirstTimeMessages() { return showFirstTimeMessages; }
    public boolean isSuppressVanillaMessages() { return suppressVanillaMessages; }
    public String getMessagePrefix() { return messagePrefix; }
    
    // getters for stats
    public boolean isStatsEnabled() { return statsEnabled; }
    public int getStatsUpdateInterval() { return statsUpdateInterval; }
    public Map<String, String> getStatsServerPaths() { return statsServerPaths; }
    public boolean isStatsApiEnabled() { return statsApiEnabled; }
    public int getStatsApiPort() { return statsApiPort; }
    public String getStatsApiKey() { return statsApiKey; }
    public String getStatsApiBind() { return statsApiBind; }
    
    // getters for auto-restart
    public boolean isAutoRestartEnabled() { return autoRestartEnabled; }
    public String getAutoRestartTimezone() { return autoRestartTimezone; }
    public int getAutoRestartDelayCheckInterval() { return autoRestartDelayCheckInterval; }
    public Map<String, RestartSchedule> getRestartSchedules() { return restartSchedules; }
    
    // getter for debug
    public boolean isDebug() { return debug; }
    
    // get formatted message with replacements
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
        
        // apply replacements in pairs (key, value)
        for (int i = 0; i < replacements.length - 1; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }
        
        return message;
    }
}