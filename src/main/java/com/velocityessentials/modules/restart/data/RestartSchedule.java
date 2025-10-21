package com.velocityessentials.modules.restart.data;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.*;

/**
 * Represents a complete restart schedule configuration
 */
public class RestartSchedule {
    private final String name;
    private final boolean enabled;
    private final Set<String> servers;
    private final ScheduleTime scheduleTime;
    private final List<Integer> warningIntervals; //seconds
    private final String warningSound;
    private final List<ScheduledCommand> commands;
    private final int minPlayersDelay; 
    
    private RestartSchedule(Builder builder) {
        this.name = builder.name;
        this.enabled = builder.enabled;
        this.servers = Set.copyOf(builder.servers);
        this.scheduleTime = builder.scheduleTime;
        this.warningIntervals = List.copyOf(builder.warningIntervals);
        this.warningSound = builder.warningSound;
        this.commands = List.copyOf(builder.commands);
        this.minPlayersDelay = builder.minPlayersDelay;
    }
    
    // Getters
    public String getName() { return name; }
    public boolean isEnabled() { return enabled; }
    public Set<String> getServers() { return servers; }
    public ScheduleTime getScheduleTime() { return scheduleTime; }
    public List<Integer> getWarningIntervals() { return warningIntervals; }
    public String getWarningSound() { return warningSound; }
    public List<ScheduledCommand> getCommands() { return commands; }
    public int getMinPlayersDelay() { return minPlayersDelay; }

    public boolean appliesTo(String serverName) {
        return servers.contains(serverName);
    }
    
    // commands to run pre restart
    public List<ScheduledCommand> getBeforeCommands() {
        return commands.stream()
            .filter(cmd -> cmd.getTiming() == CommandTiming.BEFORE)
            .sorted(Comparator.comparingInt(ScheduledCommand::getDelay).reversed())
            .toList();
    }
    
    // commands to run post restart
    public List<ScheduledCommand> getAfterCommands() {
        return commands.stream()
            .filter(cmd -> cmd.getTiming() == CommandTiming.AFTER)
            .sorted(Comparator.comparingInt(ScheduledCommand::getDelay))
            .toList();
    }
    
    // validate config
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        
        if (name == null || name.isEmpty()) {
            errors.add("Schedule name cannot be empty");
        }
        
        if (servers.isEmpty()) {
            errors.add("Schedule must specify at least one server");
        }
        
        if (scheduleTime == null) {
            errors.add("Schedule time cannot be null");
        }
        
        if (warningIntervals.isEmpty()) {
            errors.add("Schedule should have at least one warning interval");
        }
        
        // check for duplicate warning times
        Set<Integer> uniqueIntervals = new HashSet<>(warningIntervals);
        if (uniqueIntervals.size() != warningIntervals.size()) {
            errors.add("Duplicate warning intervals detected");
        }
        
        return errors;
    }
    
    @Override
    public String toString() {
        return "RestartSchedule{" +
                "name='" + name + '\'' +
                ", enabled=" + enabled +
                ", servers=" + servers +
                ", time=" + scheduleTime +
                '}';
    }
    
    // ===== BUILDER =====
    
    public static Builder builder(String name) {
        return new Builder(name);
    }
    
    public static class Builder {
        private final String name;
        private boolean enabled = true;
        private Set<String> servers = new HashSet<>();
        private ScheduleTime scheduleTime;
        private List<Integer> warningIntervals = new ArrayList<>();
        private String warningSound = "ENTITY_EXPERIENCE_ORB_PICKUP";
        private List<ScheduledCommand> commands = new ArrayList<>();
        private int minPlayersDelay = 0;
        
        private Builder(String name) {
            this.name = name;
        }
        
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }
        
        public Builder addServer(String server) {
            this.servers.add(server);
            return this;
        }
        
        public Builder servers(Collection<String> servers) {
            this.servers.addAll(servers);
            return this;
        }
        
        public Builder time(String timeString, Set<DayOfWeek> days, ZoneId timezone) {
            this.scheduleTime = new ScheduleTime(timeString, days, timezone);
            return this;
        }
        
        public Builder scheduleTime(ScheduleTime scheduleTime) {
            this.scheduleTime = scheduleTime;
            return this;
        }
        
        public Builder addWarningInterval(int seconds) {
            this.warningIntervals.add(seconds);
            return this;
        }
        
        public Builder warningIntervals(List<Integer> intervals) {
            this.warningIntervals = new ArrayList<>(intervals);
            return this;
        }
        
        public Builder warningSound(String sound) {
            this.warningSound = sound;
            return this;
        }
        
        public Builder addCommand(ScheduledCommand command) {
            this.commands.add(command);
            return this;
        }
        
        public Builder commands(List<ScheduledCommand> commands) {
            this.commands = new ArrayList<>(commands);
            return this;
        }
        
        public Builder minPlayersDelay(int minPlayers) {
            this.minPlayersDelay = minPlayers;
            return this;
        }
        
        public RestartSchedule build() {
            return new RestartSchedule(this);
        }
    }
    
    // ===== SCHEDULED COMMAND =====
    
    public static class ScheduledCommand {
        private final String command;
        private final int delay; // seconds before/after restart
        private final CommandTiming timing;
        private final CommandTarget target;
        
        public ScheduledCommand(String command, int delay, CommandTiming timing, CommandTarget target) {
            this.command = command;
            this.delay = delay;
            this.timing = timing;
            this.target = target;
        }
        
        public String getCommand() { return command; }
        public int getDelay() { return delay; }
        public CommandTiming getTiming() { return timing; }
        public CommandTarget getTarget() { return target; }
        
        @Override
        public String toString() {
            return String.format("[%s %ds] %s on %s", 
                timing, delay, command, target);
        }
    }
    
    public enum CommandTiming {
        BEFORE,  // run X seconds before restart
        AFTER    // run X seconds after restart
    }
    
    public enum CommandTarget {
        SERVER,  // run as console command on backend server
        PROXY    // run as proxy console command
    }
}