package me.opaque.genstools.tools;

import me.opaque.genstools.GensTools;
import me.opaque.genstools.enchants.CustomEnchant;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        Map<String, Integer> result = new HashMap<>();

        if (!isGensTool(item)) {
            return result;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        // Check if the item has custom enchantments
        if (!container.has(KEY_ENCHANTMENTS, PersistentDataType.STRING)) {
            return result;
        }

        // Format: "enchant1:level1,enchant2:level2,..."
        String enchantmentsStr = container.get(KEY_ENCHANTMENTS, PersistentDataType.STRING);
        if (enchantmentsStr == null || enchantmentsStr.isEmpty()) {
            return result;
        }

        // Parse the enchantments string
        String[] enchantments = enchantmentsStr.split(",");
        for (String enchantment : enchantments) {
            String[] parts = enchantment.split(":");
            if (parts.length == 2) {
                try {
                    String id = parts[0];
                    int level = Integer.parseInt(parts[1]);
                    result.put(id, level);
                } catch (NumberFormatException e) {
                    // Skip invalid entries
                }
            }
        }

        return result;
    }

    /**
     * Adds an enchantment to a GensTool with control over lore updates
     *
     * @param item The item to add the enchantment to
     * @param enchantId The ID of the enchantment to add
     * @param level The level of the enchantment
     * @param updateLore Whether to update the lore immediately
     * @return true if the enchantment was added, false otherwise
     */
    public static boolean addEnchantment(ItemStack item, String enchantId, int level, boolean updateLore) {
        if (!isGensTool(item)) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        // Get existing enchantments
        Map<String, Integer> enchantments = getEnchantments(item);

        // Add or update the enchantment
        enchantments.put(enchantId, level);

        // Convert back to string format for storage
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Integer> entry : enchantments.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            sb.append(entry.getKey()).append(":").append(entry.getValue());
            first = false;
        }

        // Store in the container
        container.set(KEY_ENCHANTMENTS, PersistentDataType.STRING, sb.toString());
        item.setItemMeta(meta);

        // Only update the lore if requested
        if (updateLore) {
            updateEnchantmentLore(item);
        }

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

        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.getLore();
        if (lore == null) {
            lore = new ArrayList<>();
        }

        // First, find the index where the level info starts
        int levelIndex = -1;
        for (int i = 0; i < lore.size(); i++) {
            String line = lore.get(i);
            if (line.contains("Level:")) {
                levelIndex = i;
                break;
            }
        }

        if (levelIndex == -1) {
            // If no level info found, assume enchantments take up the whole top section
            // Just keep everything after any non-colored lines or lines about EXP/level
            List<String> nonEnchantLines = new ArrayList<>();
            for (String line : lore) {
                if (!line.startsWith("§") || line.contains("Level:") || line.contains("EXP:")) {
                    nonEnchantLines.add(line);
                }
            }
            lore = nonEnchantLines;
        } else {
            // Keep only the lines starting from the level info
            lore = new ArrayList<>(lore.subList(levelIndex, lore.size()));
        }

        // Get all enchantments
        Map<String, Integer> enchantments = getEnchantments(item);

        // Add enchantment lines at the top
        List<String> enchantLines = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : enchantments.entrySet()) {
            String enchantId = entry.getKey();
            int level = entry.getValue();

            // Get display name from ToolManager
            CustomEnchant enchant = GensTools.getInstance().getToolManager().getEnchantById(enchantId);
            if (enchant != null) {
                String displayName = enchant.getDisplayName();

                // Format level based on config setting
                String levelDisplay = formatEnchantmentLevel(level);

                enchantLines.add(ChatColor.GRAY + displayName + " " + levelDisplay);
            }
        }

        // Add enchantment lines at the top
        for (int i = enchantLines.size() - 1; i >= 0; i--) {
            lore.add(0, enchantLines.get(i));
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
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

        // Update lore with new level and experience
        updateLore(meta, currentLevel, newExp, requiredExp);
        item.setItemMeta(meta);

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

        // Update lore
        updateLore(meta, level, 0, calculateRequiredExp(level));
        item.setItemMeta(meta);

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

        // Update lore
        updateLore(meta, currentLevel, exp, requiredExp);
        item.setItemMeta(meta);

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
        if (!isGensTool(item)) {
            return false;
        }

        Map<String, Integer> enchantments = getEnchantments(item);

        // Check if the enchantment exists
        if (!enchantments.containsKey(enchantId)) {
            return false;
        }

        // Remove the enchantment
        enchantments.remove(enchantId);

        // Update the enchantments string
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        if (enchantments.isEmpty()) {
            // If no enchantments left, remove the key
            container.remove(KEY_ENCHANTMENTS);
        } else {
            // Convert to string
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (Map.Entry<String, Integer> entry : enchantments.entrySet()) {
                if (!first) {
                    sb.append(",");
                }
                sb.append(entry.getKey()).append(":").append(entry.getValue());
                first = false;
            }

            // Store in the container
            container.set(KEY_ENCHANTMENTS, PersistentDataType.STRING, sb.toString());
        }

        item.setItemMeta(meta);

        // Update lore if requested
        if (updateLore) {
            updateEnchantmentLore(item);
        }

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
}