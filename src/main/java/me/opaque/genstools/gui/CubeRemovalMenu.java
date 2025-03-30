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
import me.opaque.genstools.enchants.CustomEnchant;
import me.opaque.genstools.enchants.EnchantmentCube;
import me.opaque.genstools.tools.GensTool;
import me.opaque.genstools.utils.NumberFormatter;
import me.opaque.genstools.utils.Utils;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Menu for viewing and removing enchantment cubes from tools
 */
public class CubeRemovalMenu extends Menu {
    private final ItemStack toolItem;
    private final Map<String, Double> appliedCubes;
    private final ToolEnchantMenu parentMenu;
    private final NumberFormatter formatter;

    // Configuration
    private final boolean enabled;
    private final Map<String, ItemInfo> itemConfig = new HashMap<>();
    private final List<Integer> cubeSlots = new ArrayList<>();
    private final Map<String, String> messages = new HashMap<>();
    private final Map<String, SoundInfo> sounds = new HashMap<>();
    private final int recoveryChance;

    /**
     * Create a new cube removal menu
     */
    public CubeRemovalMenu(GensTools plugin, Player player, ItemStack toolItem, ToolEnchantMenu parentMenu) {
        // Always use 3 rows for this menu type
        super(plugin, player,
                plugin.getGuiConfig().getString("cube-removal.title", "&6Remove Enchantment Cubes"),
                3);  // Force 3 rows

        this.toolItem = toolItem;
        this.parentMenu = parentMenu;
        this.formatter = plugin.getNumberFormatter();
        this.enabled = plugin.getGuiConfig().getBoolean("cube-removal.enabled", true);
        this.recoveryChance = plugin.getGuiConfig().getInt("cube-removal.removal.recovery-chance", 50);

        // Load cubes from the tool
        this.appliedCubes = getAppliedCubesFromTool(toolItem);

        // Log found cubes
        Utils.logDebug("Found " + appliedCubes.size() + " cubes on tool:");
        for (Map.Entry<String, Double> entry : appliedCubes.entrySet()) {
            Utils.logDebug("  - " + entry.getKey() + ": +" + entry.getValue() + "%");
        }

        // Load configurations
        loadItemConfigurations();
        loadCubeSlots();
        loadMessages();
        loadSounds();
    }

    /**
     * Custom open method for CubeRemovalMenu
     */
    @Override
    public void open() {
        openCubeMenu();
    }

    /**
     * Load item configurations from config
     */
    private void loadItemConfigurations() {
        ConfigurationSection itemsSection = plugin.getGuiConfig().getConfigurationSection("cube-removal.items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                ConfigurationSection itemSection = itemsSection.getConfigurationSection(key);
                if (itemSection != null) {
                    ItemInfo info = new ItemInfo();
                    info.enabled = itemSection.getBoolean("enabled", true);
                    info.slot = itemSection.getInt("slot", 0);
                    info.material = Material.valueOf(itemSection.getString("material", "STONE"));
                    info.name = itemSection.getString("name", "");
                    info.lore = itemSection.getStringList("lore");
                    info.glow = itemSection.getBoolean("glow", false);
                    itemConfig.put(key, info);
                }
            }
        }

