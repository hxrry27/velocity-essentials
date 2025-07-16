package com.velocityessentials.stats;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.velocityessentials.VelocityEssentials;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class StatsAPIHandler {
    private final VelocityEssentials plugin;
    private final StatsSystem statsSystem;
    private final HttpServer server;
    private final Gson gson = new Gson();
    private final String apiKey;
    
    public StatsAPIHandler(VelocityEssentials plugin, StatsSystem statsSystem, int port, String apiKey) throws IOException {
        this.plugin = plugin;
        this.statsSystem = statsSystem;
        this.apiKey = apiKey;
        
        // Create HTTP server
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        
        // Register endpoints
        server.createContext("/api/stats/player/", new PlayerStatsHandler());
        server.createContext("/api/stats/top/", new TopPlayersHandler());
        server.createContext("/api/stats/event/", new EventHandler());
        server.createContext("/api/stats/all", new AllStatsHandler());
        server.createContext("/health", new HealthHandler());
        
        server.start();
        plugin.getLogger().info("Stats API started on port " + port);
    }
    
    private void sendResponse(HttpExchange exchange, int code, JsonObject response) throws IOException {
        String responseBody = gson.toJson(response);
        byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
        
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(code, responseBytes.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
    
    private boolean checkAuth(HttpExchange exchange) {
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        return auth != null && auth.equals("Bearer " + apiKey);
    }
    
    class PlayerStatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!checkAuth(exchange)) {
                JsonObject error = new JsonObject();
                error.addProperty("error", "Unauthorized");
                sendResponse(exchange, 401, error);
                return;
            }
            
            String path = exchange.getRequestURI().getPath();
            String username = path.substring("/api/stats/player/".length());
            
            if (username.isEmpty()) {
                JsonObject error = new JsonObject();
                error.addProperty("error", "Username required");
                sendResponse(exchange, 400, error);
                return;
            }
            
            JsonObject stats = statsSystem.getPlayerStats(username);
            sendResponse(exchange, 200, stats);
        }
    }
    
    class TopPlayersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!checkAuth(exchange)) {
                JsonObject error = new JsonObject();
                error.addProperty("error", "Unauthorized");
                sendResponse(exchange, 401, error);
                return;
            }
            
            String path = exchange.getRequestURI().getPath();
            String statKey = path.substring("/api/stats/top/".length());
            
            // Parse query parameters for limit
            int limit = 10;
            String query = exchange.getRequestURI().getQuery();
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] kv = param.split("=");
                    if (kv[0].equals("limit") && kv.length > 1) {
                        try {
                            limit = Integer.parseInt(kv[1]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
            
            JsonObject topPlayers = statsSystem.getTopPlayers(statKey, limit);
            sendResponse(exchange, 200, topPlayers);
        }
    }
    
    class EventHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!checkAuth(exchange)) {
                JsonObject error = new JsonObject();
                error.addProperty("error", "Unauthorized");
                sendResponse(exchange, 401, error);
                return;
            }
            
            String path = exchange.getRequestURI().getPath();
            String eventName = path.substring("/api/stats/event/".length());
            
            JsonObject leaderboard = statsSystem.getEventLeaderboard(eventName);
            sendResponse(exchange, 200, leaderboard);
        }
    }
    
    class AllStatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!checkAuth(exchange)) {
                JsonObject error = new JsonObject();
                error.addProperty("error", "Unauthorized");
                sendResponse(exchange, 401, error);
                return;
            }
            
            // This would return aggregated stats, server totals, etc.
            JsonObject response = new JsonObject();
            response.addProperty("message", "All stats endpoint - implement as needed");
            sendResponse(exchange, 200, response);
        }
    }
    
    class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            JsonObject health = new JsonObject();
            health.addProperty("status", "healthy");
            health.addProperty("service", "VelocityEssentials Stats API");
            sendResponse(exchange, 200, health);
        }
    }
    
    public void shutdown() {
        server.stop(0);
        plugin.getLogger().info("Stats API shut down");
    }
}