package com.velocityessentials.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public class MessageUtil {
    private static final MiniMessage miniMessage = MiniMessage.miniMessage();
    
    /**
     * Parse a MiniMessage string into a Component
     */
    public static Component parse(String message) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }
        return miniMessage.deserialize(message);
    }
    
    /**
     * Parse a MiniMessage string with replacements
     */
    public static Component parse(String message, String... replacements) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }
        
        // Apply replacements in pairs
        for (int i = 0; i < replacements.length - 1; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }
        
        return miniMessage.deserialize(message);
    }
    
    /**
     * Strip formatting from a component
     */
    public static String stripFormatting(Component component) {
        return miniMessage.stripTags(miniMessage.serialize(component));
    }
}