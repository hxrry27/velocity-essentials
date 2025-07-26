package com.velocityessentials.commands;

import com.velocityessentials.stats.StatsSystem;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.time.LocalDateTime;
import java.util.List;


public class EventCommand implements SimpleCommand {
    
    private final StatsSystem statsSystem;
    
    public EventCommand(StatsSystem statsSystem) {
        this.statsSystem = statsSystem;
    }
    
    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        
        if (args.length == 0) {
            sendUsage(source);
            return;
        }
        
        switch (args[0].toLowerCase()) {
            case "create":
                if (!hasPermission(source, "velocityessentials.events.create")) {
                    sendNoPermission(source);
                    return;
                }
                if (args.length < 4) {
                    source.sendMessage(Component.text("Usage: /event create <name> <stat> <duration>")
                        .color(NamedTextColor.RED));
                    source.sendMessage(Component.text("Example: /event create \"Mining Marathon\" minecraft:mined:minecraft:stone 7d")
                        .color(NamedTextColor.GRAY));
                    return;
                }
                createEvent(source, args[1], args[2], args[3]);
                break;
                
            case "list":
                if (!hasPermission(source, "velocityessentials.events.view")) {
                    sendNoPermission(source);
                    return;
                }
                listEvents(source);
                break;
                
            case "leaderboard":
            case "lb":
                if (args.length < 2) {
                    source.sendMessage(Component.text("Usage: /event leaderboard <event>")
                        .color(NamedTextColor.RED));
                    return;
                }
                showLeaderboard(source, args[1]);
                break;
                
            case "start":
                if (!hasPermission(source, "velocityessentials.events.manage")) {
                    sendNoPermission(source);
                    return;
                }
                if (args.length < 2) {
                    source.sendMessage(Component.text("Usage: /event start <event>")
                        .color(NamedTextColor.RED));
                    return;
                }
                startEvent(source, args[1]);
                break;
                
            case "stop":
            case "end":
                if (!hasPermission(source, "velocityessentials.events.manage")) {
                    sendNoPermission(source);
                    return;
                }
                if (args.length < 2) {
                    source.sendMessage(Component.text("Usage: /event stop <event>")
                        .color(NamedTextColor.RED));
                    return;
                }
                stopEvent(source, args[1]);
                break;
                
            case "delete":
                if (!hasPermission(source, "velocityessentials.events.admin")) {
                    sendNoPermission(source);
                    return;
                }
                if (args.length < 2) {
                    source.sendMessage(Component.text("Usage: /event delete <event>")
                        .color(NamedTextColor.RED));
                    return;
                }
                deleteEvent(source, args[1]);
                break;
                
            case "stats":
                if (args.length < 2) {
                    source.sendMessage(Component.text("Usage: /event stats <stat-key>")
                        .color(NamedTextColor.RED));
                    source.sendMessage(Component.text("Example: /event stats minecraft:mined:minecraft:diamond_ore")
                        .color(NamedTextColor.GRAY));
                    return;
                }
                showStatInfo(source, args[1]);
                break;
                
            default:
                sendUsage(source);
        }
    }
    
    private void createEvent(CommandSource source, String name, String stat, String duration) {
        // Parse duration (1d, 1w, 1month, etc)
        LocalDateTime endTime = parseDuration(duration);
        
        if (endTime == null) {
            source.sendMessage(Component.text("Invalid duration! Use: 1d, 1w, 1month")
                .color(NamedTextColor.RED));
            return;
        }
        
        String creatorName = source instanceof Player ? ((Player) source).getUsername() : "Console";
        
        statsSystem.createEvent(name, stat, LocalDateTime.now(), endTime, creatorName)
            .thenAccept(success -> {
                if (success) {
                    source.sendMessage(Component.text()
                        .append(Component.text("✓ Event '", NamedTextColor.GREEN))
                        .append(Component.text(name, NamedTextColor.YELLOW))
                        .append(Component.text("' created!", NamedTextColor.GREEN))
                        .build());
                    source.sendMessage(Component.text()
                        .append(Component.text("Tracking: ", NamedTextColor.GRAY))
                        .append(Component.text(stat, NamedTextColor.AQUA))
                        .build());
                    source.sendMessage(Component.text()
                        .append(Component.text("Duration: ", NamedTextColor.GRAY))
                        .append(Component.text(duration, NamedTextColor.YELLOW))
                        .build());
                } else {
                    source.sendMessage(Component.text("Failed to create event! Check if name already exists.")
                        .color(NamedTextColor.RED));
                }
            })
            .exceptionally(throwable -> {
                source.sendMessage(Component.text("Error creating event: " + throwable.getMessage())
                    .color(NamedTextColor.RED));
                return null;
            });
    }
    
    private void listEvents(CommandSource source) {
        source.sendMessage(Component.text()
            .append(Component.text("=== ", NamedTextColor.DARK_GRAY))
            .append(Component.text("ValeSMP Events", NamedTextColor.GOLD, TextDecoration.BOLD))
            .append(Component.text(" ===", NamedTextColor.DARK_GRAY))
            .build());
        
        // TODO: Implement actual event listing from database
        source.sendMessage(Component.text("Event listing coming soon!", NamedTextColor.YELLOW));
        source.sendMessage(Component.text("Use /event leaderboard <event> to view specific events", NamedTextColor.GRAY));
    }
    
    private void showLeaderboard(CommandSource source, String eventName) {
        source.sendMessage(Component.text()
            .append(Component.text("=== Leaderboard for ", NamedTextColor.GOLD))
            .append(Component.text(eventName, NamedTextColor.YELLOW, TextDecoration.BOLD))
            .append(Component.text(" ===", NamedTextColor.GOLD))
            .build());
        
        // TODO: Implement leaderboard from database
        source.sendMessage(Component.text("Loading leaderboard...", NamedTextColor.YELLOW));
    }
    
    private void startEvent(CommandSource source, String eventName) {
        source.sendMessage(Component.text("Starting event: " + eventName, NamedTextColor.GREEN));
        // TODO: Implement event starting
    }
    
    private void stopEvent(CommandSource source, String eventName) {
        source.sendMessage(Component.text("Stopping event: " + eventName, NamedTextColor.YELLOW));
        // TODO: Implement event stopping
    }
    
    private void deleteEvent(CommandSource source, String eventName) {
        source.sendMessage(Component.text("Deleting event: " + eventName, NamedTextColor.RED));
        // TODO: Implement event deletion with confirmation
    }
    
    private void showStatInfo(CommandSource source, String statKey) {
        source.sendMessage(Component.text()
            .append(Component.text("Stat Key: ", NamedTextColor.GRAY))
            .append(Component.text(statKey, NamedTextColor.AQUA))
            .build());
        
        // Show current top players for this stat
        source.sendMessage(Component.text("Fetching current leaders...", NamedTextColor.YELLOW));
        // TODO: Implement current stat display
    }
    
    private void sendUsage(CommandSource source) {
        source.sendMessage(Component.text()
            .append(Component.text("=== ", NamedTextColor.DARK_GRAY))
            .append(Component.text("ValeSMP Events", NamedTextColor.GOLD, TextDecoration.BOLD))
            .append(Component.text(" ===", NamedTextColor.DARK_GRAY))
            .build());
        
        if (hasPermission(source, "velocityessentials.events.view")) {
            source.sendMessage(Component.text("/event list", NamedTextColor.YELLOW)
                .append(Component.text(" - View all events", NamedTextColor.GRAY)));
            source.sendMessage(Component.text("/event leaderboard <event>", NamedTextColor.YELLOW)
                .append(Component.text(" - View event leaderboard", NamedTextColor.GRAY)));
        }
        
        if (hasPermission(source, "velocityessentials.events.create")) {
            source.sendMessage(Component.text("/event create <name> <stat> <duration>", NamedTextColor.YELLOW)
                .append(Component.text(" - Create new event", NamedTextColor.GRAY)));
            source.sendMessage(Component.text("/event stats <stat-key>", NamedTextColor.YELLOW)
                .append(Component.text(" - View stat information", NamedTextColor.GRAY)));
        }
        
        if (hasPermission(source, "velocityessentials.events.manage")) {
            source.sendMessage(Component.text("/event start <event>", NamedTextColor.YELLOW)
                .append(Component.text(" - Start an event", NamedTextColor.GRAY)));
            source.sendMessage(Component.text("/event stop <event>", NamedTextColor.YELLOW)
                .append(Component.text(" - Stop an event", NamedTextColor.GRAY)));
        }
        
        if (hasPermission(source, "velocityessentials.events.admin")) {
            source.sendMessage(Component.text("/event delete <event>", NamedTextColor.YELLOW)
                .append(Component.text(" - Delete an event", NamedTextColor.GRAY)));
        }
        
        source.sendMessage(Component.text());
        source.sendMessage(Component.text("Common stat examples:", NamedTextColor.AQUA));
        source.sendMessage(Component.text("• minecraft:mined:minecraft:diamond_ore", NamedTextColor.GRAY)
            .append(Component.text(" (diamonds mined)", NamedTextColor.DARK_GRAY)));
        source.sendMessage(Component.text("• minecraft:custom:minecraft:play_time", NamedTextColor.GRAY)
            .append(Component.text(" (time played)", NamedTextColor.DARK_GRAY)));
        source.sendMessage(Component.text("• minecraft:killed:minecraft:zombie", NamedTextColor.GRAY)
            .append(Component.text(" (zombies killed)", NamedTextColor.DARK_GRAY)));
        source.sendMessage(Component.text("• minecraft:custom:minecraft:walk_one_cm", NamedTextColor.GRAY)
            .append(Component.text(" (distance walked)", NamedTextColor.DARK_GRAY)));
    }
    
    private void sendNoPermission(CommandSource source) {
        source.sendMessage(Component.text("You don't have permission to use this command!")
            .color(NamedTextColor.RED));
    }
    
    private boolean hasPermission(CommandSource source, String permission) {
        return source.hasPermission(permission) || source.hasPermission("velocityessentials.events.admin");
    }
    
    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        CommandSource source = invocation.source();
        
        if (args.length <= 1) {
            List<String> suggestions = List.of();
            
            if (hasPermission(source, "velocityessentials.events.view")) {
                suggestions = List.of("list", "leaderboard");
            }
            if (hasPermission(source, "velocityessentials.events.create")) {
                suggestions = List.of("create", "stats");
            }
            if (hasPermission(source, "velocityessentials.events.manage")) {
                suggestions = List.of("start", "stop");
            }
            if (hasPermission(source, "velocityessentials.events.admin")) {
                suggestions = List.of("delete");
            }
            
            return suggestions.stream()
                .filter(cmd -> args.length == 0 || cmd.startsWith(args[0].toLowerCase()))
                .toList();
        }
        
        if (args.length == 2 && args[0].equalsIgnoreCase("stats")) {
            // Suggest common stat keys
            return List.of(
                "minecraft:mined:minecraft:diamond_ore",
                "minecraft:custom:minecraft:play_time",
                "minecraft:killed:minecraft:zombie",
                "minecraft:custom:minecraft:walk_one_cm"
            ).stream()
                .filter(stat -> stat.toLowerCase().contains(args[1].toLowerCase()))
                .toList();
        }
        
        return List.of();
    }
    
    @Override
    public boolean hasPermission(Invocation invocation) {
        return hasPermission(invocation.source(), "velocityessentials.events.view");
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
            } else if (duration.endsWith("month") || duration.endsWith("m")) {
                String numStr = duration.endsWith("month") ? 
                    duration.substring(0, duration.length() - 5) :
                    duration.substring(0, duration.length() - 1);
                int months = Integer.parseInt(numStr);
                return now.plusMonths(months);
            } else if (duration.endsWith("h")) {
                int hours = Integer.parseInt(duration.substring(0, duration.length() - 1));
                return now.plusHours(hours);
            }
        } catch (NumberFormatException ignored) {}
        
        return null;
    }
}