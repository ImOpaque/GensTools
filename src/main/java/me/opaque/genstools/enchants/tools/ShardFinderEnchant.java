package me.opaque.genstools.enchants.tools;

import me.opaque.genscore.GensCore;
import me.opaque.genstools.GensTools;
import me.opaque.genstools.enchants.CustomEnchant;
import me.opaque.genstools.utils.Utils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.Random;

public class ShardFinderEnchant extends CustomEnchant {

    private final Random random = new Random();

    // Default values (will be overridden by config if available)
    private double baseChance = 0.05;  // 5% base chance
    private double chancePerLevel = 0.05;  // 5% additional per level
    private double baseMultiplier = 1.5;  // 1.5x base multiplier
    private double multiplierPerLevel = 0.1;  // 0.1x additional per level
    private double baseShardsPerMob = 1.0;  // Base shards per mob
    private boolean useEntityHealth = true;  // Whether to use entity's max health to determine shards
    private boolean enableMessages = true;  // Whether to display messages
    private String activationMessage = "&6&lSHARD GREED ACTIVATED: &7Gained &e{shards} shards!";
    private boolean configLoaded = false;

    public ShardFinderEnchant(String id, String displayName, String description, int maxLevel, boolean isTreasure, CurrencyType currencyType) {
        super(id, displayName, description, maxLevel, isTreasure, currencyType);
    }

    // Constructor for backward compatibility
    public ShardFinderEnchant(String id, String displayName, String description, int maxLevel, boolean isTreasure) {
        this(id, displayName, description, maxLevel, isTreasure, CurrencyType.SHARDS);
    }

    /**
     * Load configuration settings for this enchantment
     */
    private void loadConfig() {
        if (configLoaded) return;

        ConfigurationSection enchantsConfig = GensTools.getInstance().getConfigManager().enchantsConfig;
        if (enchantsConfig == null) {
            return;
        }

        ConfigurationSection config = enchantsConfig.getConfigurationSection("enchants.shard_finder");
        if (config == null) {
            return;
        }

        baseChance = config.getDouble("base-chance", baseChance);
        chancePerLevel = config.getDouble("chance-per-level", chancePerLevel);
        baseMultiplier = config.getDouble("base-multiplier", baseMultiplier);
        multiplierPerLevel = config.getDouble("multiplier-per-level", multiplierPerLevel);
        baseShardsPerMob = config.getDouble("base-shards-per-mob", baseShardsPerMob);
        useEntityHealth = config.getBoolean("use-entity-health", useEntityHealth);
        enableMessages = config.getBoolean("enable-messages", enableMessages);
        activationMessage = config.getString("activation-message", activationMessage);
        configLoaded = true;
    }

    @Override
    public boolean handleEffect(Event event, int level) {
        // Load config if not loaded yet
        if (!configLoaded) {
            loadConfig();
        }

        if (!(event instanceof EntityDeathEvent deathEvent)) {
            return false;
        }

        // Check if player killed the entity
        Player player = deathEvent.getEntity().getKiller();
        if (player == null) {
            return false;
        }

        // Calculate trigger chance based on level
        double triggerChance = baseChance + (chancePerLevel * (level - 1));

        // Cap chance at 100%
        if (triggerChance > 1.0) {
            triggerChance = 1.0;
        }

        // Try triggering the enchantment
        if (random.nextDouble() <= triggerChance) {
            // Calculate multiplier based on level
            double shardsMultiplier = baseMultiplier + (multiplierPerLevel * (level - 1));

            // Calculate base shards amount
            double baseShards = baseShardsPerMob;

            // If configured to use entity's health, factor that in
            LivingEntity entity = deathEvent.getEntity();
            if (useEntityHealth) {
                baseShards = entity.getMaxHealth() * 0.5; // 0.5 shards per health point
            }

            // Calculate final shards amount
            double shardsAmount = baseShards * shardsMultiplier;
            long finalShards = Math.round(shardsAmount);

            // Ensure at least 1 shard is given
            if (finalShards < 1) {
                finalShards = 1;
            }

            // Add shards to player
            GensCore gensCore = GensTools.getInstance().getGensCoreAPI();
            gensCore.getAPI().addShards(player.getUniqueId(), finalShards);

            // Send message if enabled
            if (enableMessages) {
                String message = activationMessage.replace("{shards}", String.valueOf(finalShards));
                Utils.sendMessage(player, message);
            }

            return true;
        }

        return false;
    }

    @Override
    public boolean canHandleEvent(Class<? extends Event> eventClass) {
        return eventClass == BlockBreakEvent.class;
    }
}