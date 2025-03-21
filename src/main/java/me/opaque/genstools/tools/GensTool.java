package me.opaque.genstools.tools;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
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
     * Adds an enchantment to a GensTool
     *
     * @param item The item to add the enchantment to
     * @param enchantId The ID of the enchantment to add
     * @param level The level of the enchantment
     * @return true if the enchantment was added, false otherwise
     */
    public static boolean addEnchantment(ItemStack item, String enchantId, int level) {
        if (!isGensTool(item)) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        // Get existing enchantments
        Map<String, Integer> enchantments = getEnchantments(item);

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

        // Store in the container
        container.set(KEY_ENCHANTMENTS, PersistentDataType.STRING, sb.toString());
        item.setItemMeta(meta);

        return true;
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
    private static int calculateRequiredExp(int level) {
        // Base formula: 1000 + (level * 500)
        return 1000 + (level * 500);
    }

    /**
     * Update lore with level and experience information
     */
    private static void updateLore(ItemMeta meta, int level, int experience, int requiredExp) {
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
}