package me.opaque.genstools.gui;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import me.opaque.genstools.GensTools;
import me.opaque.genstools.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public abstract class Menu {
    protected final GensTools plugin;
    protected final Player player;
    protected String title;
    protected final int rows;
    protected Inventory inventory;
    protected final Map<Integer, Consumer<InventoryClickEvent>> items = new HashMap<>();

    // Static map to track all active menus by player UUID
    protected static final Map<UUID, Menu> activeMenus = new HashMap<>();

    // Store the original open window packet as a template for title updates
    // This is a per-player map since different players may have different protocol versions
    private static final Map<UUID, PacketContainer> openWindowPacketTemplates = new HashMap<>();

    public Menu(GensTools plugin, Player player, String title, int rows) {
        this.plugin = plugin;
        this.player = player;
        this.title = title;
        this.rows = rows;

        // Register packet listener for this player if not already done
        if (!openWindowPacketTemplates.containsKey(player.getUniqueId())) {
            registerPacketListener();
        }
    }

    /**
     * Register a packet listener to capture inventory open packet structure
     */
    private void registerPacketListener() {
        ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();

        protocolManager.addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL,
                PacketType.Play.Server.OPEN_WINDOW) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (event.getPlayer().getUniqueId().equals(player.getUniqueId())) {
                    // Store this packet for later use
                    openWindowPacketTemplates.put(player.getUniqueId(), event.getPacket().deepClone());
                    plugin.getLogger().info("Captured open window packet for player " +
                            player.getName() + " for later title updates");
                }
            }
        });
    }

    /**
     * Build the menu contents - implemented by each menu
     */
    protected abstract void build();

    /**
     * Open the menu for the first time
     */
    public void open() {
        // Register this menu as active for the player
        activeMenus.put(player.getUniqueId(), this);

        // Create inventory with the title
        inventory = Bukkit.createInventory(null, rows * 9, ChatColor.translateAlternateColorCodes('&', title));

        // Build items
        build();

        // Open for the player
        player.openInventory(inventory);
    }

    /**
     * Update the menu content without closing/reopening
     */
    public void refresh() {
        // Clear current items map and inventory contents
        items.clear();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.clear(i);
        }

        // Rebuild with new content
        build();
    }

    /**
     * Update inventory title using the captured packet as a template
     */
    private void updateTitleWithTemplate(String newTitle) {
        PacketContainer template = openWindowPacketTemplates.get(player.getUniqueId());
        if (template == null) {
            plugin.getLogger().warning("No window packet template available for title update for player " + player.getName());
            return;
        }

        try {
            // Clone the packet so we don't modify the template
            PacketContainer packet = template.deepClone();

            // Get current container ID
            int containerId = player.getOpenInventory().getTopInventory().hashCode() & 0xFF;

            // Update the window ID to match current window
            packet.getIntegers().write(0, containerId);

            // Update just the title component
            // This should work for most versions, but might need adjustment for 1.21
            try {
                WrappedChatComponent component = WrappedChatComponent.fromText(
                        ChatColor.translateAlternateColorCodes('&', newTitle));
                packet.getChatComponents().write(0, component);
            } catch (Exception e) {
                plugin.getLogger().warning("Could not set chat component, trying alternative method: " + e.getMessage());

                // Try alternative method if available
                try {
                    packet.getStrings().write(0, ChatColor.translateAlternateColorCodes('&', newTitle));
                } catch (Exception e2) {
                    plugin.getLogger().warning("Alternative title update method also failed: " + e2.getMessage());
                }
            }

            // Send the packet
            ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
            protocolManager.sendServerPacket(player, packet);

            // Update our title field
            this.title = newTitle;

            plugin.getLogger().info("Successfully sent menu title update packet to " + player.getName());

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to update inventory title with template: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Close the menu
     */
    public void close() {
        activeMenus.remove(player.getUniqueId());
        player.closeInventory();
    }

    /**
     * Clean up resources when player logs out
     */
    public static void cleanupPlayer(UUID playerId) {
        activeMenus.remove(playerId);
        openWindowPacketTemplates.remove(playerId);
    }

    /**
     * Set an item in the inventory with no click handler
     */
    protected void setItem(int slot, ItemStack item) {
        inventory.setItem(slot, item);
    }

    /**
     * Set an item with a click handler
     */
    protected void setItem(int slot, ItemStack item, Consumer<InventoryClickEvent> handler) {
        inventory.setItem(slot, item);
        if (handler != null) {
            items.put(slot, handler);
        }
    }

    /**
     * Create an item with name and lore
     */
    protected ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null && !name.isEmpty()) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            }
            if (lore != null && !lore.isEmpty()) {
                List<String> coloredLore = lore.stream()
                        .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                        .collect(Collectors.toList());
                meta.setLore(coloredLore);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Fill empty slots with a material
     */
    protected void fillEmptySlots(Material material) {
        ItemStack filler = new ItemStack(material);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            filler.setItemMeta(meta);
        }

        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }

    /**
     * Handle click events for this menu
     */
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        Consumer<InventoryClickEvent> handler = items.get(event.getSlot());
        if (handler != null) {
            handler.accept(event);
        }
    }

    /**
     * Get the inventory
     */
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * Get the active menu for a player
     */
    public static Menu getActiveMenu(UUID playerId) {
        return activeMenus.get(playerId);
    }

    /**
     * Update inventory title using direct packet creation for Minecraft 1.21
     */
    private void updateInventoryTitle(String newTitle) {
        try {
            ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();

            // Get the current container ID
            int containerId = player.getOpenInventory().getTopInventory().hashCode() & 0xFF;

            Utils.logDebug("Updating title for container ID: " + containerId);

            // Create a brand new packet
            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.OPEN_WINDOW);

            // Set the container ID
            packet.getIntegers().write(0, containerId);

            // Set the menu type - for 1.21 we need to use the correct enum by registry ID
            // Most chest inventories use ID 0 to 6 based on rows (9x1 to 9x6)
            // For a standard chest with rows*9 slots, the ID is (rows - 1)
            int menuTypeId = Math.min(rows - 1, 5); // Ensure it's within valid range (0-5)

            // Debug packet structure
            Utils.logDebug("Packet structure before modification:");
            for (int i = 0; i < packet.getModifier().getFields().size(); i++) {
                try {
                    Object value = packet.getModifier().read(i);
                    Utils.logDebug("Field " + i + ": " + (value == null ? "null" : value.getClass().getSimpleName() + " - " + value));
                } catch (Exception e) {
                    Utils.logDebug("Field " + i + ": Error reading: " + e.getMessage());
                }
            }

            // Use a special helper method to set the menu type properly for 1.21
            setMenuTypeById(packet, menuTypeId);

            // Set the title component
            WrappedChatComponent component = WrappedChatComponent.fromText(
                    ChatColor.translateAlternateColorCodes('&', newTitle));
            packet.getChatComponents().write(0, component);

            // Debug final packet structure
            Utils.logDebug("Packet structure after modification:");
            for (int i = 0; i < packet.getModifier().getFields().size(); i++) {
                try {
                    Object value = packet.getModifier().read(i);
                    Utils.logDebug("Field " + i + ": " + (value == null ? "null" : value.getClass().getSimpleName() + " - " + value));
                } catch (Exception e) {
                    Utils.logDebug("Field " + i + ": Error reading: " + e.getMessage());
                }
            }

            // Send the packet
            protocolManager.sendServerPacket(player, packet);

            // Update our title field
            this.title = newTitle;

            Utils.logDebug("Successfully sent menu title update packet with menu type ID: " + menuTypeId);

        } catch (Exception e) {
            Utils.logError("Failed to update inventory title: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Helper method to set the MenuType by registry ID
     */
    private void setMenuTypeById(PacketContainer packet, int typeId) {
        try {
            // First try using reflection to get the proper MenuType enum value
            Class<?> registryClass = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
            Field menuTypeRegistryField = registryClass.getDeclaredField("MENU");
            menuTypeRegistryField.setAccessible(true);
            Object menuRegistry = menuTypeRegistryField.get(null);

            // Use the registry to get the MenuType by ID
            Class<?> registryApiClass = Class.forName("net.minecraft.core.Registry");
            Method byIdMethod = registryApiClass.getDeclaredMethod("byId", int.class);
            byIdMethod.setAccessible(true);
            Object menuType = byIdMethod.invoke(menuRegistry, typeId);

            // Set the menu type in the packet
            packet.getModifier().write(1, menuType);
            Utils.logDebug("Successfully set menu type using registry lookup");

        } catch (Exception e) {
            Utils.logDebug("Registry lookup failed: " + e.getMessage());

            // Alternative approach: try to directly modify field 1 with a custom wrapper
            try {
                // Try writing a type value via reflection
                Field field = packet.getHandle().getClass().getDeclaredField("menuType");
                field.setAccessible(true);

                // Get Containers class by reflection
                Class<?> containersClass = null;
                try {
                    containersClass = Class.forName("net.minecraft.world.inventory.MenuType");
                } catch (ClassNotFoundException ex) {
                    // Try fallback paths
                    try {
                        containersClass = Class.forName("net.minecraft.server.v1_21_R1.MenuType");
                    } catch (ClassNotFoundException ex2) {
                        // One more try
                        containersClass = Class.forName("net.minecraft.world.inventory.Containers");
                    }
                }

                // Get the GENERIC_9x[1-6] field
                Field containerField = null;
                String fieldName = "GENERIC_9x" + Math.min(rows, 6);
                try {
                    containerField = containersClass.getDeclaredField(fieldName);
                } catch (NoSuchFieldException ex) {
                    // Try alternative naming
                    if (rows <= 6) {
                        containerField = containersClass.getDeclaredField("GENERIC_9x" + rows);
                    } else {
                        containerField = containersClass.getDeclaredField("GENERIC_9x6");
                    }
                }

                containerField.setAccessible(true);
                Object containerType = containerField.get(null);

                // Set the field value
                field.set(packet.getHandle(), containerType);
                Utils.logDebug("Successfully set menu type using direct field reflection");

            } catch (Exception e2) {
                plugin.getLogger().severe("All menu type setting approaches failed: " + e2.getMessage());
                e2.printStackTrace();
            }
        }
    }
    /**
     * Switch to a different menu type without closing the inventory
     * Uses ProtocolLib for direct packet manipulation
     */
    public void switchTo(Menu nextMenu) {
        Utils.logDebug("Switching from " + this.getClass().getSimpleName() +
                " to " + nextMenu.getClass().getSimpleName() + " with ProtocolLib");

        try {
            // Register the new menu as active
            activeMenus.put(player.getUniqueId(), nextMenu);

            // Point next menu to our current inventory
            nextMenu.inventory = this.inventory;

            // Clear our item handlers (but not inventory items yet)
            this.items.clear();

            // If we're switching back to a ToolEnchantMenu, refresh its enchantment data
            if (nextMenu instanceof ToolEnchantMenu toolMenu) {
                toolMenu.refreshEnchantmentData();
                Utils.logDebug("Refreshed tool enchantment data during menu switch");
            }

            // Get window ID for the player's current container
            int windowId = getPlayerContainerId(player);
            Utils.logDebug("Current window ID: " + windowId);

            // 1. First send window title packet using ProtocolLib
            updateTitleWithProtocolLib(player, windowId, nextMenu.title);

            // 2. Now clear and rebuild inventory with a delay
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // Clear all items
                Utils.logDebug("Clearing inventory items");
                for (int i = 0; i < inventory.getSize(); i++) {
                    inventory.setItem(i, null);
                }

                // Have the next menu build its contents
                Utils.logDebug("Building new menu items");
                nextMenu.build();

                // Force client update
                player.updateInventory();

                Utils.logDebug("Menu switch completed with ProtocolLib");
            }, 1L);

        } catch (Exception e) {
            Utils.logError("Error during ProtocolLib menu switch: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get the container ID for the player's current open inventory
     */
    private int getPlayerContainerId(Player player) {
        try {
            // Get EntityPlayer from CraftPlayer
            Object entityPlayer = player.getClass().getMethod("getHandle").invoke(player);

            // Get active container
            Object container = null;
            try {
                // 1.21 field name
                container = entityPlayer.getClass().getDeclaredField("bV").get(entityPlayer);
            } catch (NoSuchFieldException e) {
                try {
                    // Try fallbacks
                    container = entityPlayer.getClass().getDeclaredField("containerMenu").get(entityPlayer);
                } catch (NoSuchFieldException e2) {
                    container = entityPlayer.getClass().getDeclaredField("activeContainer").get(entityPlayer);
                }
            }

            // Get window ID
            int windowId = -1;
            try {
                // 1.21 field
                windowId = container.getClass().getDeclaredField("j").getInt(container);
            } catch (NoSuchFieldException e) {
                try {
                    // Try fallbacks
                    windowId = container.getClass().getDeclaredField("windowId").getInt(container);
                } catch (NoSuchFieldException e2) {
                    windowId = container.getClass().getDeclaredField("containerId").getInt(container);
                }
            }

            return windowId;
        } catch (Exception e) {
            Utils.logError("Error getting container ID: " + e.getMessage());
            e.printStackTrace();
            return 1; // Default window ID
        }
    }

    /**
     * Update inventory title using ProtocolLib packets
     */
    private void updateTitleWithProtocolLib(Player player, int windowId, String title) {
        try {
            // Get ProtocolLib's ProtocolManager
            com.comphenix.protocol.ProtocolManager protocolManager =
                    com.comphenix.protocol.ProtocolLibrary.getProtocolManager();

            // Create a window title packet
            PacketContainer packet =
                    protocolManager.createPacket(com.comphenix.protocol.PacketType.Play.Server.OPEN_WINDOW);

            // Set window ID
            packet.getIntegers().write(0, windowId);

            // Set window type (for 1.21)
            packet.getStrings().write(0, "minecraft:generic_9x" + rows);

            // Create and set the title component for 1.21
            String coloredTitle = ChatColor.translateAlternateColorCodes('&', title);
            packet.getChatComponents().write(0,
                    com.comphenix.protocol.wrappers.WrappedChatComponent.fromText(coloredTitle));

            // Send the packet
            Utils.logDebug("Sending title packet for window ID " + windowId);
            protocolManager.sendServerPacket(player, packet);

        } catch (Exception e) {
            Utils.logError("Error sending title packet: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Debug method to be called from build() in each menu implementation
     */
    protected void debugBuild(String menuName) {
        Utils.logDebug("Building " + menuName + " menu");

        // Check if inventory is null
        if (inventory == null) {
            Utils.logError("CRITICAL: Inventory is null during build of " + menuName);
            return;
        }

        // Check if player has the inventory open
        if (!player.getOpenInventory().getTopInventory().equals(inventory)) {
            Utils.logDebug("WARNING: Player doesn't have the menu inventory open");
        }

        // Check if we're the active menu
        Menu activeMenu = activeMenus.get(player.getUniqueId());
        if (activeMenu != this) {
            Utils.logDebug("WARNING: This menu is not the active menu for player. Active: " +
                    (activeMenu != null ? activeMenu.getClass().getSimpleName() : "null"));
        }
    }

    /**
     * Prepare items in memory without applying to inventory
     */
    protected void prepareItems() {
        Utils.logDebug("Preparing items for " + this.getClass().getSimpleName());
        this.items.clear();
        build();
    }

    /**
     * Apply prepared items to the inventory
     */
    protected void applyPreparedItems() {
        Utils.logDebug("Applying prepared items to inventory");
        // Note: The inventory should already have the items added during build()
        // This method is just a hook for any post-processing
    }
}