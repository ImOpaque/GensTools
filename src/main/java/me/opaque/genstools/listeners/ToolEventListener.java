package me.opaque.genstools.listeners;

import me.opaque.genstools.GensTools;
import me.opaque.genstools.enchants.CustomEnchant;
import me.opaque.genstools.tools.GensTool;
import me.opaque.genstools.tools.types.GensPickaxe;
import me.opaque.genstools.tools.types.GensSword;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;

public class ToolEventListener implements Listener {
    private final GensTools plugin;

    public ToolEventListener(GensTools plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBlockBreak(BlockBreakEvent event) {
        // Process enchantment effects
        processEvent(event);

        // Add experience from mining
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        // Skip if not a GensTool
        if (!GensTool.isGensTool(item)) {
            return;
        }

        // Check if this is a pickaxe by checking the tool ID
        String toolId = GensTool.getToolId(item);
        if (toolId == null) return;

        GensTool tool = plugin.getToolManager().getToolById(toolId);
        if (!(tool instanceof GensPickaxe)) {
            return; // Not a pickaxe
        }

        // Get experience value for this block
        Material material = event.getBlock().getType();
        int expValue = plugin.getConfigManager().getBlockExpValue(material);

        if (expValue <= 0) {
            return; // No experience for this block
        }

        // Apply any global boosters
        double expMultiplier = plugin.getConfigManager().getGlobalExpMultiplier();
        expValue = (int) Math.round(expValue * expMultiplier);

        // Add experience to the tool
        boolean leveledUp = GensTool.addExperience(item, expValue);

        // Show level up message and effects if configured
        if (leveledUp) {
            handleLevelUp(player, item);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Process enchantment effects
        processEvent(event);

        // Check if damage was done by a player
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }

        // Add experience from combat
        ItemStack item = player.getInventory().getItemInMainHand();

        // Skip if not a GensTool
        if (!GensTool.isGensTool(item)) {
            return;
        }

        // Check if this is a sword by checking the tool ID
        String toolId = GensTool.getToolId(item);
        if (toolId == null) return;

        GensTool tool = plugin.getToolManager().getToolById(toolId);
        if (!(tool instanceof GensSword)) {
            return; // Not a sword
        }

        // Check if the damaged entity is a living entity
        if (!(event.getEntity() instanceof LivingEntity livingEntity)) {
            return;
        }

        // Calculate experience based on damage and entity type
        int baseExp = (int) Math.round(event.getFinalDamage() * 10);

        // Add bonus for entity type
        int entityBonus = plugin.getConfigManager().getMobExpBonus(livingEntity.getType());
        int totalExp = baseExp + entityBonus;

        // Apply any global boosters
        double expMultiplier = plugin.getConfigManager().getGlobalExpMultiplier();
        totalExp = (int) Math.round(totalExp * expMultiplier);

        // Add experience to the tool
        boolean leveledUp = GensTool.addExperience(item, totalExp);

        // Show level up message and effects if configured
        if (leveledUp) {
            handleLevelUp(player, item);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDeath(EntityDeathEvent event) {
        // Get the entity that died
        LivingEntity entity = event.getEntity();

        // Get the killer (if it's a player)
        Player player = entity.getKiller();
        if (player == null) {
            return;
        }

        // Process enchantment effects for Shard Greed and any other death-related enchantments
        processEvent(event);

        // Get the item in player's hand
        ItemStack item = player.getInventory().getItemInMainHand();

        // Skip if not a GensTool
        if (!GensTool.isGensTool(item)) {
            return;
        }

        // Check if this is a sword by checking the tool ID
        String toolId = GensTool.getToolId(item);
        if (toolId == null) return;

        GensTool tool = plugin.getToolManager().getToolById(toolId);
        if (!(tool instanceof GensSword)) {
            return; // Not a sword
        }

        // Add death-specific experience bonus
        // Calculate base kill experience using config value
        int baseKillExp = (int)(entity.getMaxHealth() * plugin.getConfigManager().getBaseKillExpPerHealth());

        // Add a bonus based on entity type (more XP for harder mobs)
        int entityTypeBonus = plugin.getConfigManager().getMobExpBonus(entity.getType());

        // Special bonus for boss mobs (check for custom name or other indicator)
        int bossBonus = 0;
        if (entity.isCustomNameVisible() || entity.getCustomName() != null) {
            bossBonus = plugin.getConfigManager().getBossKillBonus();
        }

        // Calculate total kill experience
        int totalKillExp = baseKillExp + entityTypeBonus + bossBonus;

        // Apply any global experience multipliers
        double expMultiplier = plugin.getConfigManager().getGlobalExpMultiplier();
        totalKillExp = (int) Math.round(totalKillExp * expMultiplier);

        // Add experience to the tool
        boolean leveledUp = GensTool.addExperience(item, totalKillExp);

        // Show level up message and effects if configured
        if (leveledUp) {
            handleLevelUp(player, item);
        }

        // Optionally show the player how much XP they gained
        if (plugin.getConfigManager().isShowExpGainMessages()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&a+" + totalKillExp + " XP &7for killing a &f" +
                            entity.getType().toString().toLowerCase().replace("_", " ")));
        }
    }

    /**
     * Process any event that might trigger enchantment effects
     *
     * @param event The event to process
     */
    private void processEvent(Event event) {
        // Check if this is a player-based event
        Player player = null;

        if (event instanceof BlockBreakEvent) {
            player = ((BlockBreakEvent) event).getPlayer();
        } else if (event instanceof EntityDamageByEntityEvent damageEvent) {
            if (damageEvent.getDamager() instanceof Player) {
                player = (Player) damageEvent.getDamager();
            }
        } else if (event instanceof EntityDeathEvent deathEvent) {
            player = deathEvent.getEntity().getKiller();
        }

        if (player == null) {
            return;
        }

        // Get the item in the player's hand
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getItemMeta() == null) {
            return;
        }

        // Check if this is a GensTool
        if (!GensTool.isGensTool(item)) {
            return;
        }

        // Get the enchantments on this tool
        Map<String, Integer> enchants = GensTool.getEnchantments(item);
        if (enchants.isEmpty()) {
            return;
        }

        // Process each enchantment
        for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
            String enchantId = entry.getKey();
            int level = entry.getValue();

            CustomEnchant enchant = plugin.getToolManager().getEnchantById(enchantId);
            if (enchant == null) {
                continue;
            }

            // Check if this enchantment can handle this event type
            if (enchant.canHandleEvent(event.getClass())) {
                enchant.handleEffect(event, level);
            }
        }
    }

    /**
     * Handle tool level up effects and messages
     *
     * @param player The player who owns the tool
     * @param item The tool that leveled up
     */
    private void handleLevelUp(Player player, ItemStack item) {
        // Check plugin configuration for level up settings
        if (!plugin.getConfigManager().isShowLevelUpMessages() &&
                !plugin.getConfigManager().isShowLevelUpEffects()) {
            return;
        }

        int level = GensTool.getLevel(item);

        // Show level up message
        if (plugin.getConfigManager().isShowLevelUpMessages()) {
            String message = plugin.getConfigManager().getLevelUpMessage()
                    .replace("{level}", String.valueOf(level))
                    .replace("{player}", player.getName());

            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        }

        // Play level up effects
        if (plugin.getConfigManager().isShowLevelUpEffects()) {
            // Play sound
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

            // Add additional effects based on level
            if (level % 10 == 0) {
                // Special effect for milestone levels (10, 20, 30, etc.)
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            }
        }
    }
}