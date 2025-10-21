package com.velocityessentials.commands;

import com.velocityessentials.VelocityEssentials;
import com.velocityessentials.modules.restart.RestartScheduler;
import com.velocityessentials.modules.restart.RestartTask;
import com.velocityessentials.modules.restart.RestartUtil;
import com.velocityessentials.modules.restart.data.RestartReason;
import com.velocityessentials.modules.restart.data.RestartSchedule;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * player and admin commands for the restart system
 * /restart check - View next restart time
 * /restart delay <minutes> [reason] - Delay restart
 * /restart cancel [reason] - Cancel restart
 * /restart now <server> - Force restart
 * /restart reload - Reload config
 * /restart list - List all schedules
 */
public class RestartCommand implements SimpleCommand {
    private final VelocityEssentials plugin;
    
    public RestartCommand(VelocityEssentials plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        
        RestartScheduler scheduler = plugin.getRestartScheduler();
        
        if (scheduler == null || !scheduler.isEnabled()) {
            source.sendMessage(Component.text("Auto-restart system is not enabled!", NamedTextColor.RED));
            return;
        }
        
        if (args.length == 0) {
            showHelp(source);
            return;
        }
        
        switch (args[0].toLowerCase()) {
            case "check" -> handleCheck(source);
            case "delay" -> handleDelay(source, args);
            case "cancel" -> handleCancel(source, args);
            case "now" -> handleNow(source, args);
            case "reload" -> handleReload(source);
            case "list" -> handleList(source);
            case "stats" -> handleStats(source);
            default -> showHelp(source);
        }
    }
    
