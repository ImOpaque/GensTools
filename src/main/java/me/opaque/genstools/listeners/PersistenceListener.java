package me.opaque.genstools.listeners;

import me.opaque.genstools.GensTools;
import me.opaque.genstools.tools.GensTool;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Listener for persistence-related events
 */
public class PersistenceListener implements Listener {
    private final GensTools plugin;

    public PersistenceListener(GensTools plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Handle player join
        plugin.getToolPersistenceManager().handlePlayerJoin(event.getPlayer());

        // Schedule a task to check inventory for tools
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            checkPlayerInventory(event.getPlayer());
        }, 20L); // 1 second delay
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Handle player quit
        plugin.getToolPersistenceManager().handlePlayerQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        // Only care about players
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        // Check if item is a GensTool
        ItemStack item = event.getItem().getItemStack();
        if (GensTool.isGensTool(item)) {
            // Register the tool
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                plugin.getToolPersistenceManager().registerTool(player, item);
            }, 1L);
        }
    }

    /**
     * Check a player's inventory for GensTools and register/update them
     * @param player The player to check
     */
    private void checkPlayerInventory(Player player) {
        // Check all items in inventory
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && GensTool.isGensTool(item)) {
                // Update the tool from storage
                boolean updated = plugin.getToolPersistenceManager().updateToolFromStorage(player, item);

                if (!updated) {
                    // If not updated (new tool), register it
                    plugin.getToolPersistenceManager().registerTool(player, item);
                }
            }
        }
    }
}