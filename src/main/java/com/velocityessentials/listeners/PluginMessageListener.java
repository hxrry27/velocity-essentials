package com.velocityessentials.listeners;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.velocityessentials.VelocityEssentials;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;

import java.util.UUID;

public class PluginMessageListener {
    private final VelocityEssentials plugin;
   
    public PluginMessageListener(VelocityEssentials plugin) {
        this.plugin = plugin;
    }
   
    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(VelocityEssentials.CHANNEL)) return;
       
        ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
        String subchannel = in.readUTF();
       
        if (subchannel.equals("chat")) {
            String uuid = in.readUTF();
            String username = in.readUTF();
            String prefix = in.readUTF();
            String message = in.readUTF();
            String serverName = in.readUTF();
           
            // Get the player
            plugin.getServer().getPlayer(UUID.fromString(uuid)).ifPresent(player -> {
                // Send to Discord with the pre-formatted prefix from backend
                plugin.getDiscordWebhook().sendFormattedChatMessage(
                    player, serverName, prefix, message
                );
            });
           
            event.setResult(PluginMessageEvent.ForwardResult.handled());
        }
    }
}