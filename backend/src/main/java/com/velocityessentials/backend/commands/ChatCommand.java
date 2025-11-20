package com.velocityessentials.backend.commands;

import com.velocityessentials.backend.VelocityEssentialsBackend;
import com.velocityessentials.backend.chat.Channel;
import com.velocityessentials.backend.chat.ChannelManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /chat command - manage chat channels
 */
public class ChatCommand implements CommandExecutor, TabCompleter {
    
    private final VelocityEssentialsBackend plugin;
    private final ChannelManager channelManager;
    private final MiniMessage miniMessage;
    
    public ChatCommand(VelocityEssentialsBackend plugin) {
        this.plugin = plugin;
        this.channelManager = plugin.getChannelManager();
        this.miniMessage = MiniMessage.miniMessage();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("this command can only be used by players");
            return true;
        }
        
        // /chat - show current channel
        if (args.length == 0) {
            showCurrentChannel(player);
            return true;
        }
        
        String subcommand = args[0].toLowerCase();
        
        // /chat list - list available channels
        if (subcommand.equals("list")) {
            listChannels(player);
            return true;
        }
        
        // /chat <channel> - switch to channel
        switchChannel(player, subcommand);
        return true;
    }
    
    /**
     * show player's current channel
     */
    private void showCurrentChannel(Player player) {
        Channel channel = channelManager.getPlayerChannel(player);
        
        if (channel == null) {
            sendMessage(player, "<red>you are not in any channel</red>");
            return;
        }
        
        sendMessage(player, String.format(
            "<gray>current channel: <aqua>%s</aqua> %s</gray>",
            channel.getName(),
            channel.isCrossServer() ? "<gray>(cross-server)</gray>" : "<gray>(local)</gray>"
        ));
    }
    
    /**
     * list all available channels for player
     */
    private void listChannels(Player player) {
        List<Channel> available = channelManager.getAvailableChannels(player);
        
        if (available.isEmpty()) {
            sendMessage(player, "<red>no channels available</red>");
            return;
        }
        
        Channel current = channelManager.getPlayerChannel(player);
        
        sendMessage(player, "<gray>available channels:</gray>");
        
        for (Channel channel : available) {
            boolean isCurrent = current != null && current.getId().equals(channel.getId());
            String marker = isCurrent ? "<green>➜</green> " : "  ";
            String type = channel.isCrossServer() ? "<gray>(cross-server)</gray>" : "<gray>(local)</gray>";
            
            sendMessage(player, String.format(
                "%s<aqua>%s</aqua> %s",
                marker, channel.getName(), type
            ));
        }
        
        sendMessage(player, "<gray>use <aqua>/chat <channel></aqua> to switch</gray>");
    }
    
    /**
     * switch player to a channel
     */
    private void switchChannel(Player player, String channelId) {
        Channel channel = channelManager.getChannel(channelId);
        
        if (channel == null) {
            sendMessage(player, String.format(
                "<red>channel '<yellow>%s</yellow>' not found</red>",
                channelId
            ));
            return;
        }
        
        if (!channelManager.canUseChannel(player, channel)) {
            sendMessage(player, String.format(
                "<red>you don't have permission to use the <yellow>%s</yellow> channel</red>",
                channel.getName()
            ));
            return;
        }
        
        // check if already in this channel
        Channel current = channelManager.getPlayerChannel(player);
        if (current != null && current.getId().equals(channelId)) {
            sendMessage(player, String.format(
                "<yellow>you are already in the <aqua>%s</aqua> channel</yellow>",
                channel.getName()
            ));
            return;
        }
        
        // switch channel
        if (channelManager.setPlayerChannel(player, channelId)) {
            sendMessage(player, String.format(
                "<green>switched to <aqua>%s</aqua> channel</green>",
                channel.getName()
            ));
        } else {
            sendMessage(player, "<red>failed to switch channels</red>");
        }
    }
    
    /**
     * send minimessage to player
     */
    private void sendMessage(Player player, String message) {
        Component component = miniMessage.deserialize(message);
        player.sendMessage(component);
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            
            // add "list" subcommand
            completions.add("list");
            
            // add available channel names
            List<Channel> available = channelManager.getAvailableChannels(player);
            completions.addAll(available.stream()
                .map(Channel::getId)
                .collect(Collectors.toList()));
            
            // filter by what player has typed
            String input = args[0].toLowerCase();
            return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(input))
                .collect(Collectors.toList());
        }
        
        return List.of();
    }
}