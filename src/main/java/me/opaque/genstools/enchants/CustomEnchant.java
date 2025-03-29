package me.opaque.genstools.enchants;

import org.bukkit.event.Event;

public abstract class CustomEnchant {
    private final String id;
    private final String displayName;
    private final String description;
    private final int maxLevel;
    private final boolean isTreasure;
    private final CurrencyType currencyType; // New property for currency

    // New enum for currency types
    public enum CurrencyType {
        SHARDS,
        RUNES
    }

    public CustomEnchant(String id, String displayName, String description, int maxLevel, boolean isTreasure, CurrencyType currencyType) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.maxLevel = maxLevel;
        this.isTreasure = isTreasure;
        this.currencyType = currencyType != null ? currencyType : CurrencyType.SHARDS; // Default to SHARDS
    }

    // Constructor overload for backward compatibility
    public CustomEnchant(String id, String displayName, String description, int maxLevel, boolean isTreasure) {
        this(id, displayName, description, maxLevel, isTreasure, CurrencyType.SHARDS); // Default to SHARDS
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public boolean isTreasure() {
        return isTreasure;
    }

    // New getter for currency type
    public CurrencyType getCurrencyType() {
        return currencyType;
    }

    /**
     * Handle the effect of this enchantment for a specific event
     *
     * @param event The event to process
     * @param level The level of this enchantment
     * @return true if the enchantment had an effect, false otherwise
     */
    public abstract boolean handleEffect(Event event, int level);

    /**
     * Check if this enchantment can handle the given event type
     *
     * @param eventClass The event class to check
     * @return true if this enchantment can handle the event, false otherwise
     */
    public abstract boolean canHandleEvent(Class<? extends Event> eventClass);
}