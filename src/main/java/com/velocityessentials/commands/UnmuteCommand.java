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

/**
 * /unmute command - unmute players
 * 
 * usage: /unmute <player>
 */
public class UnmuteCommand implements SimpleCommand {
    
    private final VelocityEssentials plugin;
    private final MuteData muteData;
    private final MiniMessage miniMessage;
    
    public UnmuteCommand(VelocityEssentials plugin, MuteData muteData) {
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
            sendMessage(executor, "<red>usage: /unmute <player></red>");
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
        
        // check if actually muted
        muteData.getMute(target.getUniqueId()).thenAccept(muteInfo -> {
            if (muteInfo == null) {
                sendMessage(executor, String.format(
                    "<yellow>%s is not muted</yellow>",
                    target.getUsername()
                ));
                return;
            }
            
            // unmute the player
            muteData.unmutePlayer(target.getUniqueId()).thenAccept(success -> {
                if (success) {
                    sendMessage(executor, String.format(
                        "<green>unmuted <yellow>%s</yellow></green>",
                        target.getUsername()
                    ));
                    
                    sendMessage(target, "<green>you have been unmuted</green>");
                } else {
                    sendMessage(executor, "<red>failed to unmute player</red>");
                }
            });
        });
    }
    
    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("velocityessentials.unmute");
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
        
        return List.of();
    }
    
    private void sendMessage(Player player, String message) {
        Component component = miniMessage.deserialize(message);
        player.sendMessage(component);
    }
}