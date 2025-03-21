package me.opaque.genstools.enchants;

import me.opaque.genstools.enchants.tools.AutoSmeltEnchant;
import me.opaque.genstools.enchants.tools.ExplosiveEnchant;
import me.opaque.genstools.enchants.weapons.CriticalStrikeEnchant;
import me.opaque.genstools.enchants.weapons.LifeStealEnchant;

public class EnchantFactory {

    /**
     * Creates a CustomEnchant instance based on the provided type
     *
     * @param type The type of enchantment to create
     * @param id The unique identifier for the enchantment
     * @param displayName The display name of the enchantment
     * @param description The description of the enchantment
     * @param maxLevel The maximum level for the enchantment
     * @param isTreasure Whether this is a treasure enchantment
     * @return A CustomEnchant instance, or null if the type is unknown
     */
    public static CustomEnchant createEnchant(String type, String id, String displayName,
                                              String description, int maxLevel, boolean isTreasure) {
        return switch (type.toLowerCase()) {
            case "explosive" -> new ExplosiveEnchant(id, displayName, description, maxLevel, isTreasure);
            case "auto_smelt" -> new AutoSmeltEnchant(id, displayName, description, maxLevel, isTreasure);
            case "critical_strike" -> new CriticalStrikeEnchant(id, displayName, description, maxLevel, isTreasure);
            case "life_steal" -> new LifeStealEnchant(id, displayName, description, maxLevel, isTreasure);
            default -> null;
        };
    }
}