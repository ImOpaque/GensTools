package me.opaque.genstools.persistence;

import me.opaque.genstools.GensTools;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;

/**
 * YAML-based storage implementation for tool data
 */
public class YamlStorage implements Storage {
    private final GensTools plugin;
    private final File dataFolder;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");

    public YamlStorage(GensTools plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "data");

        // Create data folder if it doesn't exist
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }

    @Override
    public void savePlayerData(PlayerToolData data) {
        // Create YAML configuration
        YamlConfiguration config = new YamlConfiguration();

        // Store player UUID
        config.set("player-uuid", data.getPlayerUuid().toString());

        // Store last save time
        config.set("last-saved", System.currentTimeMillis());

        // Create tools section
        ConfigurationSection toolsSection = config.createSection("tools");

        // Save each tool
        for (SavedToolData tool : data.getTools()) {
            // Use unique ID as section key
            ConfigurationSection toolSection = toolsSection.createSection(tool.getUniqueId());

            // Save tool properties
            toolSection.set("tool-id", tool.getToolId());
            toolSection.set("level", tool.getLevel());
            toolSection.set("experience", tool.getExperience());

            // Save enchantments
            ConfigurationSection enchantsSection = toolSection.createSection("enchantments");
            for (Map.Entry<String, Integer> entry : tool.getEnchantments().entrySet()) {
                enchantsSection.set(entry.getKey(), entry.getValue());
            }
        }

        // Save the file
        try {
            File playerFile = getPlayerFile(data.getPlayerUuid());
            config.save(playerFile);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save player tool data: " + data.getPlayerUuid(), e);
        }
    }

    @Override
    public PlayerToolData loadPlayerData(UUID playerUuid) {
        File playerFile = getPlayerFile(playerUuid);

        // Check if file exists
        if (!playerFile.exists()) {
            // Return new empty data
            return new PlayerToolData(playerUuid);
        }

        // Load YAML configuration
        YamlConfiguration config = YamlConfiguration.loadConfiguration(playerFile);

        // Create player data
        PlayerToolData playerData = new PlayerToolData(playerUuid);

        // Load tools
        ConfigurationSection toolsSection = config.getConfigurationSection("tools");
        if (toolsSection != null) {
            for (String uniqueId : toolsSection.getKeys(false)) {
                ConfigurationSection toolSection = toolsSection.getConfigurationSection(uniqueId);
                if (toolSection == null) continue;

                // Load tool properties
                String toolId = toolSection.getString("tool-id");
                int level = toolSection.getInt("level", 1);
                int experience = toolSection.getInt("experience", 0);

                // Load enchantments
                Map<String, Integer> enchantments = new HashMap<>();
                ConfigurationSection enchantsSection = toolSection.getConfigurationSection("enchantments");
                if (enchantsSection != null) {
                    for (String enchantId : enchantsSection.getKeys(false)) {
                        int enchantLevel = enchantsSection.getInt(enchantId, 1);
                        enchantments.put(enchantId, enchantLevel);
                    }
                }

                // Create tool data and add to player data
                SavedToolData toolData = new SavedToolData(uniqueId, toolId, level, experience, enchantments);
                playerData.addOrUpdateTool(toolData);
            }
        }

        return playerData;
    }

    @Override
    public boolean createBackup() {
        try {
            // Create backup folder if it doesn't exist
            File backupFolder = new File(plugin.getDataFolder(), "backups");
            if (!backupFolder.exists()) {
                backupFolder.mkdirs();
            }

            // Create timestamp folder for this backup
            String timestamp = dateFormat.format(new Date());
            File backupTimestampFolder = new File(backupFolder, timestamp);
            backupTimestampFolder.mkdirs();

            // Copy all player files to backup
            File[] playerFiles = dataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
            if (playerFiles != null) {
                for (File playerFile : playerFiles) {
                    YamlConfiguration config = YamlConfiguration.loadConfiguration(playerFile);
                    File backupFile = new File(backupTimestampFolder, playerFile.getName());
                    config.save(backupFile);
                }
            }

            plugin.getLogger().info("Created tool data backup: " + timestamp);
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create tool data backup", e);
            return false;
        }
    }

    /**
     * Get the file for a player's data
     * @param playerUuid The player UUID
     * @return The file
     */
    private File getPlayerFile(UUID playerUuid) {
        return new File(dataFolder, playerUuid.toString() + ".yml");
    }
}