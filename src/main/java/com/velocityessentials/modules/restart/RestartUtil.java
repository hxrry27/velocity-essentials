package com.velocityessentials.modules.restart;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.time.DayOfWeek;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * utility functions for the restart module
 */
public class RestartUtil {
    
    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)([smhd])");

    public static String formatDuration(long seconds) {
        if (seconds <= 0) {
            return "now";
        }
        
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        
        StringBuilder sb = new StringBuilder();
        
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0) {
            sb.append(minutes).append("m ");
        }
        if (secs > 0 && days == 0 && hours == 0) { // Only show seconds if < 1 hour
            sb.append(secs).append("s");
        }
        
        return sb.toString().trim();
    }

    public static long parseDuration(String durationString) {
        if (durationString == null || durationString.isEmpty()) {
            return -1;
        }
        
        long totalSeconds = 0;
        Matcher matcher = DURATION_PATTERN.matcher(durationString.toLowerCase());
        
        while (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            String unit = matcher.group(2);
            
            totalSeconds += switch (unit) {
                case "s" -> value;
                case "m" -> value * 60L;
                case "h" -> value * 3600L;
                case "d" -> value * 86400L;
                default -> 0;
            };
        }
        
        return totalSeconds > 0 ? totalSeconds : -1;
    }

    public static Set<DayOfWeek> parseDays(java.util.List<String> dayStrings) {
        Set<DayOfWeek> days = new HashSet<>();
        
        if (dayStrings == null || dayStrings.isEmpty()) {
            return days; // Empty = all days
        }
        
        for (String dayStr : dayStrings) {
            if (dayStr.equals("*") || dayStr.equalsIgnoreCase("all")) {
                return new HashSet<>(); // Empty = all days
            }
            
            try {
                days.add(DayOfWeek.valueOf(dayStr.toUpperCase()));
            } catch (IllegalArgumentException e) {
                // Invalid day name, skip it
            }
        }
        
        return days;
    }

    public static Component createWarningMessage(String serverName, long timeLeft) {
        return Component.text()
            .append(Component.text("[", NamedTextColor.DARK_GRAY))
            .append(Component.text("⚠", NamedTextColor.YELLOW))
            .append(Component.text("] ", NamedTextColor.DARK_GRAY))
            .append(Component.text(serverName, NamedTextColor.AQUA, TextDecoration.BOLD))
            .append(Component.text(" will restart in ", NamedTextColor.YELLOW))
            .append(Component.text(formatDuration(timeLeft), NamedTextColor.RED, TextDecoration.BOLD))
            .build();
    }

    public static Component createCancelledMessage(String serverName, String reason) {
        Component base = Component.text()
            .append(Component.text("[", NamedTextColor.DARK_GRAY))
            .append(Component.text("✓", NamedTextColor.GREEN))
            .append(Component.text("] ", NamedTextColor.DARK_GRAY))
            .append(Component.text(serverName, NamedTextColor.AQUA, TextDecoration.BOLD))
            .append(Component.text(" restart cancelled", NamedTextColor.GREEN))
            .build();
        
        if (reason != null && !reason.isEmpty()) {
            return base.append(Component.text(": " + reason, NamedTextColor.GRAY));
        }
        
        return base;
    }

    public static Component createDelayedMessage(String serverName, String newTime, String reason) {
        Component base = Component.text()
            .append(Component.text("[", NamedTextColor.DARK_GRAY))
            .append(Component.text("⏰", NamedTextColor.YELLOW))
            .append(Component.text("] ", NamedTextColor.DARK_GRAY))
            .append(Component.text(serverName, NamedTextColor.AQUA, TextDecoration.BOLD))
            .append(Component.text(" restart delayed to ", NamedTextColor.YELLOW))
            .append(Component.text(newTime, NamedTextColor.GOLD, TextDecoration.BOLD))
            .build();
        
        if (reason != null && !reason.isEmpty()) {
            return base.append(Component.text(": " + reason, NamedTextColor.GRAY));
        }
        
        return base;
    }

    public static Component createRestartNowMessage(String serverName) {
        return Component.text()
            .append(Component.text("[", NamedTextColor.DARK_GRAY))
            .append(Component.text("⚠", NamedTextColor.RED))
            .append(Component.text("] ", NamedTextColor.DARK_GRAY))
            .append(Component.text(serverName, NamedTextColor.AQUA, TextDecoration.BOLD))
            .append(Component.text(" is restarting ", NamedTextColor.RED, TextDecoration.BOLD))
            .append(Component.text("NOW", NamedTextColor.DARK_RED, TextDecoration.BOLD))
            .append(Component.text("!", NamedTextColor.RED))
            .build();
    }

    public static boolean isValidScheduleName(String name) {
        return name != null && name.matches("[a-zA-Z0-9_-]+");
    }

    public static int getUrgencyLevel(long seconds) {
        if (seconds <= 30) return 3;       // CRITICAL
        if (seconds <= 60) return 2;       // HIGH
        if (seconds <= 180) return 1;      // MEDIUM
        return 0;                          // LOW
    }
}