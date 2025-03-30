package me.opaque.genstools.enchants;

import me.opaque.genstools.GensTools;
import me.opaque.genstools.tools.GensTool;
import me.opaque.genstools.utils.Utils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EnchantmentCube {
    // PDC Keys
    public static final NamespacedKey KEY_CUBE_ID = new NamespacedKey(GensTools.getInstance(), "cube_id");
    public static final NamespacedKey KEY_TIER = new NamespacedKey(GensTools.getInstance(), "cube_tier");
    public static final NamespacedKey KEY_SUCCESS_RATE = new NamespacedKey(GensTools.getInstance(), "success_rate");
    public static final NamespacedKey KEY_ENCHANT = new NamespacedKey(GensTools.getInstance(), "cube_enchant");
    public static final NamespacedKey KEY_BOOST = new NamespacedKey(GensTools.getInstance(), "cube_boost");

    private final String id;
    private final String displayName;
    private final Material material;
    private final int customModelData;
    private final int tier;
    private final int successRate;
    private final String enchantId;
    private final double boost;
    private final List<String> lore;

    public EnchantmentCube(String id, String displayName, Material material, int customModelData,
                           int tier, int successRate, String enchantId, double boost, List<String> lore) {
        this.id = id;
        this.displayName = displayName;
        this.material = material;
        this.customModelData = customModelData;
        this.tier = tier;
        this.successRate = successRate;
        this.enchantId = enchantId;
        this.boost = boost;
        this.lore = lore;
    }

    /**
     * Get the cube ID
     */
    public String getId() {
        return id;
    }

    /**
     * Get the display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get the cube's material
     */
    public Material getMaterial() {
        return material;
    }

    /**
     * Get the cube's tier
     */
    public int getTier() {
        return tier;
    }

    /**
     * Get the success rate
     */
    public int getSuccessRate() {
        return successRate;
    }

    /**
     * Get the enchantment ID
     */
    public String getEnchantId() {
        return enchantId;
    }

    /**
     * Get the boost amount
     */
    public double getBoost() {
        return boost;
    }

    /**
     * Creates an ItemStack for this enchantment cube
     */
    public ItemStack createItemStack(int amount) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Set display name
        meta.setDisplayName(Utils.colorize(displayName));

        // Format lore with placeholders
        List<String> formattedLore = formatLore();
        meta.setLore(formattedLore);

        // Add enchant glow effect
        meta.addEnchant(Enchantment.DURABILITY, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        // Set custom model data if specified
        if (customModelData > 0) {
            meta.setCustomModelData(customModelData);
        }

        // Store cube data in PDC
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(KEY_CUBE_ID, PersistentDataType.STRING, id);
        pdc.set(KEY_TIER, PersistentDataType.INTEGER, tier);
        pdc.set(KEY_SUCCESS_RATE, PersistentDataType.INTEGER, successRate);
        pdc.set(KEY_ENCHANT, PersistentDataType.STRING, enchantId);
        pdc.set(KEY_BOOST, PersistentDataType.DOUBLE, boost);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Format lore with placeholders filled in
     */
    private List<String> formatLore() {
        List<String> formattedLore = new ArrayList<>();

        // Get the enchantment display name
        String enchantName = enchantId;
        CustomEnchant enchant = null;

        // Get the custom enchant if it exists
        if (GensTools.getInstance().getToolManager() != null) {
            enchant = GensTools.getInstance().getToolManager().getEnchantById(enchantId);
            if (enchant != null) {
                enchantName = enchant.getDisplayName();
            }
        }

        // Replace placeholders in lore
        for (String line : lore) {
            String formatted = Utils.colorize(line)
                    .replace("%success_rate%", String.valueOf(successRate))
                    .replace("%enchantment%", enchantName)
                    .replace("%boost%", Utils.formatGenNumber(boost));
            formattedLore.add(formatted);
        }

        return formattedLore;
    }

    /**
     * Check if an item is an enchantment cube
     *
     * @param item The item to check
     * @return true if it's an enchantment cube, false otherwise
     */
    public static boolean isEnchantmentCube(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        // Get the plugin instance
        GensTools plugin = GensTools.getInstance();

        // Check for cube marker in PDC
        NamespacedKey cubeKey = new NamespacedKey(plugin, "cube_type");
        if (container.has(cubeKey, PersistentDataType.STRING)) {
            return true;
        }

        // Fallback to checking if item has expected cube format in lore
        if (meta.hasLore() && meta.hasDisplayName()) {
            String name = ChatColor.stripColor(meta.getDisplayName());
            List<String> lore = meta.getLore();

            // Check if name contains "Cube" and lore has expected format
            return name.contains("Cube") &&
                    lore.stream().anyMatch(line ->
                            ChatColor.stripColor(line).contains("Enchantment:") ||
                                    ChatColor.stripColor(line).contains("Boost:"));
        }

        return false;
    }

    /**
     * Get all data from a cube item
     */
    public static EnchantmentCube fromItemStack(ItemStack item) {
        if (!isEnchantmentCube(item)) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        String id = pdc.get(KEY_CUBE_ID, PersistentDataType.STRING);
        int tier = pdc.get(KEY_TIER, PersistentDataType.INTEGER);
        int successRate = pdc.get(KEY_SUCCESS_RATE, PersistentDataType.INTEGER);
        String enchantId = pdc.get(KEY_ENCHANT, PersistentDataType.STRING);
        double boost = pdc.get(KEY_BOOST, PersistentDataType.DOUBLE);

        return new EnchantmentCube(
                id,
                meta.getDisplayName(),
                item.getType(),
                meta.hasCustomModelData() ? meta.getCustomModelData() : 0,
                tier,
                successRate,
                enchantId,
                boost,
                meta.getLore() != null ? meta.getLore() : new ArrayList<>()
        );
    }

    /**
     * Apply the cube to a GensTool
     * @return true if application was successful, false if it failed
     */
    public boolean applyToTool(Player player, ItemStack toolItem) {
        // Check if the item is a GensTool
        if (!GensTool.isGensTool(toolItem)) {
            return false;
        }

        // Instead of using GensTool.fromItemStack, we'll work directly with the ItemStack
        // and access the tool's data through NBT/PDC

        // Get item meta
        ItemMeta meta = toolItem.getItemMeta();
        if (meta == null) return false;

        // Get the custom enchant from your tool manager
        CustomEnchant enchant = GensTools.getInstance().getToolManager().getEnchantById(enchantId);
        if (enchant == null) {
            return false;
        }

        // Check compatibility based on tool type
        Material toolType = toolItem.getType();
        boolean isCompatible = false;

        // Basic compatibility check based on material
        if (enchantId.equalsIgnoreCase("auto_smelt") ||
                enchantId.equalsIgnoreCase("explosive") ||
                enchantId.equalsIgnoreCase("shard_finder")) {
            // These are pickaxe enchants
            isCompatible = toolType.name().contains("PICKAXE");
        }
        else if (enchantId.equalsIgnoreCase("critical_strike") ||
                enchantId.equalsIgnoreCase("life_steal") ||
                enchantId.equalsIgnoreCase("shard_greed")) {
            // These are sword enchants
            isCompatible = toolType.name().contains("SWORD");
        }
        else {
            // Default enchants (like efficiency, unbreaking, etc.) work on any tool
            isCompatible = true;
        }

        if (!isCompatible) {
            return false;
        }

        // Roll for success based on success rate
        boolean success = ThreadLocalRandom.current().nextInt(100) < successRate;

        // Show application effect
        Location loc = player.getLocation().add(0, 1, 0);

        if (success) {
            // Apply the enchantment boost by modifying the lore directly
            // This is a fallback approach since we don't know the exact method to use

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            NamespacedKey enchantKey = new NamespacedKey(GensTools.getInstance(), "enchant_" + enchantId.toLowerCase());

            // Get current level if exists
            double currentLevel = 0;
            if (pdc.has(enchantKey, PersistentDataType.DOUBLE)) {
                currentLevel = pdc.get(enchantKey, PersistentDataType.DOUBLE);
            }

            // Add the boost
            double newLevel = currentLevel + boost;
            pdc.set(enchantKey, PersistentDataType.DOUBLE, newLevel);

            // Update the lore to reflect the new enchantment level
            List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
            if (lore == null) lore = new ArrayList<>();

            // Find and update the enchantment in lore if it exists
            boolean foundInLore = false;
            for (int i = 0; i < lore.size(); i++) {
                String line = lore.get(i);
                // Simple check if the lore line contains the enchant name
                if (line.toLowerCase().contains(enchantId.toLowerCase())) {
                    // Replace with updated level
                    String enchantDisplay = enchant.getDisplayName() != null ?
                            enchant.getDisplayName() :
                            Utils.colorize("&f" + enchantId);
                    lore.set(i, Utils.colorize(enchantDisplay + " &7" + Utils.formatGenNumber(newLevel)));
                    foundInLore = true;
                    break;
                }
            }

            // If the enchantment wasn't found in lore, add it
            if (!foundInLore) {
                String enchantDisplay = enchant.getDisplayName() != null ?
                        enchant.getDisplayName() :
                        Utils.colorize("&f" + enchantId);
                lore.add(Utils.colorize(enchantDisplay + " &7" + Utils.formatGenNumber(newLevel)));
            }

            meta.setLore(lore);
            toolItem.setItemMeta(meta);

            // Play success effect
            player.getWorld().playSound(loc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);

            // Show success visual effect
            Utils.createRippleEffect(
                    loc,
                    Color.fromRGB(0, 255, 0),  // Green for success
                    2.0,
                    20,
                    20,
                    GensTools.getInstance()
            );
        } else {
            // Play failure effect
            player.getWorld().playSound(loc, Sound.ENTITY_ITEM_BREAK, 1.0f, 0.5f);

            // Show failure visual effect
            Utils.createRippleEffect(
                    loc,
                    Color.fromRGB(255, 0, 0),  // Red for failure
                    2.0,
                    20,
                    20,
                    GensTools.getInstance()
            );
        }

        return success;
    }

    /**
     * Get the enchantment ID associated with a cube item
     *
     * @param item The cube item to check
     * @return The enchantment ID or null if not found
     */
    public static String getEnchantmentId(ItemStack item) {
        if (!isEnchantmentCube(item) || !item.hasItemMeta()) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        // Get the plugin instance
        GensTools plugin = GensTools.getInstance();

        // Check for enchantment ID in PDC
        NamespacedKey enchantKey = new NamespacedKey(plugin, "cube_enchant");
        if (container.has(enchantKey, PersistentDataType.STRING)) {
            return container.get(enchantKey, PersistentDataType.STRING);
        }

        // If not found in PDC, try to parse from lore (fallback method)
        if (meta.hasLore()) {
            List<String> lore = meta.getLore();
            for (String line : lore) {
                String stripped = ChatColor.stripColor(line);

                // Check for a line like "Enchantment: Shard Greed"
                if (stripped.contains("Enchantment:")) {
                    String[] parts = stripped.split(":");
                    if (parts.length >= 2) {
                        String enchantName = parts[1].trim();

                        // Convert name to ID
                        // This requires a reverse lookup, let's add that functionality
                        return getEnchantIdFromName(enchantName);
                    }
                }
            }
        }

        return null;
    }

    /**
     * Get the boost percentage associated with a cube item
     *
     * @param item The cube item to check
     * @return The boost percentage or 0 if not found
     */
    public static int getBoostPercentage(ItemStack item) {
        if (!isEnchantmentCube(item) || !item.hasItemMeta()) {
            return 0;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        // Get the plugin instance
        GensTools plugin = GensTools.getInstance();

        // Check for boost percentage in PDC
        NamespacedKey boostKey = new NamespacedKey(plugin, "cube_boost");
        if (container.has(boostKey, PersistentDataType.INTEGER)) {
            return container.get(boostKey, PersistentDataType.INTEGER);
        }

        // If not found in PDC, try to parse from lore (fallback method)
        if (meta.hasLore()) {
            List<String> lore = meta.getLore();
            for (String line : lore) {
                String stripped = ChatColor.stripColor(line);

                // Check for a line like "Boost: +20%"
                if (stripped.contains("Boost:")) {
                    // Extract number from the line
                    Pattern pattern = Pattern.compile("\\d+");
                    Matcher matcher = pattern.matcher(stripped);
                    if (matcher.find()) {
                        try {
                            return Integer.parseInt(matcher.group());
                        } catch (NumberFormatException e) {
                            // Ignore parsing errors
                        }
                    }
                }
            }
        }

        return 0;
    }

    /**
     * Helper method to convert enchantment display name to enchantment ID
     *
     * @param displayName The display name to convert
     * @return The enchantment ID or null if not found
     */
    private static String getEnchantIdFromName(String displayName) {
        GensTools plugin = GensTools.getInstance();

        // Iterate through all enchantments to find a match
        for (String enchantId : plugin.getToolManager().getAllEnchantIds()) {
            CustomEnchant enchant = plugin.getToolManager().getEnchantById(enchantId);
            if (enchant != null && displayName.equalsIgnoreCase(enchant.getDisplayName())) {
                return enchantId;
            }
        }

        // Try direct matching if display name not found
        // Common enchantments mapping
        Map<String, String> nameToId = new HashMap<>();
        nameToId.put("Shard Greed", "shard_greed");
        nameToId.put("Critical Strike", "critical_strike");
        nameToId.put("Life Steal", "life_steal");
        nameToId.put("Explosive", "explosive");
        nameToId.put("Auto Smelt", "auto_smelt");
        // Add more mappings as needed

        return nameToId.getOrDefault(displayName, null);
    }
}