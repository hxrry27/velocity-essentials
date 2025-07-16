package com.velocityessentials.commands;

import com.velocityessentials.stats.StatsSystem;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class EventCommand implements SimpleCommand {
    
    private final StatsSystem statsSystem;
    
    public EventCommand(StatsSystem statsSystem) {
        this.statsSystem = statsSystem;
    }
    
    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        
        if (!(source instanceof Player)) {
            source.sendMessage(Component.text("This command can only be used by players!")
                .color(NamedTextColor.RED));
            return;
        }
        
        Player player = (Player) source;
        
        if (args.length == 0) {
            sendUsage(player);
            return;
        }
        
        switch (args[0].toLowerCase()) {
            case "create":
                if (args.length < 4) {
                    player.sendMessage(Component.text("Usage: /event create <name> <stat> <duration>")
                        .color(NamedTextColor.RED));
                    return;
                }
                createEvent(player, args[1], args[2], args[3]);
                break;
                
            case "list":
                listEvents(player);
                break;
                
            case "leaderboard":
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /event leaderboard <event>")
                        .color(NamedTextColor.RED));
                    return;
                }
                showLeaderboard(player, args[1]);
                break;
                
            default:
                sendUsage(player);
        }
    }
    
    private void createEvent(Player player, String name, String stat, String duration) {
        // Parse duration (1d, 1w, 1month, etc)
        LocalDateTime endTime = parseDuration(duration);
        
        if (endTime == null) {
            player.sendMessage(Component.text("Invalid duration! Use: 1d, 1w, 1month")
                .color(NamedTextColor.RED));
            return;
        }
        
        statsSystem.createEvent(name, stat, LocalDateTime.now(), endTime, player.getUsername())
            .thenAccept(success -> {
                if (success) {
                    player.sendMessage(Component.text("Event '" + name + "' created!")
                        .color(NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.text("Failed to create event!")
                        .color(NamedTextColor.RED));
                }
            });
    }
    
    private void listEvents(Player player) {
        player.sendMessage(Component.text("Active events:")
            .color(NamedTextColor.GOLD));
        // TODO: Implement listing from database
    }
    
    private void showLeaderboard(Player player, String eventName) {
        player.sendMessage(Component.text("Leaderboard for " + eventName + ":")
            .color(NamedTextColor.GOLD));
        // TODO: Implement leaderboard from database
    }
    
    private void sendUsage(Player player) {
        player.sendMessage(Component.text("=== Stats Events ===").color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("/event create <name> <stat> <duration>")
            .color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/event list").color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/event leaderboard <event>").color(NamedTextColor.YELLOW));
    }
    
    private LocalDateTime parseDuration(String duration) {
        LocalDateTime now = LocalDateTime.now();
        
        try {
            if (duration.endsWith("d")) {
                int days = Integer.parseInt(duration.substring(0, duration.length() - 1));
                return now.plusDays(days);
            } else if (duration.endsWith("w")) {
                int weeks = Integer.parseInt(duration.substring(0, duration.length() - 1));
                return now.plusWeeks(weeks);
            } else if (duration.endsWith("month")) {
                int months = Integer.parseInt(duration.substring(0, duration.length() - 5));
                return now.plusMonths(months);
            }
        } catch (NumberFormatException ignored) {}
        
        return null;
    }
}