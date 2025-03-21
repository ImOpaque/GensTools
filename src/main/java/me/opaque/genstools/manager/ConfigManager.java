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
    private FileConfiguration enchantsConfig;

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

            CustomEnchant enchant = EnchantFactory.createEnchant(
                    type, enchantId, displayName, description, maxLevel, isTreasure);

            if (enchant != null) {
                plugin.getToolManager().registerEnchant(enchant);
                plugin.getLogger().info("Registered enchant: " + enchantId);
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
}