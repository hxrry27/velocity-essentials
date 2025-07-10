package com.velocityessentials.config;

/**
 * Configuration key constants
 */
public class ConfigKeys {
    // Database
    public static final String DB_HOST = "database.host";
    public static final String DB_PORT = "database.port";
    public static final String DB_DATABASE = "database.database";
    public static final String DB_USERNAME = "database.username";
    public static final String DB_PASSWORD = "database.password";
    
    // Server Memory
    public static final String MEMORY_ENABLED = "server-memory.enabled";
    public static final String FALLBACK_SERVER = "server-memory.fallback-server";
    public static final String FIRST_JOIN_SERVER = "server-memory.first-join-server";
    public static final String REMEMBER_DAYS = "server-memory.remember-days";
    public static final String BLACKLISTED_SERVERS = "server-memory.blacklisted-servers";
    public static final String BYPASS_PERMISSION = "server-memory.bypass-permission";
    
    // Discord
    public static final String DISCORD_ENABLED = "discord.enabled";
    public static final String DISCORD_WEBHOOK_URL = "discord.webhook-url";
    public static final String DISCORD_USERNAME = "discord.username";
    public static final String DISCORD_AVATAR_URL = "discord.avatar-url";
    public static final String DISCORD_SHOW_FIRST_TIME = "discord.show-first-time";
    public static final String DISCORD_FIRST_TIME_SERVER = "discord.first-time-server";
    
    // Messages
    public static final String MESSAGES_CUSTOM_ENABLED = "messages.custom-enabled";
    public static final String MESSAGES_PREFIX = "messages.prefix";
    public static final String MESSAGES_NO_PERMISSION = "messages.no-permission";
    public static final String MESSAGES_SENDING_LAST_SERVER = "messages.sending-to-last-server";
    public static final String MESSAGES_SENDING_FALLBACK = "messages.sending-to-fallback";
    public static final String MESSAGES_FIRST_JOIN = "messages.first-join";
    
    // Other
    public static final String DEBUG = "debug";
    
    private ConfigKeys() {
        // Utility class
    }
}