package me.opaque.genstools.utils;

import me.opaque.genstools.GensTools;
import me.opaque.genstools.enchants.CustomEnchant;
import me.opaque.genstools.tools.GensTool;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LoreManager {
    private final GensTools plugin;
    private FileConfiguration loreConfig;
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([^}]+)\\}");

    public LoreManager(GensTools plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    private void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), "lore.yml");
        if (!configFile.exists()) {
            plugin.saveResource("lore.yml", false);
        }
        loreConfig = YamlConfiguration.loadConfiguration(configFile);
    }

    public void reloadConfig() {
        File configFile = new File(plugin.getDataFolder(), "lore.yml");
        loreConfig = YamlConfiguration.loadConfiguration(configFile);
    }

    /**
     * Adds the applied cubes section to the lore
     */
    private void addCubesSection(List<String> lore, ItemStack item) {
        if (!loreConfig.getBoolean("cubes.enabled", true)) return;

        String header = loreConfig.getString("cubes.header", "&8❖ &7Applied Cubes:");
        String format = loreConfig.getString("cubes.format", " &8• &7{enchant_name} &a+{boost_value}%");
        String emptyValue = loreConfig.getString("cubes.empty-value", " &8• &7None");
        boolean addSpacer = loreConfig.getBoolean("cubes.add-spacer", true);

        // Get the ItemMeta of the tool
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer container = meta.getPersistentDataContainer();

        // Check if tool has any applied cubes
        NamespacedKey cubesKey = new NamespacedKey(plugin, "applied_cubes");
        if (!container.has(cubesKey, PersistentDataType.STRING)) {
            // No cubes applied, don't show section
            return;
        }

        String cubesData = container.get(cubesKey, PersistentDataType.STRING);
        if (cubesData == null || cubesData.isEmpty()) {
            return;
        }

        // Add header
        lore.add(colorize(header));

        // Add cube entries
        String[] cubes = cubesData.split(",");
        for (String cube : cubes) {
            String[] parts = cube.split(":");
            if (parts.length == 2) {
                String enchantId = parts[0];
                String boostText = parts[1];

                CustomEnchant enchant = plugin.getToolManager().getEnchantById(enchantId);
                String enchantName = enchant != null ? enchant.getDisplayName() : enchantId;

                Map<String, String> entryPlaceholders = new HashMap<>();
                entryPlaceholders.put("enchant_name", enchantName);
                entryPlaceholders.put("boost_value", boostText);

                lore.add(colorize(replacePlaceholders(format, entryPlaceholders)));
            }
        }

        // Add spacer if configured
        if (addSpacer) {
            lore.add("");
        }
    }

    /**
     * Updates the lore of a tool with all configured sections
     *
     * @param item The tool item to update
     */
    public void updateToolLore(ItemStack item) {
        if (!GensTool.isGensTool(item) || item.getItemMeta() == null) {
            return;
        }

        // Get tool data
        String toolId = GensTool.getToolId(item);
        if (toolId == null) return;

        GensTool toolPrototype = plugin.getToolManager().getToolById(toolId);
        if (toolPrototype == null) return;

        int level = GensTool.getLevel(item);
        int experience = GensTool.getExperience(item);
        int requiredExp = GensTool.calculateRequiredExp(level);
        Map<String, Integer> enchantments = GensTool.getEnchantments(item);

        // Build the new lore
        List<String> lore = new ArrayList<>();

        // Add custom lore from the tool prototype
        addCustomLore(lore, toolPrototype);

        // Add enchantments section
        addEnchantmentsSection(lore, enchantments);

        // Add cubes section
        addCubesSection(lore, item);

        // Add stats section with level and experience
        addStatsSection(lore, level, experience, requiredExp);

        // Add footer
        addFooter(lore, item);

        // Apply the new lore
        ItemMeta meta = item.getItemMeta();
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    /**
     * Adds the custom lore from the tool prototype
     */
    private void addCustomLore(List<String> lore, GensTool toolPrototype) {
        if (!loreConfig.getBoolean("tool-display.custom-lore.enabled", true)) return;

        List<String> customLore = toolPrototype.getLore();
        boolean colorize = loreConfig.getBoolean("tool-display.custom-lore.colorize", true);
        boolean addSpacer = loreConfig.getBoolean("tool-display.custom-lore.add-spacer", true);

        for (String line : customLore) {
            lore.add(colorize ? ChatColor.translateAlternateColorCodes('&', line) : line);
        }

        // Add spacer if configured and there was custom lore
        if (addSpacer && !customLore.isEmpty()) {
            lore.add("");
        }
    }

    /**
     * Adds the enchantments section to the lore
     */
    private void addEnchantmentsSection(List<String> lore, Map<String, Integer> enchantments) {
        if (!loreConfig.getBoolean("enchantments.enabled", true)) return;

        String header = loreConfig.getString("enchantments.header", "&8❖ &7Enchantments:");
        String format = loreConfig.getString("enchantments.format", " &8• &7{enchant_name} {enchant_level}");
        String emptyValue = loreConfig.getString("enchantments.empty-value", " &8• &7None");
        boolean addSpacer = loreConfig.getBoolean("enchantments.add-spacer", true);

        // Add header
        lore.add(colorize(header));

        // Add enchantment entries or empty value
        if (enchantments.isEmpty()) {
            lore.add(colorize(emptyValue));
        } else {
            for (Map.Entry<String, Integer> entry : enchantments.entrySet()) {
                CustomEnchant enchant = plugin.getToolManager().getEnchantById(entry.getKey());
                if (enchant == null) continue;

                Map<String, String> entryPlaceholders = new HashMap<>();
                entryPlaceholders.put("enchant_name", enchant.getDisplayName());
                entryPlaceholders.put("enchant_level", GensTool.formatEnchantmentLevel(entry.getValue()));

                lore.add(colorize(replacePlaceholders(format, entryPlaceholders)));
            }
        }

        // Add spacer if configured
        if (addSpacer) {
            lore.add("");
        }
    }

    /**
     * Adds the stats section with level and experience to the lore
     */
    private void addStatsSection(List<String> lore, int level, int experience, int requiredExp) {
        if (!loreConfig.getBoolean("stats.enabled", true)) return;

        String header = loreConfig.getString("stats.header", "&8❖ &7Stats:");
        String levelFormat = loreConfig.getString("stats.level-format", " &8• &7Level: &e{level}");
        String expFormat = loreConfig.getString("stats.exp-format",
                " &8• &7EXP: &b{current_exp}&7/&b{required_exp} &7(&b{exp_percentage}%&7)");
        boolean addSpacer = loreConfig.getBoolean("stats.add-spacer", true);

        // Calculate percentage for display
        int percentage = (requiredExp > 0) ? (int) ((float) experience / requiredExp * 100) : 0;

        // Add header
        lore.add(colorize(header));

        // Add level info
        Map<String, String> levelPlaceholders = new HashMap<>();
        levelPlaceholders.put("level", String.valueOf(level));
        lore.add(colorize(replacePlaceholders(levelFormat, levelPlaceholders)));

        // Add experience info
        Map<String, String> expPlaceholders = new HashMap<>();
        expPlaceholders.put("current_exp", formatNumber(experience));
        expPlaceholders.put("required_exp", formatNumber(requiredExp));
        expPlaceholders.put("exp_percentage", String.valueOf(percentage));
        lore.add(colorize(replacePlaceholders(expFormat, expPlaceholders)));

        // Add spacer if configured
        if (addSpacer) {
            lore.add("");
        }
    }

    /**
     * Adds footer text at the bottom of the lore
     */
    private void addFooter(List<String> lore, ItemStack item) {
        if (!loreConfig.getBoolean("footer.enabled", true)) return;

        List<String> footerLines = loreConfig.getStringList("footer.lines");
        if (footerLines.isEmpty()) return;

        Material material = item.getType();
        String materialName = formatMaterialName(material.name());

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("material", materialName);

        for (String line : footerLines) {
            lore.add(colorize(replacePlaceholders(line, placeholders)));
        }
    }

    /**
     * Format material name to be more readable
     * Example: DIAMOND_PICKAXE -> Diamond Pickaxe
     */
    private String formatMaterialName(String materialName) {
        String[] parts = materialName.toLowerCase().split("_");
        StringBuilder result = new StringBuilder();

        for (String part : parts) {
            if (!part.isEmpty()) {
                result.append(part.substring(0, 1).toUpperCase())
                        .append(part.substring(1))
                        .append(" ");
            }
        }

        return result.toString().trim();
    }

    /**
     * Format a number with K, M, B suffixes if enabled in config
     */
    private String formatNumber(int number) {
        if (!plugin.getConfigManager().getConfig().getBoolean("settings.number-format.enabled", true)) {
            return String.valueOf(number);
        }

        List<String> suffixes = plugin.getConfigManager().getConfig().getStringList("settings.number-format.suffixes");
        if (suffixes.isEmpty()) {
            return String.valueOf(number);
        }

        // Parse suffixes and sort by value (largest first)
        Map<String, Long> suffixMap = new HashMap<>();
        for (String suffix : suffixes) {
            String[] parts = suffix.split("-");
            if (parts.length == 2) {
                try {
                    suffixMap.put(parts[0], Long.parseLong(parts[1]));
                } catch (NumberFormatException ignored) {}
            }
        }

        // Sort suffixes by value (largest first)
        List<Map.Entry<String, Long>> sortedSuffixes = new ArrayList<>(suffixMap.entrySet());
        sortedSuffixes.sort((e1, e2) -> Long.compare(e2.getValue(), e1.getValue()));

        // Format the number
        for (Map.Entry<String, Long> entry : sortedSuffixes) {
            if (number >= entry.getValue()) {
                double value = (double) number / entry.getValue();
                return String.format("%.1f%s", value, entry.getKey()).replace(".0", "") + "";
            }
        }

        return String.valueOf(number);
    }

    /**
     * Replace placeholders in a string with their values
     */
    private String replacePlaceholders(String text, Map<String, String> placeholders) {
        if (text == null) return "";

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String placeholder = matcher.group(1);
            String replacement = placeholders.getOrDefault(placeholder, matcher.group(0));
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Colorize a string with color codes
     */
    private String colorize(String text) {
        return Utils.colorize(text);
    }
}