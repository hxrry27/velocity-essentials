package com.velocityessentials.commands;

import com.velocityessentials.database.StatsDatabase;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import javax.inject.Inject;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class EventCommand implements SimpleCommand {
    
    private final StatsDatabase db;
    
    @Inject
    public EventCommand(StatsDatabase db) {
        this.db = db;
    }
    
    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        
        if (!(source instanceof Player)) {
            source.sendMessage(Component.text("This command can only be used by players!"));
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
                    player.sendMessage(Component.text("Usage: /ve event create <name> <stat> <duration>")
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
                    player.sendMessage(Component.text("Usage: /ve event leaderboard <event>")
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
        
        db.createEvent(name, stat, LocalDateTime.now(), endTime, player.getUsername())
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
    
    // ... rest of the methods
}