package com.velocityessentials.stats;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.velocityessentials.VelocityEssentials;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

public class StatsSystem {
    private final VelocityEssentials plugin;
    private final HikariDataSource dataSource;
    private final Gson gson = new Gson();
    private final Map<String, Path> serverStatsPaths = new HashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Set<String> recentlyQueriedPlayers = ConcurrentHashMap.newKeySet();
    
    // Caches
    private final Map<String, String> uuidToUsername = new ConcurrentHashMap<>();
    private final Map<String, Long> lastMojangQuery = new ConcurrentHashMap<>();
    
    public StatsSystem(VelocityEssentials plugin) {
        this.plugin = plugin;
        this.dataSource = setupDatabase();
        loadConfiguration();
        createTables();
        startProcessing();
    }
    
    private HikariDataSource setupDatabase() {
        Path dbPath = plugin.getDataDirectory().resolve("stats.db");
        
        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.sqlite.JDBC");
        config.setJdbcUrl("jdbc:sqlite:" + dbPath.toAbsolutePath());
        config.setMaximumPoolSize(1); // SQLite works best with single connection
        
        // SQLite performance settings
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("synchronous", "NORMAL");
        config.addDataSourceProperty("cache_size", "-64000");
        config.addDataSourceProperty("temp_store", "MEMORY");
        
        return new HikariDataSource(config);
    }
    
