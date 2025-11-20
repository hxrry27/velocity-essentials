package com.velocityessentials.backend;

import com.velocityessentials.backend.commands.AFKCommand;
import com.velocityessentials.backend.restart.RestartHandler;
import com.velocityessentials.backend.chat.ChatRelay;
import com.velocityessentials.backend.chat.ChannelManager;    
import com.velocityessentials.backend.chat.MuteManager;
import com.velocityessentials.backend.commands.ChatCommand;      

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import me.clip.placeholderapi.PlaceholderAPI;
import java.util.UUID;

public class VelocityEssentialsBackend extends JavaPlugin {
    public static final String CHANNEL = "velocityessentials:main";
    
    public boolean debug;
    public String serverName;

    private AFKManager afkManager;
    private RestartHandler restartHandler;
    private ChatRelay chatRelay;              
    private ChannelManager channelManager;     
    private MuteManager muteManager;     
    
    @Override
    public void onEnable() {
        // save default config
        saveDefaultConfig();
        loadConfiguration();
        
        // === SYSTEM 1: VANILLA MESSAGE SUPPRESSION ===
        if (getConfig().getBoolean("suppress-vanilla-join", false) || 
            getConfig().getBoolean("suppress-vanilla-quit", false)) {
            
            getServer().getPluginManager().registerEvents(new VanillaMessageSuppressor(this), this);
            getLogger().info("Vanilla message suppression enabled: Join=" + 
                getConfig().getBoolean("suppress-vanilla-join", false) + 
                " Quit=" + getConfig().getBoolean("suppress-vanilla-quit", false));
        }
        
        // === SYSTEM 2: NETWORK MESSAGING ===  
        if (getConfig().getBoolean("enable-network-messages", true)) {
            getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, new NetworkMessageHandler(this));
            getLogger().info("Network messaging enabled");
        }
        
        // === SYSTEM 3: CHAT RELAY ===
        if (getConfig().getBoolean("chat.enabled", true)) {
            getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
            chatRelay = new ChatRelay(this);
            channelManager = chatRelay.getChannelManager();
            muteManager = chatRelay.getMuteManager();
            getServer().getPluginManager().registerEvents(chatRelay, this);
            
            // register /chat command
            getCommand("chat").setExecutor(new ChatCommand(this));
            getCommand("chat").setTabCompleter(new ChatCommand(this));
            
            getLogger().info("Chat relay enabled - messages will be sent to velocity");
        }

        // === SYSTEM 4: AFK MANAGER ===
        if (getConfig().getBoolean("afk.enabled", true)) {
            getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
            afkManager = new AFKManager(this);
            
            // register /afk command
            getCommand("afk").setExecutor(new AFKCommand(this, afkManager));
            getCommand("afk").setAliases(java.util.List.of("away"));
            
            getLogger().info("AFK Manager enabled");
        }

        // === SYSTEM 5: RESTART HANDLER ===
        if (getConfig().getBoolean("auto-restart.enabled", true)) {
            getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
            restartHandler = new RestartHandler(this);
            getLogger().info("Restart Handler enabled");
        }
        
        if (getConfig().getBoolean("chat.enabled", true) && 
            Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().warning("PlaceholderAPI not found! Chat formatting will not work properly.");
        }
        
