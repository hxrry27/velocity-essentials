package com.velocityessentials.modules.restart.data;

import java.time.Instant;

/**
 * stores information about why a restart was delayed or cancelled
 */
public class RestartReason {
    private final String reason;
    private final String requestedBy;
    private final Instant timestamp;
    private final Type type;
    
    public enum Type {
        DELAY,
        CANCEL,
        MANUAL
    }
    
    public RestartReason(String reason, String requestedBy, Type type) {
        this.reason = reason;
        this.requestedBy = requestedBy;
        this.timestamp = Instant.now();
        this.type = type;
    }

    public String getReason() {
        return reason;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Type getType() {
        return type;
    }

    public String getFormattedMessage() {
        StringBuilder sb = new StringBuilder();
        
        switch (type) {
            case DELAY -> sb.append("Restart delayed");
            case CANCEL -> sb.append("Restart cancelled");
            case MANUAL -> sb.append("Manual restart initiated");
        }
        
        sb.append(" by ").append(requestedBy);
        
        if (reason != null && !reason.isEmpty()) {
            sb.append(": ").append(reason);
        }
        
        return sb.toString();
    }

    @Override
    public String toString() {
        return getFormattedMessage();
    }
}