package com.velocityessentials.modules.discord;

import com.velocityessentials.VelocityEssentials;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.awt.Color;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class DiscordWebhook {
    private final VelocityEssentials plugin;
    
    // Discord colors
    private static final int COLOR_JOIN = 0x00FF00;      // Green
    private static final int COLOR_LEAVE = 0xFF0000;     // Red
    private static final int COLOR_SWITCH = 0xFFA500;    // Orange
    private static final int COLOR_FIRST_TIME = 0xFFD700; // Gold
    
    public DiscordWebhook(VelocityEssentials plugin) {
        this.plugin = plugin;
    }
    
    public void sendJoinMessage(Player player, RegisteredServer server, boolean isFirstTime) {
        if (!plugin.getConfig().isDiscordEnabled()) return;
        
        String title;
        int color;
        
        if (isFirstTime) {
            title = player.getUsername() + " joined the server for the first time!";
            color = COLOR_FIRST_TIME;
        } else {
            title = player.getUsername() + " joined the server";
            color = COLOR_JOIN;
        }
        
        String json = createEmbed(title, null, color, player);
        sendWebhook(json);
    }
    
    public void sendLeaveMessage(Player player, RegisteredServer server) {
        if (!plugin.getConfig().isDiscordEnabled()) return;
        
        String json = createEmbed(
            player.getUsername() + " left the server",
            null,
            COLOR_LEAVE,
            player
        );
        
        sendWebhook(json);
    }
    
    public void sendSwitchMessage(Player player, String fromServer, String toServer) {
        if (!plugin.getConfig().isDiscordEnabled()) return;
        
        String json = createEmbed(
            player.getUsername() + " switched servers",
            "**" + fromServer + "** → **" + toServer + "**",
            COLOR_SWITCH,
            player
        );
        
        sendWebhook(json);
    }
    
    private String createEmbed(String title, String description, int color, Player player) {
        String uuid = player.getUniqueId().toString().replace("-", "");
        String avatarUrl = "https://mc-heads.net/avatar/" + uuid + "/100";
        
        StringBuilder json = new StringBuilder();
        json.append("{");
        
        // Custom webhook appearance
        if (!plugin.getConfig().getDiscordUsername().isEmpty()) {
            json.append("\"username\":\"").append(escapeJson(plugin.getConfig().getDiscordUsername())).append("\",");
        }
        if (!plugin.getConfig().getDiscordAvatarUrl().isEmpty()) {
            json.append("\"avatar_url\":\"").append(escapeJson(plugin.getConfig().getDiscordAvatarUrl())).append("\",");
        }
        
        // Embed
        json.append("\"embeds\":[{");
        
        // Author with avatar
        json.append("\"author\":{");
        json.append("\"name\":\"").append(escapeJson(title)).append("\",");
        json.append("\"icon_url\":\"").append(avatarUrl).append("\"");
        json.append("}");
        
        // Description if provided
        if (description != null && !description.isEmpty()) {
            json.append(",\"description\":\"").append(escapeJson(description)).append("\"");
        }
        
        // Color
        json.append(",\"color\":").append(color);
        
        json.append("}]}");
        
        return json.toString();
    }
    
    private void sendWebhook(String json) {
        CompletableFuture.runAsync(() -> {
            try {
                URL url = new URL(plugin.getConfig().getDiscordWebhookUrl());
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("User-Agent", "VelocityEssentials/1.0");
                conn.setDoOutput(true);
                
                if (plugin.getConfig().isDebug()) {
                    plugin.getLogger().info("Sending Discord webhook: " + json);
                }
                
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }
                
                int responseCode = conn.getResponseCode();
                if (responseCode != 204 && responseCode != 200) {
                    plugin.getLogger().warn("Discord webhook returned code: " + responseCode);
                }
                
                conn.disconnect();
                
            } catch (Exception e) {
                plugin.getLogger().error("Failed to send Discord webhook", e);
            }
        });
    }

    public void sendChatMessage(Player player, String serverName, String message) {
        if (!plugin.getConfig().isDiscordEnabled()) return;
        
        // Escape Discord formatting/mentions
        String safeMessage = message
            .replace("@everyone", "@\u200beveryone")
            .replace("@here", "@\u200bhere");
        
        // Get prefix based on permissions
        String prefix = "";
        Map<String, String> prefixMap = plugin.getConfig().getChatPrefixes();
        
        // Check permissions in order (first match wins)
        for (Map.Entry<String, String> entry : prefixMap.entrySet()) {
            if (player.hasPermission(entry.getKey())) {
                prefix = entry.getValue();
                break; // Use first matching permission
            }
        }
        
        // Build the message
        StringBuilder messageBuilder = new StringBuilder();
        
        // Add server prefix if enabled
        if (plugin.getConfig().isChatShowServerPrefix()) {
            String serverPrefix = plugin.getConfig().getChatServerFormat()
                .replace("{server}", serverName.toUpperCase());
            messageBuilder.append(serverPrefix).append(" ");
        }
        
        // Add player prefix if they have one
        if (!prefix.isEmpty()) {
            messageBuilder.append(prefix).append(" ");
        }
        
        // Add username and message
        messageBuilder.append(player.getUsername()).append(": ").append(safeMessage);
        
        // Build JSON
        StringBuilder json = new StringBuilder();
        json.append("{");
        
        // Webhook identity
        if (!plugin.getConfig().getDiscordUsername().isEmpty()) {
            json.append("\"username\":\"").append(escapeJson(plugin.getConfig().getDiscordUsername())).append("\",");
        }
        
        // Use player head as avatar if enabled
        if (plugin.getConfig().isUsePlayerHeadForChat()) {
            String uuid = player.getUniqueId().toString().replace("-", "");
            String avatarUrl = "https://mc-heads.net/avatar/" + uuid + "/100";
            json.append("\"avatar_url\":\"").append(avatarUrl).append("\",");
        } else if (!plugin.getConfig().getDiscordAvatarUrl().isEmpty()) {
            json.append("\"avatar_url\":\"").append(escapeJson(plugin.getConfig().getDiscordAvatarUrl())).append("\",");
        }
        
        json.append("\"content\":\"").append(escapeJson(messageBuilder.toString())).append("\"");
        json.append("}");
        
        sendWebhook(json.toString());
    }

    public void sendFormattedChatMessage(Player player, String serverName, String prefix, String message) {
        if (!plugin.getConfig().isDiscordEnabled()) return;
        if (!plugin.getConfig().isChatRelayEnabled()) return;
        
        // Escape Discord mentions
        String safeMessage = message
            .replace("@everyone", "@\u200beveryone")
            .replace("@here", "@\u200bhere");
        
        // Build the message
        StringBuilder messageBuilder = new StringBuilder();
        
        // Add server prefix if enabled
        if (plugin.getConfig().isChatShowServerPrefix()) {
            String serverPrefix = plugin.getConfig().getChatServerFormat()
                .replace("{server}", serverName.toUpperCase());
            messageBuilder.append(serverPrefix).append(" ");
        }
        
        // Add the pre-formatted prefix from backend
        if (!prefix.isEmpty()) {
            messageBuilder.append(prefix).append(" ");
        }
        
        // Add username and message
        messageBuilder.append(player.getUsername()).append(": ").append(safeMessage);
        
        // Build JSON
        StringBuilder json = new StringBuilder();
        json.append("{");
        
        // Webhook identity
        if (!plugin.getConfig().getDiscordUsername().isEmpty()) {
            json.append("\"username\":\"").append(escapeJson(plugin.getConfig().getDiscordUsername())).append("\",");
        }
        
        // Use player head as avatar if enabled
        if (plugin.getConfig().isUsePlayerHeadForChat()) {
            String uuid = player.getUniqueId().toString().replace("-", "");
            String avatarUrl = "https://mc-heads.net/avatar/" + uuid + "/100";
            json.append("\"avatar_url\":\"").append(avatarUrl).append("\",");
        } else if (!plugin.getConfig().getDiscordAvatarUrl().isEmpty()) {
            json.append("\"avatar_url\":\"").append(escapeJson(plugin.getConfig().getDiscordAvatarUrl())).append("\",");
        }
        
        json.append("\"content\":\"").append(escapeJson(messageBuilder.toString())).append("\"");
        json.append("}");
        
        sendWebhook(json.toString());
    }
    
    private String escapeJson(String input) {
        if (input == null) return "";
        return input
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\f", "\\f")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}