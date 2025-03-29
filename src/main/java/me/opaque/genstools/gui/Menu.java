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
     *//*
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
    }*/

    /**
     * Switch to a different menu type - 1.21 compatible version
     */
    public void switchTo(Menu nextMenu) {
        Utils.logDebug("Switching from " + this.getClass().getSimpleName() +
                " to " + nextMenu.getClass().getSimpleName() + " with ProtocolLib (1.21)");

        try {
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

            // Try to update the title
            send121TitleUpdatePacket(player, nextMenu.title);

            // Now clear and rebuild inventory with a delay
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // Clear all items
                for (int i = 0; i < inventory.getSize(); i++) {
                    inventory.setItem(i, null);
                }

                // Have the next menu build its contents
                nextMenu.build();

                // Force client update
                player.updateInventory();

                Utils.logDebug("Menu switch completed");
            }, 1L);

        } catch (Exception e) {
            Utils.logError("Error during menu switch: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Send a specialized OpenWindow packet for Minecraft 1.21 using registered menu types
     */
    private void send121TitleUpdatePacket(Player player, String newTitle) {
        try {
            // Get the ProtocolManager
            ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();

            // For 1.21, we need to create the packet differently
            // We'll use a ResourceLocation approach to get a valid registered menu type

            // 1. First, let's prepare the window ID and title component
            int windowId = player.getOpenInventory().getTopInventory().hashCode() & 0xFF;
            String coloredTitle = ChatColor.translateAlternateColorCodes('&', newTitle);
            WrappedChatComponent titleComponent = WrappedChatComponent.fromText(coloredTitle);

            Utils.logDebug("Preparing 1.21 title packet with window ID: " + windowId);

            try {
                // 2. We need to get a properly registered menu type from Minecraft's registry

                // First get the NMS classes we need
                Class<?> resourceLocationClass = Class.forName("net.minecraft.resources.ResourceLocation");
                Class<?> builtInRegistriesClass = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
                Class<?> registryClass = Class.forName("net.minecraft.core.Registry");

                // Get the MenuType registry
                Field menuTypeRegistryField = builtInRegistriesClass.getDeclaredField("MENU");
                menuTypeRegistryField.setAccessible(true);
                Object menuRegistry = menuTypeRegistryField.get(null);

                // Create a ResourceLocation for the correct generic container type
                Constructor<?> rlConstructor = resourceLocationClass.getConstructor(String.class);

                // Choose the right container type based on inventory size
                String containerTypeKey;
                int size = inventory.getSize();

                if (size <= 9) containerTypeKey = "minecraft:generic_9x1";
                else if (size <= 18) containerTypeKey = "minecraft:generic_9x2";
                else if (size <= 27) containerTypeKey = "minecraft:generic_9x3";
                else if (size <= 36) containerTypeKey = "minecraft:generic_9x4";
                else if (size <= 45) containerTypeKey = "minecraft:generic_9x5";
                else containerTypeKey = "minecraft:generic_9x6";

                Utils.logDebug("Using container type key: " + containerTypeKey);

                Object resourceLocation = rlConstructor.newInstance(containerTypeKey);

                // Get the menu type from the registry
                Method getMethod = registryClass.getMethod("get", resourceLocationClass);
                Object menuType = getMethod.invoke(menuRegistry, resourceLocation);

                if (menuType == null) {
                    Utils.logWarning("Registry returned null menu type for " + containerTypeKey);
                    return;
                }

                Utils.logDebug("Successfully retrieved menu type from registry: " + menuType);

                // 3. Now build the packet directly using NMS classes
                Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundOpenScreenPacket");
                Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");

                // Convert our WrappedChatComponent to NMS Component
                Object nmsComponent = null;
                try {
                    // Method 1: Try to get the handle directly
                    Method getHandleMethod = titleComponent.getClass().getMethod("getHandle");
                    nmsComponent = getHandleMethod.invoke(titleComponent);
                } catch (Exception e) {
                    // Method 2: Try to create a new component
                    Method literalMethod = componentClass.getMethod("literal", String.class);
                    nmsComponent = literalMethod.invoke(null, coloredTitle);
                }

                if (nmsComponent == null) {
                    Utils.logWarning("Could not create title component");
                    return;
                }

                // Create the packet using the constructor
                Constructor<?> packetConstructor = packetClass.getConstructor(int.class, menuType.getClass(), componentClass);
                Object nmsPacket = packetConstructor.newInstance(windowId, menuType, nmsComponent);

                Utils.logDebug("Created NMS packet: " + nmsPacket);

                // 4. Send the packet using ProtocolLib's sendServerPacket with a raw packet
                Method sendPacketMethod = protocolManager.getClass().getMethod("sendServerPacket", Player.class, Object.class);
                sendPacketMethod.invoke(protocolManager, player, nmsPacket);

                Utils.logDebug("Successfully sent NMS packet for title update");
                this.title = newTitle;

            } catch (Exception e) {
                Utils.logError("Error creating NMS packet: " + e.getMessage());
                e.printStackTrace();

                // Let's try an alternative method if the first one failed
                try {
                    Utils.logDebug("Trying alternate approach");

                    // Create an empty packet
                    PacketContainer packet = new PacketContainer(PacketType.Play.Server.OPEN_WINDOW);

                    // Set the window ID
                    packet.getIntegers().write(0, windowId);

                    // Set the title
                    packet.getChatComponents().write(0, titleComponent);

                    // For the menu type, we'll need to get it from the registry directly
                    String containerTypeKey;
                    int size = inventory.getSize();

                    if (size <= 9) containerTypeKey = "minecraft:generic_9x1";
                    else if (size <= 18) containerTypeKey = "minecraft:generic_9x2";
                    else if (size <= 27) containerTypeKey = "minecraft:generic_9x3";
                    else if (size <= 36) containerTypeKey = "minecraft:generic_9x4";
                    else if (size <= 45) containerTypeKey = "minecraft:generic_9x5";
                    else containerTypeKey = "minecraft:generic_9x6";

                    // Get a valid menu type through direct NMS access
                    Class<?> menuTypeClass = Class.forName("net.minecraft.world.inventory.MenuType");
                    Class<?> resourceLocationClass = Class.forName("net.minecraft.resources.ResourceLocation");
                    Constructor<?> rlConstructor = resourceLocationClass.getConstructor(String.class);
                    Object resourceLocation = rlConstructor.newInstance(containerTypeKey);

                    // Get the registry and the menu type
                    Class<?> builtInRegistriesClass = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
                    Field menuRegistryField = builtInRegistriesClass.getDeclaredField("MENU");
                    menuRegistryField.setAccessible(true);
                    Object registry = menuRegistryField.get(null);

                    // Get the menu type from the registry
                    Class<?> registryClass = registry.getClass();
                    Method getMethod = registryClass.getMethod("get", resourceLocationClass);
                    Object menuType = getMethod.invoke(registry, resourceLocation);

                    if (menuType == null) {
                        Utils.logWarning("Could not get menu type from registry");
                        return;
                    }

                    // Now set it in the packet using direct field access
                    Object packetHandle = packet.getHandle();
                    for (Field field : packetHandle.getClass().getDeclaredFields()) {
                        field.setAccessible(true);
                        if (field.getType().isAssignableFrom(menuType.getClass())) {
                            field.set(packetHandle, menuType);
                            Utils.logDebug("Set menu type in field: " + field.getName());
                            break;
                        }
                    }

                    // Send the packet
                    protocolManager.sendServerPacket(player, packet);
                    Utils.logDebug("Sent title update packet using alternate approach");
                    this.title = newTitle;

                } catch (Exception e2) {
                    Utils.logError("Both title update approaches failed: " + e2.getMessage());
                    e2.printStackTrace();
                }
            }

        } catch (Exception e) {
            Utils.logError("Error in send121TitleUpdatePacket: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get the container ID for the player's current open inventory - 1.21 specific version
     */
    private int getPlayerContainerId(Player player) {
        try {
            Utils.logDebug("Getting container ID for " + player.getName() + " (1.21 method)");

            // Get EntityPlayer from CraftPlayer
            Object craftPlayer = player;
            Object entityPlayer = craftPlayer.getClass().getMethod("getHandle").invoke(craftPlayer);

            Utils.logDebug("Entity player class: " + entityPlayer.getClass().getName());

            // For 1.21, the container is typically in a field called "bV"
            // but let's try multiple approaches to find it
            Object containerMenu = null;

            // Approach 1: Try direct field name for 1.21
            try {
                Field containerField = entityPlayer.getClass().getDeclaredField("bV");
                containerField.setAccessible(true);
                containerMenu = containerField.get(entityPlayer);
                Utils.logDebug("Found container using field bV");
            } catch (NoSuchFieldException e) {
                Utils.logDebug("Field bV not found, trying alternatives");
            }

            // Approach 2: Try to find container field dynamically
            if (containerMenu == null) {
                for (Field field : entityPlayer.getClass().getDeclaredFields()) {
                    field.setAccessible(true);
                    Object value = field.get(entityPlayer);

                    if (value != null &&
                            (value.getClass().getName().contains("Container") ||
                                    value.getClass().getName().contains("Menu"))) {

                        Utils.logDebug("Found potential container field: " + field.getName() +
                                " of type: " + value.getClass().getName());

                        containerMenu = value;
                        break;
                    }
                }
            }

            // Approach 3: Try using getBukkitView as a bridge
            if (containerMenu == null) {
                try {
                    // Get the inventory view
                    Object inventoryView = player.getOpenInventory();
                    // Use reflection to get the NMS container
                    Method getNmsMethod = inventoryView.getClass().getMethod("getHandle");
                    containerMenu = getNmsMethod.invoke(inventoryView);
                    Utils.logDebug("Found container using inventory view bridge");
                } catch (Exception e) {
                    Utils.logDebug("Inventory view bridge failed: " + e.getMessage());
                }
            }

            if (containerMenu == null) {
                Utils.logWarning("Could not find container menu object");
                // Fallback to a simple calculation
                return player.getOpenInventory().getTopInventory().hashCode() & 0xFF;
            }

            // Now find the container ID field in 1.21 (usually 'j')
            // Approach 1: Try direct field name for 1.21
            try {
                Field idField = containerMenu.getClass().getDeclaredField("j");
                idField.setAccessible(true);
                int id = idField.getInt(containerMenu);
                Utils.logDebug("Found container ID: " + id + " using field j");
                return id;
            } catch (NoSuchFieldException e) {
                Utils.logDebug("Field j not found, trying alternatives");
            }

            // Approach 2: Try other common field names
            for (String fieldName : new String[]{"containerId", "windowId", "containerCounter"}) {
                try {
                    Field idField = containerMenu.getClass().getDeclaredField(fieldName);
                    idField.setAccessible(true);
                    int id = idField.getInt(containerMenu);
                    Utils.logDebug("Found container ID: " + id + " using field " + fieldName);
                    return id;
                } catch (NoSuchFieldException e) {
                    // Continue to next field name
                }
            }

            // Approach 3: Find by scanning for int fields with appropriate values
            for (Field field : containerMenu.getClass().getDeclaredFields()) {
                field.setAccessible(true);

                // Only consider int fields
                if (field.getType() == int.class) {
                    int value = field.getInt(containerMenu);

                    // Container IDs are typically small positive integers
                    if (value > 0 && value < 100) {
                        Utils.logDebug("Found potential container ID field: " + field.getName() +
                                " with value: " + value);
                        return value;
                    }
                }
            }

            // Fallback to a simple ID calculation that should work in most cases
            int fallbackId = player.getOpenInventory().getTopInventory().hashCode() & 0xFF;
            Utils.logDebug("Using fallback container ID: " + fallbackId);
            return fallbackId;

        } catch (Exception e) {
            Utils.logError("Error getting container ID: " + e.getMessage());
            e.printStackTrace();
            // Fallback to a simple ID calculation
            return player.getOpenInventory().getTopInventory().hashCode() & 0xFF;
        }
    }

    /**
     * Update inventory title using ProtocolLib packets - version-agnostic approach
     */
    private void updateTitleWithProtocolLib(Player player, int windowId, String title) {
        try {
            // Get ProtocolLib's ProtocolManager
            ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();

            // Create a window title packet
            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.OPEN_WINDOW);

            Utils.logDebug("Created OPEN_WINDOW packet for title update");

            // Log the packet's structure
            debugPacketStructure(packet);

            // Try to set the windowId first
            boolean setWindowId = false;
            try {
                packet.getIntegers().write(0, windowId);
                setWindowId = true;
                Utils.logDebug("Set window ID to: " + windowId);
            } catch (Exception e) {
                Utils.logWarning("Could not set window ID: " + e.getMessage());
            }

            // Try different approaches to set the inventory type
            boolean setInventoryType = false;

            // Determine inventory type string based on size
            String inventoryTypeStr;
            if (inventory.getSize() <= 9) inventoryTypeStr = "minecraft:generic_9x1";
            else if (inventory.getSize() <= 18) inventoryTypeStr = "minecraft:generic_9x2";
            else if (inventory.getSize() <= 27) inventoryTypeStr = "minecraft:generic_9x3";
            else if (inventory.getSize() <= 36) inventoryTypeStr = "minecraft:generic_9x4";
            else if (inventory.getSize() <= 45) inventoryTypeStr = "minecraft:generic_9x5";
            else inventoryTypeStr = "minecraft:generic_9x6";

            // Try approach 1: Using string modifier
            try {
                packet.getStrings().write(0, inventoryTypeStr);
                setInventoryType = true;
                Utils.logDebug("Set inventory type as string: " + inventoryTypeStr);
            } catch (Exception e) {
                Utils.logDebug("Could not set inventory type as string: " + e.getMessage());
            }

            // Try approach 2: Find a suitable field for the chat component
            boolean setTitle = false;
            String coloredTitle = ChatColor.translateAlternateColorCodes('&', title);

            // Try to find chat components accessor
            try {
                WrappedChatComponent component = WrappedChatComponent.fromText(coloredTitle);
                packet.getChatComponents().write(0, component);
                setTitle = true;
                Utils.logDebug("Set title using chat component");
            } catch (Exception e) {
                Utils.logDebug("Could not set title using chat component: " + e.getMessage());

                // Try raw JSON instead
                try {
                    String json = "{\"text\":\"" + coloredTitle.replace("\"", "\\\"") + "\"}";
                    Field titleField = null;

                    // Try to find a suitable field for the title
                    for (Field field : packet.getHandle().getClass().getDeclaredFields()) {
                        field.setAccessible(true);
                        if (field.getType().getName().contains("Component") ||
                                field.getType().getName().contains("IChatBase")) {
                            titleField = field;
                            break;
                        }
                    }

                    if (titleField != null) {
                        // Need to convert the JSON to a component
                        Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");
                        Method fromJson = componentClass.getDeclaredMethod("a", String.class);
                        fromJson.setAccessible(true);
                        Object component = fromJson.invoke(null, json);

                        titleField.set(packet.getHandle(), component);
                        setTitle = true;
                        Utils.logDebug("Set title using reflected component field");
                    }
                } catch (Exception e2) {
                    Utils.logWarning("Could not set title using reflection: " + e2.getMessage());
                }
            }

            // Log a summary of what we've done
            Utils.logDebug("Title update packet preparation: " +
                    "WindowID=" + setWindowId +
                    ", InventoryType=" + setInventoryType +
                    ", Title=" + setTitle);

            // Only send the packet if at least window ID was set
            if (setWindowId) {
                // Send the packet
                protocolManager.sendServerPacket(player, packet);
                Utils.logDebug("Sent title update packet");

                // Update our title field
                this.title = title;
            } else {
                Utils.logWarning("Did not send title packet as window ID could not be set");
            }
        } catch (Exception e) {
            Utils.logError("Error in updateTitleWithProtocolLib: " + e.getMessage());
            e.printStackTrace();
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
}