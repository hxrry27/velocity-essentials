package com.velocityessentials.stats;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.velocityessentials.VelocityEssentials;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class StatsProcessor {
    private final VelocityEssentials plugin;
    private final Path statsDirectory;
    private final Gson gson = new Gson();
    
    public StatsProcessor(VelocityEssentials plugin, Path statsDirectory) {
        this.plugin = plugin;
        this.statsDirectory = statsDirectory;
    }
    
    public void startProcessing() {
        // Schedule periodic processing
        plugin.getServer().getScheduler()
            .buildTask(plugin, this::processAllStats)
            .delay(1, TimeUnit.MINUTES)
            .repeat(5, TimeUnit.MINUTES)
            .schedule();
    }
    
    private void processAllStats() {
        if (!Files.exists(statsDirectory)) {
            plugin.getLogger().warn("Stats directory not found: " + statsDirectory);
            return;
        }
        
        try (Stream<Path> files = Files.list(statsDirectory)) {
            files.filter(path -> path.toString().endsWith(".json"))
                .forEach(this::processPlayerStats);
        } catch (IOException e) {
            plugin.getLogger().error("Failed to read stats directory", e);
        }
    }
    
    private void processPlayerStats(Path statsFile) {
        try {
            String fileName = statsFile.getFileName().toString();
            String uuidString = fileName.replace(".json", "");
            UUID playerUuid = UUID.fromString(uuidString);
            
            String content = Files.readString(statsFile);
            JsonObject data = gson.fromJson(content, JsonObject.class);
            
            if (data.has("stats")) {
                JsonObject stats = data.getAsJsonObject("stats");
                processStatsObject(playerUuid, stats);
            }
            
        } catch (Exception e) {
            plugin.getLogger().error("Failed to process stats file: " + statsFile, e);
        }
    }
    
    private void processStatsObject(UUID playerUuid, JsonObject stats) {
        // Store in SQLite database alongside other VelocityEssentials data
        String sql = """
            INSERT OR REPLACE INTO player_stats 
            (uuid, stat_key, stat_value, last_updated) 
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
            """;
            
        try (Connection conn = plugin.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            for (String category : stats.keySet()) {
                JsonObject categoryStats = stats.getAsJsonObject(category);
                
                for (String statKey : categoryStats.keySet()) {
                    String fullKey = category + ":" + statKey;
                    long value = categoryStats.get(statKey).getAsLong();
                    
                    stmt.setString(1, playerUuid.toString());
                    stmt.setString(2, fullKey);
                    stmt.setLong(3, value);
                    stmt.addBatch();
                }
            }
            
            stmt.executeBatch();
            
        } catch (SQLException e) {
            plugin.getLogger().error("Failed to save stats for " + playerUuid, e);
        }
    }
}