    /**
     * /restart check - Show next restart time for player's server
     */
    private void handleCheck(CommandSource source) {
        if (source instanceof Player player) {
            player.getCurrentServer().ifPresent(conn -> {
                String serverName = conn.getServerInfo().getName();
                String info = plugin.getRestartScheduler().getNextRestartInfo(serverName);
                
                if (info != null) {
                    player.sendMessage(Component.text()
                        .append(Component.text("[", NamedTextColor.DARK_GRAY))
                        .append(Component.text("⏰", NamedTextColor.YELLOW))
                        .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(serverName, NamedTextColor.AQUA))
                        .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(info, NamedTextColor.YELLOW))
                        .build());
                } else {
                    player.sendMessage(Component.text()
                        .append(Component.text("[", NamedTextColor.DARK_GRAY))
                        .append(Component.text("ℹ", NamedTextColor.BLUE))
                        .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                        .append(Component.text("No restart scheduled for ", NamedTextColor.GRAY))
                        .append(Component.text(serverName, NamedTextColor.AQUA))
                        .build());
                }
            });
        } else {
            Map<String, RestartTask> tasks = plugin.getRestartScheduler().getActiveTasks();
            
            if (tasks.isEmpty()) {
                source.sendMessage(Component.text("No active restarts scheduled", NamedTextColor.YELLOW));
            } else {
                source.sendMessage(Component.text("=== Active Restarts ===", NamedTextColor.GOLD));
                tasks.forEach((name, task) -> {
                    String servers = String.join(", ", task.getSchedule().getServers());
                    long seconds = task.getSecondsUntilRestart();
                    source.sendMessage(Component.text()
                        .append(Component.text("  • ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(name, NamedTextColor.AQUA))
                        .append(Component.text(" (", NamedTextColor.GRAY))
                        .append(Component.text(servers, NamedTextColor.WHITE))
                        .append(Component.text("): ", NamedTextColor.GRAY))
                        .append(Component.text(RestartUtil.formatDuration(seconds), NamedTextColor.YELLOW))
                        .build());
                });
            }
        }
    }
    
    /**
     * /restart delay <minutes> [reason] - Delay restart
     */
    private void handleDelay(CommandSource source, String[] args) {
        if (!source.hasPermission("velocityessentials.restart.delay")) {
            source.sendMessage(Component.text("You don't have permission to delay restarts!", NamedTextColor.RED));
            return;
        }
        
        if (args.length < 2) {
            source.sendMessage(Component.text("Usage: /restart delay <minutes> [reason]", NamedTextColor.RED));
            return;
        }
        
        String scheduleName = null;
        int minutesArgIndex = 1;
        
        if (source instanceof Player player) {
            player.getCurrentServer().ifPresent(conn -> {
                
                String serverName = conn.getServerInfo().getName();
                plugin.getRestartScheduler().getActiveTasks().forEach((name, task) -> {
                    if (task.getSchedule().appliesTo(serverName)) {
                    }
                });
            });
        }
        
        int minutes;
        try {
            minutes = Integer.parseInt(args[minutesArgIndex]);
        } catch (NumberFormatException e) {
            source.sendMessage(Component.text("Invalid number of minutes!", NamedTextColor.RED));
            return;
        }
        
        if (minutes <= 0 || minutes > 1440) { 
            source.sendMessage(Component.text("Minutes must be between 1 and 1440 (24 hours)!", NamedTextColor.RED));
            return;
        }
        
        String reason = args.length > minutesArgIndex + 1 ? 
            String.join(" ", Arrays.copyOfRange(args, minutesArgIndex + 1, args.length)) : 
            "No reason given";
        
        String requester = source instanceof Player p ? p.getUsername() : "Console";
        
        if (source instanceof Player player) {
            player.getCurrentServer().ifPresent(conn -> {
                String serverName = conn.getServerInfo().getName();
                
                for (Map.Entry<String, RestartTask> entry : plugin.getRestartScheduler().getActiveTasks().entrySet()) {
                    if (entry.getValue().getSchedule().appliesTo(serverName)) {
                        boolean delayed = plugin.getRestartScheduler().delayRestart(
                            entry.getKey(),
                            minutes,
                            new RestartReason(reason, requester, RestartReason.Type.DELAY)
                        );
                        
                        if (delayed) {
                            source.sendMessage(Component.text()
                                .append(Component.text("[", NamedTextColor.DARK_GRAY))
                                .append(Component.text("✓", NamedTextColor.GREEN))
                                .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                                .append(Component.text("Restart delayed by ", NamedTextColor.GREEN))
                                .append(Component.text(minutes + " minutes", NamedTextColor.YELLOW, TextDecoration.BOLD))
                                .build());
                        } else {
                            source.sendMessage(Component.text("Failed to delay restart!", NamedTextColor.RED));
                        }
                        return;
                    }
                }
                
                source.sendMessage(Component.text("No active restart found for this server!", NamedTextColor.RED));
            });
        }
    }
    
    /**
     * /restart cancel [reason] - Cancel restart
     */
    private void handleCancel(CommandSource source, String[] args) {
        if (!source.hasPermission("velocityessentials.restart.cancel")) {
            source.sendMessage(Component.text("You don't have permission to cancel restarts!", NamedTextColor.RED));
            return;
        }
        
        String reason = args.length > 1 ? 
            String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : 
            "No reason given";
        
        String requester = source instanceof Player p ? p.getUsername() : "Console";
        
        if (source instanceof Player player) {
            player.getCurrentServer().ifPresent(conn -> {
                String serverName = conn.getServerInfo().getName();
                
                for (Map.Entry<String, RestartTask> entry : plugin.getRestartScheduler().getActiveTasks().entrySet()) {
                    if (entry.getValue().getSchedule().appliesTo(serverName)) {
                        boolean cancelled = plugin.getRestartScheduler().cancelRestart(
                            entry.getKey(),
                            new RestartReason(reason, requester, RestartReason.Type.CANCEL)
                        );
                        
                        if (cancelled) {
                            source.sendMessage(Component.text()
                                .append(Component.text("[", NamedTextColor.DARK_GRAY))
                                .append(Component.text("✓", NamedTextColor.GREEN))
                                .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                                .append(Component.text("Restart cancelled", NamedTextColor.GREEN))
                                .build());
                        } else {
                            source.sendMessage(Component.text("Failed to cancel restart!", NamedTextColor.RED));
                        }
                        return;
                    }
                }
                
                source.sendMessage(Component.text("No active restart found for this server!", NamedTextColor.RED));
            });
        } else {
            source.sendMessage(Component.text("Console must specify schedule name", NamedTextColor.RED));
        }
    }
    
    /**
     * /restart now <server> - Force immediate restart
     */
    private void handleNow(CommandSource source, String[] args) {
        if (!source.hasPermission("velocityessentials.restart.now")) {
            source.sendMessage(Component.text("You don't have permission to force restarts!", NamedTextColor.RED));
            return;
        }
        
        if (args.length < 2) {
            source.sendMessage(Component.text("Usage: /restart now <server>", NamedTextColor.RED));
            return;
        }
        
        String serverName = args[1];
        RegisteredServer server = plugin.getServer().getServer(serverName).orElse(null);
        
        if (server == null) {
            source.sendMessage(Component.text("Server not found: " + serverName, NamedTextColor.RED));
            return;
        }
        
        String requester = source instanceof Player p ? p.getUsername() : "Console";
        
        boolean success = plugin.getRestartScheduler().restartNow(List.of(serverName), requester);
        
        if (success) {
            source.sendMessage(Component.text()
                .append(Component.text("[", NamedTextColor.DARK_GRAY))
                .append(Component.text("⚠", NamedTextColor.RED))
                .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Forcing restart of ", NamedTextColor.YELLOW))
                .append(Component.text(serverName, NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.text(" in 10 seconds!", NamedTextColor.RED))
                .build());
        } else {
            source.sendMessage(Component.text("Failed to initiate restart!", NamedTextColor.RED));
        }
    }
    
    /**
     * /restart reload - Reload configuration
     */
    private void handleReload(CommandSource source) {
        if (!source.hasPermission("velocityessentials.restart.reload")) {
            source.sendMessage(Component.text("You don't have permission to reload!", NamedTextColor.RED));
            return;
        }
        
        plugin.getRestartScheduler().reload();
        source.sendMessage(Component.text("Restart scheduler reloaded!", NamedTextColor.GREEN));
    }
    
    /**
     * /restart list - List all schedules
     */
    private void handleList(CommandSource source) {
        Map<String, RestartSchedule> schedules = plugin.getRestartScheduler().getSchedules();
        
        source.sendMessage(Component.text("=== Restart Schedules ===", NamedTextColor.GOLD));
        
        schedules.forEach((name, schedule) -> {
            String servers = String.join(", ", schedule.getServers());
            String timeStr = schedule.getScheduleTime().toString();
            NamedTextColor color = schedule.isEnabled() ? NamedTextColor.GREEN : NamedTextColor.GRAY;
            
            source.sendMessage(Component.text()
                .append(Component.text("  • ", NamedTextColor.DARK_GRAY))
                .append(Component.text(name, color, TextDecoration.BOLD))
                .append(Component.text(" - ", NamedTextColor.GRAY))
                .append(Component.text(servers, NamedTextColor.AQUA))
                .append(Component.text(" @ ", NamedTextColor.GRAY))
                .append(Component.text(timeStr, NamedTextColor.YELLOW))
                .build());
        });
    }
    
    /**
     * /restart stats - Show statistics
     */
    private void handleStats(CommandSource source) {
        if (!source.hasPermission("velocityessentials.restart.admin")) {
            source.sendMessage(Component.text("You don't have permission!", NamedTextColor.RED));
            return;
        }
        
        String stats = plugin.getRestartScheduler().getStats();
        source.sendMessage(Component.text(stats, NamedTextColor.GRAY));
    }
    
    private void showHelp(CommandSource source) {
        source.sendMessage(Component.text("=== Restart Commands ===", NamedTextColor.GOLD));
        
        source.sendMessage(Component.text("/restart check", NamedTextColor.YELLOW)
            .append(Component.text(" - View next restart time", NamedTextColor.GRAY)));
        
        if (source.hasPermission("velocityessentials.restart.delay")) {
            source.sendMessage(Component.text("/restart delay <minutes> [reason]", NamedTextColor.YELLOW)
                .append(Component.text(" - Delay restart", NamedTextColor.GRAY)));
        }
        
        if (source.hasPermission("velocityessentials.restart.cancel")) {
            source.sendMessage(Component.text("/restart cancel [reason]", NamedTextColor.YELLOW)
                .append(Component.text(" - Cancel restart", NamedTextColor.GRAY)));
        }
        
        if (source.hasPermission("velocityessentials.restart.now")) {
            source.sendMessage(Component.text("/restart now <server>", NamedTextColor.YELLOW)
                .append(Component.text(" - Force restart", NamedTextColor.GRAY)));
        }
        
        if (source.hasPermission("velocityessentials.restart.reload")) {
            source.sendMessage(Component.text("/restart reload", NamedTextColor.YELLOW)
                .append(Component.text(" - Reload config", NamedTextColor.GRAY)));
            source.sendMessage(Component.text("/restart list", NamedTextColor.YELLOW)
                .append(Component.text(" - List schedules", NamedTextColor.GRAY)));
        }
    }
    
    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        
        if (args.length == 0 || args.length == 1) {
            List<String> suggestions = new ArrayList<>(List.of("check"));
            
            if (invocation.source().hasPermission("velocityessentials.restart.delay")) {
                suggestions.add("delay");
            }
            if (invocation.source().hasPermission("velocityessentials.restart.cancel")) {
                suggestions.add("cancel");
            }
            if (invocation.source().hasPermission("velocityessentials.restart.now")) {
                suggestions.add("now");
            }
            if (invocation.source().hasPermission("velocityessentials.restart.reload")) {
                suggestions.addAll(List.of("reload", "list", "stats"));
            }
            
            return suggestions.stream()
                .filter(s -> args.length == 0 || s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        if (args.length == 2 && args[0].equalsIgnoreCase("now")) {
            return plugin.getServer().getAllServers().stream()
                .map(s -> s.getServerInfo().getName())
                .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        return List.of();
    }
    
    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("velocityessentials.restart.check") ||
               invocation.source().hasPermission("velocityessentials.restart.admin");
    }
}