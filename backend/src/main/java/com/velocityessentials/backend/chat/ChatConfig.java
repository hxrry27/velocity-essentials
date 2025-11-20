package com.velocityessentials.backend.chat;

import com.velocityessentials.backend.VelocityEssentialsBackend;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;

/**
 * loads and manages chat configuration from main config.yml.
 * handles chat formatting, permissions, and placeholderapi integration.
 */
public class ChatConfig {
    
    private final VelocityEssentialsBackend plugin;
    
    // chat settings
    private boolean enabled;
    private boolean sendToVelocity;
    private boolean usePlaceholderApi;
    private String displayFormat;
    private Map<String, String> placeholders;
    
    // permission settings
    private String colorPermission;
    private boolean colorDefault;
    private String gradientPermission;
    private boolean gradientDefault;
    private String rainbowPermission;
    private boolean rainbowDefault;
    private String minimessagePermission;
    private boolean minimessageDefault;
    
    public ChatConfig(VelocityEssentialsBackend plugin) {
        this.plugin = plugin;
        this.placeholders = new HashMap<>();
        load();
    }
    
    public void load() {
        ConfigurationSection chatSection = plugin.getConfig().getConfigurationSection("chat");
        if (chatSection == null) {
            plugin.getLogger().warning("chat section not found in config.yml - using defaults");
            setDefaults();
            return;
        }
        
        enabled = chatSection.getBoolean("enabled", true);
        sendToVelocity = chatSection.getBoolean("send-to-velocity", true);
        usePlaceholderApi = chatSection.getBoolean("use-placeholderapi", true);
        displayFormat = chatSection.getString("display-format", "{prefix}{name}{suffix}");
        
        // load placeholders
        ConfigurationSection placeholdersSection = chatSection.getConfigurationSection("placeholders");
        if (placeholdersSection != null) {
            placeholders.clear();
            for (String key : placeholdersSection.getKeys(false)) {
                placeholders.put(key, placeholdersSection.getString(key));
            }
        } else {
            // defaults
            placeholders.put("prefix", "%vault_prefix%");
            placeholders.put("name", "%player_name%");
            placeholders.put("suffix", "%vault_suffix%");
        }
        
        // load permissions
        ConfigurationSection perms = chatSection.getConfigurationSection("permissions");
        if (perms != null) {
            loadPermissionSection(perms, "colors");
            loadPermissionSection(perms, "gradients");
            loadPermissionSection(perms, "rainbow");
            loadPermissionSection(perms, "minimessage");
        }
    }
    
    private void loadPermissionSection(ConfigurationSection perms, String name) {
        ConfigurationSection section = perms.getConfigurationSection(name);
        if (section != null) {
            String perm = section.getString("permission");
            boolean def = section.getBoolean("default");
            
            switch (name) {
                case "colors" -> {
                    colorPermission = perm;
                    colorDefault = def;
                }
                case "gradients" -> {
                    gradientPermission = perm;
                    gradientDefault = def;
                }
                case "rainbow" -> {
                    rainbowPermission = perm;
                    rainbowDefault = def;
                }
                case "minimessage" -> {
                    minimessagePermission = perm;
                    minimessageDefault = def;
                }
            }
        }
    }
    
    private void setDefaults() {
        enabled = true;
        sendToVelocity = true;
        usePlaceholderApi = true;
        displayFormat = "{prefix}{name}{suffix}";
        placeholders.put("prefix", "%vault_prefix%");
        placeholders.put("name", "%player_name%");
        placeholders.put("suffix", "%vault_suffix%");
        colorPermission = "velocityessentials.chat.color";
        colorDefault = true;
        gradientPermission = "velocityessentials.chat.gradient";
        gradientDefault = false;
        rainbowPermission = "velocityessentials.chat.rainbow";
        rainbowDefault = false;
        minimessagePermission = "velocityessentials.chat.minimessage";
        minimessageDefault = false;
    }
    
    public void reload() {
        load();
    }
    
    // getters
    public boolean isEnabled() { return enabled; }
    public boolean isSendToVelocity() { return sendToVelocity; }
    public boolean isUsePlaceholderApi() { return usePlaceholderApi; }
    public String getDisplayFormat() { return displayFormat; }
    public Map<String, String> getPlaceholders() { return placeholders; }
    
    public String getColorPermission() { return colorPermission; }
    public boolean isColorDefault() { return colorDefault; }
    public String getGradientPermission() { return gradientPermission; }
    public boolean isGradientDefault() { return gradientDefault; }
    public String getRainbowPermission() { return rainbowPermission; }
    public boolean isRainbowDefault() { return rainbowDefault; }
    public String getMinimessagePermission() { return minimessagePermission; }
    public boolean isMinimessageDefault() { return minimessageDefault; }
}