package me.opaque.genstools.enchants;

import org.bukkit.event.Event;

public abstract class CustomEnchant {
    private final String id;
    private final String displayName;
    private final String description;
    private final int maxLevel;
    private final boolean isTreasure;

    public CustomEnchant(String id, String displayName, String description, int maxLevel, boolean isTreasure) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.maxLevel = maxLevel;
        this.isTreasure = isTreasure;
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