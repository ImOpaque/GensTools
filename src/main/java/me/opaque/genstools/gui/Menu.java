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
import java.util.*;
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

        if (this instanceof CubeRemovalMenu) {
            // Force exactly 3 rows for CubeRemovalMenu using direct reflection
            try {
                // Create the inventory first
                inventory = Bukkit.createInventory(null, 3 * 9, ChatColor.translateAlternateColorCodes('&', title));

                // Build items
                build();

                // Open the inventory
                player.openInventory(inventory);

                // Use direct NMS approach to force 3 rows
                forceInventoryRows(player, 3);

                Utils.logDebug("Opened CubeRemovalMenu with forced 3 rows");
            } catch (Exception e) {
                Utils.logError("Failed to open CubeRemovalMenu with 3 rows: " + e.getMessage());
                e.printStackTrace();

                // Fallback to regular inventory if reflection fails
                inventory = Bukkit.createInventory(null, 3 * 9, ChatColor.translateAlternateColorCodes('&', title));
                build();
                player.openInventory(inventory);
            }
        } else {
            // Normal menu opening for other menu types
            inventory = Bukkit.createInventory(null, rows * 9, ChatColor.translateAlternateColorCodes('&', title));
            build();
            player.openInventory(inventory);
        }
    }

    /**
     * Force a specific number of rows for an inventory using direct NMS code
     * This is a last resort method for CubeRemovalMenu
     */
    private void forceInventoryRows(Player player, int rows) {
        try {
            // Use ProtocolLib to send a custom OPEN_WINDOW packet
            ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();

            // Create a new packet for opening a window
            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.OPEN_WINDOW);

            // Get the container ID for the current inventory
            int windowId = player.getOpenInventory().getTopInventory().hashCode() & 0xFF;
            packet.getIntegers().write(0, windowId);

            // Set the inventory title
            WrappedChatComponent component = WrappedChatComponent.fromText(
                    ChatColor.translateAlternateColorCodes('&', title));
            packet.getChatComponents().write(0, component);

            // This is the critical part - we need to set the type based on rows
            // Try multiple approaches to support different MC versions

            // 1. Try setting the container type via registry for 1.19+
            try {
                // Get the registry ID for GENERIC_9x(rows)
                int containerId = rows - 1;  // 0 = GENERIC_9x1, 1 = GENERIC_9x2, etc.

                // First, try direct integer approach (works in some versions)
                try {
                    packet.getIntegers().write(1, containerId);
                    Utils.logDebug("Set container type using integer ID: " + containerId);
                } catch (Exception e) {
                    Utils.logDebug("Integer container type failed: " + e.getMessage());

                    // Next, try using the registry directly (for 1.21+)
                    try {
                        Class<?> registryClass = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
                        Field menuTypeRegistryField = registryClass.getDeclaredField("MENU");
                        menuTypeRegistryField.setAccessible(true);
                        Object menuRegistry = menuTypeRegistryField.get(null);

                        Class<?> registryApiClass = Class.forName("net.minecraft.core.Registry");
                        Method byIdMethod = registryApiClass.getMethod("byId", int.class);
                        Object menuType = byIdMethod.invoke(menuRegistry, containerId);

                        if (menuType != null) {
                            packet.getModifier().write(1, menuType);
                            Utils.logDebug("Set container type using registry object");
                        } else {
                            Utils.logWarning("Registry returned null container type for ID " + containerId);
                        }
                    } catch (Exception ex) {
                        Utils.logDebug("Registry approach failed: " + ex.getMessage());

                        // Try a direct reflection lookup of the MenuType field
                        try {
                            Class<?> containerTypes = Class.forName("net.minecraft.world.inventory.MenuType");
                            Field[] fields = containerTypes.getDeclaredFields();

                            // Look for fields that contain "GENERIC" and the row count
                            String fieldNameToFind = "GENERIC_9x" + rows;
                            for (Field field : fields) {
                                if (field.getName().contains(fieldNameToFind)) {
                                    field.setAccessible(true);
                                    Object menuType = field.get(null);
                                    if (menuType != null) {
                                        packet.getModifier().write(1, menuType);
                                        Utils.logDebug("Set container type using direct field access: " + field.getName());
                                        break;
                                    }
                                }
                            }
                        } catch (Exception e2) {
                            Utils.logDebug("Direct field access failed: " + e2.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                Utils.logWarning("Failed to set container type: " + e.getMessage());
            }

            // Send the packet after a short delay to override the previously opened inventory
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    protocolManager.sendServerPacket(player, packet);
                    Utils.logDebug("Sent custom OPEN_WINDOW packet to force " + rows + " rows");

                    // Force the client to update its inventory
                    Bukkit.getScheduler().runTaskLater(plugin, player::updateInventory, 1L);
                } catch (Exception e) {
                    Utils.logError("Failed to send custom packet: " + e.getMessage());
                    e.printStackTrace();
                }
            }, 1L);

        } catch (Exception e) {
            Utils.logError("Failed to force inventory rows: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Fix inventory size using ProtocolLib (primarily for CubeRemovalMenu)
     * @param desiredRows The number of rows to display (regardless of actual inventory size)
     */
    private void fixInventorySize(int desiredRows) {
        try {
            // Must be between 1-6 rows
            if (desiredRows < 1 || desiredRows > 6) {
                Utils.logWarning("Invalid row count: " + desiredRows + ", must be between 1-6");
                return;
            }

            Utils.logDebug("Fixing inventory size to " + desiredRows + " rows using direct packet replacement");

            // Get ProtocolManager
            ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();

            // We need to close the current inventory and reopen it with the correct size
            // This is more reliable than trying to modify the packet on the fly
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    // Get the current inventory contents
                    ItemStack[] contents = inventory.getContents();

                    // Store the active menu for this player - it should be this menu
                    Menu activeMenu = activeMenus.get(player.getUniqueId());

                    // Close the current inventory
                    player.closeInventory();

                    // Create a new inventory with the desired size
                    Inventory newInventory = Bukkit.createInventory(null, desiredRows * 9,
                            ChatColor.translateAlternateColorCodes('&', title));

                    // Copy items from old inventory to new, up to the capacity of the new inventory
                    for (int i = 0; i < Math.min(contents.length, newInventory.getSize()); i++) {
                        if (contents[i] != null) {
                            newInventory.setItem(i, contents[i]);
                        }
                    }

                    // Update inventory reference
                    inventory = newInventory;

                    // Restore active menu
                    activeMenus.put(player.getUniqueId(), activeMenu != null ? activeMenu : this);

                    // Open the new inventory
                    player.openInventory(newInventory);

                    Utils.logDebug("Reopened inventory with " + desiredRows + " rows");
                } catch (Exception e) {
                    Utils.logError("Error reopening inventory with fixed size: " + e.getMessage());
                    e.printStackTrace();
                }
            }, 1L);
        } catch (Exception e) {
            Utils.logError("Error setting up inventory size fix: " + e.getMessage());
            e.printStackTrace();
        }
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
     * Special method for opening the CubeRemovalMenu with exactly 3 rows
     */
    public void openCubeMenu() {
        if (!(this instanceof CubeRemovalMenu)) {
            // Only for CubeRemovalMenu
            open();
            return;
        }

        Utils.logDebug("Opening CubeRemovalMenu");

        // Register this menu as active
        activeMenus.put(player.getUniqueId(), this);

        // Create the inventory with the correct number of slots
        inventory = Bukkit.createInventory(null, 27, ChatColor.translateAlternateColorCodes('&', title));
        Utils.logDebug("Created inventory with " + inventory.getSize() + " slots");

        // Build the menu content
        build();

        // Open the inventory
        player.openInventory(inventory);

        // Force an inventory update after a short delay to ensure everything displays correctly
        Bukkit.getScheduler().runTaskLater(plugin, player::updateInventory, 1L);
    }

    /**
     * Count non-empty items in the inventory
     */
    private int countItems() {
        int count = 0;
        if (inventory != null) {
            for (int i = 0; i < inventory.getSize(); i++) {
                ItemStack item = inventory.getItem(i);
                if (item != null && item.getType() != Material.AIR) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Helper method to get the EntityPlayer from a Bukkit Player
     */
    private Object getEntityPlayer(Player player) {
        try {
            Method getHandle = player.getClass().getMethod("getHandle");
            return getHandle.invoke(player);
        } catch (Exception e) {
            Utils.logDebug("Could not get EntityPlayer: " + e.getMessage());
            return null;
        }
    }

    /**
     * Helper method to find a field by multiple possible names
     */
    private Field findField(Class<?> clazz, String... fieldNames) {
        for (String fieldName : fieldNames) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                if (field != null) {
                    return field;
                }
            } catch (NoSuchFieldException e) {
                // Try next name
            }
        }

        // Try superclass if available
        if (clazz.getSuperclass() != null) {
            return findField(clazz.getSuperclass(), fieldNames);
        }

        return null;
    }

    /**
     * Send a custom OPEN_WINDOW packet with the exact number of rows we want
     */
    private void sendCustomOpenWindowPacket(int rows) {
        try {
            ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();

            // Create the packet
            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.OPEN_WINDOW);

            // Get current container ID
            int windowId = player.getOpenInventory().getTopInventory().hashCode() & 0xFF;
            packet.getIntegers().write(0, windowId);

            // Set the title
            WrappedChatComponent component = WrappedChatComponent.fromText(
                    ChatColor.translateAlternateColorCodes('&', title));
            packet.getChatComponents().write(0, component);

            // Set the container type based on rows (0-indexed)
            // GENERIC_9x1 = 0, GENERIC_9x2 = 1, GENERIC_9x3 = 2, etc.
            int containerTypeId = rows - 1;

            // Try various approaches to set the container type
            boolean success = false;

            // Approach 1: Direct integer (works on some versions)
            try {
                packet.getIntegers().write(1, containerTypeId);
                success = true;
                Utils.logDebug("Set container type using integer ID");
            } catch (Exception e) {
                Utils.logDebug("Integer container type approach failed: " + e.getMessage());
            }

            // Approach 2: Using reflection to get the registry (for 1.21+)
            if (!success) {
                try {
                    Class<?> registryClass = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
                    Field menuTypeField = registryClass.getDeclaredField("MENU");
                    menuTypeField.setAccessible(true);
                    Object menuRegistry = menuTypeField.get(null);

                    Class<?> registryClass2 = Class.forName("net.minecraft.core.Registry");
                    Method byIdMethod = registryClass2.getMethod("byId", int.class);
                    Object menuType = byIdMethod.invoke(menuRegistry, containerTypeId);

                    if (menuType != null) {
                        packet.getModifier().write(1, menuType);
                        success = true;
                        Utils.logDebug("Set container type using registry lookup");
                    }
                } catch (Exception e) {
                    Utils.logDebug("Registry approach failed: " + e.getMessage());
                }
            }

            // Approach 3: Using reflection to directly access the menu type constants
            if (!success) {
                try {
                    Class<?> menuTypeClass = Class.forName("net.minecraft.world.inventory.MenuType");

                    // Try to find the field for the right generic container
                    Field[] fields = menuTypeClass.getDeclaredFields();
                    String fieldName = "GENERIC_9x" + rows;

                    for (Field field : fields) {
                        if (field.getName().contains(fieldName) ||
                                field.getName().equals("a") && containerTypeId == 0 ||
                                field.getName().equals("b") && containerTypeId == 1 ||
                                field.getName().equals("c") && containerTypeId == 2) {

                            field.setAccessible(true);
                            Object menuType = field.get(null);

                            if (menuType != null) {
                                packet.getModifier().write(1, menuType);
                                success = true;
                                Utils.logDebug("Set container type using direct field: " + field.getName());
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    Utils.logDebug("Direct field approach failed: " + e.getMessage());
                }
            }

            if (!success) {
                Utils.logWarning("Could not set container type using any method - menu may display incorrectly");
            }

            // Send the packet
            protocolManager.sendServerPacket(player, packet);
            Utils.logDebug("Sent custom OPEN_WINDOW packet with " + rows + " rows");

            // Force inventory update
            Bukkit.getScheduler().runTaskLater(plugin, player::updateInventory, 1L);

        } catch (Exception e) {
            Utils.logError("Failed to send custom OPEN_WINDOW packet: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Switch to a different menu without using packet manipulation
     * This avoids issues with Minecraft 1.21's registry system
     */
    public void switchTo(Menu nextMenu) {
        Utils.logDebug("Switching from " + this.getClass().getSimpleName() +
                " to " + nextMenu.getClass().getSimpleName() + " - safe method");

        try {
            // If we're switching to a CubeRemovalMenu, use special handling
            if (nextMenu instanceof CubeRemovalMenu) {
                // Close current menu
                activeMenus.remove(player.getUniqueId());
                player.closeInventory();

                // Open the cube menu with special handling
                nextMenu.openCubeMenu();
                return;
            }

            // If we're switching from a CubeRemovalMenu to another menu type,
            // we need to handle the inventory size change
            if (this instanceof CubeRemovalMenu) {
                // Close current menu
                activeMenus.remove(player.getUniqueId());
                player.closeInventory();

                // Create appropriate inventory for target menu
                if (nextMenu instanceof ToolEnchantMenu) {
                    nextMenu.inventory = Bukkit.createInventory(null, 54, ChatColor.translateAlternateColorCodes('&', nextMenu.title));
                } else {
                    nextMenu.inventory = Bukkit.createInventory(null, nextMenu.rows * 9, ChatColor.translateAlternateColorCodes('&', nextMenu.title));
                }

                // Register the new menu as active
                activeMenus.put(player.getUniqueId(), nextMenu);

                // If we're switching to a ToolEnchantMenu, refresh its enchantment data
                if (nextMenu instanceof ToolEnchantMenu toolMenu) {
                    toolMenu.refreshEnchantmentData();
                }

                // Build and open the new menu
                nextMenu.build();
                player.openInventory(nextMenu.inventory);

                // Force client update
                Bukkit.getScheduler().runTaskLater(plugin, player::updateInventory, 1L);

                return;
            }

            // Regular menu switching for other menu types
            // Register the new menu as active
            activeMenus.put(player.getUniqueId(), nextMenu);

            // Point next menu to our current inventory
            nextMenu.inventory = this.inventory;

            // Clear our item handlers
            this.items.clear();

            // If we're switching to a ToolEnchantMenu, refresh its enchantment data
            if (nextMenu instanceof ToolEnchantMenu toolMenu) {
                toolMenu.refreshEnchantmentData();
            }

            // Update title field
            this.title = nextMenu.title;

            // Now clear and rebuild inventory contents
            for (int i = 0; i < inventory.getSize(); i++) {
                inventory.setItem(i, null);
            }

            // Build new menu contents
            nextMenu.build();

            // Force client update
            player.updateInventory();

            Utils.logDebug("Menu switch completed safely");
        } catch (Exception e) {
            Utils.logError("Error during menu switch: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Update inventory title using specific Minecraft 1.21 registry technique
     */
    private boolean updateTitle121(Player player, int windowId, String title) {
        try {
            Utils.logDebug("Attempting 1.21 registry-aware title update");
            ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();

            // Create a new packet
            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.OPEN_WINDOW);

            // Set window ID
            packet.getIntegers().write(0, windowId);

            // Convert title to chat component
            WrappedChatComponent component = WrappedChatComponent.fromText(
                    ChatColor.translateAlternateColorCodes('&', title));

            // This is the critical part for 1.21:
            // Using proper registry ID for menu type

            // Get the NMS player to access their current container
            Object nmsPlayer = getMinecraftPlayer(player);
            if (nmsPlayer == null) {
                Utils.logError("Could not get NMS player");
                return false;
            }

            // Get the active container
            Object activeContainer = getPlayerContainer(nmsPlayer);
            if (activeContainer == null) {
                Utils.logError("Could not get active container");
                return false;
            }

            // Get the container's menu type
            Object menuType = getContainerType(activeContainer);
            if (menuType == null) {
                Utils.logError("Could not get container type");
                return false;
            }

            Utils.logDebug("Got container type: " + menuType.toString());

            // Set the menu type in the packet
            packet.getModifier().write(1, menuType);

            // Set the title
            packet.getChatComponents().write(0, component);

            // Send the packet
            protocolManager.sendServerPacket(player, packet);

            // Also update the client's inventory immediately to prevent desync
            Bukkit.getScheduler().runTaskLater(plugin, player::updateInventory, 1L);

            Utils.logDebug("Title update packet sent successfully");
            return true;
        } catch (Exception e) {
            Utils.logError("Error in updateTitle121: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Final fallback for 1.21-R1 specifically
     */
    private boolean updateTitle121Direct(Player player, String title) {
        try {
            Utils.logDebug("Attempting direct 1.21-R1 title update implementation");

            // Direct implementation for 1.21-R1
            Object craftPlayer = player.getClass().getMethod("getHandle").invoke(player);

            // Try several potential field names for player connection in 1.21
            Object connection = null;
            String[] connectionFieldNames = {"c", "b", "connection", "playerConnection"};
            for (String fieldName : connectionFieldNames) {
                try {
                    Field connectionField = craftPlayer.getClass().getDeclaredField(fieldName);
                    connectionField.setAccessible(true);
                    connection = connectionField.get(craftPlayer);
                    if (connection != null) {
                        Utils.logDebug("Found connection field: " + fieldName);
                        break;
                    }
                } catch (NoSuchFieldException ignored) {}
            }

            if (connection == null) {
                Utils.logError("Could not find player connection field");
                return false;
            }

            // Try several potential field names for container in 1.21
            Object container = null;
            String[] containerFieldNames = {"bW", "bU", "bP", "containerMenu", "activeContainer"};
            for (String fieldName : containerFieldNames) {
                try {
                    Field containerField = craftPlayer.getClass().getDeclaredField(fieldName);
                    containerField.setAccessible(true);
                    container = containerField.get(craftPlayer);
                    if (container != null) {
                        Utils.logDebug("Found container field: " + fieldName);
                        break;
                    }
                } catch (NoSuchFieldException ignored) {}
            }

            if (container == null) {
                Utils.logError("Could not find player container field");
                return false;
            }

            // Try several potential field names for window ID in 1.21
            int windowId = -1;
            String[] windowIdFieldNames = {"j", "windowId", "containerId"};
            for (String fieldName : windowIdFieldNames) {
                try {
                    Field windowIdField = container.getClass().getDeclaredField(fieldName);
                    windowIdField.setAccessible(true);
                    windowId = windowIdField.getInt(container);
                    Utils.logDebug("Found window ID field: " + fieldName + " = " + windowId);
                    break;
                } catch (NoSuchFieldException ignored) {}
            }

            if (windowId == -1) {
                Utils.logError("Could not find window ID field");
                windowId = player.getOpenInventory().getTopInventory().hashCode() & 0xFF;
                Utils.logDebug("Using fallback window ID calculation: " + windowId);
            }

            // Try several potential field names for container type in 1.21
            Object containerType = null;
            String[] containerTypeFieldNames = {"a", "b", "type", "menuType", "containerType"};
            for (String fieldName : containerTypeFieldNames) {
                try {
                    Field containerTypeField = container.getClass().getDeclaredField(fieldName);
                    containerTypeField.setAccessible(true);
                    containerType = containerTypeField.get(container);
                    if (containerType != null) {
                        Utils.logDebug("Found container type field: " + fieldName);
                        break;
                    }
                } catch (NoSuchFieldException ignored) {}
            }

            // If static field approach failed, try retrieving type through a method
            if (containerType == null) {
                for (Method method : container.getClass().getMethods()) {
                    if (method.getParameterCount() == 0 &&
                            (method.getName().equals("a") ||
                                    method.getName().equals("getType") ||
                                    method.getName().equals("getMenuType"))) {
                        try {
                            containerType = method.invoke(container);
                            if (containerType != null) {
                                Utils.logDebug("Found container type method: " + method.getName());
                                break;
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }

            if (containerType == null) {
                Utils.logError("Could not find container type");
                return false;
            }

            // Create title component
            String coloredTitle = ChatColor.translateAlternateColorCodes('&', title);
            Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");
            Method literalMethod = componentClass.getMethod("literal", String.class);
            Object titleComponent = literalMethod.invoke(null, coloredTitle);

            // Create open window packet
            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundOpenScreenPacket");

            // Find the right constructor
            Constructor<?> packetConstructor = null;
            for (Constructor<?> constructor : packetClass.getConstructors()) {
                if (constructor.getParameterCount() == 3) {
                    packetConstructor = constructor;
                    break;
                }
            }

            if (packetConstructor == null) {
                Utils.logError("Could not find appropriate packet constructor");
                return false;
            }

            // Create packet
            Object packet = packetConstructor.newInstance(windowId, containerType, titleComponent);

            // Send packet
            String[] sendMethodNames = {"send", "sendPacket", "a"};
            for (String methodName : sendMethodNames) {
                try {
                    Method sendPacketMethod = connection.getClass().getMethod(methodName,
                            Class.forName("net.minecraft.network.protocol.Packet"));
                    sendPacketMethod.invoke(connection, packet);
                    Utils.logDebug("Sent packet using method: " + methodName);

                    // Force inventory update to prevent desync
                    Bukkit.getScheduler().runTaskLater(plugin, player::updateInventory, 1L);

                    return true;
                } catch (NoSuchMethodException ignored) {}
            }

            Utils.logError("Could not find appropriate send packet method");
            return false;
        } catch (Exception e) {
            Utils.logDebug("Direct 1.21-R1 implementation failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Get the Minecraft player entity from a Bukkit player
     */
    private Object getMinecraftPlayer(Player player) {
        try {
            Method getHandle = player.getClass().getMethod("getHandle");
            return getHandle.invoke(player);
        } catch (Exception e) {
            Utils.logError("Error getting NMS player: " + e.getMessage());
            return null;
        }
    }

    /**
     * Get the player's active container
     */
    private Object getPlayerContainer(Object nmsPlayer) {
        try {
            // Field name might vary based on server version
            // In 1.21 it could be 'bW' or 'bP' or something similar
            // Try common field names first
            String[] possibleFieldNames = {"bW", "bP", "bU", "containerMenu", "activeContainer"};

            for (String fieldName : possibleFieldNames) {
                try {
                    Field containerField = nmsPlayer.getClass().getDeclaredField(fieldName);
                    containerField.setAccessible(true);
                    Object container = containerField.get(nmsPlayer);
                    if (container != null) {
                        Utils.logDebug("Found container field: " + fieldName);
                        return container;
                    }
                } catch (NoSuchFieldException ignored) {
                    // Try next field name
                }
            }

            // If specific fields didn't work, try to find by field type
            for (Field field : nmsPlayer.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                if (field.getType().getSimpleName().contains("Container") ||
                        field.getType().getSimpleName().contains("Menu")) {
                    Object container = field.get(nmsPlayer);
                    if (container != null) {
                        Utils.logDebug("Found container by type: " + field.getName());
                        return container;
                    }
                }
            }

            Utils.logError("Could not find container field in player object");
            return null;
        } catch (Exception e) {
            Utils.logError("Error getting player container: " + e.getMessage());
            return null;
        }
    }

    /**
     * Get the container type from a container
     */
    private Object getContainerType(Object container) {
        try {
            // Try common field names for the container type
            String[] possibleFieldNames = {"a", "b", "c", "type", "containerType", "menuType"};

            for (String fieldName : possibleFieldNames) {
                try {
                    Field typeField = container.getClass().getDeclaredField(fieldName);
                    typeField.setAccessible(true);
                    Object type = typeField.get(container);
                    if (type != null) {
                        Utils.logDebug("Found container type field: " + fieldName);
                        return type;
                    }
                } catch (NoSuchFieldException ignored) {
                    // Try next field name
                }
            }

            // If specific fields didn't work, try to find the right method
            Method[] methods = container.getClass().getMethods();
            for (Method method : methods) {
                String methodName = method.getName();
                if (methodName.equalsIgnoreCase("getType") ||
                        methodName.equalsIgnoreCase("getMenuType") ||
                        methodName.equalsIgnoreCase("a") ||
                        methodName.equalsIgnoreCase("b") ||
                        methodName.equalsIgnoreCase("c")) {

                    if (method.getParameterCount() == 0) {
                        Object type = method.invoke(container);
                        if (type != null) {
                            Utils.logDebug("Found container type method: " + methodName);
                            return type;
                        }
                    }
                }
            }

            // If we still can't find it, try getting it from a static registry
            // This is specific to Minecraft 1.21
            try {
                // Get the container class
                Class<?> containerClass = container.getClass();

                // Try to find the registry
                Class<?> registryClass = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
                Field menuTypeRegistryField = registryClass.getDeclaredField("MENU");
                menuTypeRegistryField.setAccessible(true);
                Object menuRegistry = menuTypeRegistryField.get(null);

                // Get the container size to determine the generic container type
                Method getSizeMethod = containerClass.getMethod("getSize");
                int containerSize = (int) getSizeMethod.invoke(container);

                // Calculate generic container type based on size
                int rows = Math.min(6, (containerSize + 8) / 9); // Round up to nearest row, max 6

                // Get the appropriate registry ID based on rows
                // GENERIC_9x1 is usually ID 0, GENERIC_9x2 is ID 1, etc.
                int genericTypeId = rows - 1;

                // Use the registry to get the MenuType by ID
                Class<?> registryApiClass = Class.forName("net.minecraft.core.Registry");
                Method byIdMethod = registryApiClass.getMethod("byId", int.class);
                Object menuType = byIdMethod.invoke(menuRegistry, genericTypeId);

                if (menuType != null) {
                    Utils.logDebug("Found container type through registry lookup: " + genericTypeId);
                    return menuType;
                }
            } catch (Exception e) {
                Utils.logDebug("Registry lookup failed: " + e.getMessage());
            }

            Utils.logError("Could not find container type in container object");
            return null;
        } catch (Exception e) {
            Utils.logError("Error getting container type: " + e.getMessage());
            return null;
        }
    }

    /**
     * Fallback methods for updating inventory title
     */
    private boolean updateTitleFallbacks(Player player, String title) {
        Utils.logDebug("Trying title update fallbacks...");

        // Try the template packet method first
        if (openWindowPacketTemplates.containsKey(player.getUniqueId())) {
            try {
                updateTitleWithTemplate(title);
                Utils.logDebug("Updated title using template method");
                return true;
            } catch (Exception e) {
                Utils.logDebug("Template title update failed: " + e.getMessage());
            }
        }

        // Try alternative NMS approach
        try {
            // Get CraftPlayer
            Object craftPlayer = player.getClass().getMethod("getHandle").invoke(player);

            // Get player connection
            Field connectionField = null;
            for (Field f : craftPlayer.getClass().getDeclaredFields()) {
                if (f.getType().getSimpleName().contains("Connection")) {
                    connectionField = f;
                    connectionField.setAccessible(true);
                    break;
                }
            }

            if (connectionField != null) {
                Object connection = connectionField.get(craftPlayer);

                // Get window ID from current inventory
                int windowId = player.getOpenInventory().getTopInventory().hashCode() & 0xFF;

                // Create component for title
                Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");
                Method literalMethod = componentClass.getMethod("literal", String.class);
                Object titleComponent = literalMethod.invoke(null,
                        ChatColor.translateAlternateColorCodes('&', title));

                // Create packet
                Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundOpenScreenPacket");

                // Find the right constructor
                Constructor<?>[] constructors = packetClass.getConstructors();
                for (Constructor<?> constructor : constructors) {
                    if (constructor.getParameterCount() == 3) {
                        // Create a packet with window ID, integer type ID, and title
                        // For Minecraft 1.21, use type ID based on rows
                        int rows = Math.min(6, (inventory.getSize() + 8) / 9);
                        int typeId = rows - 1;

                        // Try to get menu type from registry
                        Object menuType = null;
                        try {
                            Class<?> registryClass = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
                            Field menuTypeRegistryField = registryClass.getDeclaredField("MENU");
                            menuTypeRegistryField.setAccessible(true);
                            Object menuRegistry = menuTypeRegistryField.get(null);

                            Class<?> registryApiClass = Class.forName("net.minecraft.core.Registry");
                            Method byIdMethod = registryApiClass.getMethod("byId", int.class);
                            menuType = byIdMethod.invoke(menuRegistry, typeId);
                        } catch (Exception e) {
                            Utils.logDebug("Registry lookup failed in fallback: " + e.getMessage());
                            continue;
                        }

                        if (menuType == null) {
                            continue;
                        }

                        Object packet = constructor.newInstance(windowId, menuType, titleComponent);

                        // Send packet
                        Method sendPacketMethod = connection.getClass().getMethod("send",
                                Class.forName("net.minecraft.network.protocol.Packet"));
                        sendPacketMethod.invoke(connection, packet);

                        Utils.logDebug("Updated title using NMS fallback method");
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Utils.logDebug("NMS fallback failed: " + e.getMessage());
        }

        return false;
    }

    /**
     * Send an action bar message
     */
    private void sendActionBar(Player player, String message) {
        try {
            player.spigot().sendMessage(
                    net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText(message)
            );
        } catch (Exception e) {
            Utils.logDebug("Could not send action bar: " + e.getMessage());
        }
    }

    /**
     * Debug helper to print the structure of a packet
     */
    private void debugPacketStructure(PacketContainer packet) {
        Utils.logDebug("Packet structure for: " + packet.getType().name());

        // Debug ints
        try {
            Utils.logDebug("Integer modifiers: " + packet.getIntegers().size());
            for (int i = 0; i < packet.getIntegers().size(); i++) {
                try {
                    Utils.logDebug("  Int[" + i + "]: " + packet.getIntegers().read(i));
                } catch (Exception e) {
                    Utils.logDebug("  Int[" + i + "]: Error reading: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            Utils.logDebug("Error reading integers: " + e.getMessage());
        }

        // Debug strings
        try {
            Utils.logDebug("String modifiers: " + packet.getStrings().size());
            for (int i = 0; i < packet.getStrings().size(); i++) {
                try {
                    Utils.logDebug("  String[" + i + "]: " + packet.getStrings().read(i));
                } catch (Exception e) {
                    Utils.logDebug("  String[" + i + "]: Error reading: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            Utils.logDebug("Error reading strings: " + e.getMessage());
        }

        // Debug chat components
        try {
            Utils.logDebug("Chat component modifiers: " + packet.getChatComponents().size());
            for (int i = 0; i < packet.getChatComponents().size(); i++) {
                try {
                    Utils.logDebug("  Component[" + i + "]: " + packet.getChatComponents().read(i));
                } catch (Exception e) {
                    Utils.logDebug("  Component[" + i + "]: Error reading: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            Utils.logDebug("Error reading chat components: " + e.getMessage());
        }

        // Try to examine the raw class
        try {
            Utils.logDebug("Raw packet class: " + packet.getHandle().getClass().getName());
            for (Field field : packet.getHandle().getClass().getDeclaredFields()) {
                field.setAccessible(true);
                try {
                    Utils.logDebug("  Field: " + field.getName() + " (" + field.getType().getSimpleName() +
                            "): " + field.get(packet.getHandle()));
                } catch (Exception e) {
                    Utils.logDebug("  Field: " + field.getName() + ": Error reading: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            Utils.logDebug("Error examining raw packet: " + e.getMessage());
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

    /**
     * Get a custom ItemsAdder item for menu elements
     * Falls back to default material if ItemsAdder is not available or item doesn't exist
     */
    protected ItemStack getItemsAdderItem(String namespace, Material fallback) {
        try {
            // Use ItemsAdder API to get custom item
            Class<?> customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Method getInstance = customStackClass.getMethod("getInstance", String.class);

            Object customStack = getInstance.invoke(null, namespace);
            if (customStack != null) {
                Method getItemStackMethod = customStack.getClass().getMethod("getItemStack");
                return (ItemStack) getItemStackMethod.invoke(customStack);
            }
        } catch (Exception e) {
            Utils.logDebug("Error getting ItemsAdder item '" + namespace + "': " + e.getMessage());
        }

        // Fallback to default material
        return new ItemStack(fallback);
    }

    /**
     * Create a standardized header row with ItemsAdder custom items
     */
    protected void createItemsAdderHeader() {
        try {
            // Get custom background item
            ItemStack backgroundItem = getItemsAdderItem("genstools:menu_background", Material.BLACK_STAINED_GLASS_PANE);
            ItemMeta backgroundMeta = backgroundItem.getItemMeta();
            if (backgroundMeta != null) {
                backgroundMeta.setDisplayName(" ");
                backgroundItem.setItemMeta(backgroundMeta);
            }

            // Fill the top row with background items
            for (int i = 0; i < 9; i++) {
                if (i != 4) { // Skip center for title item
                    setItem(i, backgroundItem);
                }
            }

            // Create a custom title item based on menu type
            String menuType = this.getClass().getSimpleName().toLowerCase().replace("menu", "");
            ItemStack titleItem = getItemsAdderItem("genstools:menu_" + menuType, Material.BOOK);

            // If specific menu item doesn't exist, use generic one
            if (titleItem.getType() == Material.BOOK) {
                titleItem = getItemsAdderItem("genstools:menu_generic", Material.ENCHANTED_BOOK);
            }

            // Customize the title item
            ItemMeta titleMeta = titleItem.getItemMeta();
            if (titleMeta != null) {
                // Use ItemsAdder font if available
                String coloredTitle = ChatColor.translateAlternateColorCodes('&', title);

                // Try to use custom font
                try {
                    Class<?> fontClass = Class.forName("dev.lone.itemsadder.api.FontImages.FontImageWrapper");
                    Method applyFont = fontClass.getMethod("applyToItemMeta", ItemMeta.class, String.class);

                    // Apply custom font to the title
                    applyFont.invoke(null, titleMeta, "genstools:menu_title:" + coloredTitle);
                } catch (Exception e) {
                    // Fallback to regular text
                    titleMeta.setDisplayName(ChatColor.GOLD + coloredTitle);
                }

                // Add lore with menu description
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Current Menu: " + ChatColor.WHITE + menuType);
                titleMeta.setLore(lore);

                titleMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
                titleItem.setItemMeta(titleMeta);
            }

            // Add the title item to the center
            setItem(4, titleItem);

        } catch (Exception e) {
            Utils.logError("Error creating ItemsAdder header: " + e.getMessage());

            // Fallback to standard header
            createStandardHeader();
        }
    }

    /**
     * Create a standard header without ItemsAdder
     */
    protected void createStandardHeader() {
        // Add a row of glass panes at the top
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta paneMeta = pane.getItemMeta();
        if (paneMeta != null) {
            paneMeta.setDisplayName(" ");
            pane.setItemMeta(paneMeta);
        }

        // Fill the top row with panes
        for (int i = 0; i < 9; i++) {
            if (i != 4) { // Skip the center slot
                setItem(i, pane);
            }
        }

        // Create a title indicator item for the center
        ItemStack titleItem = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta titleMeta = titleItem.getItemMeta();
        if (titleMeta != null) {
            String coloredTitle = ChatColor.translateAlternateColorCodes('&', title);
            titleMeta.setDisplayName(ChatColor.GOLD + coloredTitle);

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Current Menu");
            titleMeta.setLore(lore);

            titleMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            titleItem.setItemMeta(titleMeta);
        }

        // Add the title item to the center
        setItem(4, titleItem);
    }

    /**
     * Create an enhanced button using ItemsAdder custom models
     */
    protected ItemStack createItemsAdderButton(String buttonType, String name, List<String> lore) {
        // Try to get custom ItemsAdder button
        ItemStack button = getItemsAdderItem("genstools:button_" + buttonType, Material.STONE_BUTTON);

        // Customize the button
        ItemMeta meta = button.getItemMeta();
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
            button.setItemMeta(meta);
        }

        return button;
    }

    /**
     * Create an enhanced decorative item using ItemsAdder
     */
    protected ItemStack createItemsAdderDecoration(String decorationType) {
        return getItemsAdderItem("genstools:decor_" + decorationType, Material.LIGHT_BLUE_STAINED_GLASS_PANE);
    }
}