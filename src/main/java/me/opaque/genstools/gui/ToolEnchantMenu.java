package me.opaque.genstools.gui;

import me.opaque.genscore.hooks.GensCoreAPI;
import me.opaque.genstools.GensTools;
import me.opaque.genstools.enchants.CustomEnchant;
import me.opaque.genstools.enchants.EnchantmentApplicability;
import me.opaque.genstools.tools.GensTool;
import me.opaque.genstools.utils.NumberFormatter;
import me.opaque.genstools.utils.Utils;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Menu for upgrading tool enchantments
 * Designed for maximum performance and extensibility
 * Now fully configurable via gui_config.yml
 */
public class ToolEnchantMenu extends Menu {
    private final ItemStack toolItem;
    private final String toolId;
    private final Set<String> applicableEnchants;
    private final Map<String, Integer> currentEnchants;
    private int page = 0;
    private boolean isConfirmingReset = false;
    private final NumberFormatter formatter;

    // Configuration
    private final String title;
    private final int rows;
    private final List<Integer> enchantSlots = new ArrayList<>();
    private final Map<String, ItemInfo> itemConfig = new HashMap<>();
    private final Map<String, EnchantIconInfo> enchantIcons = new HashMap<>();
    private final Map<String, String> messages = new HashMap<>();
    private final Map<String, SoundInfo> sounds = new HashMap<>();
    private final Map<String, Long> baseShardCosts = new HashMap<>();
    private final Map<String, Long> baseRuneCosts = new HashMap<>();
    private final double costMultiplier;
    private final double refundRate;

    /**
     * Create a new tool enchantment menu
     */
    public ToolEnchantMenu(GensTools plugin, Player player, ItemStack toolItem) {
        super(plugin, player,
                plugin.getGuiConfig().getString("tool-gui.title", "&6Tool Enchantments"),
                plugin.getGuiConfig().getInt("tool-gui.rows", 6));

        this.toolItem = toolItem;
        this.toolId = GensTool.getToolId(toolItem);
        this.applicableEnchants = EnchantmentApplicability.getApplicableEnchants(toolItem);
        this.currentEnchants = GensTool.getEnchantments(toolItem);
        this.formatter = plugin.getNumberFormatter();

        // Load configuration
        this.title = plugin.getGuiConfig().getString("tool-gui.title", "&6Tool Enchantments");
        this.rows = plugin.getGuiConfig().getInt("tool-gui.rows", 6);
        this.costMultiplier = plugin.getGuiConfig().getDouble("tool-gui.cost-multiplier", 1.5);
        this.refundRate = plugin.getGuiConfig().getDouble("tool-gui.refund-rate", 0.7);

        // Load slot configuration
        loadSlotConfiguration();

        // Load item configurations
        loadItemConfigurations();

        // Load enchant icons
        loadEnchantIcons();

        // Load messages
        loadMessages();

        // Load sound configurations
        loadSoundConfigurations();

        // Load enchant costs
        loadEnchantCosts();
    }

    /**
     * Load slot configuration for enchantment slots
     */
    private void loadSlotConfiguration() {
        List<Integer> slots = plugin.getGuiConfig().getIntegerList("tool-gui.enchant-display.slots");
        if (slots.isEmpty()) {
            // Default slots if not configured
            int[] defaultSlots = {
                    10, 11, 12, 13, 14, 15, 16,
                    19, 20, 21, 22, 23, 24, 25,
                    28, 29, 30, 31, 32, 33, 34
            };
            for (int slot : defaultSlots) {
                enchantSlots.add(slot);
            }
        } else {
            enchantSlots.addAll(slots);
        }
    }

