package com.velocityessentials.commands;

import com.velocityessentials.VelocityEssentials;
import com.velocityessentials.utils.MessageUtil;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;

public class MainCommand implements SimpleCommand {
    private final VelocityEssentials plugin;
    
    public MainCommand(VelocityEssentials plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        
        if (args.length == 0) {
            showHelp(source);
            return;
        }
        
        switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(source);
            case "info" -> handleInfo(source, args);
            case "test" -> handleTest(source, args);
            case "debug" -> handleDebug(source);
            default -> showHelp(source);
        }
    }
    
    private void handleReload(CommandSource source) {
        if (!source.hasPermission("velocityessentials.admin.reload")) {
            source.sendMessage(MessageUtil.parse(plugin.getConfig().getMessage("no-permission")));
            return;
        }
        
        plugin.reload();
        source.sendMessage(Component.text("VelocityEssentials configuration reloaded!", NamedTextColor.GREEN));
    }
    
    private void handleInfo(CommandSource source, String[] args) {
        if (!source.hasPermission("velocityessentials.admin.info")) {
            source.sendMessage(MessageUtil.parse(plugin.getConfig().getMessage("no-permission")));
            return;
        }
        
        if (args.length < 2) {
            source.sendMessage(Component.text("Usage: /ve info <player>", NamedTextColor.RED));
            return;
        }
        
        String targetName = args[1];
        
        plugin.getPlayerData().getPlayerInfo(targetName).thenAccept(info -> {
            if (info == null) {
                source.sendMessage(Component.text("Player not found: " + targetName, NamedTextColor.RED));
                return;
            }
            
            // Calculate time ago
            long lastSeenAgo = System.currentTimeMillis() - info.lastSeen.getTime();
            String timeAgo = formatTimeAgo(lastSeenAgo);
            
            source.sendMessage(Component.text("=== Player Information ===", NamedTextColor.GOLD));
            source.sendMessage(Component.text("Username: ", NamedTextColor.GRAY)
                .append(Component.text(info.username, NamedTextColor.WHITE)));
            source.sendMessage(Component.text("Last Server: ", NamedTextColor.GRAY)
                .append(Component.text(info.lastServer, NamedTextColor.GREEN)));
            source.sendMessage(Component.text("Last Seen: ", NamedTextColor.GRAY)
                .append(Component.text(timeAgo + " ago", NamedTextColor.YELLOW)));
            source.sendMessage(Component.text("First Joined: ", NamedTextColor.GRAY)
                .append(Component.text(info.firstJoined.toString(), NamedTextColor.AQUA)));
        });
    }
    
    private void handleTest(CommandSource source, String[] args) {
        if (!source.hasPermission("velocityessentials.admin.test")) {
            source.sendMessage(MessageUtil.parse(plugin.getConfig().getMessage("no-permission")));
            return;
        }
        
        if (args.length < 2) {
            source.sendMessage(Component.text("Usage: /ve test <server>", NamedTextColor.RED));
            return;
        }
        
        String serverName = args[1];
        plugin.getServer().getServer(serverName).ifPresentOrElse(server -> {
            plugin.getMessageHandler().sendTestMessage(server);
            source.sendMessage(Component.text("Test message sent to " + serverName, NamedTextColor.GREEN));
        }, () -> {
            source.sendMessage(Component.text("Server not found: " + serverName, NamedTextColor.RED));
        });
    }
    
    private void handleDebug(CommandSource source) {
        if (!source.hasPermission("velocityessentials.admin.debug")) {
            source.sendMessage(MessageUtil.parse(plugin.getConfig().getMessage("no-permission")));
            return;
        }
        
        source.sendMessage(Component.text("=== VelocityEssentials Debug Info ===", NamedTextColor.GOLD));
        source.sendMessage(Component.text("Database Connected: ", NamedTextColor.GRAY)
            .append(Component.text(plugin.getDatabase().isConnected() ? "Yes" : "No", 
                plugin.getDatabase().isConnected() ? NamedTextColor.GREEN : NamedTextColor.RED)));
        source.sendMessage(Component.text("Discord Enabled: ", NamedTextColor.GRAY)
            .append(Component.text(plugin.getConfig().isDiscordEnabled() ? "Yes" : "No",
                plugin.getConfig().isDiscordEnabled() ? NamedTextColor.GREEN : NamedTextColor.RED)));
        source.sendMessage(Component.text("Custom Messages: ", NamedTextColor.GRAY)
            .append(Component.text(plugin.getConfig().isCustomMessagesEnabled() ? "Yes" : "No",
                plugin.getConfig().isCustomMessagesEnabled() ? NamedTextColor.GREEN : NamedTextColor.RED)));
        source.sendMessage(Component.text("Total Servers: ", NamedTextColor.GRAY)
            .append(Component.text(plugin.getServer().getAllServers().size(), NamedTextColor.AQUA)));
        source.sendMessage(Component.text("Online Players: ", NamedTextColor.GRAY)
            .append(Component.text(plugin.getServer().getPlayerCount(), NamedTextColor.AQUA)));
    }
    
    private void showHelp(CommandSource source) {
        source.sendMessage(Component.text("=== VelocityEssentials Commands ===", NamedTextColor.GOLD));
        if (source.hasPermission("velocityessentials.admin.reload")) {
            source.sendMessage(Component.text("/ve reload", NamedTextColor.YELLOW)
                .append(Component.text(" - Reload configuration", NamedTextColor.GRAY)));
        }
        if (source.hasPermission("velocityessentials.admin.info")) {
            source.sendMessage(Component.text("/ve info <player>", NamedTextColor.YELLOW)
                .append(Component.text(" - View player information", NamedTextColor.GRAY)));
        }
        if (source.hasPermission("velocityessentials.admin.test")) {
            source.sendMessage(Component.text("/ve test <server>", NamedTextColor.YELLOW)
                .append(Component.text(" - Test backend connection", NamedTextColor.GRAY)));
        }
        if (source.hasPermission("velocityessentials.admin.debug")) {
            source.sendMessage(Component.text("/ve debug", NamedTextColor.YELLOW)
                .append(Component.text(" - Show debug information", NamedTextColor.GRAY)));
        }
    }
    
    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        
        if (args.length == 0) {
            return List.of("reload", "info", "test", "debug");
        }
        
        if (args.length == 1) {
            return List.of("reload", "info", "test", "debug").stream()
                .filter(cmd -> cmd.startsWith(args[0].toLowerCase()))
                .toList();
        }
        
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("info")) {
                // Suggest online players
                return plugin.getServer().getAllPlayers().stream()
                    .map(Player::getUsername)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
            } else if (args[0].equalsIgnoreCase("test")) {
                // Suggest servers
                return plugin.getServer().getAllServers().stream()
                    .map(server -> server.getServerInfo().getName())
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
            }
        }
        
        return List.of();
    }
    
    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("velocityessentials.admin");
    }
    
    private String formatTimeAgo(long millis) {
        long seconds = millis / 1000;
        if (seconds < 60) return seconds + " seconds";
        
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + " minutes";
        
        long hours = minutes / 60;
        if (hours < 24) return hours + " hours";
        
        long days = hours / 24;
        return days + " days";
    }
}