package com.velocityessentials.backend.restart;

import com.velocityessentials.backend.VelocityEssentialsBackend;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * backend handler for restart system
 * receives messages from velocity proxy and executes actions
 */
public class RestartHandler {
    private final VelocityEssentialsBackend plugin;
    private final Map<Integer, BukkitTask> scheduledWarnings = new HashMap<>();
    
    public RestartHandler(VelocityEssentialsBackend plugin) {
        this.plugin = plugin;
    }

    public void handleWarning(int secondsUntilRestart, String soundName) {
        if (plugin.debug) {
            plugin.getLogger().info("Restart warning: " + secondsUntilRestart + " seconds");
        }

        Component message = createWarningMessage(secondsUntilRestart);

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(message);

            if (soundName != null && !soundName.isEmpty()) {
                playSound(player, soundName);
            }

            if (secondsUntilRestart <= 60) {
                showRestartTitle(player, secondsUntilRestart);
            }
        }

        plugin.getLogger().warning("Server restarting in " + formatTime(secondsUntilRestart));
    }

    public void executeCommand(String command) {
        if (plugin.debug) {
            plugin.getLogger().info("Executing command: " + command);
        }
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to execute command: " + command);
                e.printStackTrace();
            }
        });
    }
    
    /**
     * initiate server shutdown
     */
    public void initiateShutdown() {
        plugin.getLogger().warning("Shutting down server as requested by proxy...");

        Component message = Component.text()
            .append(Component.text("[", NamedTextColor.DARK_GRAY))
            .append(Component.text("⚠", NamedTextColor.RED))
            .append(Component.text("] ", NamedTextColor.DARK_GRAY))
            .append(Component.text("Server is restarting NOW!", NamedTextColor.RED, TextDecoration.BOLD))
            .build();
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(message);

            Title title = Title.title(
                Component.text("RESTARTING", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("Please reconnect in a moment", NamedTextColor.YELLOW),
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ofSeconds(1))
            );
            player.showTitle(title);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Bukkit.shutdown();
        }, 20L); // 1 second delay
    }
    

    public void handleCancel(String reason) {
        if (plugin.debug) {
            plugin.getLogger().info("Restart cancelled: " + reason);
        }

        scheduledWarnings.values().forEach(BukkitTask::cancel);
        scheduledWarnings.clear();

        Component message = Component.text()
            .append(Component.text("[", NamedTextColor.DARK_GRAY))
            .append(Component.text("✓", NamedTextColor.GREEN))
            .append(Component.text("] ", NamedTextColor.DARK_GRAY))
            .append(Component.text("Scheduled restart cancelled", NamedTextColor.GREEN))
            .build();
        
        if (reason != null && !reason.isEmpty()) {
            message = message.append(Component.text(": " + reason, NamedTextColor.GRAY));
        }
        
        Bukkit.broadcast(message);
        plugin.getLogger().info("Restart cancelled: " + (reason != null ? reason : "No reason given"));
    }

    /**
     * create a formatted warning message
     */
    private Component createWarningMessage(int seconds) {
        String timeStr = formatTime(seconds);
        NamedTextColor color = getColorForTime(seconds);
        
        return Component.text()
            .append(Component.text("[", NamedTextColor.DARK_GRAY))
            .append(Component.text("⚠", NamedTextColor.YELLOW))
            .append(Component.text("] ", NamedTextColor.DARK_GRAY))
            .append(Component.text("Server restarting in ", color))
            .append(Component.text(timeStr, color, TextDecoration.BOLD))
            .build();
    }
    
    /**
     * show title to player for urgent warnings
     */
    private void showRestartTitle(Player player, int seconds) {
        String timeStr = formatTime(seconds);
        NamedTextColor color = seconds <= 10 ? NamedTextColor.RED : NamedTextColor.YELLOW;
        
        Title title = Title.title(
            Component.text("⚠ RESTARTING ⚠", color, TextDecoration.BOLD),
            Component.text(timeStr, color),
            Title.Times.times(
                Duration.ofMillis(500),
                Duration.ofSeconds(2),
                Duration.ofMillis(500)
            )
        );
        
        player.showTitle(title);
    }
    
    /**
     * play sound to player
     */
    private void playSound(Player player, String soundName) {
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException e) {
            if (plugin.debug) {
                plugin.getLogger().warning("Invalid sound: " + soundName);
            }
        }
    }
    
    /**
     * format seconds into readable time
     */
    private String formatTime(int seconds) {
        if (seconds < 60) {
            return seconds + " second" + (seconds != 1 ? "s" : "");
        } else if (seconds < 3600) {
            int minutes = seconds / 60;
            int remainingSeconds = seconds % 60;
            if (remainingSeconds > 0) {
                return minutes + " minute" + (minutes != 1 ? "s" : "") + 
                       " " + remainingSeconds + " second" + (remainingSeconds != 1 ? "s" : "");
            }
            return minutes + " minute" + (minutes != 1 ? "s" : "");
        } else {
            int hours = seconds / 3600;
            int minutes = (seconds % 3600) / 60;
            if (minutes > 0) {
                return hours + " hour" + (hours != 1 ? "s" : "") + 
                       " " + minutes + " minute" + (minutes != 1 ? "s" : "");
            }
            return hours + " hour" + (hours != 1 ? "s" : "");
        }
    }

    private NamedTextColor getColorForTime(int seconds) {
        if (seconds <= 30) return NamedTextColor.RED;
        if (seconds <= 60) return NamedTextColor.GOLD;
        if (seconds <= 180) return NamedTextColor.YELLOW;
        return NamedTextColor.GREEN;
    }

    public void shutdown() {
        scheduledWarnings.values().forEach(BukkitTask::cancel);
        scheduledWarnings.clear();
    }
}