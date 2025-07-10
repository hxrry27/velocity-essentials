package com.velocityessentials.backend;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VelocityEssentialsBackend extends JavaPlugin implements PluginMessageListener, Listener {
    private static final String CHANNEL = "velocityessentials:main";
    
    // Track players we should suppress messages for
    private final Map<String, Long> suppressedPlayers = new ConcurrentHashMap<>();
    private static final long SUPPRESS_DURATION = 5000; // 5 seconds
    
    private String serverName;
    private boolean debug;
    
    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();
        loadConfiguration();
        
        // Register plugin messaging channel
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        
        // Register event listeners
        getServer().getPluginManager().registerEvents(this, this);
        
        // Schedule cleanup task
        getServer().getScheduler().runTaskTimerAsynchronously(this, this::cleanupSuppressed, 100L, 100L);
        
        getLogger().info("VelocityEssentials Backend enabled!");
        getLogger().info("Server name: " + serverName);
    }
    
    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        getLogger().info("VelocityEssentials Backend disabled!");
    }
    
    private void loadConfiguration() {
        FileConfiguration config = getConfig();
        serverName = config.getString("server-name", "unknown");
        debug = config.getBoolean("debug", false);
    }
    
    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals(CHANNEL)) {
            return;
        }
        
        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        String subchannel = in.readUTF();
        
        if (debug) {
            getLogger().info("Received message: " + subchannel);
        }
        
        switch (subchannel) {
            case "join" -> handleJoinMessage(in);
            case "leave" -> handleLeaveMessage(in);
            case "switch" -> handleSwitchMessage(in);
            case "firstjoin" -> handleFirstJoinMessage(in);
            case "suppress" -> handleSuppressMessage(in);
            case "test" -> handleTestMessage(in);
        }
    }
    
    private void handleJoinMessage(ByteArrayDataInput in) {
        String playerName = in.readUTF();
        String server = in.readUTF();
        
        // Only show if it's for this server
        if (!server.equals(serverName)) {
            return;
        }
        
        Component message = Component.text()
            .append(Component.text("[", NamedTextColor.DARK_GRAY))
            .append(Component.text("+", NamedTextColor.GREEN))
            .append(Component.text("] ", NamedTextColor.DARK_GRAY))
            .append(Component.text(playerName, NamedTextColor.GREEN))
            .append(Component.text(" joined the game", NamedTextColor.YELLOW))
            .build();
        
        Bukkit.broadcast(message);
    }
    
    private void handleLeaveMessage(ByteArrayDataInput in) {
        String playerName = in.readUTF();
        String server = in.readUTF();
        
        // Only show if it's for this server
        if (!server.equals(serverName)) {
            return;
        }
        
        Component message = Component.text()
            .append(Component.text("[", NamedTextColor.DARK_GRAY))
            .append(Component.text("-", NamedTextColor.RED))
            .append(Component.text("] ", NamedTextColor.DARK_GRAY))
            .append(Component.text(playerName, NamedTextColor.RED))
            .append(Component.text(" left the game", NamedTextColor.YELLOW))
            .build();
        
        Bukkit.broadcast(message);
    }
    
    private void handleSwitchMessage(ByteArrayDataInput in) {
        String playerName = in.readUTF();
        String fromServer = in.readUTF();
        String toServer = in.readUTF();
        
        Component message = Component.text()
            .append(Component.text("[", NamedTextColor.DARK_GRAY))
            .append(Component.text("↔", NamedTextColor.GOLD))
            .append(Component.text("] ", NamedTextColor.DARK_GRAY))
            .append(Component.text(playerName, NamedTextColor.GOLD))
            .append(Component.text(" switched servers: ", NamedTextColor.YELLOW))
            .append(Component.text(fromServer, NamedTextColor.WHITE))
            .append(Component.text(" → ", NamedTextColor.GRAY))
            .append(Component.text(toServer, NamedTextColor.WHITE))
            .build();
        
        Bukkit.broadcast(message);
    }
    
    private void handleFirstJoinMessage(ByteArrayDataInput in) {
        String playerName = in.readUTF();
        String server = in.readUTF();
        
        // Only show if it's for this server
        if (!server.equals(serverName)) {
            return;
        }
        
        Component message = Component.text()
            .append(Component.text("[", NamedTextColor.DARK_GRAY))
            .append(Component.text("★", NamedTextColor.GOLD))
            .append(Component.text("] ", NamedTextColor.DARK_GRAY))
            .append(Component.text(playerName, NamedTextColor.GOLD))
            .append(Component.text(" joined for the first time!", NamedTextColor.YELLOW)
                .decorate(TextDecoration.BOLD))
            .build();
        
        Bukkit.broadcast(message);
    }
    
    private void handleSuppressMessage(ByteArrayDataInput in) {
        String playerName = in.readUTF();
        suppressedPlayers.put(playerName.toLowerCase(), System.currentTimeMillis());
        
        if (debug) {
            getLogger().info("Suppressing messages for: " + playerName);
        }
    }
    
    private void handleTestMessage(ByteArrayDataInput in) {
        String message = in.readUTF();
        getLogger().info("Test message received: " + message);
        
        Component broadcast = Component.text()
            .append(Component.text("[VE Test] ", NamedTextColor.AQUA))
            .append(Component.text(message, NamedTextColor.WHITE))
            .build();
        
        Bukkit.broadcast(broadcast);
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        String playerName = event.getPlayer().getName().toLowerCase();
        
        if (shouldSuppress(playerName)) {
            event.joinMessage(null); // Suppress vanilla message
            suppressedPlayers.remove(playerName);
            
            if (debug) {
                getLogger().info("Suppressed join message for: " + event.getPlayer().getName());
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        String playerName = event.getPlayer().getName().toLowerCase();
        
        if (shouldSuppress(playerName)) {
            event.quitMessage(null); // Suppress vanilla message
            suppressedPlayers.remove(playerName);
            
            if (debug) {
                getLogger().info("Suppressed quit message for: " + event.getPlayer().getName());
            }
        }
    }
    
    private boolean shouldSuppress(String playerName) {
        Long suppressTime = suppressedPlayers.get(playerName);
        if (suppressTime == null) {
            return false;
        }
        
        // Check if suppression is still valid
        return System.currentTimeMillis() - suppressTime < SUPPRESS_DURATION;
    }
    
    private void cleanupSuppressed() {
        long now = System.currentTimeMillis();
        suppressedPlayers.entrySet().removeIf(entry -> 
            now - entry.getValue() > SUPPRESS_DURATION
        );
    }
}