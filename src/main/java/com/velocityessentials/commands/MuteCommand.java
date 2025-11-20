package com.velocityessentials.commands;

import com.velocityessentials.VelocityEssentials;
import com.velocityessentials.modules.moderation.MuteData;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * /mute command - mute players network-wide
 */
public class MuteCommand implements SimpleCommand {
    
    private final VelocityEssentials plugin;
    private final MuteData muteData;
    private final MiniMessage miniMessage;
    
    public MuteCommand(VelocityEssentials plugin, MuteData muteData) {
        this.plugin = plugin;
        this.muteData = muteData;
        this.miniMessage = MiniMessage.miniMessage();
    }
    
    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player executor)) {
            invocation.source().sendMessage(Component.text("this command can only be used by players"));
            return;
        }
        
        String[] args = invocation.arguments();
        
        if (args.length < 1) {
            sendMessage(executor, "<red>usage: /mute <player> [duration] [reason]</red>");
            sendMessage(executor, "<gray>examples:</gray>");
            sendMessage(executor, "<gray>  /mute Player123 - permanent mute</gray>");
            sendMessage(executor, "<gray>  /mute Player123 1h - mute for 1 hour</gray>");
            sendMessage(executor, "<gray>  /mute Player123 30m spamming - mute with reason</gray>");
            return;
        }
        
        String targetName = args[0];
        
        // find target player
        Optional<Player> targetOpt = plugin.getServer().getPlayer(targetName);
        
        if (targetOpt.isEmpty()) {
            sendMessage(executor, String.format("<red>player '<yellow>%s</yellow>' not found</red>", targetName));
            return;
        }
        
        Player target = targetOpt.get();
        
        // parse duration (if provided)
        long tempExpiresAt = 0; // 0 = permanent
        String tempReason = "";
        int reasonStartIndex = 1;

        if (args.length >= 2) {
            String durationStr = args[1];
            long duration = parseDuration(durationStr);
            
            if (duration > 0) {
                tempExpiresAt = System.currentTimeMillis() + duration;
                reasonStartIndex = 2;
            } else {
                // not a duration, treat as reason
                reasonStartIndex = 1;
            }
        }

        // parse reason (if provided)
        if (args.length > reasonStartIndex) {
            StringBuilder reasonBuilder = new StringBuilder();
            for (int i = reasonStartIndex; i < args.length; i++) {
                if (i > reasonStartIndex) reasonBuilder.append(" ");
                reasonBuilder.append(args[i]);
            }
            tempReason = reasonBuilder.toString();
        }

        // make final for lambda
        final String reason = tempReason;
        final long expiresAt = tempExpiresAt;

        // check if already muted
        muteData.getMute(target.getUniqueId()).thenAccept(existingMute -> {
            if (existingMute != null) {
                sendMessage(executor, String.format(
                    "<yellow>%s is already muted</yellow>",
                    target.getUsername()
                ));
                return;
            }
            
            // mute the player (use reason and expiresAt directly now)
            muteData.mutePlayer(
                target.getUniqueId(),
                target.getUsername(),
                reason,
                executor.getUsername(),
                expiresAt
            ).thenAccept(success -> {
                if (success) {
                    // notify executor
                    if (expiresAt == 0) {
                        sendMessage(executor, String.format(
                            "<green>muted <yellow>%s</yellow> permanently</green>%s",
                            target.getUsername(),
                            reason.isEmpty() ? "" : "\n<gray>reason: <white>" + reason + "</white></gray>"
                        ));
                    } else {
                        String durationStr = formatDuration(expiresAt - System.currentTimeMillis());
                        sendMessage(executor, String.format(
                            "<green>muted <yellow>%s</yellow> for <aqua>%s</aqua></green>%s",
                            target.getUsername(),
                            durationStr,
                            reason.isEmpty() ? "" : "\n<gray>reason: <white>" + reason + "</white></gray>"
                        ));
                    }
                    
                    // notify target
                    if (reason.isEmpty()) {
                        sendMessage(target, "<red>you have been muted</red>");
                    } else {
                        sendMessage(target, "<red>you have been muted</red>\n<gray>reason: <white>" + reason + "</white></gray>");
                    }
                    
                } else {
                    sendMessage(executor, "<red>failed to mute player</red>");
                }
            });
        });
    }
    
    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("velocityessentials.mute");
    }
    
    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        
        if (args.length == 1) {
            // suggest online player names
            List<String> players = new ArrayList<>();
            String input = args[0].toLowerCase();
            
            for (Player player : plugin.getServer().getAllPlayers()) {
                if (player.getUsername().toLowerCase().startsWith(input)) {
                    players.add(player.getUsername());
                }
            }
            
            return players;
        }
        
        if (args.length == 2) {
            // suggest durations
            return List.of("30m", "1h", "6h", "12h", "1d", "7d", "30d");
        }
        
        return List.of();
    }
    
    /**
     * parse duration string to milliseconds
     * formats: 30s, 5m, 1h, 2d, 1w
     */
    private long parseDuration(String str) {
        try {
            if (str.length() < 2) return 0;
            
            String numberPart = str.substring(0, str.length() - 1);
            char unit = str.charAt(str.length() - 1);
            
            long amount = Long.parseLong(numberPart);
            
            return switch (unit) {
                case 's' -> TimeUnit.SECONDS.toMillis(amount);
                case 'm' -> TimeUnit.MINUTES.toMillis(amount);
                case 'h' -> TimeUnit.HOURS.toMillis(amount);
                case 'd' -> TimeUnit.DAYS.toMillis(amount);
                case 'w' -> TimeUnit.DAYS.toMillis(amount * 7);
                default -> 0;
            };
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    /**
     * format duration in milliseconds to readable string
     */
    private String formatDuration(long millis) {
        long days = TimeUnit.MILLISECONDS.toDays(millis);
        long hours = TimeUnit.MILLISECONDS.toHours(millis) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        
        if (days > 0) {
            return days + "d " + hours + "h";
        } else if (hours > 0) {
            return hours + "h " + minutes + "m";
        } else {
            return minutes + "m";
        }
    }
    
    private void sendMessage(Player player, String message) {
        Component component = miniMessage.deserialize(message);
        player.sendMessage(component);
    }
}