    /**
     * Load item configurations from config
     */
    private void loadItemConfigurations() {
        ConfigurationSection itemsSection = plugin.getGuiConfig().getConfigurationSection("tool-gui.items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                if (key.equals("reset-enchants")) continue; // Handle reset config separately

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

        // Load reset enchants configuration
        loadResetConfiguration(itemsSection);

        // Load enchant display configurations
        ConfigurationSection enchantDisplaySection = plugin.getGuiConfig().getConfigurationSection("tool-gui.enchant-display");
        if (enchantDisplaySection != null) {
            loadEnchantDisplayConfig("locked", enchantDisplaySection);
            loadEnchantDisplayConfig("unlocked", enchantDisplaySection);
            loadEnchantDisplayConfig("maxed", enchantDisplaySection);
        }
    }

    /**
     * Load reset enchantments configuration
     */
    private void loadResetConfiguration(ConfigurationSection itemsSection) {
        if (itemsSection == null) return;

        ConfigurationSection resetSection = itemsSection.getConfigurationSection("reset-enchants");
        if (resetSection != null) {
            ItemInfo resetInfo = new ItemInfo();
            resetInfo.enabled = resetSection.getBoolean("enabled", true);
            resetInfo.slot = resetSection.getInt("slot", 8);
            resetInfo.material = Material.valueOf(resetSection.getString("material", "BARRIER"));
            resetInfo.name = resetSection.getString("name", "&c&lReset Enchantments");
            resetInfo.lore = resetSection.getStringList("lore");
            itemConfig.put("reset-enchants", resetInfo);

            // Load confirm dialog configuration
            ConfigurationSection confirmSection = resetSection.getConfigurationSection("confirm");
            if (confirmSection != null) {
                // Confirm button
                ItemInfo confirmInfo = new ItemInfo();
                confirmInfo.enabled = confirmSection.getBoolean("enabled", true);
                confirmInfo.slot = confirmSection.getInt("confirm-slot", 11);
                confirmInfo.material = Material.valueOf(confirmSection.getString("confirm-material", "LIME_WOOL"));
                confirmInfo.name = confirmSection.getString("confirm-name", "&a&lConfirm Reset");
                confirmInfo.lore = confirmSection.getStringList("confirm-lore");
                itemConfig.put("reset-confirm", confirmInfo);

                // Cancel button
                ItemInfo cancelInfo = new ItemInfo();
                cancelInfo.enabled = true;
                cancelInfo.slot = confirmSection.getInt("cancel-slot", 15);
                cancelInfo.material = Material.valueOf(confirmSection.getString("cancel-material", "RED_WOOL"));
                cancelInfo.name = confirmSection.getString("cancel-name", "&c&lCancel");
                cancelInfo.lore = confirmSection.getStringList("cancel-lore");
                itemConfig.put("reset-cancel", cancelInfo);
            }
        }
    }

    /**
     * Load enchant icons configuration from config
     */
    private void loadEnchantIcons() {
        ConfigurationSection iconsSection = plugin.getGuiConfig().getConfigurationSection("tool-gui.enchant-icons");
        if (iconsSection != null) {
            // Load individual enchant icons
            for (String enchantId : iconsSection.getKeys(false)) {
                if (enchantId.equals("default")) continue; // Skip default section, we'll handle it separately

                ConfigurationSection enchantSection = iconsSection.getConfigurationSection(enchantId);
                if (enchantSection != null) {
                    EnchantIconInfo iconInfo = new EnchantIconInfo();

                    // Direct configuration for all states
                    Material material = Material.valueOf(enchantSection.getString("material", "ENCHANTED_BOOK"));
                    boolean glow = enchantSection.getBoolean("glow", true);

                    iconInfo.lockedMaterial = material;
                    iconInfo.lockedGlow = glow;
                    iconInfo.unlockedMaterial = material;
                    iconInfo.unlockedGlow = glow;
                    iconInfo.maxedMaterial = material;
                    iconInfo.maxedGlow = glow;

                    enchantIcons.put(enchantId, iconInfo);
                }
            }

            // Load default icons
            ConfigurationSection defaultSection = iconsSection.getConfigurationSection("default");
            if (defaultSection != null) {
                EnchantIconInfo defaultInfo = new EnchantIconInfo();

                // Load locked state
                ConfigurationSection lockedSection = defaultSection.getConfigurationSection("locked");
                if (lockedSection != null) {
                    defaultInfo.lockedMaterial = Material.valueOf(lockedSection.getString("material", "BOOK"));
                    defaultInfo.lockedGlow = lockedSection.getBoolean("glow", false);
                }

                // Load unlocked state
                ConfigurationSection unlockedSection = defaultSection.getConfigurationSection("unlocked");
                if (unlockedSection != null) {
                    defaultInfo.unlockedMaterial = Material.valueOf(unlockedSection.getString("material", "ENCHANTED_BOOK"));
                    defaultInfo.unlockedGlow = unlockedSection.getBoolean("glow", true);
                }

                // Load maxed state
                ConfigurationSection maxedSection = defaultSection.getConfigurationSection("maxed");
                if (maxedSection != null) {
                    defaultInfo.maxedMaterial = Material.valueOf(maxedSection.getString("material", "KNOWLEDGE_BOOK"));
                    defaultInfo.maxedGlow = maxedSection.getBoolean("glow", true);
                }

                enchantIcons.put("default", defaultInfo);
            }
        }
    }

    /**
     * Load enchant display configurations
     */
    private void loadEnchantDisplayConfig(String configKey, ConfigurationSection parentSection) {
        ConfigurationSection section = parentSection.getConfigurationSection(configKey);
        if (section != null) {
            ItemInfo info = new ItemInfo();
            info.enabled = true;
            // Material is now handled by enchant-icons
            info.name = section.getString("name", "&6{enchant_name}");
            info.lore = section.getStringList("lore");
            itemConfig.put("enchant-" + configKey, info);
        }
    }

    /**
     * Load message configurations from config
     */
    private void loadMessages() {
        ConfigurationSection messagesSection = plugin.getGuiConfig().getConfigurationSection("tool-gui.messages");
        if (messagesSection != null) {
            for (String key : messagesSection.getKeys(false)) {
                messages.put(key, messagesSection.getString(key, ""));
            }
        }
    }

    /**
     * Load sound configurations from config
     */
    private void loadSoundConfigurations() {
        ConfigurationSection soundsSection = plugin.getGuiConfig().getConfigurationSection("tool-gui.sounds");
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
     * Load enchantment costs from config
     */
    private void loadEnchantCosts() {
        ConfigurationSection costsSection = plugin.getGuiConfig().getConfigurationSection("tool-gui.enchant-costs");
        if (costsSection != null) {
            for (String enchantId : costsSection.getKeys(false)) {
                ConfigurationSection enchantSection = costsSection.getConfigurationSection(enchantId);
                if (enchantSection != null) {
                    baseShardCosts.put(enchantId, enchantSection.getLong("shards", 1000));
                    baseRuneCosts.put(enchantId, enchantSection.getLong("runes", 100));
                }
            }
        }
    }

    /**
     * Add this method to your ToolEnchantMenu class to refresh enchantment data
     */
    public void refreshEnchantmentData() {
        // Reload the current enchantments from the tool
        Map<String, Integer> refreshedEnchants = GensTool.getEnchantments(toolItem);

        // Clear and update the map
        this.currentEnchants.clear();
        this.currentEnchants.putAll(refreshedEnchants);

        Utils.logDebug("Refreshed enchantment data in ToolEnchantMenu: " + currentEnchants);
    }

    @Override
    protected void build() {
        Utils.logDebug("Building " + this.getClass().getSimpleName() + " for " + player.getName());
        if (isConfirmingReset) {
            buildResetConfirmation();
        } else {
            buildMainMenu();
        }

        // At the end, count items
        int itemCount = 0;
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) != null) {
                itemCount++;
            }
        }
        Utils.logDebug(this.getClass().getSimpleName() + " built with " + itemCount + " items");
    }

    /**
     * Build the main enchantment menu
     */
    private void buildMainMenu() {
        // Fill background
        ItemInfo fillerInfo = itemConfig.getOrDefault("filler", new ItemInfo());
        fillEmptySlots(fillerInfo.material);

        // Add tool info at the top
        addToolInfo();

        // Add tool display
        ItemInfo toolDisplayInfo = itemConfig.getOrDefault("tool-display", new ItemInfo());
        if (toolDisplayInfo.enabled) {
            ItemStack displayItem = toolItem.clone();

            // Make the tool glow if configured
            if (toolDisplayInfo.glow) {
                displayItem = GensTool.applyGlow(displayItem);
            }

            setItem(toolDisplayInfo.slot, displayItem);
        }

        // Add reset enchantments button
        addResetButton();

        // Add cube management button
        addCubeManagementButton();

        // Add enchants
        addEnchantments();

        // Add navigation
        addNavigation();
    }

    /**
     * Add tool information to the menu
     */
    private void addToolInfo() {
        ItemInfo infoInfo = itemConfig.getOrDefault("tool-info", new ItemInfo());
        if (!infoInfo.enabled) return;

        int level = GensTool.getLevel(toolItem);
        int exp = GensTool.getExperience(toolItem);
        int reqExp = GensTool.calculateRequiredExp(level);

        List<String> lore = new ArrayList<>();
        for (String line : infoInfo.lore) {
            line = line.replace("{level}", String.valueOf(level))
                    .replace("{exp}", formatter.format(exp))  // Format with NumberFormatter
                    .replace("{req_exp}", formatter.format(reqExp))  // Format with NumberFormatter
                    .replace("{enchant_count}", String.valueOf(currentEnchants.size()));
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
     * Add the reset button to the menu if there are enchantments to reset
     */
    private void addResetButton() {
        // Only show reset button if there are enchantments
        if (currentEnchants.isEmpty()) {
            return;
        }

        ItemInfo resetInfo = itemConfig.getOrDefault("reset-enchants", new ItemInfo());
        if (resetInfo.enabled) {
            // Calculate refund amounts
            Map<String, Long> refundInfo = calculateRefundAmounts();
            long refundShards = refundInfo.getOrDefault("shards", 0L);
            long refundRunes = refundInfo.getOrDefault("runes", 0L);

            // Replace placeholders in lore
            List<String> lore = new ArrayList<>();
            for (String line : resetInfo.lore) {
                line = line.replace("{refund_shards}", formatter.format(refundShards))  // Format with NumberFormatter
                        .replace("{refund_runes}", formatter.format(refundRunes));  // Format with NumberFormatter
                lore.add(line);
            }

            ItemStack resetItem = createItem(resetInfo.material, resetInfo.name, lore);
            // Apply glow effect if configured
            resetItem = applyGlowIfConfigured(resetItem, resetInfo.glow);

            setItem(resetInfo.slot, resetItem, e -> {
                isConfirmingReset = true;
                buildResetConfirmation();
            });
        }
    }

    /**
     * Calculate the refund amounts for shards and runes
     */
    private Map<String, Long> calculateRefundAmounts() {
        Map<String, Long> result = new HashMap<>();

        long totalShards = 0;
        long totalRunes = 0;

        // Calculate total invested in each enchantment
        for (Map.Entry<String, Integer> entry : currentEnchants.entrySet()) {
            String enchantId = entry.getKey();
            int level = entry.getValue();

            // Get the enchant to determine its currency type
            CustomEnchant enchant = plugin.getToolManager().getEnchantById(enchantId);
            if (enchant == null) continue;

            // Determine if this enchant uses shards or runes
            boolean useShards = enchant.getCurrencyType() == CustomEnchant.CurrencyType.SHARDS;

            // Get base cost for the appropriate currency
            long baseCost;
            if (useShards) {
                baseCost = baseShardCosts.getOrDefault(enchantId,
                        baseShardCosts.getOrDefault("default", 1000L));
            } else {
                baseCost = baseRuneCosts.getOrDefault(enchantId,
                        baseRuneCosts.getOrDefault("default", 100L));
            }

            // Sum up costs for each level
            for (int i = 1; i <= level; i++) {
                long levelCost = (long) (baseCost * Math.pow(costMultiplier, i - 1));

                if (useShards) {
                    totalShards += levelCost;
                } else {
                    totalRunes += levelCost;
                }
            }
        }

        // Apply refund rate
        result.put("shards", (long) (totalShards * refundRate));
        result.put("runes", (long) (totalRunes * refundRate));

        return result;
    }

    /**
     * Build the reset confirmation menu
     */
    private void buildResetConfirmation() {
        // Clear all existing items
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, null);
            items.remove(i);
        }

        // Fill background
        ItemInfo fillerInfo = itemConfig.getOrDefault("filler", new ItemInfo());
        fillEmptySlots(fillerInfo.material);

        // Add tool display in center
        ItemInfo toolDisplayInfo = itemConfig.getOrDefault("tool-display", new ItemInfo());
        if (toolDisplayInfo.enabled) {
            ItemStack displayItem = toolItem.clone();

            // Make the tool glow if configured
            displayItem = applyGlowIfConfigured(displayItem, toolDisplayInfo.glow);

            setItem(toolDisplayInfo.slot, displayItem);
        }

        // Add confirm button
        ItemInfo confirmInfo = itemConfig.getOrDefault("reset-confirm", new ItemInfo());
        if (confirmInfo.enabled) {
            // Calculate refund amounts
            Map<String, Long> refundInfo = calculateRefundAmounts();
            long refundShards = refundInfo.getOrDefault("shards", 0L);
            long refundRunes = refundInfo.getOrDefault("runes", 0L);

            // Replace placeholders in lore
            List<String> lore = new ArrayList<>();
            for (String line : confirmInfo.lore) {
                line = line.replace("{refund_shards}", formatter.format(refundShards))  // Format with NumberFormatter
                        .replace("{refund_runes}", formatter.format(refundRunes));  // Format with NumberFormatter
                lore.add(line);
            }

            ItemStack confirmItem = createItem(confirmInfo.material, confirmInfo.name, lore);
            confirmItem = applyGlowIfConfigured(confirmItem, confirmInfo.glow);

            setItem(confirmInfo.slot, confirmItem, e -> handleResetConfirm());
        }

        // Add cancel button
        ItemInfo cancelInfo = itemConfig.getOrDefault("reset-cancel", new ItemInfo());
        if (cancelInfo.enabled) {
            // Replace placeholders in lore
            List<String> lore = new ArrayList<>();
            for (String line : cancelInfo.lore) {
                lore.add(line);
            }

            ItemStack cancelItem = createItem(cancelInfo.material, cancelInfo.name, lore);
            cancelItem = applyGlowIfConfigured(cancelItem, cancelInfo.glow);

            setItem(cancelInfo.slot, cancelItem, e -> handleResetCancel());
        }
    }

    /**
     * Handle reset confirmation
     */
    private void handleResetConfirm() {
        // Calculate refund amounts
        Map<String, Long> refundInfo = calculateRefundAmounts();
        long refundShards = refundInfo.getOrDefault("shards", 0L);
        long refundRunes = refundInfo.getOrDefault("runes", 0L);

        // Apply refund
        GensCoreAPI api = plugin.getGensCoreAPI().getAPI();
        api.addShards(player.getUniqueId(), refundShards);
        api.addRunes(player.getUniqueId(), refundRunes);

        // Reset all enchantments
        boolean success = true;
        // Use the current enchantments map to know which ones to remove
        for (String enchantId : new ArrayList<>(currentEnchants.keySet())) {
            boolean removed = GensTool.removeEnchantment(toolItem, enchantId, false); // Don't update lore on each removal
            if (!removed) {
                success = false;
            }
        }

        // Update lore once after all removals
        GensTool.updateEnchantmentLore(toolItem);

        plugin.getToolPersistenceManager().handleToolUpdate(player, toolItem);
        // Clear the local enchants map
        currentEnchants.clear();

        // Sync player's hand if that's where the tool is
        syncPlayerHand();

        // Show success message
        if (success) {
            playSound("reset-success");
            sendMessage("reset-success", Map.of(
                    "{refund_shards}", formatter.format(refundShards),  // Format with NumberFormatter
                    "{refund_runes}", formatter.format(refundRunes)  // Format with NumberFormatter
            ));
        } else {
            playSound("reset-failed");
            sendMessage("reset-failed");
        }

        // Return to main menu
        isConfirmingReset = false;

        // Clear the entire inventory first
        clearInventory();

        // Then rebuild it with the main menu
        build();
    }

    /**
     * Handle reset cancellation
     */
    private void handleResetCancel() {
        playSound("reset-cancelled");
        sendMessage("reset-cancelled");

        // Return to main menu
        isConfirmingReset = false;

        // Clear the entire inventory first
        clearInventory();

        // Then rebuild it with the main menu
        build();
    }

    /**
     * Completely clear the inventory
     */
    private void clearInventory() {
        // Clear all existing items
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, null);
        }

        // Clear the item handlers map
        items.clear();
    }

    // Helper methods for configuration
    private String getConfigString(Map<String, Object> config, String key, String defaultValue) {
        return config.getOrDefault(key, defaultValue).toString();
    }

    private int getConfigInt(Map<String, Object> config, String key, int defaultValue) {
        Object value = config.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    /**
     * Apply glow effect to an item if configured
     */
    private ItemStack applyGlowIfConfigured(ItemStack item, boolean shouldGlow) {
        if (shouldGlow && item != null) {
            return GensTool.applyGlow(item);
        }
        return item;
    }

    @SuppressWarnings("unchecked")
    private List<String> getConfigList(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value instanceof List) {
            return (List<String>) value;
        }
        return new ArrayList<>();
    }

    /**
     * Add enchantments to the menu
     */
    private void addEnchantments() {
        // Calculate pagination
        int enchantCount = applicableEnchants.size();
        int maxEnchants = enchantSlots.size();
        int maxPages = (int) Math.ceil((double) enchantCount / maxEnchants);

        if (page >= maxPages) page = 0;

        // Get enchants for current page
        List<String> pageEnchants = new ArrayList<>(applicableEnchants);
        int startIndex = page * maxEnchants;
        int endIndex = Math.min(startIndex + maxEnchants, enchantCount);

        if (startIndex >= enchantCount) return;

        // Add enchants for this page
        for (int i = 0; i < endIndex - startIndex; i++) {
            if (i >= enchantSlots.size()) break;
            String enchantId = pageEnchants.get(startIndex + i);
            addEnchantmentSlot(enchantSlots.get(i), enchantId);
        }
    }

    /**
     * Add cube management button
     */
    private void addCubeManagementButton() {
        ItemInfo cubeInfo = itemConfig.getOrDefault("manage-cubes", new ItemInfo());
        if (!cubeInfo.enabled) return;

        // Get cube count directly from the tool
        int cubeCount = getCubeCount(toolItem);

        // Skip if no cubes are applied
        if (cubeCount == 0 && !plugin.getConfig().getBoolean("settings.show-empty-cube-menu", false)) {
            return;
        }

        // Replace placeholders in lore
        List<String> lore = new ArrayList<>();
        for (String line : cubeInfo.lore) {
            line = line.replace("{cube_count}", String.valueOf(cubeCount));
            lore.add(line);
        }

        ItemStack cubeItem = createItem(cubeInfo.material, cubeInfo.name, lore);

        // Apply glow if configured
        if (cubeInfo.glow) {
            cubeItem = GensTool.applyGlow(cubeItem);
        }

        setItem(cubeInfo.slot, cubeItem, e -> {
            // Create and switch to the cube removal menu
            CubeRemovalMenu cubeMenu = new CubeRemovalMenu(plugin, player, toolItem, this);
            switchTo(cubeMenu);
        });
    }

    /**
     * Get count of applied cubes from the tool
     */
    private int getCubeCount(ItemStack toolItem) {
        if (!GensTool.isGensTool(toolItem)) {
            return 0;
        }

        ItemMeta meta = toolItem.getItemMeta();
        if (meta == null) {
            return 0;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();

        // Check for the consolidated cubes string
        NamespacedKey cubesKey = new NamespacedKey(plugin, "applied_cubes");
        if (container.has(cubesKey, PersistentDataType.STRING)) {
            String cubesData = container.get(cubesKey, PersistentDataType.STRING);
            if (cubesData != null && !cubesData.isEmpty()) {
                return cubesData.split(",").length;
            }
        }

        return 0;
    }

    /**
     * Add an enchantment to a slot
     */
    private void addEnchantmentSlot(int slot, String enchantId) {
        CustomEnchant enchant = plugin.getToolManager().getEnchantById(enchantId);
        String shardsConfigVar = Utils.colorize(plugin.getConfig().getString("currencies.shards.color"));
        String runesConfigVar = Utils.colorize(plugin.getConfig().getString("currencies.runes.color"));
        if (enchant == null) return;

        int currentLevel = currentEnchants.getOrDefault(enchantId, 0);
        int maxLevel = enchant.getMaxLevel();

        // Determine currency type
        boolean useShards = enchant.getCurrencyType() == CustomEnchant.CurrencyType.SHARDS;
        String currencyName = useShards ? "Shards" : "Runes";
        String currencyColor = useShards ? shardsConfigVar : runesConfigVar;

        // Choose the appropriate configuration
        String configKey = currentLevel == 0 ? "enchant-locked" :
                currentLevel >= maxLevel ? "enchant-maxed" : "enchant-unlocked";
        ItemInfo enchantInfo = itemConfig.getOrDefault(configKey, new ItemInfo());

        // Replace placeholders in name
        String name = enchantInfo.name
                .replace("{enchant_name}", enchant.getDisplayName())
                .replace("{level}", currentLevel > 0 ? GensTool.formatEnchantmentLevel(currentLevel) : "")
                .replace("{current_level}", String.valueOf(currentLevel))
                .replace("{max_level}", String.valueOf(maxLevel))
                .replace("{currency}", currencyName)
                .replace("{currency_color}", currencyColor);

        // Calculate cost for display - use the appropriate currency
        long cost = calculateCost(enchantId, currentLevel, useShards);

        // Replace placeholders in lore
        List<String> lore = new ArrayList<>();
        boolean addedCurrencyLine = false;

        for (String line : enchantInfo.lore) {
            // Skip lines with left-click/right-click distinctions
            if (line.contains("Left-Click") || line.contains("Right-Click")) {
                continue;
            }

            // Add our unified currency line
            if ((line.contains("{shard_cost}") || line.contains("{rune_cost}")) && !addedCurrencyLine) {
                if (currentLevel < maxLevel) {
                    lore.add(ChatColor.translateAlternateColorCodes('&', "&aClick &7to upgrade with " + currencyColor + currencyName));
                    lore.add(ChatColor.translateAlternateColorCodes('&', "&7Cost: " + currencyColor + formatter.format(cost) + " " + currencyName));
                    addedCurrencyLine = true;
                }
                continue;
            }

            line = line.replace("{enchant_description}", enchant.getDescription())
                    .replace("{enchant_name}", enchant.getDisplayName())
                    .replace("{current_level}", currentLevel > 0 ? String.valueOf(currentLevel) : "Not Unlocked")
                    .replace("{max_level}", String.valueOf(maxLevel))
                    .replace("{cost}", formatter.format(cost))
                    .replace("{currency}", currencyName);

            // For backward compatibility
            if (useShards) {
                line = line.replace("{shard_cost}", formatter.format(cost));
            } else {
                line = line.replace("{rune_cost}", formatter.format(cost));
            }

            line = line.replace("{level}", currentLevel > 0 ? GensTool.formatEnchantmentLevel(currentLevel) : "");
            lore.add(ChatColor.translateAlternateColorCodes('&', line));
        }

        // Get enchant icon info
        EnchantIconInfo iconInfo = enchantIcons.getOrDefault(enchantId, enchantIcons.getOrDefault("default", new EnchantIconInfo()));

        // Choose the appropriate material and glow based on enchant level
        Material material;
        boolean shouldGlow;

        if (currentLevel == 0) {
            material = iconInfo.lockedMaterial;
            shouldGlow = iconInfo.lockedGlow;
        } else if (currentLevel >= maxLevel) {
            material = iconInfo.maxedMaterial;
            shouldGlow = iconInfo.maxedGlow;
        } else {
            material = iconInfo.unlockedMaterial;
            shouldGlow = iconInfo.unlockedGlow;
        }

        // Create the item
        ItemStack item = createEnchantItem(material, name, lore, shouldGlow);

        setItem(slot, item, event -> {
            // Play navigation sound
            playSound("navigation");

            // Create new menu and switch to it (preserves cursor)
            EnchantDetailMenu detailMenu = new EnchantDetailMenu(plugin, player, toolItem, enchantId, this);
            switchTo(detailMenu);
        });
    }

    /**
     * Create an enchantment item with optional glow effect
     */
    private ItemStack createEnchantItem(Material material, String name, List<String> lore, boolean glow) {
        ItemStack item = createItem(material, name, lore);

        if (glow) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(meta);
            }
        }

        return item;
    }

    /**
     * Calculate the cost for upgrading an enchantment
     */
    private long calculateCost(String enchantId, int currentLevel, boolean isShards) {
        Map<String, Long> costs = isShards ? baseShardCosts : baseRuneCosts;
        long baseCost = costs.getOrDefault(enchantId,
                costs.getOrDefault("default", isShards ? 1000L : 100L));

        // First level costs base amount
        if (currentLevel == 0) return baseCost;

        // Higher levels cost more
        return (long) (baseCost * Math.pow(costMultiplier, currentLevel));
    }

    /**
     * Handle enchantment upgrade
     */
    private void handleEnchantUpgrade(String enchantId, boolean isShards) {
        CustomEnchant enchant = plugin.getToolManager().getEnchantById(enchantId);
        if (enchant == null) return;

        // Get current level directly from the tool item to ensure it's up-to-date
        int currentLevel = GensTool.getEnchantmentLevel(toolItem, enchantId);

        // Already max level
        if (currentLevel >= enchant.getMaxLevel()) {
            playSound("upgrade-failed");
            sendMessage("max-level", Map.of("{enchant_name}", enchant.getDisplayName()));
            return;
        }

        // Calculate cost
        long cost = calculateCost(enchantId, currentLevel, isShards);

        // Check balance
        GensCoreAPI api = plugin.getGensCoreAPI().getAPI();
        long balance = isShards ?
                api.getShardsBalance(player.getUniqueId()) :
                api.getRunesBalance(player.getUniqueId());

        if (balance < cost) {
            playSound("upgrade-failed");
            String currency = isShards ? "Shards" : "Runes";
            sendMessage("insufficient-funds", Map.of(
                    "{currency}", currency,
                    "{cost}", formatter.format(cost),       // Format with NumberFormatter
                    "{balance}", formatter.format(balance)  // Format with NumberFormatter
            ));
            return;
        }

        // Deduct currency
        boolean success = isShards ?
                api.removeShards(player.getUniqueId(), cost) :
                api.removeRunes(player.getUniqueId(), cost);

        if (!success) {
            sendMessage("payment-failed", null);
            return;
        }

        // Apply upgrade
        boolean upgradeSuccess = plugin.getToolManager().addEnchantToTool(toolItem, enchantId, currentLevel + 1);

        if (!upgradeSuccess) {
            // Refund if failed
            if (isShards) {
                api.addShards(player.getUniqueId(), cost);
            } else {
                api.addRunes(player.getUniqueId(), cost);
            }
            sendMessage("upgrade-failed", null);
            return;
        }

        // Update the local enchantments map to reflect the new level
        // This is crucial for consecutive upgrades
        currentEnchants.put(enchantId, currentLevel + 1);

        // Sync player's hand if that's where the tool is
        syncPlayerHand();

        // Show success message
        playSound("upgrade-success");
        sendMessage("upgrade-success", Map.of(
                "{enchant_name}", enchant.getDisplayName(),
                "{level}", GensTool.formatEnchantmentLevel(currentLevel + 1)
        ));

        plugin.getToolPersistenceManager().handleToolUpdate(player, toolItem);

        // Rebuild the menu to reflect the updated enchantment level
        Bukkit.getScheduler().runTask(plugin, this::build);
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
     * Overloaded version of sendMessage with no placeholders
     */
    private void sendMessage(String messageKey) {
        sendMessage(messageKey, null);
    }

    /**
     * Sync the player's hand if they're holding the tool
     */
    private void syncPlayerHand() {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (GensTool.isGensTool(mainHand) && GensTool.getToolId(mainHand).equals(toolId)) {
            player.getInventory().setItemInMainHand(toolItem);
        }
    }

    /**
     * Add navigation buttons
     */
    private void addNavigation() {
        // Calculate pagination
        int enchantCount = applicableEnchants.size();
        int maxEnchants = enchantSlots.size();
        int maxPages = (int) Math.ceil((double) enchantCount / maxEnchants);

        // Previous page
        ItemInfo prevInfo = itemConfig.getOrDefault("prev-page", new ItemInfo());
        if (prevInfo.enabled && page > 0) {
            List<String> lore = new ArrayList<>(prevInfo.lore);
            ItemStack prevItem = createItem(prevInfo.material, prevInfo.name, lore);

            // Apply glow if configured
            if (prevInfo.glow) {
                prevItem = GensTool.applyGlow(prevItem);
            }

            setItem(prevInfo.slot, prevItem, e -> {
                page--;
                build();
                playSound("navigation");
            });
        }

        // Next page
        ItemInfo nextInfo = itemConfig.getOrDefault("next-page", new ItemInfo());
        if (nextInfo.enabled && page < maxPages - 1) {
            List<String> lore = new ArrayList<>(nextInfo.lore);
            ItemStack nextItem = createItem(nextInfo.material, nextInfo.name, lore);

            // Apply glow if configured
            if (nextInfo.glow) {
                nextItem = GensTool.applyGlow(nextItem);
            }

            setItem(nextInfo.slot, nextItem, e -> {
                page++;
                build();
                playSound("navigation");
            });
        }

        // Page indicator
        ItemInfo pageInfo = itemConfig.getOrDefault("page-indicator", new ItemInfo());
        if (pageInfo.enabled && maxPages > 1) {
            String name = pageInfo.name
                    .replace("{page}", String.valueOf(page + 1))
                    .replace("{max_page}", String.valueOf(maxPages));

            List<String> lore = new ArrayList<>();
            for (String line : pageInfo.lore) {
                line = line.replace("{page}", String.valueOf(page + 1))
                        .replace("{max_page}", String.valueOf(maxPages));
                lore.add(line);
            }

            ItemStack pageItem = createItem(pageInfo.material, name, lore);

            // Apply glow if configured
            if (pageInfo.glow) {
                pageItem = GensTool.applyGlow(pageItem);
            }

            setItem(pageInfo.slot, pageItem);
        }
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
     * Enchant icon information class
     */
    private static class EnchantIconInfo {
        Material lockedMaterial = Material.BOOK;
        boolean lockedGlow = false;
        Material unlockedMaterial = Material.ENCHANTED_BOOK;
        boolean unlockedGlow = true;
        Material maxedMaterial = Material.KNOWLEDGE_BOOK;
        boolean maxedGlow = true;
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