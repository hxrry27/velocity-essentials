package com.velocityessentials.backend.chat;

public class Channel {
    
    private final String id;
    private final String name;
    private final boolean enabled;
    private final String permission;
    private final boolean permissionRequired;
    private final boolean crossServer;
    private final String format;
    private final String receivePermission;
    
    private Channel(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.enabled = builder.enabled;
        this.permission = builder.permission;
        this.permissionRequired = builder.permissionRequired;
        this.crossServer = builder.crossServer;
        this.format = builder.format;
        this.receivePermission = builder.receivePermission;
    }
    
    // getters
    public String getId() { return id; }
    public String getName() { return name; }
    public boolean isEnabled() { return enabled; }
    public String getPermission() { return permission; }
    public boolean isPermissionRequired() { return permissionRequired; }
    public boolean isCrossServer() { return crossServer; }
    public String getFormat() { return format; }
    public String getReceivePermission() { return receivePermission; }
    

     // check if this channel has a receive permission (who can see messages).

    public boolean hasReceivePermission() {
        return receivePermission != null && !receivePermission.isEmpty();
    }
    
    @Override
    public String toString() {
        return String.format("Channel{id=%s, name=%s, crossServer=%s, permissionRequired=%s}",
            id, name, crossServer, permissionRequired);
    }
    
    // builder pattern
    public static Builder builder(String id) {
        return new Builder(id);
    }
    
    public static class Builder {
        private final String id;
        private String name;
        private boolean enabled = true;
        private String permission;
        private boolean permissionRequired = false;
        private boolean crossServer = true;
        private String format = "{display}: {message}";
        private String receivePermission;
        
        public Builder(String id) {
            this.id = id;
            this.name = id; // default name to id
        }
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }
        
        public Builder permission(String permission) {
            this.permission = permission;
            return this;
        }
        
        public Builder permissionRequired(boolean required) {
            this.permissionRequired = required;
            return this;
        }
        
        public Builder crossServer(boolean crossServer) {
            this.crossServer = crossServer;
            return this;
        }
        
        public Builder format(String format) {
            this.format = format;
            return this;
        }
        
        public Builder receivePermission(String receivePermission) {
            this.receivePermission = receivePermission;
            return this;
        }
        
        public Channel build() {
            return new Channel(this);
        }
    }
}