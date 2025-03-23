package me.opaque.genstools.persistence;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents saved data for a single tool
 */
public class SavedToolData {
    private final String uniqueId;
    private final String toolId;
    private int level;
    private int experience;
    private final Map<String, Integer> enchantments;

    public SavedToolData(String uniqueId, String toolId, int level, int experience, Map<String, Integer> enchantments) {
        this.uniqueId = uniqueId;
        this.toolId = toolId;
        this.level = level;
        this.experience = experience;
        this.enchantments = new HashMap<>(enchantments);
    }

    // Getters

    public String getUniqueId() {
        return uniqueId;
    }

    public String getToolId() {
        return toolId;
    }

    public int getLevel() {
        return level;
    }

    public int getExperience() {
        return experience;
    }

    public Map<String, Integer> getEnchantments() {
        return new HashMap<>(enchantments);
    }

    // Setters

    public void setLevel(int level) {
        this.level = level;
    }

    public void setExperience(int experience) {
        this.experience = experience;
    }

    public void setEnchantment(String enchantId, int level) {
        if (level <= 0) {
            enchantments.remove(enchantId);
        } else {
            enchantments.put(enchantId, level);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SavedToolData that = (SavedToolData) o;
        return Objects.equals(uniqueId, that.uniqueId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uniqueId);
    }
}