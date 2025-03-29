package me.opaque.genstools.manager;

import me.opaque.genstools.GensTools;
import me.opaque.genstools.enchants.CustomEnchant;
import me.opaque.genstools.enchants.EnchantFactory;
import me.opaque.genstools.tools.types.GensPickaxe;
import me.opaque.genstools.tools.types.GensSword;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class ConfigManager {
    private final GensTools plugin;
    private FileConfiguration config;
    private FileConfiguration toolsConfig;
    public FileConfiguration enchantsConfig;

    public ConfigManager(GensTools plugin) {
        this.plugin = plugin;
        setupConfig();
        setupToolsConfig();
        setupEnchantsConfig();
        loadConfigs();
    }

    private void setupConfig() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);
    }

    private void setupToolsConfig() {
        File toolsFile = new File(plugin.getDataFolder(), "tools.yml");
        if (!toolsFile.exists()) {
            plugin.saveResource("tools.yml", false);
        }

        toolsConfig = YamlConfiguration.loadConfiguration(toolsFile);
    }

    private void setupEnchantsConfig() {
        File enchantsFile = new File(plugin.getDataFolder(), "enchants.yml");
        if (!enchantsFile.exists()) {
            plugin.saveResource("enchants.yml", false);
        }

        enchantsConfig = YamlConfiguration.loadConfiguration(enchantsFile);
    }

    public void loadConfigs() {
        loadTools();
        loadEnchants();
    }

    private void loadTools() {
        FileConfiguration config = toolsConfig;
        ConfigurationSection toolsSection = config.getConfigurationSection("tools");

        if (toolsSection == null) {
            plugin.getLogger().warning("No tools section found in tools.yml");
            return;
        }

        for (String toolId : toolsSection.getKeys(false)) {
            ConfigurationSection toolSection = toolsSection.getConfigurationSection(toolId);
            if (toolSection == null) continue;

            String displayName = ChatColor.translateAlternateColorCodes('&',
                    toolSection.getString("display-name", toolId));
            String type = toolSection.getString("type", "PICKAXE").toUpperCase();

            List<String> lore = toolSection.getStringList("lore");

            if (type.equals("PICKAXE")) {
                GensPickaxe pickaxe = new GensPickaxe(toolId, displayName, lore);
                plugin.getToolManager().registerTool(pickaxe);
                plugin.getLogger().info("Registered pickaxe: " + toolId);
            } else if (type.equals("SWORD")) {
                GensSword sword = new GensSword(toolId, displayName, lore);
                plugin.getToolManager().registerTool(sword);
                plugin.getLogger().info("Registered sword: " + toolId);
            } else {
                plugin.getLogger().warning("Unknown tool type: " + type + " for tool: " + toolId);
            }
        }
    }

    private void loadEnchants() {
        FileConfiguration config = enchantsConfig;
        ConfigurationSection enchantsSection = config.getConfigurationSection("enchants");

        if (enchantsSection == null) {
            plugin.getLogger().warning("No enchants section found in enchants.yml");
            return;
        }

        for (String enchantId : enchantsSection.getKeys(false)) {
            ConfigurationSection enchantSection = enchantsSection.getConfigurationSection(enchantId);
            if (enchantSection == null) continue;

            String displayName = ChatColor.translateAlternateColorCodes('&',
                    enchantSection.getString("display-name", enchantId));
            String description = ChatColor.translateAlternateColorCodes('&',
                    enchantSection.getString("description", ""));
            int maxLevel = enchantSection.getInt("max-level", 5);
            boolean isTreasure = enchantSection.getBoolean("is-treasure", false);
            String type = enchantSection.getString("type", "default").toLowerCase();

            // Added: Read the currency type from config
            String currencyStr = enchantSection.getString("currency", "SHARDS").toUpperCase();
            CustomEnchant.CurrencyType currencyType;
            try {
                currencyType = CustomEnchant.CurrencyType.valueOf(currencyStr);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid currency type '" + currencyStr + "' for enchantment " +
                        enchantId + ". Defaulting to SHARDS.");
                currencyType = CustomEnchant.CurrencyType.SHARDS;
            }

            // Updated: Pass the currency type to the factory
            CustomEnchant enchant = EnchantFactory.createEnchant(
                    type, enchantId, displayName, description, maxLevel, isTreasure, currencyType);

            if (enchant != null) {
                plugin.getToolManager().registerEnchant(enchant);
                plugin.getLogger().info("Registered enchant: " + enchantId + " with currency: " + currencyType);
            } else {
                plugin.getLogger().warning("Unknown enchant type: " + type + " for enchant: " + enchantId);
            }
        }
    }

    /**
     * Gets the experience value for breaking a specific block type
     *
     * @param material The block material
     * @return The experience value
     */
    public int getBlockExpValue(Material material) {
        String materialName = material.name();
        return config.getInt("tool-settings.pickaxe.exp-values." + materialName, 0);
    }

    /**
     * Gets the bonus experience for a specific entity type
     *
     * @param entityType The entity type
     * @return The bonus experience
     */
    public int getMobExpBonus(EntityType entityType) {
        String entityName = entityType.name();
        return config.getInt("tool-settings.sword.mob-bonuses." + entityName, 0);
    }

    /**
     * Checks if level up messages should be shown
     *
     * @return true if level up messages should be shown
     */
    public boolean isShowLevelUpMessages() {
        return config.getBoolean("leveling.show-messages", true);
    }

    /**
     * Checks if level up effects should be shown
     *
     * @return true if level up effects should be shown
     */
    public boolean isShowLevelUpEffects() {
        return config.getBoolean("leveling.show-effects", true);
    }

    /**
     * Gets the level up message from the config
     *
     * @return The level up message
     */
    public String getLevelUpMessage() {
        return config.getString("leveling.message", "&aYour tool leveled up to &e{level}&a!");
    }

    /**
     * Reloads all configuration files
     */
    public void reloadConfigs() {
        reloadMainConfig();
        reloadToolsConfig();
        reloadEnchantsConfig();
        loadConfigs();

        plugin.getMenuManager().loadConfig();
        plugin.getLoreManager().reloadConfig();
    }

    private void reloadMainConfig() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    private void reloadToolsConfig() {
        File toolsFile = new File(plugin.getDataFolder(), "tools.yml");
        toolsConfig = YamlConfiguration.loadConfiguration(toolsFile);
    }

    private void reloadEnchantsConfig() {
        File enchantsFile = new File(plugin.getDataFolder(), "enchants.yml");
        enchantsConfig = YamlConfiguration.loadConfiguration(enchantsFile);
    }

    /**
     * Gets the global experience multiplier
     *
     * @return The global experience multiplier
     */
    public double getGlobalExpMultiplier() {
        return config.getDouble("leveling.global-exp-multiplier", 1.0);
    }

    /**
     * Gets the maximum level tools can reach
     *
     * @return The maximum level
     */
    public int getMaxLevel() {
        return config.getInt("leveling.max-level", 100);
    }

    /**
     * Gets whether to show an exp action bar message
     *
     * @return true if action bar messages should be shown
     */
    public boolean isShowExpActionBar() {
        return config.getBoolean("leveling.show-exp-actionbar", true);
    }

    /**
     * Check if numeric enchant display is enabled
     *
     * @return true if numeric display is enabled
     */
    public boolean useNumericEnchantDisplay() {
        return config.getBoolean("enchants.use-numbers", true);
    }

    /**
     * Get the maximum enchantment level allowed
     *
     * @return The maximum enchantment level
     */
    public int getMaxEnchantmentLevel() {
        return config.getInt("enchants.max-level", Integer.MAX_VALUE);
    }

    public FileConfiguration getEnchantsConfig() {
        return enchantsConfig;
    }

    public FileConfiguration getToolsConfig() {
        return toolsConfig;
    }

    public FileConfiguration getConfig() {
        return config;
    }

    /**
     * Get whether to show experience gain messages
     * @return true if exp gain messages should be shown
     */
    public boolean isShowExpGainMessages() {
        return config.getBoolean("experience.show-exp-gain-messages", true);
    }

    /**
     * Get the base kill experience per health point
     * @return the base kill experience per health point
     */
    public int getBaseKillExpPerHealth() {
        return config.getInt("experience.base-kill-exp-per-health", 5);
    }

    /**
     * Get the bonus experience for killing a boss/named entity
     * @return the boss kill bonus experience
     */
    public int getBossKillBonus() {
        return config.getInt("experience.boss-kill-bonus", 500);
    }

    /**
     * Get the multiplier for a specific kill type
     * @param type the kill type (melee, ranged, magic)
     * @return the multiplier
     */
    public double getKillTypeMultiplier(String type) {
        return config.getDouble("experience.kill-multipliers." + type, 1.0);
    }
}