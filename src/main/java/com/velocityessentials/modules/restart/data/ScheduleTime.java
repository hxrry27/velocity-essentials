package com.velocityessentials.modules.restart.data;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/**
 * represents a scheduled time for server restarts
 * handles time parsing, timezone conversion, and next occurrence calculation
 */
public class ScheduleTime {
    private final LocalTime time;
    private final Set<DayOfWeek> days;
    private final ZoneId timezone;
    
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    
    /**
     * create a schedule time
     * @param timeString time in HH:mm format (24-hour)
     * @param days set of days this schedule applies to (empty = all days)
     * @param timezone timezone for the schedule
     */
    public ScheduleTime(String timeString, Set<DayOfWeek> days, ZoneId timezone) {
        this.time = LocalTime.parse(timeString, TIME_FORMAT);
        this.days = days;
        this.timezone = timezone;
    }
    
    /**
     * calculate the next occurrence of this scheduled time
     * @return
     */
    public ZonedDateTime getNextOccurrence() {
        ZonedDateTime now = ZonedDateTime.now(timezone);
        ZonedDateTime scheduled = now.with(time);
        
        // if the time has passed today, start from tomorrow
        if (scheduled.isBefore(now) || scheduled.isEqual(now)) {
            scheduled = scheduled.plusDays(1);
        }
        
        // if specific days are set, find the next valid day
        if (!days.isEmpty()) {
            while (!days.contains(scheduled.getDayOfWeek())) {
                scheduled = scheduled.plusDays(1);
            }
        }
        
        return scheduled;
    }
    
    /**
     * calculate seconds until the next restart
     * @return seconds until next occurrence
     */
    public long getSecondsUntilNext() {
        ZonedDateTime now = ZonedDateTime.now(timezone);
        ZonedDateTime next = getNextOccurrence();
        return ChronoUnit.SECONDS.between(now, next);
    }

    public boolean runsOnDay(DayOfWeek day) {
        return days.isEmpty() || days.contains(day);
    }

    public String getFormattedTimeUntil() {
        long seconds = getSecondsUntilNext();
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        
        if (hours > 24) {
            long days = hours / 24;
            return "in " + days + " day" + (days != 1 ? "s" : "");
        } else if (hours > 0) {
            if (minutes > 0) {
                return "in " + hours + " hour" + (hours != 1 ? "s" : "") + " " + minutes + " minute" + (minutes != 1 ? "s" : "");
            } else {
                return "in " + hours + " hour" + (hours != 1 ? "s" : "");
            }
        } else if (minutes > 0) {
            return "in " + minutes + " minute" + (minutes != 1 ? "s" : "");
        } else {
            return "in " + seconds + " second" + (seconds != 1 ? "s" : "");
        }
    }

    public LocalTime getTime() {
        return time;
    }

    public Set<DayOfWeek> getDays() {
        return days;
    }

    public ZoneId getTimezone() {
        return timezone;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(time.format(TIME_FORMAT));
        
        if (!days.isEmpty()) {
            sb.append(" on ");
            if (days.size() == 7) {
                sb.append("every day");
            } else if (days.size() == 1) {
                sb.append(days.iterator().next());
            } else {
                sb.append(days.toString());
            }
        }
        
        sb.append(" (").append(timezone.getId()).append(")");
        return sb.toString();
    }
}