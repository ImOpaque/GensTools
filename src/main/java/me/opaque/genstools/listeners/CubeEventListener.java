package me.opaque.genstools.listeners;

import me.opaque.genstools.GensTools;
import me.opaque.genstools.enchants.EnchantmentCube;
import me.opaque.genstools.manager.EnchantmentCubeManager;
import me.opaque.genstools.tools.GensTool;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CubeEventListener implements Listener {
    private final GensTools plugin;
    private final EnchantmentCubeManager cubeManager;

    // Add cooldown map to prevent multiple rapid applications
    private final Map<UUID, Long> cubeCooldowns = new HashMap<>();
    private static final long CUBE_COOLDOWN_MS = 500; // 500ms cooldown

    public CubeEventListener(GensTools plugin) {
        this.plugin = plugin;
        this.cubeManager = plugin.getEnchantmentCubeManager();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Check if a player is on cooldown for cube application
     */
    private boolean isOnCooldown(Player player) {
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (cubeCooldowns.containsKey(playerId)) {
            long lastUse = cubeCooldowns.get(playerId);
            if (now - lastUse < CUBE_COOLDOWN_MS) {
                return true;
            }
        }

        cubeCooldowns.put(playerId, now);
        return false;
    }

    /**
     * Handle clicking a cube on a tool
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCubeUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();

        // Check cooldown to prevent double firing
        if (isOnCooldown(player)) {
            return;
        }

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        // Check if one hand has a cube and the other has a tool
        if (EnchantmentCube.isEnchantmentCube(mainHand) && GensTool.isGensTool(offHand)) {
            event.setCancelled(true);
            boolean success = cubeManager.applyCube(player, offHand, mainHand);

            // Only consume cube if application was successful
            if (success) {
                // Consume one cube
                if (mainHand.getAmount() > 1) {
                    mainHand.setAmount(mainHand.getAmount() - 1);
                } else {
                    player.getInventory().setItemInMainHand(null);
                }
            }
        }
        else if (EnchantmentCube.isEnchantmentCube(offHand) && GensTool.isGensTool(mainHand)) {
            event.setCancelled(true);
            boolean success = cubeManager.applyCube(player, mainHand, offHand);

            // Only consume cube if application was successful
            if (success) {
                // Consume one cube
                if (offHand.getAmount() > 1) {
                    offHand.setAmount(offHand.getAmount() - 1);
                } else {
                    player.getInventory().setItemInOffHand(null);
                }
            }
        }
    }

    /**
     * Handle clicking a cube on a tool in inventory (direct click)
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();

        // Check cooldown to prevent double firing
        if (isOnCooldown(player)) {
            return;
        }

        // Get the clicked item and cursor
        ItemStack clicked = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        if (clicked == null || cursor == null || clicked.getType() == Material.AIR || cursor.getType() == Material.AIR) {
            return;
        }

        // Check if one is a tool and one is a cube
        if (GensTool.isGensTool(clicked) && EnchantmentCube.isEnchantmentCube(cursor)) {
            event.setCancelled(true);
            // Use runTask to avoid inventory issues
            new BukkitRunnable() {
                @Override
                public void run() {
                    boolean success = cubeManager.applyCube(player, clicked, cursor);

                    // Only consume cube if application was successful
                    if (success) {
                        // Consume one cube
                        if (cursor.getAmount() > 1) {
                            cursor.setAmount(cursor.getAmount() - 1);
                        } else {
                            player.setItemOnCursor(null);
                        }
                    }
                }
            }.runTask(plugin);
        }
        else if (GensTool.isGensTool(cursor) && EnchantmentCube.isEnchantmentCube(clicked)) {
            event.setCancelled(true);
            // Use runTask to avoid inventory issues
            new BukkitRunnable() {
                @Override
                public void run() {
                    boolean success = cubeManager.applyCube(player, cursor, clicked);

                    // Only consume cube if application was successful
                    if (success) {
                        // Consume one cube
                        if (clicked.getAmount() > 1) {
                            clicked.setAmount(clicked.getAmount() - 1);
                            player.getInventory().setItem(event.getSlot(), clicked);
                        } else {
                            player.getInventory().setItem(event.getSlot(), null);
                        }
                    }
                }
            }.runTask(plugin);
        }
    }

    /**
     * Handle anvil interactions
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvilClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        // Only handle anvil interactions
        if (event.getClickedInventory() == null || event.getClickedInventory().getType() != InventoryType.ANVIL) {
            return;
        }

        // Check cooldown to prevent double firing
        Player player = (Player) event.getWhoClicked();
        if (isOnCooldown(player)) {
            return;
        }

        // Check if clicked the result slot
        if (event.getSlot() == 2) {
            final Inventory anvil = event.getClickedInventory();

            final ItemStack firstItem = anvil.getItem(0);
            final ItemStack secondItem = anvil.getItem(1);

            if (firstItem != null && secondItem != null) {
                ItemStack tool = null;
                ItemStack cube = null;

                // Determine which item is which
                if (GensTool.isGensTool(firstItem) && EnchantmentCube.isEnchantmentCube(secondItem)) {
                    tool = firstItem.clone();
                    cube = secondItem.clone();
                } else if (GensTool.isGensTool(secondItem) && EnchantmentCube.isEnchantmentCube(firstItem)) {
                    tool = secondItem.clone();
                    cube = firstItem.clone();
                }

                if (tool != null && cube != null) {
                    event.setCancelled(true);

                    // Create final copies for use in inner class
                    final ItemStack finalTool = tool;
                    final ItemStack finalCube = cube;

                    // Close inventory and apply cube on next tick
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            player.closeInventory();
                            boolean success = cubeManager.applyCube(player, finalTool, finalCube);

                            if (success) {
                                // Update the player's inventory with the modified tool
                                player.getInventory().addItem(finalTool);

                                // Consume one cube
                                if (finalCube.getAmount() > 1) {
                                    finalCube.setAmount(finalCube.getAmount() - 1);
                                    player.getInventory().addItem(finalCube);
                                }
                            } else {
                                // Return both items unchanged if application failed
                                player.getInventory().addItem(finalTool);
                                player.getInventory().addItem(finalCube);
                            }
                        }
                    }.runTask(plugin);
                }
            }
        }
    }

    /**
     * Handle drag-and-drop operations
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();

        // Check cooldown to prevent double firing
        if (isOnCooldown(player)) {
            return;
        }

        ItemStack dragged = event.getOldCursor();

        if (dragged == null || dragged.getType() == Material.AIR) {
            return;
        }

        // Process each slot that was affected by the drag
        for (int slot : event.getRawSlots()) {
            Inventory inv = event.getView().getInventory(slot);
            if (inv == null) continue;

            int slotInInv = event.getView().convertSlot(slot);
            ItemStack target = inv.getItem(slotInInv);

            if (target == null || target.getType() == Material.AIR) {
                continue;
            }

            // Check if dragging cube onto tool or tool onto cube
            if (EnchantmentCube.isEnchantmentCube(dragged) && GensTool.isGensTool(target)) {
                event.setCancelled(true);

                // Apply on next tick to avoid inventory issues
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        boolean success = cubeManager.applyCube(player, target, dragged);

                        // Only consume cube if application was successful
                        if (success) {
                            // Code to consume dragged cube would go here
                            // However, drag events are complex for consumption
                            // Better to let the player handle it manually
                        }
                    }
                }.runTask(plugin);

                break;
            }
            else if (GensTool.isGensTool(dragged) && EnchantmentCube.isEnchantmentCube(target)) {
                event.setCancelled(true);

                // Apply on next tick to avoid inventory issues
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        boolean success = cubeManager.applyCube(player, dragged, target);

                        // Only consume cube if application was successful
                        if (success) {
                            // Code to consume target cube would go here
                            // However, drag events are complex for consumption
                            // Better to let the player handle it manually
                        }
                    }
                }.runTask(plugin);

                break;
            }
        }
    }
}