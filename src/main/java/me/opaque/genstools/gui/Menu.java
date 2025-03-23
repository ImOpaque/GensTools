package me.opaque.genstools.gui;

import me.opaque.genstools.GensTools;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A highly optimized and extensible GUI menu framework
 */
public abstract class Menu {
    // Map to track open menus for each player
    private static final Map<UUID, Menu> OPEN_MENUS = new HashMap<>();

    // Core menu components
    protected final GensTools plugin;
    protected final Player player;
    protected final Inventory inventory;
    protected final Map<Integer, MenuItem> items = new HashMap<>();

    /**
     * Create a new menu
     */
    public Menu(GensTools plugin, Player player, String title, int rows) {
        this.plugin = plugin;
        this.player = player;
        this.inventory = Bukkit.createInventory(null, rows * 9, ChatColor.translateAlternateColorCodes('&', title));
    }

    /**
     * Open the menu for the player
     */
    public void open() {
        // Build the menu
        build();

        // Register this menu as open
        OPEN_MENUS.put(player.getUniqueId(), this);

        // Open the inventory
        player.openInventory(inventory);
    }

    /**
     * Close the menu
     */
    public void close() {
        OPEN_MENUS.remove(player.getUniqueId());
        player.closeInventory();
    }

    /**
     * Build the menu contents
     */
    protected abstract void build();

    /**
     * Handle a click in the menu
     */
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);

        int slot = event.getRawSlot();
        MenuItem item = items.get(slot);
        if (item != null && item.getClickHandler() != null) {
            item.getClickHandler().accept(event);
        }
    }

    /**
     * Fill the menu with a border
     */
    protected void addBorder(Material material) {
        int size = inventory.getSize();
        int rows = size / 9;

        // Top and bottom rows
        for (int i = 0; i < 9; i++) {
            setItem(i, createItem(material, " ", null));
            setItem(size - 9 + i, createItem(material, " ", null));
        }

        // Left and right columns
        for (int i = 1; i < rows - 1; i++) {
            setItem(i * 9, createItem(material, " ", null));
            setItem(i * 9 + 8, createItem(material, " ", null));
        }
    }

    /**
     * Fill all empty slots with a material
     */
    protected void fillEmptySlots(Material material) {
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                setItem(i, createItem(material, " ", null));
            }
        }
    }

    /**
     * Set an item in the menu with a click handler
     */
    protected void setItem(int slot, ItemStack item, Consumer<InventoryClickEvent> clickHandler) {
        inventory.setItem(slot, item);
        items.put(slot, new MenuItem(item, clickHandler));
    }

    /**
     * Set an item in the menu without a click handler
     */
    protected void setItem(int slot, ItemStack item) {
        setItem(slot, item, null);
    }

    /**
     * Create a simple item
     */
    protected ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (name != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        }

        if (lore != null) {
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) {
                coloredLore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(coloredLore);
        }

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Get the menu a player has open
     */
    public static Menu getOpenMenu(UUID playerUuid) {
        return OPEN_MENUS.get(playerUuid);
    }

    /**
     * Remove a player's open menu
     */
    public static void removeOpenMenu(UUID playerUuid) {
        OPEN_MENUS.remove(playerUuid);
    }

    /**
     * Class representing a menu item with a click handler
     */
    protected static class MenuItem {
        private final ItemStack item;
        private final Consumer<InventoryClickEvent> clickHandler;

        public MenuItem(ItemStack item, Consumer<InventoryClickEvent> clickHandler) {
            this.item = item;
            this.clickHandler = clickHandler;
        }

        public ItemStack getItem() {
            return item;
        }

        public Consumer<InventoryClickEvent> getClickHandler() {
            return clickHandler;
        }
    }
}