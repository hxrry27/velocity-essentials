package com.velocityessentials.backend.commands;

import com.velocityessentials.backend.AFKManager;
import com.velocityessentials.backend.VelocityEssentialsBackend;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class AFKCommand implements CommandExecutor {
    private final VelocityEssentialsBackend plugin;
    private final AFKManager afkManager;
    
    public AFKCommand(VelocityEssentialsBackend plugin, AFKManager afkManager) {
        this.plugin = plugin;
        this.afkManager = afkManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("This command can only be used by players!", NamedTextColor.RED));
            return true;
        }
        
        Player player = (Player) sender;
        
        // Check permission
        if (!player.hasPermission("velocityessentials.afk")) {
            player.sendMessage(Component.text("You don't have permission to use this command!", NamedTextColor.RED));
            return true;
        }
        
         String afkMessage = null;
        if (args.length > 0) {
            // Check if they have permission to set AFK messages
            if (!player.hasPermission("velocityessentials.afk.message")) {
                player.sendMessage(Component.text("You don't have permission to set AFK messages!", NamedTextColor.RED));
                return true;
            }
            
            // Join all arguments as the AFK message
            afkMessage = String.join(" ", args);
            
            // Optional: Limit message length
            if (afkMessage.length() > 50) {
                player.sendMessage(Component.text("AFK message is too long! Maximum 50 characters.", NamedTextColor.RED));
                return true;
            }
        }
        
        // Toggle AFK status with message
        afkManager.toggleAFK(player, afkMessage);
        
        return true;
    }
}