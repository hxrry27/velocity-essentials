package com.velocityessentials.modules.restart;

import com.velocityessentials.VelocityEssentials;
import com.velocityessentials.modules.restart.data.RestartReason;
import com.velocityessentials.modules.restart.data.RestartSchedule;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * represents an active restart countdown
 * manages warnings, commands, and execution
 */
public class RestartTask {
    private final VelocityEssentials plugin;
    private final RestartSchedule schedule;
    private final RestartMessenger messenger;
    
    private Instant scheduledTime;
    private RestartReason delayReason;
    private TaskState state = TaskState.PENDING;
    
    private final Map<Integer, ScheduledTask> warningTasks = new HashMap<>();
    private final Map<String, ScheduledTask> commandTasks = new HashMap<>();
    private ScheduledTask shutdownTask;
    private ScheduledTask delayCheckTask;
    
    public RestartTask(VelocityEssentials plugin, RestartSchedule schedule, Instant scheduledTime) {
        this.plugin = plugin;
        this.schedule = schedule;
        this.scheduledTime = scheduledTime;
        this.messenger = new RestartMessenger(plugin);
    }
    
    public void start() {
        if (state != TaskState.PENDING) {
            plugin.getLogger().warn("Attempted to start task that is already " + state);
            return;
        }
        
        state = TaskState.ACTIVE;
        
        long secondsUntilRestart = getSecondsUntilRestart();
        
        if (secondsUntilRestart <= 0) {
            executeRestart();
            return;
        }
        
        // Schedule warnings
        scheduleWarnings(secondsUntilRestart);
        
        // Schedule pre-restart commands
        scheduleBeforeCommands(secondsUntilRestart);
        
        scheduleShutdown(secondsUntilRestart);
        
        if (schedule.getMinPlayersDelay() > 0) {
            startDelayChecker();
        }
        
        plugin.getLogger().info("Started restart task for schedule: " + schedule.getName() + 
                              " (restart in " + RestartUtil.formatDuration(secondsUntilRestart) + ")");
    }
    
    private void scheduleWarnings(long secondsUntilRestart) {
        for (int warningInterval : schedule.getWarningIntervals()) {
            long delay = secondsUntilRestart - warningInterval;
            
            if (delay < 0) continue;
            
            ScheduledTask task = plugin.getServer().getScheduler()
                .buildTask(plugin, () -> sendWarning(warningInterval))
                .delay(delay, TimeUnit.SECONDS)
                .schedule();
            
            warningTasks.put(warningInterval, task);
        }
    }
    
    private void scheduleBeforeCommands(long secondsUntilRestart) {
        for (RestartSchedule.ScheduledCommand cmd : schedule.getBeforeCommands()) {
            long delay = secondsUntilRestart - cmd.getDelay();
            
            if (delay < 0) continue;
            
            ScheduledTask task = plugin.getServer().getScheduler()
                .buildTask(plugin, () -> executeScheduledCommand(cmd))
                .delay(delay, TimeUnit.SECONDS)
                .schedule();
            
            commandTasks.put("before_" + cmd.getDelay() + "_" + cmd.getCommand(), task);
        }
    }
    
    private void scheduleShutdown(long secondsUntilRestart) {
        shutdownTask = plugin.getServer().getScheduler()
            .buildTask(plugin, this::executeRestart)
            .delay(secondsUntilRestart, TimeUnit.SECONDS)
            .schedule();
    }
    
    private void startDelayChecker() {
        delayCheckTask = plugin.getServer().getScheduler()
            .buildTask(plugin, this::checkPlayerCountDelay)
            .repeat(plugin.getConfig().getAutoRestartDelayCheckInterval(), TimeUnit.SECONDS)
            .schedule();
    }
    
    private void checkPlayerCountDelay() {
        long secondsLeft = getSecondsUntilRestart();
        
        if (secondsLeft > 300) return;
        
        int totalPlayers = getAffectedServers().stream()
            .mapToInt(s -> s.getPlayersConnected().size())
            .sum();
        
        if (totalPlayers > schedule.getMinPlayersDelay()) {
            delay(300, new RestartReason(
                totalPlayers + " players online (min: " + schedule.getMinPlayersDelay() + ")",
                "System",
                RestartReason.Type.DELAY
            ));
        }
    }
    
    private void sendWarning(int secondsLeft) {
        if (state != TaskState.ACTIVE) return;
        
        Collection<RegisteredServer> servers = getAffectedServers();
        messenger.sendWarning(servers, secondsLeft, schedule.getWarningSound());
        
        Component message = RestartUtil.createWarningMessage(
            String.join(", ", schedule.getServers()),
            secondsLeft
        );
        
        servers.forEach(server -> 
            server.getPlayersConnected().forEach(player -> player.sendMessage(message))
        );
        
        if (plugin.getConfig().isDebug()) {
            plugin.getLogger().info("Sent " + secondsLeft + "s warning for " + schedule.getName());
        }
    }
    
