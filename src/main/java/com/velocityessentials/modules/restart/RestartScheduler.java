package com.velocityessentials.modules.restart;

import com.velocityessentials.VelocityEssentials;
import com.velocityessentials.modules.restart.data.RestartReason;
import com.velocityessentials.modules.restart.data.RestartSchedule;
import com.velocityessentials.modules.restart.data.ScheduleTime;
import com.velocitypowered.api.scheduler.ScheduledTask;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * main manager for the auto-restart system
 * loads schedules, manages tasks, handles commands
 */
public class RestartScheduler {
    private final VelocityEssentials plugin;
    private final Map<String, RestartSchedule> schedules = new ConcurrentHashMap<>();
    private final Map<String, RestartTask> activeTasks = new ConcurrentHashMap<>();
    private ScheduledTask schedulerTask;
    
    private boolean enabled;
    
    public RestartScheduler(VelocityEssentials plugin) {
        this.plugin = plugin;
        loadConfiguration();
        
        if (enabled) {
            startScheduler();
            plugin.getLogger().info("RestartScheduler initialized with " + schedules.size() + " schedules");
        } else {
            plugin.getLogger().info("RestartScheduler disabled in config");
        }
    }
    
    private void loadConfiguration() {
        enabled = plugin.getConfig().isAutoRestartEnabled();
        
        if (!enabled) return;
        
        Map<String, RestartSchedule> loadedSchedules = plugin.getConfig().getRestartSchedules();
        
        if (loadedSchedules != null && !loadedSchedules.isEmpty()) {
            schedules.putAll(loadedSchedules);
            
            schedules.forEach((name, schedule) -> {
                List<String> errors = schedule.validate();
                if (!errors.isEmpty()) {
                    plugin.getLogger().error("Schedule '" + name + "' has validation errors:");
                    errors.forEach(error -> plugin.getLogger().error("  - " + error));
                }
            });
        } else {
            plugin.getLogger().warn("No restart schedules configured!");
        }
    }
    
    private void startScheduler() {
        schedulerTask = plugin.getServer().getScheduler()
            .buildTask(plugin, this::checkSchedules)
            .repeat(1, TimeUnit.MINUTES)
            .schedule();
        
        checkSchedules();
        
        plugin.getLogger().info("Restart scheduler started");
    }
    
    private void checkSchedules() {
        if (!enabled) return;
        
        for (RestartSchedule schedule : schedules.values()) {
            if (!schedule.isEnabled()) continue;
            
            if (activeTasks.containsKey(schedule.getName())) {
                continue;
            }
            
            ScheduleTime scheduleTime = schedule.getScheduleTime();
            Instant nextRestart = scheduleTime.getNextOccurrence().toInstant();
            long secondsUntil = Instant.now().until(nextRestart, java.time.temporal.ChronoUnit.SECONDS);
            
            if (secondsUntil <= 3600) {
                startTask(schedule, nextRestart);
            }
        }
    }
    
    private void startTask(RestartSchedule schedule, Instant scheduledTime) {
        String name = schedule.getName();
        
        if (activeTasks.containsKey(name)) {
            plugin.getLogger().warn("Task already active for schedule: " + name);
            return;
        }
        
        RestartTask task = new RestartTask(plugin, schedule, scheduledTime);
        activeTasks.put(name, task);
        
        task.start();
        
        plugin.getLogger().info("Started restart task for: " + name + 
                              " (in " + RestartUtil.formatDuration(task.getSecondsUntilRestart()) + ")");
    }
    
    public boolean restartNow(Collection<String> serverNames, String requestedBy) {
        
        RestartSchedule tempSchedule = RestartSchedule.builder("manual_restart")
            .enabled(true)
            .servers(serverNames)
            .scheduleTime(new ScheduleTime(
                java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
                Set.of(),
                java.time.ZoneId.systemDefault()
            ))
            .warningIntervals(List.of(10, 5, 3, 1))
            .warningSound("ENTITY_ENDER_DRAGON_GROWL")
            .build();
        
        RestartTask task = new RestartTask(plugin, tempSchedule, Instant.now().plusSeconds(10));
        task.start();
        
        plugin.getLogger().info("Manual restart initiated by " + requestedBy + 
                              " for servers: " + String.join(", ", serverNames));
        
        return true;
    }
    
