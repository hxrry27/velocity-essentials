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
    private final Map<UUID, String> afkMessages = new HashMap<>();
    private BukkitTask checkTask;
    
    // Configuration
    private boolean enabled; // top level module enabler
    private int autoAfkTime; // seconds until auto-AFK
    private boolean kickEnabled; // whether player will be kicked when AFK
    private int kickTime; // seconds until kick after AFK
    private boolean broadcastAfk; // whether to send a message to chat about AFK
    private boolean moveCancel; // whether moving cancels the AFK
    private boolean interactCancel; // whether interacting with the player cancels the AFK
    private boolean excludeFromSleep; // whether to exclude AFK players from the Sleep Count for percentage gamerule in MP
    
    public AFKManager(VelocityEssentialsBackend plugin) {
        this.plugin = plugin;
        loadConfig();
        
        if (enabled) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            startAfkChecker();

            // Start sleep count updater if config toggle enabled
            if (excludeFromSleep) {
                startSleepCountUpdater();
            }

            plugin.getLogger().info("AFK Manager enabled - Auto-AFK after " + autoAfkTime + " seconds");
        }
    }
    
    private void loadConfig() {
        this.enabled = plugin.getConfig().getBoolean("afk.enabled", true); // module enabled by default
        this.autoAfkTime = plugin.getConfig().getInt("afk.auto-afk-time", 300); // 5 minutes default auto AFK time
        this.kickEnabled = plugin.getConfig().getBoolean("afk.kick.enabled", false); // kick for AFK disabled by default
        this.kickTime = plugin.getConfig().getInt("afk.kick.time", 600); // 10 minutes default Kick time IF manually enabled above
        this.broadcastAfk = plugin.getConfig().getBoolean("afk.broadcast", true); // tells chat by default
        this.moveCancel = plugin.getConfig().getBoolean("afk.cancel-on-move", true); // cancels on move is true by default
        this.interactCancel = plugin.getConfig().getBoolean("afk.cancel-on-interact", true); // cancels on interact is true by default
        this.excludeFromSleep = plugin.getConfig().getBoolean("afk.exclude-from-sleep", true); // doesn't count players to sleep count by default
    }
    
    private void startAfkChecker() {
        checkTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                AFKPlayer afkPlayer = players.get(uuid);
                
                if (afkPlayer == null) continue;
                
                long idleTime = (now - afkPlayer.lastActivity) / 1000;
                
                // check for auto-AFK
                if (!afkPlayer.isAfk && idleTime >= autoAfkTime) {
                    if (!player.hasPermission("velocityessentials.afk.exempt")) { // check bypass permission node
                        setAFK(player, true, false);
                    }
                }
                
                // check for kick
                if (kickEnabled && afkPlayer.isAfk && idleTime >= (autoAfkTime + kickTime)) {
                    if (!player.hasPermission("velocityessentials.afk.kickexempt")) { // check bypass permission node
                        player.kickPlayer("You have been kicked for being AFK too long!");
                        if (plugin.debug) {
                            plugin.getLogger().info("Kicked " + player.getName() + " for being AFK");
                        }
                    }
                }
            }
        }, 20L, 20L); // run every second
    }

    private void startSleepCountUpdater() {
        // update sleep ignored status every 5 seconds
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                boolean isAfk = isAFK(player);
                
                // use Paper API to set sleep ignored status
                try {
                    player.setSleepingIgnored(isAfk);
                } catch (NoSuchMethodError e) {
                    // non paper warning message
                    if (plugin.debug) {
                        plugin.getLogger().warning("setSleepingIgnored not available - using Paper/Purpur is recommended for sleep count exclusion");
                    }
                }
            }
        }, 100L, 100L); // every 5 seconds (testing, less intensive on server then)
    }
    
    public void setAFK(Player player, boolean afk, boolean manual, String message) {
        UUID uuid = player.getUniqueId();
        AFKPlayer afkPlayer = players.get(uuid);
        
        if (afkPlayer == null) {
            afkPlayer = new AFKPlayer(uuid);
            players.put(uuid, afkPlayer);
        }
        
        if (afkPlayer.isAfk == afk) return;
        
        afkPlayer.isAfk = afk;
        afkPlayer.afkStart = afk ? System.currentTimeMillis() : 0;
        
        if (afk) {
            afkPlayers.add(uuid);
            if (message != null) {
                afkMessages.put(uuid, message);
            }
            if (excludeFromSleep) { // set player as sleep ignored
                try {
                    player.setSleepingIgnored(true);
                } catch (NoSuchMethodError ignored) {}
            }
        } else {
            afkPlayers.remove(uuid);
            afkMessages.remove(uuid);
            afkPlayer.lastActivity = System.currentTimeMillis();
            if (excludeFromSleep) { // set player as no longer sleep ignored
                try {
                    player.setSleepingIgnored(false);
                } catch (NoSuchMethodError ignored) {}
            }
        }
        
        // send to velocity for network control
        if (broadcastAfk) {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("afk_status_message");
            out.writeUTF(player.getUniqueId().toString());
            out.writeUTF(player.getName());
            out.writeBoolean(afk);
            out.writeBoolean(manual);
            out.writeUTF(message != null ? message : "");
            out.writeUTF(plugin.serverName);
            
            // send to any online player
            if (!Bukkit.getOnlinePlayers().isEmpty()) {
                Bukkit.getOnlinePlayers().iterator().next()
                    .sendPluginMessage(plugin, VelocityEssentialsBackend.CHANNEL, out.toByteArray());
                
                if (plugin.debug) {
                    plugin.getLogger().info("Sent AFK status to Velocity: " + player.getName() + " = " + afk + (message != null ? " (Message: " + message + ")" : ""));
                }
            }
        }
    }

    // for back compat
    public void setAFK(Player player, boolean afk, boolean manual) {
        setAFK(player, afk, manual, null);
    }
    
    public boolean isAFK(Player player) {
        AFKPlayer afkPlayer = players.get(player.getUniqueId());
        return afkPlayer != null && afkPlayer.isAfk;
    }
    
    public String getAFKMessage(Player player) {
        return afkMessages.get(player.getUniqueId());
    }

    public void toggleAFK(Player player) {
        toggleAFK(player, null);
    }

    public void toggleAFK(Player player, String message) {
        setAFK(player, !isAFK(player), true, message);
    }
    
     public void handleNetworkAFKMessage(String playerName, boolean isAfk, boolean manual, String message) {
        // display the AFK message locally when received from the proxy
        String displayMessage;
        if (isAfk) {
            if (manual) {
                if (message != null && !message.isEmpty()) {
                    displayMessage = "§7* §e" + playerName + " §7is now AFK: §f" + message;
                } else {
                    displayMessage = "§7* §e" + playerName + " §7is now AFK";
                }
            } else {
                displayMessage = "§7* §e" + playerName + " §7has gone AFK";
            }
        } else {
            displayMessage = "§7* §e" + playerName + " §7is no longer AFK";
        }
        
        Component component = Component.text(displayMessage);
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(component));
    }

    // for back compat 
    public void handleNetworkAFKMessage(String playerName, boolean isAfk, boolean manual) {
        handleNetworkAFKMessage(playerName, isAfk, manual, null);
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
        
        // cancel the afk status if theyre afk and perform an action that triggers updateActivity
        if (afkPlayer.isAfk) {
            setAFK(player, false, false, null);
        }
    }
    
    // ===== EVENT HANDLERS =====
    
   @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        AFKPlayer afkPlayer = new AFKPlayer(player.getUniqueId());
        afkPlayer.lastLocation = player.getLocation();
        players.put(player.getUniqueId(), afkPlayer);
        
        // initialize sleep ignored status
        if (excludeFromSleep) {
            try {
                player.setSleepingIgnored(false);
            } catch (NoSuchMethodError ignored) {}
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        players.remove(uuid);
        afkPlayers.remove(uuid);
        afkMessages.remove(uuid);
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!moveCancel) return;
        
        Player player = event.getPlayer();
        AFKPlayer afkPlayer = players.get(player.getUniqueId());
        if (afkPlayer == null) return;
        
        Location from = event.getFrom();
        Location to = event.getTo();
        
        // only count as activity if they actually moved (not just looked around)
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
        // don't reset AFK for /afk command itself
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
    
    public int getActivePlayers() {
        return (int) Bukkit.getOnlinePlayers().stream()
            .filter(p -> !isAFK(p))
            .count();
    }
    
    public void shutdown() {
        if (checkTask != null) {
            checkTask.cancel();
        }
        
        // reset all sleep ignored statuses on shutdown
        if (excludeFromSleep) {
            Bukkit.getOnlinePlayers().forEach(player -> {
                try {
                    player.setSleepingIgnored(false);
                } catch (NoSuchMethodError ignored) {}
            });
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