package com.velocityessentials.modules.restart;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.velocityessentials.VelocityEssentials;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.util.Collection;

/**
 * handles sending plugin messages to backend servers
 * similar to MessageHandler but specifically for restart system, doesn't need to be used elsewhere
 */
public class RestartMessenger {
    private final VelocityEssentials plugin;
    
    public RestartMessenger(VelocityEssentials plugin) {
        this.plugin = plugin;
    }

    public void sendWarning(Collection<RegisteredServer> servers, int secondsUntilRestart, String soundName) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("restart_warning");
        out.writeInt(secondsUntilRestart);
        out.writeUTF(soundName != null ? soundName : "");
        
        byte[] data = out.toByteArray();
        
        for (RegisteredServer server : servers) {
            if (!server.getPlayersConnected().isEmpty()) {
                server.sendPluginMessage(VelocityEssentials.CHANNEL, data);
                
                if (plugin.getConfig().isDebug()) {
                    plugin.getLogger().info("Sent restart warning to " + server.getServerInfo().getName() + 
                                          ": " + secondsUntilRestart + "s");
                }
            }
        }
    }

    public void sendCommand(RegisteredServer server, String command) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("restart_command");
        out.writeUTF(command);
        
        if (!server.getPlayersConnected().isEmpty()) {
            server.sendPluginMessage(VelocityEssentials.CHANNEL, out.toByteArray());
            
            if (plugin.getConfig().isDebug()) {
                plugin.getLogger().info("Sent command to " + server.getServerInfo().getName() + 
                                      ": " + command);
            }
        }
    }

    public void sendShutdown(RegisteredServer server) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("restart_shutdown");
        
        if (!server.getPlayersConnected().isEmpty()) {
            server.sendPluginMessage(VelocityEssentials.CHANNEL, out.toByteArray());
            
            if (plugin.getConfig().isDebug()) {
                plugin.getLogger().info("Sent shutdown signal to " + server.getServerInfo().getName());
            }
        } else {
            plugin.getLogger().warn("Cannot send shutdown to " + server.getServerInfo().getName() + 
                                     " - no players connected!");
        }
    }

    public void sendCancel(Collection<RegisteredServer> servers, String reason) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("restart_cancel");
        out.writeUTF(reason != null ? reason : "");
        
        byte[] data = out.toByteArray();
        
        for (RegisteredServer server : servers) {
            if (!server.getPlayersConnected().isEmpty()) {
                server.sendPluginMessage(VelocityEssentials.CHANNEL, data);
                
                if (plugin.getConfig().isDebug()) {
                    plugin.getLogger().info("Sent cancel to " + server.getServerInfo().getName());
                }
            }
        }
    }

    public void sendTest(RegisteredServer server) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("restart_test");
        out.writeUTF("Restart system test from proxy");
        
        if (!server.getPlayersConnected().isEmpty()) {
            server.sendPluginMessage(VelocityEssentials.CHANNEL, out.toByteArray());
            plugin.getLogger().info("Sent restart test to " + server.getServerInfo().getName());
        } else {
            plugin.getLogger().warn("Cannot send test to " + server.getServerInfo().getName() + 
                                     " - no players connected!");
        }
    }
}