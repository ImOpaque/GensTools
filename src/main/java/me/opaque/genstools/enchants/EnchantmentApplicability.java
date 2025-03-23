package me.opaque.genstools.enchants;

import me.opaque.genstools.GensTools;
import me.opaque.genstools.tools.GensTool;
import me.opaque.genstools.tools.types.GensPickaxe;
import me.opaque.genstools.tools.types.GensSword;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages which enchantments can be applied to which tool types
 * Optimized for performance with cached results
 */
public class EnchantmentApplicability {
    private static final Map<String, Set<String>> TOOL_TYPE_ENCHANTS = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, Boolean>> APPLICABILITY_CACHE = new ConcurrentHashMap<>();

    /**
     * Initialize the enchantment applicability system from config
     */
    public static void initialize() {
        // Clear caches
        TOOL_TYPE_ENCHANTS.clear();
        APPLICABILITY_CACHE.clear();

        GensTools plugin = GensTools.getInstance();
        ConfigurationSection enchantsConfig = plugin.getConfigManager().getEnchantsConfig().getConfigurationSection("enchants");
        if (enchantsConfig == null) return;

        // Initialize sets for each tool type with thread-safe collections
        TOOL_TYPE_ENCHANTS.put("PICKAXE", ConcurrentHashMap.newKeySet());
        TOOL_TYPE_ENCHANTS.put("SWORD", ConcurrentHashMap.newKeySet());

        // Iterate through all enchants in config
        for (String enchantId : enchantsConfig.getKeys(false)) {
            ConfigurationSection enchantSection = enchantsConfig.getConfigurationSection(enchantId);
            if (enchantSection == null) continue;

            // Get applicable tool types
            if (enchantSection.contains("applicable-tools")) {
                for (String toolType : enchantSection.getStringList("applicable-tools")) {
                    toolType = toolType.toUpperCase();
                    TOOL_TYPE_ENCHANTS.computeIfAbsent(toolType, k -> ConcurrentHashMap.newKeySet())
                            .add(enchantId);
                    plugin.getLogger().info("Added enchant " + enchantId + " to tool type " + toolType + " from config");
                }
            } else {
                // Default behavior based on enchant type - THIS IS WHERE THE ISSUE IS
                String type = enchantSection.getString("type", "default").toLowerCase();
                plugin.getLogger().info("Processing enchant " + enchantId + " with type " + type);

                if (type.contains("weapon") || type.equals("sword")) {
                    TOOL_TYPE_ENCHANTS.get("SWORD").add(enchantId);
                    plugin.getLogger().info("Added SWORD-specific enchant: " + enchantId);
                } else if (type.contains("mining") || type.equals("pickaxe")) {
                    TOOL_TYPE_ENCHANTS.get("PICKAXE").add(enchantId);
                    plugin.getLogger().info("Added PICKAXE-specific enchant: " + enchantId);
                } else {
                    // THIS ELSE CLAUSE IS PROBLEMATIC - DO NOT ADD TO ALL TOOL TYPES
                    // Instead, add to a generic "ALL" category that we can check later
                    plugin.getLogger().warning("Enchant " + enchantId + " has no specific tool type - NOT adding to all tools");
                    // Create an ALL category for truly generic enchants if needed
                    TOOL_TYPE_ENCHANTS.computeIfAbsent("ALL", k -> ConcurrentHashMap.newKeySet())
                            .add(enchantId);
                }
            }
        }

        // For debugging - show which enchants are configured for each tool type
        for (Map.Entry<String, Set<String>> entry : TOOL_TYPE_ENCHANTS.entrySet()) {
            plugin.getLogger().info("Tool type " + entry.getKey() + " has enchants: " + String.join(", ", entry.getValue()));
        }
    }

    /**
     * Check if an enchantment is applicable to a specific tool type
     * Uses caching for performance
     */
    public static boolean isApplicable(String enchantId, String toolType) {
        // Check cache first
        Map<String, Boolean> enchantCache = APPLICABILITY_CACHE.computeIfAbsent(toolType.toUpperCase(), k -> new HashMap<>());
        if (enchantCache.containsKey(enchantId)) {
            return enchantCache.get(enchantId);
        }

        // Get the specific tool type enchants
        Set<String> applicableEnchants = TOOL_TYPE_ENCHANTS.get(toolType.toUpperCase());

        // Check specific tool type first
        boolean result = applicableEnchants != null && applicableEnchants.contains(enchantId);

        // If not found in specific type, check if it's in the "ALL" category
        if (!result) {
            Set<String> genericEnchants = TOOL_TYPE_ENCHANTS.get("ALL");
            result = genericEnchants != null && genericEnchants.contains(enchantId);
        }

        // Cache and log the result
        enchantCache.put(enchantId, result);
        GensTools.getInstance().getLogger().info("Compatibility check (fresh calculation): " + enchantId +
                " for " + toolType + " = " + result);
        return result;
    }

    /**
     * Get all enchantment IDs applicable to a specific tool type
     */
    public static Set<String> getApplicableEnchants(String toolType) {
        Set<String> result = TOOL_TYPE_ENCHANTS.get(toolType.toUpperCase());
        return result != null ? new HashSet<>(result) : Collections.emptySet();
    }

    /**
     * Get the tool type from a GensTool
     */
    // In EnchantmentApplicability.java, modify getToolType to include logging
    public static String getToolType(GensTool tool) {
        String result;
        if (tool instanceof GensPickaxe) {
            result = "PICKAXE";
        } else if (tool instanceof GensSword) {
            result = "SWORD";
        } else {
            result = "UNKNOWN";
        }

        GensTools.getInstance().getLogger().info("Tool type detected for " + tool.getId() + ": " + result);
        return result;
    }

    /**
     * Get all enchantment IDs applicable to a specific item
     */
    public static Set<String> getApplicableEnchants(ItemStack item) {
        if (!GensTool.isGensTool(item)) {
            return Collections.emptySet();
        }

        String toolId = GensTool.getToolId(item);
        if (toolId == null) {
            return Collections.emptySet();
        }

        GensTool tool = GensTools.getInstance().getToolManager().getToolById(toolId);
        if (tool == null) {
            return Collections.emptySet();
        }

        return getApplicableEnchants(getToolType(tool));
    }
}