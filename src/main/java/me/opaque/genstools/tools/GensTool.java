package me.opaque.genstools.tools;

import me.opaque.genstools.GensTools;
import me.opaque.genstools.enchants.CustomEnchant;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class GensTool {
    public static final NamespacedKey KEY_TOOL_ID = new NamespacedKey("genstools", "tool_id");
    public static final NamespacedKey KEY_LEVEL = new NamespacedKey("genstools", "level");
    public static final NamespacedKey KEY_EXPERIENCE = new NamespacedKey("genstools", "experience");
    public static final NamespacedKey KEY_ENCHANTMENTS = new NamespacedKey("genstools", "enchantments");

    private final String id;
    private final String displayName;
    private final List<String> lore;

    public GensTool(String id, String displayName, List<String> lore) {
        this.id = id;
        this.displayName = displayName;
        this.lore = lore;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getLore() {
        return lore;
    }

    /**
     * Checks if the specified item is a GensTool
     *
     * @param item The item to check
     * @return true if the item is a GensTool, false otherwise
     */
    public static boolean isGensTool(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        return container.has(KEY_TOOL_ID, PersistentDataType.STRING);
    }

    /**
     * Gets the tool ID from an item
     *
     * @param item The item to get the tool ID from
     * @return The tool ID, or null if the item is not a GensTool
     */
    public static String getToolId(ItemStack item) {
        if (!isGensTool(item)) {
            return null;
        }

        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        return container.get(KEY_TOOL_ID, PersistentDataType.STRING);
    }

    /**
     * Gets the level of a GensTool
     *
     * @param item The item to get the level from
     * @return The level, or 0 if the item is not a GensTool
     */
    public static int getLevel(ItemStack item) {
        if (!isGensTool(item)) {
            return 0;
        }

        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        return container.getOrDefault(KEY_LEVEL, PersistentDataType.INTEGER, 0);
    }

    /**
     * Gets the experience of a GensTool
     *
     * @param item The item to get the experience from
     * @return The experience, or 0 if the item is not a GensTool
     */
    public static int getExperience(ItemStack item) {
        if (!isGensTool(item)) {
            return 0;
        }

        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        return container.getOrDefault(KEY_EXPERIENCE, PersistentDataType.INTEGER, 0);
    }

    /**
     * Gets all enchantments on a GensTool
     *
     * @param item The item to get the enchantments from
     * @return A map of enchantment IDs to levels
     */
    public static Map<String, Integer> getEnchantments(ItemStack item) {
        Map<String, Integer> enchantments = new HashMap<>();

        if (!isGensTool(item) || !item.hasItemMeta()) {
            return enchantments;
        }

        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        NamespacedKey enchantKey = new NamespacedKey(GensTools.getInstance(), "enchants");

        // First try to get from consolidated string
        if (container.has(enchantKey, PersistentDataType.STRING)) {
            String enchantData = container.get(enchantKey, PersistentDataType.STRING);
            if (enchantData != null && !enchantData.isEmpty()) {
                for (String entry : enchantData.split(",")) {
                    String[] parts = entry.split(":");
                    if (parts.length == 2) {
                        try {
                            enchantments.put(parts[0], Integer.parseInt(parts[1]));
                        } catch (NumberFormatException e) {
                            // Handle case where the stored value might be a double
                            try {
                                double doubleValue = Double.parseDouble(parts[1]);
                                enchantments.put(parts[0], (int) doubleValue);
                            } catch (NumberFormatException e2) {
                                // Ignore invalid entries
                            }
                        }
                    }
                }
            }
        }

        // Also check for individual enchantment keys
        Set<String> allEnchantIds = GensTools.getInstance().getToolManager().getAllEnchantIds();

        for (String id : allEnchantIds) {
            // Skip if we already have this enchantment from the consolidated string
            if (enchantments.containsKey(id)) {
                continue;
            }

            NamespacedKey individualKey = new NamespacedKey(GensTools.getInstance(), "enchant_" + id);

            // Try INTEGER first
            if (container.has(individualKey, PersistentDataType.INTEGER)) {
                int level = container.get(individualKey, PersistentDataType.INTEGER);
                if (level > 0) {
                    enchantments.put(id, level);
                }
            }
            // Then try DOUBLE if INTEGER fails
            else if (container.has(individualKey, PersistentDataType.DOUBLE)) {
                double level = container.get(individualKey, PersistentDataType.DOUBLE);
                if (level > 0) {
                    enchantments.put(id, (int) level);
                }
            }
        }

        return enchantments;
    }

    /**
     * Debug method to print all data stored on an item
     * @param item The item to debug
     * @return A debug string
     */
    public static String debugItemData(ItemStack item) {
        StringBuilder debug = new StringBuilder();
        debug.append("==== Item Debug Info ====\n");

        if (!isGensTool(item) || !item.hasItemMeta()) {
            debug.append("Not a GensTool or missing ItemMeta");
            return debug.toString();
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        // Check tool ID
        debug.append("Tool ID: ").append(container.has(KEY_TOOL_ID, PersistentDataType.STRING) ?
                container.get(KEY_TOOL_ID, PersistentDataType.STRING) : "Not found").append("\n");

        // Check level
        debug.append("Level: ").append(container.has(KEY_LEVEL, PersistentDataType.INTEGER) ?
                container.get(KEY_LEVEL, PersistentDataType.INTEGER) : "Not found").append("\n");

        // Check experience
        debug.append("Experience: ").append(container.has(KEY_EXPERIENCE, PersistentDataType.INTEGER) ?
                container.get(KEY_EXPERIENCE, PersistentDataType.INTEGER) : "Not found").append("\n");

        // Check consolidated enchantment string
        NamespacedKey enchantKey = new NamespacedKey(GensTools.getInstance(), "enchants");
        debug.append("Consolidated enchantments: ").append(container.has(enchantKey, PersistentDataType.STRING) ?
                container.get(enchantKey, PersistentDataType.STRING) : "Not found").append("\n");

        // Check applied cubes
        NamespacedKey cubesKey = new NamespacedKey(GensTools.getInstance(), "applied_cubes");
        debug.append("Applied cubes: ").append(container.has(cubesKey, PersistentDataType.STRING) ?
                container.get(cubesKey, PersistentDataType.STRING) : "Not found").append("\n");

        // Check individual enchantment keys
        debug.append("\nIndividual enchantment keys:\n");

        // List all keys in the PDC
        debug.append("All keys in PDC:\n");
        for (NamespacedKey key : container.getKeys()) {
            debug.append("Key: ").append(key.toString()).append("\n");

            // Try to get the value as different types
            if (container.has(key, PersistentDataType.INTEGER)) {
                debug.append("  INTEGER value: ").append(container.get(key, PersistentDataType.INTEGER)).append("\n");
            }
            if (container.has(key, PersistentDataType.DOUBLE)) {
                debug.append("  DOUBLE value: ").append(container.get(key, PersistentDataType.DOUBLE)).append("\n");
            }
            if (container.has(key, PersistentDataType.STRING)) {
                debug.append("  STRING value: ").append(container.get(key, PersistentDataType.STRING)).append("\n");
            }
        }

        return debug.toString();
    }

    /**
     * Add an enchantment to a tool
     *
     * @param item The tool to add the enchantment to
     * @param enchantId The ID of the enchantment to add
     * @param level The level of the enchantment
     * @param updateLore Whether to update the lore immediately
     * @return true if the enchantment was added, false otherwise
     */
    public static boolean addEnchantment(ItemStack item, String enchantId, int level, boolean updateLore) {
        if (!isGensTool(item) || item.getItemMeta() == null) {
            return false;
        }

        // Check if the enchantment exists
        CustomEnchant enchant = GensTools.getInstance().getToolManager().getEnchantById(enchantId);
        if (enchant == null) {
            return false;
        }

        // Cap level to max level
        int maxLevel = enchant.getMaxLevel();
        if (level > maxLevel) {
            level = maxLevel;
        }

        // Get the existing enchantments
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        // Store both individual key and update consolidated enchantment string
        NamespacedKey enchantKey = new NamespacedKey(GensTools.getInstance(), "enchant_" + enchantId);
        container.set(enchantKey, PersistentDataType.INTEGER, level);

        // Update the consolidated enchantment string
        Map<String, Integer> enchantments = new HashMap<>();

        // First get existing enchantments from the consolidated string if it exists
        NamespacedKey enchantmentsKey = new NamespacedKey(GensTools.getInstance(), "enchants");
        if (container.has(enchantmentsKey, PersistentDataType.STRING)) {
            String enchantData = container.get(enchantmentsKey, PersistentDataType.STRING);
            if (enchantData != null && !enchantData.isEmpty()) {
                for (String entry : enchantData.split(",")) {
                    String[] parts = entry.split(":");
                    if (parts.length == 2) {
                        try {
                            enchantments.put(parts[0], Integer.parseInt(parts[1]));
                        } catch (NumberFormatException e) {
                            // Handle case where the stored value might be a double
                            try {
                                double doubleValue = Double.parseDouble(parts[1]);
                                enchantments.put(parts[0], (int) doubleValue);
                            } catch (NumberFormatException e2) {
                                // Ignore invalid entries
                            }
                        }
                    }
                }
            }
        }

        // Add or update the enchantment
        enchantments.put(enchantId, level);

        // Convert back to string
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Integer> entry : enchantments.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            sb.append(entry.getKey()).append(":").append(entry.getValue());
            first = false;
        }

        // Store the consolidated string
        container.set(enchantmentsKey, PersistentDataType.STRING, sb.toString());
        item.setItemMeta(meta);

        // Update the lore if requested
        if (updateLore) {
            GensTools.getInstance().getLoreManager().updateToolLore(item);
        }

        updatePersistence(item);
        return true;
    }

    /**
     * Adds an enchantment to a GensTool (defaults to updating lore)
     * This overload maintains backward compatibility with existing code
     *
     * @param item The item to add the enchantment to
     * @param enchantId The ID of the enchantment to add
     * @param level The level of the enchantment
     * @return true if the enchantment was added, false otherwise
     */
    public static boolean addEnchantment(ItemStack item, String enchantId, int level) {
        // Call the more detailed method with updateLore = true for backward compatibility
        return addEnchantment(item, enchantId, level, true);
    }

    /**
     * Format enchantment level for display (numeric or Roman)
     *
     * @param level The level to format
     * @return Formatted level string
     */
    public static String formatEnchantmentLevel(int level) {
        if (GensTools.getInstance().getConfigManager().useNumericEnchantDisplay()) {
            return String.valueOf(level);
        } else {
            return toRoman(level);
        }
    }

    /**
     * Applies a glow effect to an ItemStack by adding a hidden enchantment
     *
     * @param item The ItemStack to apply glow to
     * @return The modified ItemStack with glow effect
     */
    public static ItemStack applyGlow(ItemStack item) {
        if (item != null && item.getType() != Material.AIR) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    /**
     * Removes the glow effect from an ItemStack
     *
     * @param item The ItemStack to remove glow from
     * @return The modified ItemStack without glow effect
     */
    public static ItemStack removeGlow(ItemStack item) {
        if (item != null && item.getType() != Material.AIR) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasEnchant(Enchantment.DURABILITY)) {
                meta.removeEnchant(Enchantment.DURABILITY);
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    /**
     * Checks if an ItemStack has the glow effect
     *
     * @param item The ItemStack to check
     * @return True if the item has glow effect, false otherwise
     */
    public static boolean hasGlow(ItemStack item) {
        if (item != null && item.getType() != Material.AIR) {
            ItemMeta meta = item.getItemMeta();
            return meta != null && meta.hasEnchant(Enchantment.DURABILITY) && meta.hasItemFlag(ItemFlag.HIDE_ENCHANTS);
        }
        return false;
    }

    /**
     * Convert a number to Roman numerals
     *
     * @param number The number to convert
     * @return Roman numeral representation or numeric string if too large
     */
    private static String toRoman(int number) {
        if (number <= 0) {
            return String.valueOf(number);
        }

        // For large numbers, just return the numeric value
        if (number > 3999) {
            return String.valueOf(number);
        }

        String[] thousands = {"", "M", "MM", "MMM"};
        String[] hundreds = {"", "C", "CC", "CCC", "CD", "D", "DC", "DCC", "DCCC", "CM"};
        String[] tens = {"", "X", "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC"};
        String[] ones = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};

        return thousands[number / 1000] +
                hundreds[(number % 1000) / 100] +
                tens[(number % 100) / 10] +
                ones[number % 10];
    }

    /**
     * Update the lore to show all enchantments properly
     *
     * @param item The tool to update
     */
    public static void updateEnchantmentLore(ItemStack item) {
        if (!isGensTool(item) || item.getItemMeta() == null) {
            return;
        }

        // Use the LoreManager to update the lore
        GensTools.getInstance().getLoreManager().updateToolLore(item);
    }

    /**
     * Adds experience to a tool and checks for level up
     *
     * @param item The tool item to add experience to
     * @param amount The amount of experience to add
     * @return true if the tool leveled up, false otherwise
     */
    public static boolean addExperience(ItemStack item, int amount) {
        if (!isGensTool(item) || item.getItemMeta() == null) {
            return false;
        }

        // Get current experience and level
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        int currentExp = container.getOrDefault(KEY_EXPERIENCE, PersistentDataType.INTEGER, 0);
        int currentLevel = container.getOrDefault(KEY_LEVEL, PersistentDataType.INTEGER, 1);

        // Calculate new experience
        int newExp = currentExp + amount;

        // Check for level up
        boolean leveledUp = false;
        int requiredExp = calculateRequiredExp(currentLevel);

        while (newExp >= requiredExp) {
            // Level up
            currentLevel++;
            newExp -= requiredExp;
            leveledUp = true;
            requiredExp = calculateRequiredExp(currentLevel);
        }

        // Update the item
        container.set(KEY_EXPERIENCE, PersistentDataType.INTEGER, newExp);
        container.set(KEY_LEVEL, PersistentDataType.INTEGER, currentLevel);
        item.setItemMeta(meta);

        // Update the lore
        GensTools.getInstance().getLoreManager().updateToolLore(item);

        updatePersistence(item);

        return leveledUp;
    }

    /**
     * Calculate required experience for the next level
     */
    public static int calculateRequiredExp(int level) {
        // Base formula: 1000 + (level * 500)
        return 1000 + (level * 500);
    }

    /**
     * Update lore with level and experience information
     * This is now public so it can be accessed in commands
     */
    public static void updateLore(ItemMeta meta, int level, int experience, int requiredExp) {
        List<String> lore = meta.getLore();
        if (lore == null) {
            lore = new ArrayList<>();
        }

        // Find and update level and exp lines
        boolean foundLevel = false;
        boolean foundExp = false;

        for (int i = 0; i < lore.size(); i++) {
            String line = lore.get(i);
            if (line.contains("Level:")) {
                lore.set(i, ChatColor.GRAY + "Level: " + ChatColor.YELLOW + level);
                foundLevel = true;
            } else if (line.contains("EXP:")) {
                lore.set(i, ChatColor.GRAY + "EXP: " + ChatColor.AQUA + experience + "/" + requiredExp);
                foundExp = true;
            }
        }

        // Add lines if not found
        if (!foundLevel) {
            lore.add(ChatColor.GRAY + "Level: " + ChatColor.YELLOW + level);
        }
        if (!foundExp) {
            lore.add(ChatColor.GRAY + "EXP: " + ChatColor.AQUA + experience + "/" + requiredExp);
        }

        meta.setLore(lore);
    }

    /**
     * Sets the level of a tool directly
     *
     * @param item The tool to modify
     * @param level The new level
     * @return true if successful, false otherwise
     */
    public static boolean setLevel(ItemStack item, int level) {
        if (!isGensTool(item) || item.getItemMeta() == null) {
            return false;
        }

        if (level < 1) {
            level = 1;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        // Set the new level
        container.set(KEY_LEVEL, PersistentDataType.INTEGER, level);

        // Reset experience to 0
        container.set(KEY_EXPERIENCE, PersistentDataType.INTEGER, 0);

        // Apply the metadata
        item.setItemMeta(meta);

        // Update the lore
        GensTools.getInstance().getLoreManager().updateToolLore(item);

        updatePersistence(item);

        return true;
    }

    /**
     * Sets the experience of a tool directly
     *
     * @param item The tool to modify
     * @param exp The new experience amount
     * @return true if successful, false otherwise
     */
    public static boolean setExperience(ItemStack item, int exp) {
        if (!isGensTool(item) || item.getItemMeta() == null) {
            return false;
        }

        if (exp < 0) {
            exp = 0;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        int currentLevel = container.getOrDefault(KEY_LEVEL, PersistentDataType.INTEGER, 1);
        int requiredExp = calculateRequiredExp(currentLevel);

        // Cap experience at required amount
        if (exp > requiredExp) {
            exp = requiredExp;
        }

        // Set the new experience
        container.set(KEY_EXPERIENCE, PersistentDataType.INTEGER, exp);

        // Apply the metadata
        item.setItemMeta(meta);

        // Update the lore
        GensTools.getInstance().getLoreManager().updateToolLore(item);

        updatePersistence(item);

        return true;
    }

    // Add compatibility methods for the info command that may be using different method names:

    /**
     * Alias for getLevel() method to maintain compatibility
     */
    public static int getToolLevel(ItemStack item) {
        return getLevel(item);
    }

    /**
     * Alias for getExperience() method to maintain compatibility
     */
    public static int getToolExp(ItemStack item) {
        return getExperience(item);
    }

    /**
     * Alias for getEnchantments() method to maintain compatibility
     */
    public static Map<String, Integer> getToolEnchantments(ItemStack item) {
        return getEnchantments(item);
    }

    /**
     * Alias for addExperience() method to maintain compatibility
     */
    public static boolean addToolExp(ItemStack item, int amount) {
        return addExperience(item, amount);
    }

    /**
     * Alias for setLevel() method to maintain compatibility
     */
    public static boolean setToolLevel(ItemStack item, int level) {
        return setLevel(item, level);
    }

    /**
     * Alias for setExperience() method to maintain compatibility
     */
    public static boolean setToolExp(ItemStack item, int exp) {
        return setExperience(item, exp);
    }

    /**
     * Updates multiple enchantments at once and updates lore only once for efficiency
     *
     * @param item The item to modify
     * @param enchantments Map of enchantment IDs to levels to add/update
     * @return true if successful, false otherwise
     */
    public static boolean updateEnchantments(ItemStack item, Map<String, Integer> enchantments) {
        if (!isGensTool(item)) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        // Get existing enchantments and merge with new ones
        Map<String, Integer> currentEnchants = getEnchantments(item);
        currentEnchants.putAll(enchantments);

        // Remove any enchantment set to level 0
        currentEnchants.entrySet().removeIf(entry -> entry.getValue() <= 0);

        // Convert to string
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Integer> entry : currentEnchants.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            sb.append(entry.getKey()).append(":").append(entry.getValue());
            first = false;
        }

        // Store in the container
        container.set(KEY_ENCHANTMENTS, PersistentDataType.STRING, sb.toString());
        item.setItemMeta(meta);

        // Update lore once
        updateEnchantmentLore(item);

        return true;
    }

    /**
     * Removes an enchantment from a GensTool
     *
     * @param item The item to remove the enchantment from
     * @param enchantId The ID of the enchantment to remove
     * @param updateLore Whether to update the lore immediately
     * @return true if the enchantment was removed, false otherwise
     */
    public static boolean removeEnchantment(ItemStack item, String enchantId, boolean updateLore) {
        if (!isGensTool(item) || item.getItemMeta() == null) {
            return false;
        }

        // Check if the enchantment exists
        if (!hasEnchantment(item, enchantId)) {
            return false;
        }

        // Remove the enchantment
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey enchantKey = new NamespacedKey(GensTools.getInstance(), "enchant_" + enchantId);

        // Remove the specific NamespacedKey that stores this enchantment
        container.remove(enchantKey);

        item.setItemMeta(meta);

        // Update lore if requested
        if (updateLore) {
            updateEnchantmentLore(item);
        }

        updatePersistence(item);

        return true;
    }

    /**
     * Removes an enchantment from a GensTool (defaults to updating lore)
     *
     * @param item The item to remove the enchantment from
     * @param enchantId The ID of the enchantment to remove
     * @return true if the enchantment was removed, false otherwise
     */
    public static boolean removeEnchantment(ItemStack item, String enchantId) {
        return removeEnchantment(item, enchantId, true);
    }

    /**
     * Checks if a GensTool has a specific enchantment
     *
     * @param item The item to check
     * @param enchantId The ID of the enchantment to check for
     * @return true if the item has the enchantment, false otherwise
     */
    public static boolean hasEnchantment(ItemStack item, String enchantId) {
        if (!isGensTool(item)) {
            return false;
        }

        Map<String, Integer> enchantments = getEnchantments(item);
        return enchantments.containsKey(enchantId);
    }

    /**
     * Gets the level of a specific enchantment on a GensTool
     *
     * @param item The item to check
     * @param enchantId The ID of the enchantment to get the level of
     * @return The level of the enchantment, or 0 if the item doesn't have the enchantment
     */
    public static int getEnchantmentLevel(ItemStack item, String enchantId) {
        if (!isGensTool(item)) {
            return 0;
        }

        Map<String, Integer> enchantments = getEnchantments(item);
        return enchantments.getOrDefault(enchantId, 0);
    }

    /**
     * Updates the persistence system when a tool is modified
     *
     * @param item The tool that was modified
     */
    public static void updatePersistence(ItemStack item) {
        if (!isGensTool(item)) {
            return;
        }

        GensTools plugin = GensTools.getInstance();

        // Find the player who has this tool
        for (Player player : Bukkit.getOnlinePlayers()) {
            // Check main hand
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            if (mainHand.equals(item)) {
                plugin.getToolPersistenceManager().handleToolUpdate(player, item);
                return;
            }

            // Check inventory
            for (ItemStack inventoryItem : player.getInventory().getContents()) {
                if (inventoryItem != null && inventoryItem.equals(item)) {
                    plugin.getToolPersistenceManager().handleToolUpdate(player, item);
                    return;
                }
            }
        }
    }
}