    public boolean delayRestart(String scheduleName, int additionalMinutes, RestartReason reason) {
        RestartTask task = activeTasks.get(scheduleName);
        
        if (task == null) {
            return false;
        }
        
        if (task.getState() != RestartTask.TaskState.ACTIVE) {
            plugin.getLogger().warn("Cannot delay task in state: " + task.getState());
            return false;
        }
        
        task.delay(additionalMinutes * 60L, reason);
        return true;
    }
    
    public boolean cancelRestart(String scheduleName, RestartReason reason) {
        RestartTask task = activeTasks.get(scheduleName);
        
        if (task == null) {
            return false;
        }
        
        task.cancel(reason);
        activeTasks.remove(scheduleName);
        return true;
    }
    
    public String getNextRestartInfo(String serverName) {
        
        for (RestartTask task : activeTasks.values()) {
            if (task.getSchedule().appliesTo(serverName)) {
                long seconds = task.getSecondsUntilRestart();
                return "Restart in " + RestartUtil.formatDuration(seconds);
            }
        }
        
        
        RestartSchedule nextSchedule = null;
        long shortestTime = Long.MAX_VALUE;
        
        for (RestartSchedule schedule : schedules.values()) {
            if (!schedule.isEnabled() || !schedule.appliesTo(serverName)) {
                continue;
            }
            
            long secondsUntil = schedule.getScheduleTime().getSecondsUntilNext();
            if (secondsUntil < shortestTime) {
                shortestTime = secondsUntil;
                nextSchedule = schedule;
            }
        }
        
        if (nextSchedule != null) {
            return "Next restart " + nextSchedule.getScheduleTime().getFormattedTimeUntil();
        }
        
        return null;
    }
    
    public Map<String, RestartTask> getActiveTasks() {
        return new HashMap<>(activeTasks);
    }
    
    public Map<String, RestartSchedule> getSchedules() {
        return new HashMap<>(schedules);
    }
    
    public void reload() {
        plugin.getLogger().info("Reloading restart scheduler...");
        
        activeTasks.values().forEach(task -> 
            task.cancel(new RestartReason("System reload", "Console", RestartReason.Type.CANCEL))
        );
        activeTasks.clear();
        
        schedules.clear();
        
        loadConfiguration();
        
        if (schedulerTask != null) {
            schedulerTask.cancel();
        }
        
        if (enabled) {
            startScheduler();
            plugin.getLogger().info("Restart scheduler reloaded with " + schedules.size() + " schedules");
        } else {
            plugin.getLogger().info("Restart scheduler disabled");
        }
    }

    public void shutdown() {
        plugin.getLogger().info("Shutting down restart scheduler...");
        
        if (schedulerTask != null) {
            schedulerTask.cancel();
        }
        
        activeTasks.values().forEach(task ->
            task.cancel(new RestartReason("Server shutdown", "System", RestartReason.Type.CANCEL))
        );
        activeTasks.clear();
        
        plugin.getLogger().info("Restart scheduler shut down");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getStats() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Restart System Stats ===\n");
        sb.append("Enabled: ").append(enabled).append("\n");
        sb.append("Configured Schedules: ").append(schedules.size()).append("\n");
        sb.append("Active Tasks: ").append(activeTasks.size()).append("\n");
        
        if (!activeTasks.isEmpty()) {
            sb.append("\nActive Restarts:\n");
            activeTasks.forEach((name, task) -> {
                sb.append("  - ").append(name).append(": ");
                sb.append(RestartUtil.formatDuration(task.getSecondsUntilRestart()));
                sb.append(" (").append(task.getState()).append(")\n");
            });
        }
        
        return sb.toString();
    }
}