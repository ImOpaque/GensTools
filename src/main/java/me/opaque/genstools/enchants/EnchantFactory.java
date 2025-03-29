package me.opaque.genstools.enchants;

import me.opaque.genstools.enchants.tools.AutoSmeltEnchant;
import me.opaque.genstools.enchants.tools.ExplosiveEnchant;
import me.opaque.genstools.enchants.tools.ShardFinderEnchant;
import me.opaque.genstools.enchants.weapons.CriticalStrikeEnchant;
import me.opaque.genstools.enchants.weapons.LifeStealEnchant;
import me.opaque.genstools.enchants.weapons.ShardGreedEnchant;

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
     * @param currencyType The currency type for this enchantment (SHARDS or RUNES)
     * @return A CustomEnchant instance, or null if the type is unknown
     */
    public static CustomEnchant createEnchant(String type, String id, String displayName,
                                              String description, int maxLevel, boolean isTreasure,
                                              CustomEnchant.CurrencyType currencyType) {
        return switch (type.toLowerCase()) {
            case "explosive" -> new ExplosiveEnchant(id, displayName, description, maxLevel, isTreasure, currencyType);
            case "auto_smelt" -> new AutoSmeltEnchant(id, displayName, description, maxLevel, isTreasure, currencyType);
            case "critical_strike" -> new CriticalStrikeEnchant(id, displayName, description, maxLevel, isTreasure, currencyType);
            case "life_steal" -> new LifeStealEnchant(id, displayName, description, maxLevel, isTreasure, currencyType);
            case "shard_greed" -> new ShardGreedEnchant(id, displayName, description, maxLevel, isTreasure, currencyType);
            case "shard_finder" -> new ShardFinderEnchant(id, displayName, description, maxLevel, isTreasure, currencyType);
            default -> null;
        };
    }

    /**
     * Overloaded method for backward compatibility
     */
    public static CustomEnchant createEnchant(String type, String id, String displayName,
                                              String description, int maxLevel, boolean isTreasure) {
        return createEnchant(type, id, displayName, description, maxLevel, isTreasure, CustomEnchant.CurrencyType.SHARDS);
    }
}