package me.opaque.genstools.listeners;

import me.opaque.genstools.GensTools;
import me.opaque.genstools.enchants.CustomEnchant;
import me.opaque.genstools.tools.GensTool;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
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
        processEvent(event);
        // TODO: Add experience to tools when mining
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        processEvent(event);
        // TODO: Add experience to tools when fighting
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
}