package com.velocityessentials;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.command.CommandMeta;
import com.velocityessentials.commands.EventCommand;
import com.velocityessentials.commands.MainCommand;
import com.velocityessentials.config.Config;
import com.velocityessentials.database.Database;
import com.velocityessentials.database.PlayerData;
import com.velocityessentials.listeners.PluginMessageListener;
import com.velocityessentials.listeners.PlayerListener;
import com.velocityessentials.listeners.ServerSwitchListener;
import com.velocityessentials.modules.afk.AFKHandler;
import com.velocityessentials.modules.discord.DiscordWebhook;
import com.velocityessentials.modules.messages.MessageHandler;
import com.velocityessentials.utils.PlayerTracker;
import com.velocityessentials.stats.StatsSystem;
import com.velocityessentials.stats.StatsAPIHandler;

import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Plugin(
    id = "velocityessentials",
    name = "VelocityEssentials",
    version = "1.0.0",
    description = "Essential utilities for Velocity proxy networks",
    authors = {"hxrry27"}
)
public class VelocityEssentials {
    private static VelocityEssentials instance;
    
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    
    
    // Core components
    private Config config;
    private Database database;
    private PlayerData playerData;
    private PlayerTracker playerTracker;
    private AFKHandler afkHandler;
    
    // Modules
    private DiscordWebhook discordWebhook;
    private MessageHandler messageHandler;
    private StatsSystem statsSystem;
    private StatsAPIHandler statsAPI;
    
    // Plugin messaging channel
    public static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from("velocityessentials:main");
    
    @Inject
    public VelocityEssentials(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        instance = this;
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }
    
    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("VelocityEssentials is starting...");
        
        // Load configuration
        config = new Config(this);
        if (!config.load()) {
            logger.error("Failed to load configuration! Disabling plugin.");
            return;
        }

        // Initialize database
        database = new Database(this);
        if (!database.connect()) {
            logger.error("Failed to connect to database! Disabling plugin.");
            return;
        }

        // Initialize components
        playerData = new PlayerData(this);
        playerTracker = new PlayerTracker(this);
        discordWebhook = new DiscordWebhook(this);
        messageHandler = new MessageHandler(this);
        afkHandler = new AFKHandler(this);
        
        // Register plugin messaging channel
        server.getChannelRegistrar().register(CHANNEL);
        
        
        // Register listeners
        server.getEventManager().register(this, new PlayerListener(this));
        server.getEventManager().register(this, new ServerSwitchListener(this));
        server.getEventManager().register(this, new PluginMessageListener(this));

        // Register commands
        CommandMeta commandMeta = server.getCommandManager()
            .metaBuilder("velocityessentials")
            .aliases("ve", "vess")
            .plugin(this)
            .build();
        
        // Register main command
        server.getCommandManager().register(commandMeta, new MainCommand(this));
        
        // Initialize stats system if enabled
        if (config.isStatsEnabled()) {
            try {
                statsSystem = new StatsSystem(this);
                logger.info("Stats system initialized!");
                
                // Start API if enabled
                if (config.isStatsApiEnabled()) {
                    statsAPI = new StatsAPIHandler(
                        this, 
                        statsSystem,
                        config.getStatsApiPort(),
                        config.getStatsApiKey()
                    );
                    logger.info("Stats API started on port " + config.getStatsApiPort());
                }
                
                // Move the event command registration here
                CommandMeta eventMeta = server.getCommandManager()
                    .metaBuilder("event")
                    .aliases("valeevent", "ve")
                    .plugin(this)
                    .build();
                    
                server.getCommandManager().register(eventMeta, new EventCommand(statsSystem));
                
            } catch (Exception e) {
                logger.error("Failed to initialize stats system!", e);
            }
        }

        // Schedule cleanup task
        server.getScheduler()
            .buildTask(this, () -> playerData.cleanupOldEntries())
            .delay(1, TimeUnit.HOURS)
            .repeat(6, TimeUnit.HOURS)
            .schedule();
        
        logger.info("VelocityEssentials has been enabled!");
    }
    
    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("VelocityEssentials is shutting down...");
        
        if (database != null) {
            database.close();
        }
        
        if (statsAPI != null) {
            statsAPI.shutdown();
        }
        
        if (statsSystem != null) {
            statsSystem.shutdown();
        }

        logger.info("VelocityEssentials has been disabled!");
    }
    
    public void reload() {
        logger.info("Reloading configuration...");
        if (config.load()) {
            logger.info("Configuration reloaded successfully!");
        } else {
            logger.error("Failed to reload configuration!");
        }
    }
    
    // Getters
    public static VelocityEssentials getInstance() {
        return instance;
    }
    
    public ProxyServer getServer() {
        return server;
    }
    
    public Logger getLogger() {
        return logger;
    }
    
    public Path getDataDirectory() {
        return dataDirectory;
    }
    
    public Config getConfig() {
        return config;
    }
    
    public Database getDatabase() {
        return database;
    }
    
    public PlayerData getPlayerData() {
        return playerData;
    }
    
    public PlayerTracker getPlayerTracker() {
        return playerTracker;
    }
    
    public DiscordWebhook getDiscordWebhook() {
        return discordWebhook;
    }
    
    public MessageHandler getMessageHandler() {
        return messageHandler;
    }

    public AFKHandler getAFKHandler() {
        return afkHandler;
    }
}