        getLogger().info("VelocityEssentials Backend enabled!");
        getLogger().info("Server: " + serverName);
    }
    
    @Override
    public void onDisable() {
        if (afkManager != null) {
            afkManager.shutdown();
        }

        if (restartHandler != null) {
            restartHandler.shutdown();
        }

        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        getLogger().info("VelocityEssentials Backend disabled!");
    }
    
    private void loadConfiguration() {
        serverName = getConfig().getString("server-name", "unknown");
        debug = getConfig().getBoolean("debug", false);
    }

    public AFKManager getAFKManager() {
        return afkManager;
    }

    public RestartHandler getRestartHandler() {
        return restartHandler;
    }

    public ChatRelay getChatRelay() {
        return chatRelay;
    }
    
    public ChannelManager getChannelManager() {
        return channelManager;
    }
    
    public MuteManager getMuteManager() {
        return muteManager;
    }
    
    // === VANILLA MESSAGE SUPPRESSION ===
    public static class VanillaMessageSuppressor implements Listener {
        private final VelocityEssentialsBackend plugin;
        private final boolean suppressJoin;
        private final boolean suppressQuit;
        
        public VanillaMessageSuppressor(VelocityEssentialsBackend plugin) {
            this.plugin = plugin;
            this.suppressJoin = plugin.getConfig().getBoolean("suppress-vanilla-join", false);
            this.suppressQuit = plugin.getConfig().getBoolean("suppress-vanilla-quit", false);
        }
        
        @EventHandler(priority = EventPriority.LOWEST)
        public void onPlayerJoin(PlayerJoinEvent event) {
            if (suppressJoin) {
                event.joinMessage(null);
                if (plugin.debug) {
                    plugin.getLogger().info("Suppressed vanilla join message for: " + event.getPlayer().getName());
                }
            }
        }
        
        @EventHandler(priority = EventPriority.LOWEST)
        public void onPlayerQuit(PlayerQuitEvent event) {
            if (suppressQuit) {
                event.quitMessage(null);
                if (plugin.debug) {
                    plugin.getLogger().info("Suppressed vanilla quit message for: " + event.getPlayer().getName());
                }
            }
        }
    }
    
    // === NETWORK MESSAGE HANDLER ===
    public static class NetworkMessageHandler implements PluginMessageListener {
        private final VelocityEssentialsBackend plugin;
        private final MiniMessage miniMessage;
        
        public NetworkMessageHandler(VelocityEssentialsBackend plugin) {
            this.plugin = plugin;
            this.miniMessage = MiniMessage.miniMessage();
        }
        
        @Override
        public void onPluginMessageReceived(String channel, Player player, byte[] message) {
            if (!channel.equals(CHANNEL)) return;
            
            ByteArrayDataInput in = ByteStreams.newDataInput(message);
            String subchannel = in.readUTF();
            
            if (plugin.debug) {
                plugin.getLogger().info("Received network message: " + subchannel);
            }
            
            switch (subchannel) {
                case "network_join" -> handleNetworkJoin(in);
                case "network_leave" -> handleNetworkLeave(in);  
                case "network_switch" -> handleNetworkSwitch(in);
                case "network_chat" -> handleNetworkChat(in);
                case "network_afk" -> handleNetworkAFK(in);
                case "network_afk_message" -> handleNetworkAFKWithMessage(in);
                case "mute_player" -> handleMutePlayer(in);
                case "unmute_player" -> handleUnmutePlayer(in);
                case "test" -> handleTest(in);
                case "restart_warning" -> handleRestartWarning(in);
                case "restart_command" -> handleRestartCommand(in);
                case "restart_shutdown" -> handleRestartShutdown();
                case "restart_cancel" -> handleRestartCancel(in);
                case "restart_test" -> handleRestartTest(in);
            }
        }
        
        private void handleNetworkJoin(ByteArrayDataInput in) {
            String playerName = in.readUTF();
            String serverName = in.readUTF();
            String formattedMessage = in.readUTF();
            
            // parse minimessage and broadcast
            Component message = miniMessage.deserialize(formattedMessage);
            Bukkit.broadcast(message);
            
            if (plugin.debug) {
                plugin.getLogger().info("network join: " + playerName + " on " + serverName);
            }
        }
        
        private void handleNetworkLeave(ByteArrayDataInput in) {
            String playerName = in.readUTF();
            String serverName = in.readUTF();
            String formattedMessage = in.readUTF();
            
            // parse minimessage and broadcast
            Component message = miniMessage.deserialize(formattedMessage);
            Bukkit.broadcast(message);
            
            if (plugin.debug) {
                plugin.getLogger().info("network leave: " + playerName + " from " + serverName);
            }
        }
        
        private void handleNetworkSwitch(ByteArrayDataInput in) {
            String playerName = in.readUTF();
            String serverName = in.readUTF();
            String formattedMessage = in.readUTF();
            
            // parse minimessage and broadcast
            Component message = miniMessage.deserialize(formattedMessage);
            Bukkit.broadcast(message);
            
            if (plugin.debug) {
                plugin.getLogger().info("network switch: " + playerName + " to " + serverName);
            }
        }

        private void handleNetworkAFK(ByteArrayDataInput in) {
            String playerName = in.readUTF();
            boolean isAfk = in.readBoolean();
            boolean manual = in.readBoolean();
            
            if (plugin.getAFKManager() != null) {
                plugin.getAFKManager().handleNetworkAFKMessage(playerName, isAfk, manual);
            }
            
            if (plugin.debug) {
                plugin.getLogger().info("Network AFK: " + playerName + " is " + (isAfk ? "now" : "no longer") + " AFK");
            }
        }

        private void handleNetworkChat(ByteArrayDataInput in) {
            try {
                String formattedMessage = in.readUTF();
                String channelId = in.readUTF();  // NEW - read channel id
                
                if (plugin.debug) {
                    plugin.getLogger().info("network chat raw [" + channelId + "]: " + formattedMessage);
                }
                
                // parse minimessage
                Component message = miniMessage.deserialize(formattedMessage);
                
                // get channel from manager
                if (plugin.getChannelManager() != null) {
                    com.velocityessentials.backend.chat.Channel channel = 
                        plugin.getChannelManager().getChannel(channelId);
                    
                    if (channel != null && channel.hasReceivePermission()) {
                        // filter by permission - only send to players who can see this channel
                        String receivePermission = channel.getReceivePermission();
                        
                        int sent = 0;
                        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
                            if (player.hasPermission(receivePermission)) {
                                player.sendMessage(message);
                                sent++;
                            }
                        }
                        
                        if (plugin.debug) {
                            plugin.getLogger().info("network chat sent to " + sent + " players with permission");
                        }
                        
                        return;
                    }
                }
                
                // no permission filtering - broadcast to all
                Bukkit.broadcast(message);
                
                if (plugin.debug) {
                    plugin.getLogger().info("network chat broadcasted to all players");
                }
                
            } catch (Exception e) {
                plugin.getLogger().warning("error handling network chat: " + e.getMessage());
                e.printStackTrace();
            }
        }

        private void handleNetworkAFKWithMessage(ByteArrayDataInput in) {
            String playerName = in.readUTF();
            boolean isAfk = in.readBoolean();
            boolean manual = in.readBoolean();
            String afkMessage = in.readUTF();
            
            if (plugin.getAFKManager() != null) {
                String message = afkMessage.isEmpty() ? null : afkMessage;
                plugin.getAFKManager().handleNetworkAFKMessage(playerName, isAfk, manual, message);
            }
            
            if (plugin.debug) {
                plugin.getLogger().info("Network AFK: " + playerName + " is " + (isAfk ? "now" : "no longer") + " AFK" + (afkMessage.isEmpty() ? "" : " (Message: " + afkMessage + ")"));
            }
        }   
        
        private void handleTest(ByteArrayDataInput in) {
            String testMessage = in.readUTF();
            plugin.getLogger().info("received test message: " + testMessage);
        }

        private void handleRestartWarning(ByteArrayDataInput in) {
            int secondsUntilRestart = in.readInt();
            String soundName = in.readUTF();
            
            if (plugin.getRestartHandler() != null) {
                plugin.getRestartHandler().handleWarning(secondsUntilRestart, soundName);
            }
            
            if (plugin.debug) {
                plugin.getLogger().info("Restart warning: " + secondsUntilRestart + "s");
            }
        }

        private void handleRestartCommand(ByteArrayDataInput in) {
            String command = in.readUTF();
            
            if (plugin.getRestartHandler() != null) {
                plugin.getRestartHandler().executeCommand(command);
            }
            
            if (plugin.debug) {
                plugin.getLogger().info("Restart command: " + command);
            }
        }

        private void handleRestartShutdown() {
            if (plugin.getRestartHandler() != null) {
                plugin.getRestartHandler().initiateShutdown();
            }
            
            plugin.getLogger().warning("Shutdown signal received from proxy!");
        }

        private void handleRestartCancel(ByteArrayDataInput in) {
            String reason = in.readUTF();
            
            if (plugin.getRestartHandler() != null) {
                plugin.getRestartHandler().handleCancel(reason);
            }
            
            if (plugin.debug) {
                plugin.getLogger().info("Restart cancelled: " + reason);
            }
        }

        private void handleRestartTest(ByteArrayDataInput in) {
            String testMessage = in.readUTF();
            plugin.getLogger().info("Restart test message: " + testMessage);
            
            Component broadcast = Component.text("[Restart Test] " + testMessage)
                .color(NamedTextColor.AQUA);
            Bukkit.getOnlinePlayers().forEach(player -> player.sendMessage(broadcast));
        }

        private void handleMutePlayer(ByteArrayDataInput in) {
            String uuidStr = in.readUTF();
            String reason = in.readUTF();
            long expiresAt = in.readLong();
            
            if (plugin.getMuteManager() != null) {
                UUID uuid = UUID.fromString(uuidStr);
                plugin.getMuteManager().mute(uuid, reason, expiresAt);
                
                if (plugin.debug) {
                    plugin.getLogger().info("received mute for: " + uuid + 
                        (expiresAt == 0 ? " (permanent)" : " (temporary)"));
                }
            }
        }

        private void handleUnmutePlayer(ByteArrayDataInput in) {
            String uuidStr = in.readUTF();
            
            if (plugin.getMuteManager() != null) {
                UUID uuid = UUID.fromString(uuidStr);
                plugin.getMuteManager().unmute(uuid);
                
                if (plugin.debug) {
                    plugin.getLogger().info("received unmute for: " + uuid);
                }
            }
        }
    }

}