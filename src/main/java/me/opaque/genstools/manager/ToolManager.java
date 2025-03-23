package me.opaque.genstools.manager;

import me.opaque.genstools.GensTools;
import me.opaque.genstools.enchants.CustomEnchant;
import me.opaque.genstools.enchants.EnchantFactory;
import me.opaque.genstools.tools.GensTool;
import me.opaque.genstools.tools.types.GensPickaxe;
import me.opaque.genstools.tools.types.GensSword;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ToolManager {
    private final GensTools plugin;
    private final Map<String, GensTool> toolPrototypes;
    private final Map<String, CustomEnchant> customEnchants;

    public ToolManager(GensTools plugin) {
        this.plugin = plugin;
        this.toolPrototypes = new HashMap<>();
        this.customEnchants = new HashMap<>();
    }

    public void registerTool(GensTool tool) {
        toolPrototypes.put(tool.getId(), tool);
    }

    public void registerEnchant(CustomEnchant enchant) {
        customEnchants.put(enchant.getId(), enchant);
    }

    public GensTool getToolById(String id) {
        return toolPrototypes.get(id);
    }

    public CustomEnchant getEnchantById(String id) {
        return customEnchants.get(id);
    }

    public Set<String> getAllToolIds() {
        return toolPrototypes.keySet();
    }

    public Set<String> getAllEnchantIds() {
        return customEnchants.keySet();
    }


    /**
     * Format the enchantment level for display
     * Delegates to GensTool for consistency
     *
     * @param level The level to format
     * @return Formatted level display
     */
    public String formatEnchantmentLevel(int level) {
        return GensTool.formatEnchantmentLevel(level);
    }

    public ItemStack createTool(String id, Player player) {
        GensTool prototype = toolPrototypes.get(id);
        if (prototype == null) {
            return null;
        }

        Material material;
        if (prototype instanceof GensPickaxe) {
            material = Material.DIAMOND_PICKAXE;
        } else if (prototype instanceof GensSword) {
            material = Material.DIAMOND_SWORD;
        } else {
            // Default to pickaxe
            material = Material.DIAMOND_PICKAXE;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        // Set name
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', prototype.getDisplayName()));

        // Store data
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(GensTool.KEY_TOOL_ID, PersistentDataType.STRING, id);
        container.set(GensTool.KEY_LEVEL, PersistentDataType.INTEGER, 1);
        container.set(GensTool.KEY_EXPERIENCE, PersistentDataType.INTEGER, 0);

        // Apply metadata
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);

        // Apply initial glow effect to the tool
        GensTool.applyGlow(item);

        // Update the lore using the LoreManager
        plugin.getLoreManager().updateToolLore(item);

        if (player != null) {
            plugin.getToolPersistenceManager().registerTool(player, item);
        }

        return item;
    }

    /*public boolean addEnchantToTool(ItemStack item, String enchantId, int level) {
        if (!GensTool.isGensTool(item)) {
            return false;
        }

        CustomEnchant enchant = customEnchants.get(enchantId);
        if (enchant == null) {
            return false;
        }

        if (level <= 0 || level > enchant.getMaxLevel()) {
            return false;
        }

        GensTool.addEnchantment(item, enchantId, level);

        // Update the lore to show the enchantment
        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.getLore();
        if (lore == null) {
            lore = new ArrayList<>();
        }

        // Remove existing enchantment lines
        lore.removeIf(line -> line.contains(enchant.getDisplayName()));

        // Add the enchantment line at the top of lore
        lore.add(0, ChatColor.GRAY + enchant.getDisplayName() + " " + toRoman(level));
        meta.setLore(lore);
        item.setItemMeta(meta);

        return true;
    }*/

    /**
     * Add an enchantment to a tool with level and compatibility validation
     *
     * @param item The tool to enchant
     * @param enchantId The enchantment ID
     * @param level The level to apply
     * @return true if successful
     */
    public boolean addEnchantToTool(ItemStack item, String enchantId, int level) {
        if (!GensTool.isGensTool(item)) {
            return false;
        }

        CustomEnchant enchant = customEnchants.get(enchantId);
        if (enchant == null) {
            return false;
        }

        // Validate level
        if (level <= 0) {
            return false;
        }

        // Get the tool object to determine its type
        String toolId = GensTool.getToolId(item);
        if (toolId == null) {
            plugin.getLogger().warning("Failed to get tool ID for compatibility check");
            return false;
        }

        GensTool tool = getToolById(toolId);
        if (tool == null) {
            plugin.getLogger().warning("Failed to get tool object for ID: " + toolId);
            return false;
        }

        // Determine tool type using the EnchantmentApplicability class
        String toolType = me.opaque.genstools.enchants.EnchantmentApplicability.getToolType(tool);

        // Check compatibility
        boolean isCompatible = me.opaque.genstools.enchants.EnchantmentApplicability.isApplicable(enchantId, toolType);

        // Log for debugging
        plugin.getLogger().info("Enchant compatibility check: " + enchantId + " on " + toolType + " = " + isCompatible);

        // If not compatible, don't apply the enchantment
        if (!isCompatible) {
            plugin.getLogger().info("Rejected incompatible enchant: " + enchantId + " on tool type: " + toolType);
            return false;
        }

        // Cap at max level if configured
        int maxLevel = plugin.getConfigManager().getMaxEnchantmentLevel();
        if (level > maxLevel) {
            level = maxLevel;
        }

        // Ensure the tool has glow effect
        GensTool.applyGlow(item);

        // Use the enhanced method with explicit lore update control
        return GensTool.addEnchantment(item, enchantId, level, true);
    }

    private String toRoman(int number) {
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(number);
        };
    }

    /**
     * Gives a tool to a player
     *
     * @param player The player to give the tool to
     * @param toolId The ID of the tool to give
     * @return true if the tool was given, false otherwise
     */
    public boolean giveTool(Player player, String toolId) {
        if (player == null || toolId == null) {
            return false;
        }

        // Check if the tool exists
        if (!toolPrototypes.containsKey(toolId)) {
            return false;
        }

        // Create the tool
        ItemStack toolItem = createTool(toolId, player);
        if (toolItem == null) {
            return false;
        }

        // Give the tool to the player
        if (player.getInventory().firstEmpty() == -1) {
            // Inventory is full, drop at player's location
            player.getWorld().dropItemNaturally(player.getLocation(), toolItem);
        } else {
            // Add to inventory
            player.getInventory().addItem(toolItem);
        }

        plugin.getToolPersistenceManager().registerTool(player, toolItem);
        return true;
    }
}