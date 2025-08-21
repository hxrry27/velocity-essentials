package com.velocityessentials.backend;

import com.velocityessentials.backend.commands.AFKCommand;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

public class VelocityEssentialsBackend extends JavaPlugin {
    public static final String CHANNEL = "velocityessentials:main";
    
    public boolean debug;
    public String serverName;

    private AFKManager afkManager;
    
    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();
        loadConfiguration();
        
        // === SYSTEM 1: VANILLA MESSAGE SUPPRESSION ===
        // Simple, standalone system, NOT conditional on network messaging anymore
        if (getConfig().getBoolean("suppress-vanilla-join", false) || 
            getConfig().getBoolean("suppress-vanilla-quit", false)) {
            
            getServer().getPluginManager().registerEvents(new VanillaMessageSuppressor(this), this);
            getLogger().info("Vanilla message suppression enabled: Join=" + 
                getConfig().getBoolean("suppress-vanilla-join", false) + 
                " Quit=" + getConfig().getBoolean("suppress-vanilla-quit", false));
        }
        
        // === SYSTEM 2: NETWORK MESSAGING ===  
        // Separate system from vanilla message supression for receiving custom messages from Velocity
        if (getConfig().getBoolean("enable-network-messages", true)) {
            getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, new NetworkMessageHandler(this));
            getLogger().info("Network messaging enabled");
        }
        
        // === SYSTEM 3: CHAT RELAY ===
        if (getConfig().getBoolean("enable-chat-processing", false)) {
            getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
            getServer().getPluginManager().registerEvents(new ChatProcessor(this), this);
            getLogger().info("Chat processing enabled");
        }

        // === SYSTEM 4: AFK MANAGER ===
        if (getConfig().getBoolean("afk.enabled", true)) {
            getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
            afkManager = new AFKManager(this);
            
            // Register /afk command
            getCommand("afk").setExecutor(new AFKCommand(this, afkManager));
            getCommand("afk").setAliases(java.util.List.of("away"));
            
            getLogger().info("AFK Manager enabled");
        }
        
        // Check for PlaceholderAPI if chat processing is enabled
        if (getConfig().getBoolean("enable-chat-processing", false) && 
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
        
        public NetworkMessageHandler(VelocityEssentialsBackend plugin) {
            this.plugin = plugin;
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
                case "network_afk" -> handleNetworkAFK(in);
                case "network_afk_message" -> handleNetworkAFKWithMessage(in);
                case "test" -> handleTest(in);
            }
        }
        
        private void handleNetworkJoin(ByteArrayDataInput in) {
            String playerName = in.readUTF();
            String serverName = in.readUTF();
            String customMessage = in.readUTF();
            
            // Broadcast custom message to all players on this server
            Component message = Component.text(customMessage);
            Bukkit.getOnlinePlayers().forEach(player -> player.sendMessage(message));
            
            if (plugin.debug) {
                plugin.getLogger().info("Network join: " + playerName + " → " + serverName);
            }
        }
        
        private void handleNetworkLeave(ByteArrayDataInput in) {
            String playerName = in.readUTF();
            String serverName = in.readUTF();
            String customMessage = in.readUTF();
            
            Component message = Component.text(customMessage);
            Bukkit.getOnlinePlayers().forEach(player -> player.sendMessage(message));
            
            if (plugin.debug) {
                plugin.getLogger().info("Network leave: " + playerName + " ← " + serverName);
            }
        }
        
        private void handleNetworkSwitch(ByteArrayDataInput in) {
            String playerName = in.readUTF();
            String serverName = in.readUTF(); // New server they switched to
            String customMessage = in.readUTF();
            
            Component message = Component.text(customMessage);
            Bukkit.getOnlinePlayers().forEach(player -> player.sendMessage(message));
            
            if (plugin.debug) {
                plugin.getLogger().info("Network switch: " + customMessage);
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
            plugin.getLogger().info("Test message: " + testMessage);
            
            Component broadcast = Component.text("[VE Test] " + testMessage)
                .color(NamedTextColor.AQUA);
            Bukkit.getOnlinePlayers().forEach(player -> player.sendMessage(broadcast));
        }
    }
    
    // === CHAT PROCESSOR (Optional) ===
    public static class ChatProcessor implements Listener {
        private final VelocityEssentialsBackend plugin;
        
        public ChatProcessor(VelocityEssentialsBackend plugin) {
            this.plugin = plugin;
        }
        
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onPlayerChat(AsyncPlayerChatEvent event) {
            // Don't process if PlaceholderAPI isn't available
            if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return;
            
            Player player = event.getPlayer();
            
            // Get formatted prefix from PlaceholderAPI
            String prefix = PlaceholderAPI.setPlaceholders(player, "%playercustomisation_prefix%");
            
            if (plugin.debug) {
                plugin.getLogger().info("Raw prefix from PAPI: '" + prefix + "'");
            }

            // Clean up prefix for Discord (remove color codes)
            prefix = ChatColor.stripColor(prefix).trim();
            
            // Convert [Prefix] to **Prefix** for Discord
            if (prefix.startsWith("[") && prefix.endsWith("]")) {
                prefix = "**" + prefix.substring(1, prefix.length() - 1) + "**";
            } else if (!prefix.isEmpty()) {
                prefix = "**" + prefix + "**";
            }
            
            // Send to Velocity
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("chat");
            out.writeUTF(player.getUniqueId().toString());
            out.writeUTF(player.getName());
            out.writeUTF(prefix);
            out.writeUTF(event.getMessage());
            out.writeUTF(plugin.serverName);
            
            // Send to any online player (plugin messaging requires a player)
            if (!Bukkit.getOnlinePlayers().isEmpty()) {
                Bukkit.getOnlinePlayers().iterator().next()
                    .sendPluginMessage(plugin, CHANNEL, out.toByteArray());
                
                if (plugin.debug) {
                    plugin.getLogger().info("Sent chat to Velocity: " + prefix + " " + player.getName() + ": " + event.getMessage());
                }
            }
        }
    }
}