        // Load cube display configuration
        ConfigurationSection cubeDisplaySection = plugin.getGuiConfig().getConfigurationSection("cube-removal.cube-display.item");
        if (cubeDisplaySection != null) {
            ItemInfo info = new ItemInfo();
            info.enabled = true;
            info.material = Material.valueOf(cubeDisplaySection.getString("material", "MAGENTA_GLAZED_TERRACOTTA"));
            info.name = cubeDisplaySection.getString("name", "&d{enchant_name} Cube: &6+{boost}%");
            info.lore = cubeDisplaySection.getStringList("lore");
            info.glow = cubeDisplaySection.getBoolean("glow", true);
            itemConfig.put("cube-item", info);
        }
    }

    /**
     * Load cube slot configuration
     */
    private void loadCubeSlots() {
        List<Integer> slots = plugin.getGuiConfig().getIntegerList("cube-removal.cube-display.slots");
        if (slots.isEmpty()) {
            // Default slots if not configured
            int[] defaultSlots = {10, 11, 12, 13, 14, 15, 16};
            for (int slot : defaultSlots) {
                cubeSlots.add(slot);
            }
        } else {
            cubeSlots.addAll(slots);
        }
    }

    /**
     * Load message configurations
     */
    private void loadMessages() {
        ConfigurationSection messagesSection = plugin.getGuiConfig().getConfigurationSection("cube-removal.removal.messages");
        if (messagesSection != null) {
            for (String key : messagesSection.getKeys(false)) {
                messages.put(key, messagesSection.getString(key, ""));
            }
        }
    }

    /**
     * Load sound configurations
     */
    private void loadSounds() {
        ConfigurationSection soundsSection = plugin.getGuiConfig().getConfigurationSection("cube-removal.removal.sounds");
        if (soundsSection != null) {
            for (String key : soundsSection.getKeys(false)) {
                ConfigurationSection soundSection = soundsSection.getConfigurationSection(key);
                if (soundSection != null) {
                    SoundInfo info = new SoundInfo();
                    info.sound = Sound.valueOf(soundSection.getString("sound", "UI_BUTTON_CLICK"));
                    info.volume = (float) soundSection.getDouble("volume", 0.5);
                    info.pitch = (float) soundSection.getDouble("pitch", 1.0);
                    sounds.put(key, info);
                }
            }
        }
    }

    /**
     * Get applied cubes from the tool
     *
     * @param toolItem The tool to check
     * @return Map of enchantment IDs to boost percentages
     */
    private Map<String, Double> getAppliedCubesFromTool(ItemStack toolItem) {
        Map<String, Double> cubes = new HashMap<>();

        if (!GensTool.isGensTool(toolItem)) {
            return cubes;
        }

        ItemMeta meta = toolItem.getItemMeta();
        if (meta == null) {
            return cubes;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();

        // Check for the consolidated cubes string
        NamespacedKey cubesKey = new NamespacedKey(plugin, "applied_cubes");
        if (container.has(cubesKey, PersistentDataType.STRING)) {
            String cubesData = container.get(cubesKey, PersistentDataType.STRING);
            if (cubesData != null && !cubesData.isEmpty()) {
                // Parse existing cubes
                for (String cube : cubesData.split(",")) {
                    String[] parts = cube.split(":");
                    if (parts.length == 2) {
                        String enchantId = parts[0];
                        double boostPercent;
                        try {
                            boostPercent = Integer.parseInt(parts[1]);
                            cubes.put(enchantId, boostPercent);
                        } catch (NumberFormatException e) {
                            // Skip invalid entries
                        }
                    }
                }
            }
        }

        // Also check individual multiplier keys for any cubes that might have been missed
        for (String enchantId : GensTool.getEnchantments(toolItem).keySet()) {
            NamespacedKey multiplierKey = new NamespacedKey(plugin, "enchant_multiplier_" + enchantId);
            if (container.has(multiplierKey, PersistentDataType.DOUBLE)) {
                double multiplier = container.get(multiplierKey, PersistentDataType.DOUBLE);
                if (multiplier > 0) {
                    // Convert multiplier to percentage (0.2 -> 20%)
                    cubes.put(enchantId, multiplier * 100);
                }
            }
        }

        return cubes;
    }

    @Override
    protected void build() {
        Utils.logDebug("Building CubeRemovalMenu for " + player.getName() + " with inventory size: " +
                (inventory != null ? inventory.getSize() : "null"));

        // Clear all slots first
        if (inventory != null) {
            for (int i = 0; i < inventory.getSize(); i++) {
                inventory.clear(i);
            }
        } else {
            Utils.logError("Inventory is null during build!");
            return;
        }

        // Add tool info at the top
        addToolInfo();

        // Add back button
        addBackButton();

        // Add cube displays
        addCubeDisplays();

        // Fill background
        ItemInfo fillerInfo = itemConfig.getOrDefault("filler", new ItemInfo());
        fillEmptySlots(fillerInfo.material);

        // Verify items were added
        int itemCount = 0;
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) != null) {
                itemCount++;
            }
        }
        Utils.logDebug("Added " + itemCount + " items to inventory");
    }

    /**
     * Add cube displays to the menu
     */
    private void addCubeDisplays() {
        Utils.logDebug("Adding cube displays. Total cubes: " + appliedCubes.size());

        if (appliedCubes.isEmpty()) {
            // No cubes applied to this tool
            return;
        }

        ItemInfo cubeItemInfo = itemConfig.getOrDefault("cube-item", new ItemInfo());
        int slotIndex = 0;

        for (Map.Entry<String, Double> entry : appliedCubes.entrySet()) {
            if (slotIndex >= cubeSlots.size()) break;

            String enchantId = entry.getKey();
            double boost = entry.getValue();
            int slot = cubeSlots.get(slotIndex);

            // Skip if slot out of range
            if (slot >= inventory.getSize()) {
                Utils.logWarning("Cube slot " + slot + " is outside inventory size " + inventory.getSize());
                slotIndex++;
                continue;
            }

            // Get enchant name from enchant manager
            CustomEnchant enchant = plugin.getToolManager().getEnchantById(enchantId);
            String enchantName = enchant != null ? enchant.getDisplayName() : enchantId;

            // Replace placeholders in name
            String name = cubeItemInfo.name
                    .replace("{enchant_name}", enchantName)
                    .replace("{boost}", formatter.format(boost));

            // Replace placeholders in lore
            List<String> lore = new ArrayList<>();
            for (String line : cubeItemInfo.lore) {
                line = line.replace("{enchant_name}", enchantName)
                        .replace("{boost}", formatter.format(boost))
                        .replace("{removal_chance}", String.valueOf(recoveryChance));
                lore.add(line);
            }

            ItemStack cubeItem = createItem(cubeItemInfo.material, name, lore);

            // Apply glow if configured
            if (cubeItemInfo.glow) {
                cubeItem = GensTool.applyGlow(cubeItem);
            }

            // Store the enchant ID in the item's persistent data
            ItemMeta meta = cubeItem.getItemMeta();
            if (meta != null) {
                PersistentDataContainer container = meta.getPersistentDataContainer();
                container.set(new NamespacedKey(plugin, "cube_enchant_id"), PersistentDataType.STRING, enchantId);
                cubeItem.setItemMeta(meta);
            }

            // Add the item with a click handler to remove the cube
            setItem(slot, cubeItem, e -> handleCubeRemoval(enchantId, enchantName, boost));
            Utils.logDebug("Added cube to slot " + slot + ": " + enchantName + " with boost " + boost);

            slotIndex++;
        }
    }

    // Ensure fill only works on visible slots
    @Override
    protected void fillEmptySlots(Material material) {
        if (inventory == null) return;

        ItemStack filler = new ItemStack(material);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            filler.setItemMeta(meta);
        }

        // Only fill up to the inventory size (should be 27 slots for 3 rows)
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }

    /**
     * Add tool information to the menu
     */
    private void addToolInfo() {
        ItemInfo infoInfo = itemConfig.getOrDefault("tool-info", new ItemInfo());
        if (!infoInfo.enabled) return;

        int level = GensTool.getLevel(toolItem);

        List<String> lore = new ArrayList<>();
        for (String line : infoInfo.lore) {
            line = line.replace("{level}", String.valueOf(level))
                    .replace("{cube_count}", String.valueOf(appliedCubes.size()));
            lore.add(line);
        }

        ItemStack infoItem = createItem(infoInfo.material, infoInfo.name, lore);

        // Apply glow if configured
        if (infoInfo.glow) {
            infoItem = GensTool.applyGlow(infoItem);
        }

        setItem(infoInfo.slot, infoItem);
    }

    /**
     * Add back button to return to the enchantment menu
     */
    private void addBackButton() {
        ItemInfo backInfo = itemConfig.getOrDefault("back-button", new ItemInfo());
        if (!backInfo.enabled) return;

        ItemStack backItem = createItem(backInfo.material, backInfo.name, backInfo.lore);

        // Apply glow if configured
        if (backInfo.glow) {
            backItem = GensTool.applyGlow(backItem);
        }

        setItem(backInfo.slot, backItem, e -> {
            // Switch back to the parent menu
            switchTo(parentMenu);
        });
    }

    /**
     * Handle removing a cube from the tool
     *
     * @param enchantId The ID of the enchantment
     * @param enchantName The display name of the enchantment
     * @param boostPercent The boost percentage
     */
    private void handleCubeRemoval(String enchantId, String enchantName, double boostPercent) {
        boolean success = removeCubeFromTool(toolItem, enchantId);

        if (success) {
            // Play success sound
            playSound("removal-success");

            // Send success message
            sendMessage("removal-success", Map.of("{enchant_name}", enchantName));

            // Check if we should recover the cube
            boolean recovered = Math.random() * 100 < recoveryChance;
            if (recovered) {
                // Create a cube item and give it to the player
                ItemStack cubeItem = createCubeItem(enchantId, enchantName, (int)boostPercent);
                player.getInventory().addItem(cubeItem);

                // Play recovery sound
                playSound("removal-recovered");

                // Send recovery message
                sendMessage("removal-recovered", Map.of("{enchant_name}", enchantName));
            }

            // Update the parent menu's enchantment data
            parentMenu.refreshEnchantmentData();

            // Rebuild the menu to reflect the changes
            // Also remove the cube from our local map
            appliedCubes.remove(enchantId);
            build();
        } else {
            // Play failure sound if available
            playSound("removal-failed");

            // Send failure message
            sendMessage("removal-failed", Map.of("{enchant_name}", enchantName));
        }
    }

    /**
     * Remove a cube from a tool
     *
     * @param toolItem The tool to modify
     * @param enchantId The enchantment ID to remove the cube from
     * @return true if successful, false otherwise
     */
    private boolean removeCubeFromTool(ItemStack toolItem, String enchantId) {
        if (!GensTool.isGensTool(toolItem)) {
            return false;
        }

        ItemMeta meta = toolItem.getItemMeta();
        if (meta == null) {
            return false;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();

        // Remove the individual multiplier key
        NamespacedKey multiplierKey = new NamespacedKey(plugin, "enchant_multiplier_" + enchantId);
        container.remove(multiplierKey);

        // Update the consolidated cubes string
        NamespacedKey cubesKey = new NamespacedKey(plugin, "applied_cubes");
        if (container.has(cubesKey, PersistentDataType.STRING)) {
            String cubesData = container.get(cubesKey, PersistentDataType.STRING);
            if (cubesData != null && !cubesData.isEmpty()) {
                // Parse existing cubes and rebuild without the removed one
                StringBuilder sb = new StringBuilder();
                boolean first = true;

                for (String cube : cubesData.split(",")) {
                    String[] parts = cube.split(":");
                    if (parts.length == 2) {
                        String cubeEnchantId = parts[0];
                        if (!cubeEnchantId.equals(enchantId)) {
                            if (!first) {
                                sb.append(",");
                            }
                            sb.append(cube);
                            first = false;
                        }
                    }
                }

                container.set(cubesKey, PersistentDataType.STRING, sb.toString());
            }
        }

        // Apply changes
        toolItem.setItemMeta(meta);

        // Update the tool lore
        GensTool.updateEnchantmentLore(toolItem);

        // Sync with player's hand
        syncPlayerHand();

        // Register the tool update with the persistence manager
        plugin.getToolPersistenceManager().handleToolUpdate(player, toolItem);

        return true;
    }

    /**
     * Create a cube item for the player to receive
     *
     * @param enchantId The enchantment ID
     * @param enchantName The enchantment display name
     * @param boostPercent The boost percentage
     * @return The created cube item
     */
    private ItemStack createCubeItem(String enchantId, String enchantName, int boostPercent) {
        try {
            // Get the lowest tier for this enchantment
            int tier = 1; // Default to tier 1 if no specific tier is determined
            int successRate = 100; // Default to 100% success rate for recovered cubes
            double boostDecimal = boostPercent / 100.0; // Convert from percentage to decimal
            int amount = 1; // Create one cube

            // Call the existing createCube method with the proper parameters
            ItemStack cubeItem = plugin.getEnchantmentCubeManager().createCube(
                    tier,
                    enchantId,
                    successRate,
                    boostDecimal,
                    amount
            );

            // If successful, return the created cube
            if (cubeItem != null) {
                return cubeItem;
            }
        } catch (Exception e) {
            Utils.logWarning("Failed to create cube using EnchantmentCubeManager: " + e.getMessage());
        }

        // Fallback to manual creation if needed
        ItemStack cubeItem = new ItemStack(Material.MAGENTA_GLAZED_TERRACOTTA);
        ItemMeta meta = cubeItem.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&d" + enchantName + " Cube"));

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.translateAlternateColorCodes('&', "&7Enchantment: &e" + enchantName));
            lore.add(ChatColor.translateAlternateColorCodes('&', "&7Boost: &6+" + boostPercent + "%"));
            lore.add("");
            lore.add(ChatColor.translateAlternateColorCodes('&', "&aRight-click on a tool with this"));
            lore.add(ChatColor.translateAlternateColorCodes('&', "&aenchantment to apply the boost."));
            meta.setLore(lore);

            // Store the enchantment ID and boost in the item's persistent data
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(new NamespacedKey(plugin, "cube_type"), PersistentDataType.STRING, "enchantment");
            container.set(new NamespacedKey(plugin, "cube_enchant"), PersistentDataType.STRING, enchantId);
            container.set(new NamespacedKey(plugin, "cube_boost"), PersistentDataType.INTEGER, boostPercent);

            cubeItem.setItemMeta(meta);
        }

        return cubeItem;
    }

    /**
     * Sync the player's hand if they're holding the tool
     */
    private void syncPlayerHand() {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (GensTool.isGensTool(mainHand) &&
                GensTool.getToolId(mainHand).equals(GensTool.getToolId(toolItem))) {
            player.getInventory().setItemInMainHand(toolItem);
        }
    }

    /**
     * Play a sound from configuration
     */
    private void playSound(String soundKey) {
        SoundInfo soundInfo = sounds.get(soundKey);
        if (soundInfo != null) {
            player.playSound(player.getLocation(), soundInfo.sound, soundInfo.volume, soundInfo.pitch);
        }
    }

    /**
     * Send a message from configuration
     */
    private void sendMessage(String messageKey, Map<String, String> placeholders) {
        String message = messages.get(messageKey);
        if (message == null || message.isEmpty()) return;

        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                message = message.replace(entry.getKey(), entry.getValue());
            }
        }

        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    /**
     * Item configuration class for easy access to config values
     */
    private static class ItemInfo {
        boolean enabled = true;
        int slot = 0;
        Material material = Material.STONE;
        String name = "";
        List<String> lore = new ArrayList<>();
        boolean glow = false;
    }

    /**
     * Sound configuration class
     */
    private static class SoundInfo {
        Sound sound = Sound.UI_BUTTON_CLICK;
        float volume = 0.5f;
        float pitch = 1.0f;
    }
}