    private void executeScheduledCommand(RestartSchedule.ScheduledCommand cmd) {
        if (state != TaskState.ACTIVE) return;
        
        Collection<RegisteredServer> servers = getAffectedServers();
        
        switch (cmd.getTarget()) {
            case SERVER -> {
                for (RegisteredServer server : servers) {
                    messenger.sendCommand(server, cmd.getCommand());
                }
            }
            case PROXY -> {
                try {
                    plugin.getServer().getCommandManager().executeAsync(
                        plugin.getServer().getConsoleCommandSource(),
                        cmd.getCommand()
                    );
                } catch (Exception e) {
                    plugin.getLogger().error("Failed to execute proxy command: " + cmd.getCommand(), e);
                }
            }
        }
        
        if (plugin.getConfig().isDebug()) {
            plugin.getLogger().info("Executed command: " + cmd);
        }
    }
    
    private void executeRestart() {
        if (state != TaskState.ACTIVE) return;
        
        state = TaskState.EXECUTING;
        
        plugin.getLogger().info("Executing restart for: " + schedule.getName());
        
        Collection<RegisteredServer> servers = getAffectedServers();
        
        for (RegisteredServer server : servers) {
            messenger.sendShutdown(server);
        }
        
        
        scheduleAfterCommands();
        
        state = TaskState.COMPLETED;
    }
    
    private void scheduleAfterCommands() {
        for (RestartSchedule.ScheduledCommand cmd : schedule.getAfterCommands()) {
            plugin.getServer().getScheduler()
                .buildTask(plugin, () -> executeScheduledCommand(cmd))
                .delay(cmd.getDelay(), TimeUnit.SECONDS)
                .schedule();
        }
    }

    public void delay(long additionalSeconds, RestartReason reason) {
        if (state != TaskState.ACTIVE) {
            plugin.getLogger().warn("Cannot delay task in state: " + state);
            return;
        }
        
        cancelAllTasks();
        
        scheduledTime = Instant.now().plusSeconds(additionalSeconds);
        delayReason = reason;
        
        start();

        Component message = RestartUtil.createDelayedMessage(
            String.join(", ", schedule.getServers()),
            RestartUtil.formatDuration(additionalSeconds),
            reason.getReason()
        );
        
        getAffectedServers().forEach(server ->
            server.getPlayersConnected().forEach(player -> player.sendMessage(message))
        );
        
        plugin.getLogger().info("Delayed restart: " + reason);
    }

    public void cancel(RestartReason reason) {
        if (state == TaskState.CANCELLED || state == TaskState.COMPLETED) {
            return;
        }
        
        state = TaskState.CANCELLED;
        
        cancelAllTasks();
        

        messenger.sendCancel(getAffectedServers(), reason.getReason());

        Component message = RestartUtil.createCancelledMessage(
            String.join(", ", schedule.getServers()),
            reason.getReason()
        );
        
        getAffectedServers().forEach(server ->
            server.getPlayersConnected().forEach(player -> player.sendMessage(message))
        );
        
        plugin.getLogger().info("Cancelled restart: " + reason);
    }

    private void cancelAllTasks() {
        warningTasks.values().forEach(ScheduledTask::cancel);
        warningTasks.clear();
        
        commandTasks.values().forEach(ScheduledTask::cancel);
        commandTasks.clear();
        
        if (shutdownTask != null) {
            shutdownTask.cancel();
            shutdownTask = null;
        }
        
        if (delayCheckTask != null) {
            delayCheckTask.cancel();
            delayCheckTask = null;
        }
    }

    public long getSecondsUntilRestart() {
        return Instant.now().until(scheduledTime, java.time.temporal.ChronoUnit.SECONDS);
    }

    private Collection<RegisteredServer> getAffectedServers() {
        return schedule.getServers().stream()
            .map(name -> plugin.getServer().getServer(name))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }

    public RestartSchedule getSchedule() { return schedule; }
    public Instant getScheduledTime() { return scheduledTime; }
    public TaskState getState() { return state; }
    public RestartReason getDelayReason() { return delayReason; }

    public enum TaskState {
        PENDING,    // created but not started
        ACTIVE,     // countdown in progress
        EXECUTING,  // currently restarting servers
        COMPLETED,  // restart finished
        CANCELLED   // restart cancelled
    }
}