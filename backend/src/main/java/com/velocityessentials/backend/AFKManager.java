package com.velocityessentials.backend;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitTask;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import net.kyori.adventure.text.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AFKManager implements Listener {
    private final VelocityEssentialsBackend plugin;
    private final Map<UUID, AFKPlayer> players = new HashMap<>();
    private final Set<UUID> afkPlayers = new HashSet<>();
    private BukkitTask checkTask;
    
    // Configuration
    private boolean enabled;
    private int autoAfkTime; // seconds until auto-AFK
    private boolean kickEnabled;
    private int kickTime; // seconds until kick after AFK
    private boolean broadcastAfk;
    private boolean moveCancel;
    private boolean interactCancel;
    
    public AFKManager(VelocityEssentialsBackend plugin) {
        this.plugin = plugin;
        loadConfig();
        
        if (enabled) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            startAfkChecker();
            plugin.getLogger().info("AFK Manager enabled - Auto-AFK after " + autoAfkTime + " seconds");
        }
    }
    
    private void loadConfig() {
        this.enabled = plugin.getConfig().getBoolean("afk.enabled", true);
        this.autoAfkTime = plugin.getConfig().getInt("afk.auto-afk-time", 300); // 5 minutes default
        this.kickEnabled = plugin.getConfig().getBoolean("afk.kick.enabled", false);
        this.kickTime = plugin.getConfig().getInt("afk.kick.time", 600); // 10 minutes default
        this.broadcastAfk = plugin.getConfig().getBoolean("afk.broadcast", true);
        this.moveCancel = plugin.getConfig().getBoolean("afk.cancel-on-move", true);
        this.interactCancel = plugin.getConfig().getBoolean("afk.cancel-on-interact", true);
    }
    
    private void startAfkChecker() {
        checkTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                AFKPlayer afkPlayer = players.get(uuid);
                
                if (afkPlayer == null) continue;
                
                long idleTime = (now - afkPlayer.lastActivity) / 1000; // Convert to seconds
                
                // Check for auto-AFK
                if (!afkPlayer.isAfk && idleTime >= autoAfkTime) {
                    if (!player.hasPermission("velocityessentials.afk.exempt")) {
                        setAFK(player, true, false);
                    }
                }
                
                // Check for kick
                if (kickEnabled && afkPlayer.isAfk && idleTime >= (autoAfkTime + kickTime)) {
                    if (!player.hasPermission("velocityessentials.afk.kickexempt")) {
                        player.kickPlayer("You have been kicked for being AFK too long!");
                        if (plugin.debug) {
                            plugin.getLogger().info("Kicked " + player.getName() + " for being AFK");
                        }
                    }
                }
            }
        }, 20L, 20L); // Run every second
    }
    
    public void setAFK(Player player, boolean afk, boolean manual) {
        UUID uuid = player.getUniqueId();
        AFKPlayer afkPlayer = players.get(uuid);
        
        if (afkPlayer == null) {
            afkPlayer = new AFKPlayer(uuid);
            players.put(uuid, afkPlayer);
        }
        
        // Don't send duplicate messages
        if (afkPlayer.isAfk == afk) return;
        
        afkPlayer.isAfk = afk;
        afkPlayer.afkStart = afk ? System.currentTimeMillis() : 0;
        
        if (afk) {
            afkPlayers.add(uuid);
        } else {
            afkPlayers.remove(uuid);
            afkPlayer.lastActivity = System.currentTimeMillis();
        }
        
        // Send to Velocity for network-wide broadcast
        if (broadcastAfk) {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("afk_status");
            out.writeUTF(player.getUniqueId().toString());
            out.writeUTF(player.getName());
            out.writeBoolean(afk);
            out.writeBoolean(manual);
            out.writeUTF(plugin.serverName);
            
            // Send to any online player (plugin messaging requires a player)
            if (!Bukkit.getOnlinePlayers().isEmpty()) {
                Bukkit.getOnlinePlayers().iterator().next()
                    .sendPluginMessage(plugin, VelocityEssentialsBackend.CHANNEL, out.toByteArray());
                
                if (plugin.debug) {
                    plugin.getLogger().info("Sent AFK status to Velocity: " + player.getName() + " = " + afk);
                }
            }
        }
    }
    
    public boolean isAFK(Player player) {
        AFKPlayer afkPlayer = players.get(player.getUniqueId());
        return afkPlayer != null && afkPlayer.isAfk;
    }
    
    public void toggleAFK(Player player) {
        setAFK(player, !isAFK(player), true);
    }
    
    public void handleNetworkAFKMessage(String playerName, boolean isAfk, boolean manual) {
        // Display the AFK message locally when received from Velocity
        String message;
        if (isAfk) {
            if (manual) {
                message = "§7* §e" + playerName + " §7is now AFK";
            } else {
                message = "§7* §e" + playerName + " §7has gone AFK";
            }
        } else {
            message = "§7* §e" + playerName + " §7is no longer AFK";
        }
        
        Component component = Component.text(message);
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(component));
    }
    
    private void updateActivity(Player player) {
        if (!enabled) return;
        
        UUID uuid = player.getUniqueId();
        AFKPlayer afkPlayer = players.get(uuid);
        
        if (afkPlayer == null) {
            afkPlayer = new AFKPlayer(uuid);
            players.put(uuid, afkPlayer);
        }
        
        afkPlayer.lastActivity = System.currentTimeMillis();
        
        // Cancel AFK if they're AFK and performed an action
        if (afkPlayer.isAfk) {
            setAFK(player, false, false);
        }
    }
    
    // ===== EVENT HANDLERS =====
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        AFKPlayer afkPlayer = new AFKPlayer(player.getUniqueId());
        afkPlayer.lastLocation = player.getLocation();
        players.put(player.getUniqueId(), afkPlayer);
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        players.remove(uuid);
        afkPlayers.remove(uuid);
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!moveCancel) return;
        
        Player player = event.getPlayer();
        AFKPlayer afkPlayer = players.get(player.getUniqueId());
        if (afkPlayer == null) return;
        
        Location from = event.getFrom();
        Location to = event.getTo();
        
        // Only count as activity if they actually moved (not just looked around)
        if (to != null && (from.getBlockX() != to.getBlockX() || 
            from.getBlockY() != to.getBlockY() || 
            from.getBlockZ() != to.getBlockZ())) {
            
            updateActivity(player);
            afkPlayer.lastLocation = to;
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        updateActivity(event.getPlayer());
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        // Don't reset AFK for /afk command itself
        String cmd = event.getMessage().toLowerCase();
        if (!cmd.startsWith("/afk") && !cmd.startsWith("/away")) {
            updateActivity(event.getPlayer());
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (interactCancel) {
            updateActivity(event.getPlayer());
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        updateActivity(event.getPlayer());
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        updateActivity(event.getPlayer());
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            updateActivity((Player) event.getWhoClicked());
        }
    }
    
    public void shutdown() {
        if (checkTask != null) {
            checkTask.cancel();
        }
    }
    
    // ===== INTERNAL CLASSES =====
    
    private static class AFKPlayer {
        private final UUID uuid;
        private long lastActivity;
        private boolean isAfk;
        private long afkStart;
        private Location lastLocation;
        
        public AFKPlayer(UUID uuid) {
            this.uuid = uuid;
            this.lastActivity = System.currentTimeMillis();
            this.isAfk = false;
            this.afkStart = 0;
        }
    }
}