    private void createTables() {
        try (Connection conn = dataSource.getConnection()) {
            // Players table
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS players (
                    uuid TEXT PRIMARY KEY,
                    username TEXT,
                    last_seen INTEGER,
                    first_seen INTEGER DEFAULT (strftime('%s', 'now'))
                )
                """);
            
            // Stats table - stores ALL Minecraft stats
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS player_stats (
                    uuid TEXT,
                    server_name TEXT,
                    stat_key TEXT,
                    stat_value INTEGER,
                    last_updated INTEGER,
                    PRIMARY KEY (uuid, server_name, stat_key)
                )
                """);
            
            // Events system
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT UNIQUE,
                    display_name TEXT,
                    stat_key TEXT,
                    start_time INTEGER,
                    end_time INTEGER,
                    created_by TEXT,
                    status TEXT DEFAULT 'pending'
                )
                """);
            
            // Event baselines
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS event_baselines (
                    event_id INTEGER,
                    player_uuid TEXT,
                    server_name TEXT,
                    baseline_value INTEGER,
                    PRIMARY KEY (event_id, player_uuid, server_name),
                    FOREIGN KEY (event_id) REFERENCES events(id)
                )
                """);
            
            // Event results
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS event_results (
                    event_id INTEGER,
                    player_uuid TEXT,
                    final_progress INTEGER,
                    rank INTEGER,
                    PRIMARY KEY (event_id, player_uuid),
                    FOREIGN KEY (event_id) REFERENCES events(id)
                )
                """);
            
            // Indexes for performance
            conn.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_stats_uuid ON player_stats(uuid)");
            conn.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_stats_key ON player_stats(stat_key)");
            conn.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_stats_updated ON player_stats(last_updated)");
            conn.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_events_status ON events(status)");
            
            plugin.getLogger().info("Stats database tables created successfully");
            
        } catch (SQLException e) {
            plugin.getLogger().error("Failed to create stats tables", e);
        }
    }
    
    private void loadConfiguration() {
        // Load server paths from config
        Map<String, String> configPaths = plugin.getConfig().getStatsServerPaths();
        
        for (Map.Entry<String, String> entry : configPaths.entrySet()) {
            String serverName = entry.getKey();
            String pathStr = entry.getValue();
            
            if (!pathStr.isEmpty()) {
                Path path = Path.of(pathStr);
                serverStatsPaths.put(serverName, path);
                plugin.getLogger().info("Registered stats path for " + serverName + ": " + path);
            }
        }
        
        plugin.getLogger().info("Loaded stats paths for " + serverStatsPaths.size() + " servers");
    }
    
    private void startProcessing() {
        int updateInterval = plugin.getConfig().getStatsUpdateInterval();
        
        // Initial delay of 1 minute, then every X minutes
        scheduler.scheduleAtFixedRate(
            this::processAllStats,
            1,
            updateInterval,
            TimeUnit.MINUTES
        );
        
        // Event processor - runs every minute
        scheduler.scheduleAtFixedRate(
            this::processEvents,
            0,
            1,
            TimeUnit.MINUTES
        );
        
        plugin.getLogger().info("Stats processing scheduled every " + updateInterval + " minutes");
    }
    
    private void processAllStats() {
        plugin.getLogger().info("Starting stats processing cycle...");
        long startTime = System.currentTimeMillis();
        int totalProcessed = 0;
        
        for (Map.Entry<String, Path> entry : serverStatsPaths.entrySet()) {
            String serverName = entry.getKey();
            Path statsPath = entry.getValue();
            
            if (!Files.exists(statsPath)) {
                plugin.getLogger().warn("Stats path not found for " + serverName + ": " + statsPath);
                continue;
            }
            
            try (Stream<Path> files = Files.list(statsPath)) {
                List<Path> statFiles = files
                    .filter(p -> p.toString().endsWith(".json"))
                    .toList();
                
                plugin.getLogger().info("Processing " + statFiles.size() + " stat files for " + serverName);
                
                for (Path statFile : statFiles) {
                    if (processPlayerStats(statFile, serverName)) {
                        totalProcessed++;
                    }
                }
                
            } catch (IOException e) {
                plugin.getLogger().error("Failed to read stats directory for " + serverName, e);
            }
        }
        
        long duration = System.currentTimeMillis() - startTime;
        plugin.getLogger().info("Stats processing complete! Processed " + totalProcessed + 
                             " files in " + duration + "ms");
    }
    
    private boolean processPlayerStats(Path statsFile, String serverName) {
        try {
            String fileName = statsFile.getFileName().toString();
            String uuidString = fileName.replace(".json", "");
            
            // Validate UUID format
            try {
                UUID.fromString(uuidString);
            } catch (IllegalArgumentException e) {
                return false;
            }
            
            // Read and parse stats
            String content = Files.readString(statsFile);
            JsonObject data = gson.fromJson(content, JsonObject.class);
            
            if (!data.has("stats")) {
                return false;
            }
            
            JsonObject stats = data.getAsJsonObject("stats");
            long lastModified = Files.getLastModifiedTime(statsFile).toMillis() / 1000;
            
            // Update player info
            updatePlayerInfo(uuidString, lastModified);
            
            // Process all stats
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                
                // Clear old stats for this player/server
                try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM player_stats WHERE uuid = ? AND server_name = ?")) {
                    ps.setString(1, uuidString);
                    ps.setString(2, serverName);
                    ps.executeUpdate();
                }
                
                // Insert all stats
                try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO player_stats (uuid, server_name, stat_key, stat_value, last_updated) " +
                    "VALUES (?, ?, ?, ?, ?)")) {
                    
                    for (Map.Entry<String, com.google.gson.JsonElement> category : stats.entrySet()) {
                        String categoryName = category.getKey();
                        JsonObject categoryStats = category.getValue().getAsJsonObject();
                        
                        for (Map.Entry<String, com.google.gson.JsonElement> stat : categoryStats.entrySet()) {
                            String statKey = categoryName + ":" + stat.getKey();
                            long value = stat.getValue().getAsLong();
                            
                            ps.setString(1, uuidString);
                            ps.setString(2, serverName);
                            ps.setString(3, statKey);
                            ps.setLong(4, value);
                            ps.setLong(5, lastModified);
                            ps.addBatch();
                        }
                    }
                    
                    ps.executeBatch();
                }
                
                conn.commit();
                return true;
                
            } catch (SQLException e) {
                plugin.getLogger().error("Failed to save stats for " + uuidString, e);
                return false;
            }
            
        } catch (IOException e) {
            plugin.getLogger().error("Failed to read stats file: " + statsFile, e);
            return false;
        }
    }
    
    private void updatePlayerInfo(String uuid, long lastSeen) {
        // Check if we need to fetch username
        String username = uuidToUsername.get(uuid);
        
        if (username == null) {
            // Check if we've queried recently (rate limiting)
            Long lastQuery = lastMojangQuery.get(uuid);
            long now = System.currentTimeMillis();
            
            if (lastQuery == null || now - lastQuery > 3600000) { // 1 hour
                username = fetchUsernameFromMojang(uuid);
                lastMojangQuery.put(uuid, now);
            }
        }
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT OR REPLACE INTO players (uuid, username, last_seen) VALUES (?, ?, ?)")) {
            
            ps.setString(1, uuid);
            ps.setString(2, username);
            ps.setLong(3, lastSeen);
            ps.executeUpdate();
            
        } catch (SQLException e) {
            plugin.getLogger().error("Failed to update player info for " + uuid, e);
        }
    }
    
    private String fetchUsernameFromMojang(String uuid) {
        try {
            URL url = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            if (conn.getResponseCode() == 200) {
                String response = new String(conn.getInputStream().readAllBytes());
                JsonObject profile = gson.fromJson(response, JsonObject.class);
                String username = profile.get("name").getAsString();
                
                uuidToUsername.put(uuid, username);
                return username;
            }
        } catch (Exception e) {
            // Silently fail - we'll try again later
        }
        
        return null;
    }
    
    // ===== EVENTS SYSTEM =====
    
    public CompletableFuture<Boolean> createEvent(String name, String statKey, 
                                                  LocalDateTime start, LocalDateTime end, 
                                                  String creator) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO events (name, display_name, stat_key, start_time, end_time, created_by) " +
                     "VALUES (?, ?, ?, ?, ?, ?)")) {
                
                ps.setString(1, name.toLowerCase().replace(" ", "_"));
                ps.setString(2, name);
                ps.setString(3, statKey);
                ps.setLong(4, start.toEpochSecond(java.time.ZoneOffset.UTC));
                ps.setLong(5, end.toEpochSecond(java.time.ZoneOffset.UTC));
                ps.setString(6, creator);
                
                ps.executeUpdate();
                
                plugin.getLogger().info("Event created: " + name + " tracking " + statKey);
                return true;
                
            } catch (SQLException e) {
                plugin.getLogger().error("Failed to create event", e);
                return false;
            }
        });
    }
    
    private void processEvents() {
        try (Connection conn = dataSource.getConnection()) {
            long now = Instant.now().getEpochSecond();
            
            // Activate pending events
            conn.createStatement().executeUpdate(
                "UPDATE events SET status = 'active' WHERE status = 'pending' AND start_time <= " + now
            );
            
            // Create baselines for newly active events
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, stat_key FROM events WHERE status = 'active' AND " +
                "id NOT IN (SELECT DISTINCT event_id FROM event_baselines)")) {
                
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    createEventBaselines(conn, rs.getInt("id"), rs.getString("stat_key"));
                }
            }
            
            // Finish ended events
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM events WHERE status = 'active' AND end_time <= " + now)) {
                
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    finishEvent(conn, rs.getInt("id"));
                }
            }
            
        } catch (SQLException e) {
            plugin.getLogger().error("Failed to process events", e);
        }
    }
    
    private void createEventBaselines(Connection conn, int eventId, String statKey) throws SQLException {
        // Snapshot current values for this stat
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO event_baselines (event_id, player_uuid, server_name, baseline_value) " +
            "SELECT ?, uuid, server_name, stat_value FROM player_stats WHERE stat_key = ?")) {
            
            ps.setInt(1, eventId);
            ps.setString(2, statKey);
            ps.executeUpdate();
            
            plugin.getLogger().info("Created baselines for event " + eventId);
        }
    }
    
    private void finishEvent(Connection conn, int eventId) throws SQLException {
        // Calculate final rankings
        String sql = """
            INSERT INTO event_results (event_id, player_uuid, final_progress, rank)
            SELECT 
                eb.event_id,
                eb.player_uuid,
                COALESCE(ps.stat_value, 0) - COALESCE(eb.baseline_value, 0) as progress,
                RANK() OVER (ORDER BY COALESCE(ps.stat_value, 0) - COALESCE(eb.baseline_value, 0) DESC) as rank
            FROM event_baselines eb
            LEFT JOIN events e ON e.id = eb.event_id
            LEFT JOIN player_stats ps ON 
                ps.uuid = eb.player_uuid AND 
                ps.server_name = eb.server_name AND 
                ps.stat_key = e.stat_key
            WHERE eb.event_id = ?
            """;
            
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, eventId);
            ps.executeUpdate();
        }
        
        // Mark as finished
        try (PreparedStatement ps = conn.prepareStatement(
            "UPDATE events SET status = 'finished' WHERE id = ?")) {
            ps.setInt(1, eventId);
            ps.executeUpdate();
        }
        
        plugin.getLogger().info("Finished event " + eventId);
    }
    
    // ===== API METHODS =====
    
    public JsonObject getPlayerStats(String username) {
        JsonObject result = new JsonObject();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT s.*, p.username FROM player_stats s " +
                 "JOIN players p ON s.uuid = p.uuid " +
                 "WHERE p.username = ?")) {
            
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            
            JsonObject servers = new JsonObject();
            while (rs.next()) {
                String server = rs.getString("server_name");
                String statKey = rs.getString("stat_key");
                long value = rs.getLong("stat_value");
                
                if (!servers.has(server)) {
                    servers.add(server, new JsonObject());
                }
                
                servers.getAsJsonObject(server).addProperty(statKey, value);
            }
            
            result.add("servers", servers);
            result.addProperty("success", true);
            
        } catch (SQLException e) {
            result.addProperty("success", false);
            result.addProperty("error", e.getMessage());
        }
        
        return result;
    }
    
    public JsonObject getTopPlayers(String statKey, int limit) {
        JsonObject result = new JsonObject();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT p.username, SUM(s.stat_value) as total " +
                 "FROM player_stats s " +
                 "JOIN players p ON s.uuid = p.uuid " +
                 "WHERE s.stat_key = ? " +
                 "GROUP BY s.uuid " +
                 "ORDER BY total DESC " +
                 "LIMIT ?")) {
            
            ps.setString(1, statKey);
            ps.setInt(2, limit);
            ResultSet rs = ps.executeQuery();
            
            com.google.gson.JsonArray players = new com.google.gson.JsonArray();
            while (rs.next()) {
                JsonObject player = new JsonObject();
                player.addProperty("username", rs.getString("username"));
                player.addProperty("value", rs.getLong("total"));
                players.add(player);
            }
            
            result.add("players", players);
            result.addProperty("success", true);
            
        } catch (SQLException e) {
            result.addProperty("success", false);
            result.addProperty("error", e.getMessage());
        }
        
        return result;
    }
    
    public JsonObject getEventLeaderboard(String eventName) {
        JsonObject result = new JsonObject();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT p.username, er.final_progress, er.rank " +
                 "FROM event_results er " +
                 "JOIN events e ON er.event_id = e.id " +
                 "JOIN players p ON er.player_uuid = p.uuid " +
                 "WHERE e.name = ? " +
                 "ORDER BY er.rank")) {
            
            ps.setString(1, eventName);
            ResultSet rs = ps.executeQuery();
            
            com.google.gson.JsonArray leaderboard = new com.google.gson.JsonArray();
            while (rs.next()) {
                JsonObject entry = new JsonObject();
                entry.addProperty("username", rs.getString("username"));
                entry.addProperty("progress", rs.getLong("final_progress"));
                entry.addProperty("rank", rs.getInt("rank"));
                leaderboard.add(entry);
            }
            
            result.add("leaderboard", leaderboard);
            result.addProperty("success", true);
            
        } catch (SQLException e) {
            result.addProperty("success", false);
            result.addProperty("error", e.getMessage());
        }
        
        return result;
    }
    
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
        
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}