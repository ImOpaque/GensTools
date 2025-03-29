package me.opaque.genstools.gui;

import me.opaque.genscore.hooks.GensCoreAPI;
import me.opaque.genstools.GensTools;
import me.opaque.genstools.enchants.CustomEnchant;
import me.opaque.genstools.tools.GensTool;
import me.opaque.genstools.utils.NumberFormatter;
import me.opaque.genstools.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Detailed menu for a specific enchantment, allowing for different upgrade increments
 */
public class EnchantDetailMenu extends Menu {
    private final ItemStack toolItem;
    private final String enchantId;
    private final CustomEnchant enchant;
    private final ToolEnchantMenu parentMenu;
    private final boolean messageToggleEnabled;
    private final boolean enchantToggleEnabled;
    private final NumberFormatter formatter;

    // Increment options for upgrading
    private final int[] incrementOptions = {1, 10, 50, 100, 250, 500, 750, 1000};

    // Configuration and state
    private final Map<String, Sound> sounds = new HashMap<>();
    private final Map<String, String> messages = new HashMap<>();
    private boolean messagesEnabled = true;
    private boolean enchantEnabled = true;

    public EnchantDetailMenu(GensTools plugin, Player player, ItemStack toolItem,
                             String enchantId, ToolEnchantMenu parentMenu) {
        super(plugin, player,
                plugin.getGuiConfig().getString("enchant-detail.title", "&6Enchantment: &e{enchant_name}")
                        .replace("{enchant_name}", getEnchantName(plugin, enchantId)),
                plugin.getGuiConfig().getInt("enchant-detail.rows", 5));

        this.toolItem = toolItem;
        this.enchantId = enchantId;
        this.enchant = plugin.getToolManager().getEnchantById(enchantId);
        this.parentMenu = parentMenu;
        this.formatter = plugin.getNumberFormatter();

        // Load configuration options
        this.messageToggleEnabled = plugin.getGuiConfig().getBoolean("enchant-detail.message-toggle.enabled", true);
        this.enchantToggleEnabled = plugin.getGuiConfig().getBoolean("enchant-detail.enchant-toggle.enabled", true);

        // Check if we should load player's message preference
        if (messageToggleEnabled) {
            this.messagesEnabled = !plugin.getToolManager().hasDisabledMessages(player.getUniqueId(), enchantId);
        }

        // Check if we should load player's enchant toggle preference
        if (enchantToggleEnabled) {
            this.enchantEnabled = !plugin.getToolManager().hasDisabledEnchant(player.getUniqueId(), enchantId, toolItem);
        }

        // Load sounds
        loadSounds();

        // Load messages
        loadMessages();
    }

    /**
     * Get enchant name for the title
     */
    private static String getEnchantName(GensTools plugin, String enchantId) {
        CustomEnchant enchant = plugin.getToolManager().getEnchantById(enchantId);
        return enchant != null ? enchant.getDisplayName() : enchantId;
    }

    /**
     * Load sound configurations
     */
    private void loadSounds() {
        // Reuse the same sounds from the main menu
        if (plugin.getGuiConfig().contains("tool-gui.sounds.upgrade-success")) {
            String soundName = plugin.getGuiConfig().getString("tool-gui.sounds.upgrade-success.sound", "ENTITY_PLAYER_LEVELUP");
            sounds.put("upgrade-success", Sound.valueOf(soundName));
        }

        if (plugin.getGuiConfig().contains("tool-gui.sounds.upgrade-failed")) {
            String soundName = plugin.getGuiConfig().getString("tool-gui.sounds.upgrade-failed.sound", "ENTITY_ENDERMAN_TELEPORT");
            sounds.put("upgrade-failed", Sound.valueOf(soundName));
        }

        if (plugin.getGuiConfig().contains("tool-gui.sounds.navigation")) {
            String soundName = plugin.getGuiConfig().getString("tool-gui.sounds.navigation.sound", "UI_BUTTON_CLICK");
            sounds.put("navigation", Sound.valueOf(soundName));
        }
    }

    /**
     * Load message configurations
     */
    private void loadMessages() {
        // Reuse the same messages from the main menu
        if (plugin.getGuiConfig().contains("tool-gui.messages.upgrade-success")) {
            messages.put("upgrade-success", plugin.getGuiConfig().getString("tool-gui.messages.upgrade-success"));
        }

        if (plugin.getGuiConfig().contains("tool-gui.messages.max-level")) {
            messages.put("max-level", plugin.getGuiConfig().getString("tool-gui.messages.max-level"));
        }

        if (plugin.getGuiConfig().contains("tool-gui.messages.insufficient-funds")) {
            messages.put("insufficient-funds", plugin.getGuiConfig().getString("tool-gui.messages.insufficient-funds"));
        }

        if (plugin.getGuiConfig().contains("tool-gui.messages.payment-failed")) {
            messages.put("payment-failed", plugin.getGuiConfig().getString("tool-gui.messages.payment-failed"));
        }

        if (plugin.getGuiConfig().contains("tool-gui.messages.upgrade-failed")) {
            messages.put("upgrade-failed", plugin.getGuiConfig().getString("tool-gui.messages.upgrade-failed"));
        }
    }

    @Override
    protected void build() {
        debugBuild("EnchantDetail");
        // Fill background
        Material fillerMaterial = Material.valueOf(
                plugin.getGuiConfig().getString("enchant-detail.filler.material", "GRAY_STAINED_GLASS_PANE"));
        fillEmptySlots(fillerMaterial);

        // Add enchantment info at the top
        addEnchantInfo();

        // Add upgrade options in the middle row
        addUpgradeOptions();

        // Add tool display
        addToolDisplay();

        // Add back button
        addBackButton();

        // Add message toggle if enabled
        if (messageToggleEnabled) {
            addMessageToggle();
        }

        // Add enchant toggle if enabled
        if (enchantToggleEnabled) {
            addEnchantToggle();
        }

        // Add max upgrade button
        addMaxUpgradeButton();
    }

    /**
     * Add enchantment information at the top of the menu
     */
    private void addEnchantInfo() {
        if (enchant == null) return;

        int slot = plugin.getGuiConfig().getInt("enchant-detail.enchant-info.slot", 4);
        Material material = Material.valueOf(
                plugin.getGuiConfig().getString("enchant-detail.enchant-info.material", "ENCHANTED_BOOK"));

        int currentLevel = GensTool.getEnchantmentLevel(toolItem, enchantId);
        int maxLevel = enchant.getMaxLevel();

        String shardsConfigVar = Utils.colorize(plugin.getConfig().getString("currencies.shards.color"));
        String runesConfigVar = Utils.colorize(plugin.getConfig().getString("currencies.runes.color"));

        // Get currency type for display
        String currencyName = enchant.getCurrencyType() == CustomEnchant.CurrencyType.SHARDS ? "Shards" : "Runes";
        String currencyColor = enchant.getCurrencyType() == CustomEnchant.CurrencyType.SHARDS ? shardsConfigVar : runesConfigVar;

        String name = ChatColor.translateAlternateColorCodes('&',
                plugin.getGuiConfig().getString("enchant-detail.enchant-info.name", "&6{enchant_name} {level}")
                        .replace("{enchant_name}", enchant.getDisplayName())
                        .replace("{level}", currentLevel > 0 ? GensTool.formatEnchantmentLevel(currentLevel) : "")
                        .replace("{current_level}", String.valueOf(currentLevel))
                        .replace("{max_level}", String.valueOf(maxLevel))
                        .replace("{currency}", currencyName))
                        .replace("{currency_color}", currencyColor);

        List<String> loreConfig = plugin.getGuiConfig().getStringList("enchant-detail.enchant-info.lore");
        List<String> lore = new ArrayList<>();

        for (String line : loreConfig) {
            line = line.replace("{enchant_description}", enchant.getDescription())
                    .replace("{enchant_name}", enchant.getDisplayName())
                    .replace("{current_level}", String.valueOf(currentLevel))
                    .replace("{max_level}", String.valueOf(maxLevel))
                    .replace("{level}", currentLevel > 0 ? GensTool.formatEnchantmentLevel(currentLevel) : "")
                    .replace("{currency}", currencyName)
                    .replace("{currency_color}", currencyColor);

            lore.add(ChatColor.translateAlternateColorCodes('&', line));
        }

        ItemStack infoItem = createItem(material, name, lore);

        // Apply glow effect
        ItemMeta meta = infoItem.getItemMeta();
        if (meta != null) {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            infoItem.setItemMeta(meta);
        }

        infoItem = GensTool.applyGlow(infoItem);

        setItem(slot, infoItem);
    }

    /**
     * Add upgrade options in the middle row
     */
    private void addUpgradeOptions() {
        int startSlot = plugin.getGuiConfig().getInt("enchant-detail.upgrade-options.start-slot", 10);
        int currentLevel = GensTool.getEnchantmentLevel(toolItem, enchantId);
        int maxLevel = enchant != null ? enchant.getMaxLevel() : 1;

        // Check if already at max level
        if (currentLevel >= maxLevel) {
            return;
        }

        // Get the materials for the upgrade buttons from config
        String defaultMaterial = plugin.getGuiConfig().getString("enchant-detail.upgrade-options.material", "MAGMA_CREAM");

        // Add each upgrade option
        for (int i = 0; i < incrementOptions.length; i++) {
            final int increment = incrementOptions[i]; // Make it final
            int slot = startSlot + i;

            // Skip if increment would exceed max level
            if (currentLevel + increment > maxLevel) {
                // If this is the first option, set it to max what's possible
                if (i == 0 && maxLevel - currentLevel > 0) {
                    final int possibleIncrement = maxLevel - currentLevel; // Create a new final variable
                    addUpgradeOption(slot, possibleIncrement, defaultMaterial, currentLevel, maxLevel);
                }
                continue;
            }

            addUpgradeOption(slot, increment, defaultMaterial, currentLevel, maxLevel);
        }
    }

    /**
     * Helper method to add a single upgrade option
     */
    private void addUpgradeOption(int slot, int increment, String defaultMaterial, int currentLevel, int maxLevel) {
        // Get material, which might be different for each increment
        String materialPath = "enchant-detail.upgrade-options.materials." + increment;
        Material material = Material.valueOf(
                plugin.getGuiConfig().getString(materialPath, defaultMaterial));

        // Determine if we use shards or runes based on the enchant's currency type
        boolean useShards = enchant.getCurrencyType() == CustomEnchant.CurrencyType.SHARDS;

        // Calculate cost for this increment using the determined currency
        final long totalCost = calculateTotalCost(currentLevel, increment, useShards);

        String shardsConfigVar = Utils.colorize(plugin.getConfig().getString("currencies.shards.color"));
        String runesConfigVar = Utils.colorize(plugin.getConfig().getString("currencies.runes.color"));

        // Get currency name for display
        String currencyName = useShards ? "Shards" : "Runes";
        String currencyColor = useShards ? shardsConfigVar : runesConfigVar;

        // Create the lore
        List<String> loreConfig = plugin.getGuiConfig().getStringList("enchant-detail.upgrade-options.lore");
        List<String> lore = new ArrayList<>();

        // Only add relevant lines that don't contain left-click/right-click distinctions
        boolean addedCurrencyLine = false;
        for (String line : loreConfig) {
            // Skip lines with left-click/right-click references
            if (line.contains("Left-Click") || line.contains("Right-Click")) {
                continue;
            }

            // Add our unified currency line if we encounter a cost placeholder
            if ((line.contains("{shard_cost}") || line.contains("{rune_cost}")) && !addedCurrencyLine) {
                lore.add(ChatColor.translateAlternateColorCodes('&', "&aClick &7to upgrade with " + currencyColor + currencyName));
                lore.add(ChatColor.translateAlternateColorCodes('&', "&7Cost: " + currencyColor + formatter.format(totalCost) + " " + currencyName));
                addedCurrencyLine = true;
                continue;
            }

            // Skip lines specific to the other currency
            if ((useShards && line.contains("{rune_cost}")) ||
                    (!useShards && line.contains("{shard_cost}"))) {
                continue;
            }

            // Replace placeholders
            line = line.replace("{increment}", String.valueOf(increment))
                    .replace("{cost}", formatter.format(totalCost))
                    .replace("{currency}", currencyName)
                    .replace("{currency_color}", currencyColor);

            // For backwards compatibility
            if (useShards) {
                line = line.replace("{shard_cost}", formatter.format(totalCost));
            } else {
                line = line.replace("{rune_cost}", formatter.format(totalCost));
            }

            line = line.replace("{new_level}", String.valueOf(currentLevel + increment))
                    .replace("{max_level}", String.valueOf(maxLevel));

            lore.add(ChatColor.translateAlternateColorCodes('&', line));
        }

        // If we didn't add a currency line yet, add it at the end
        if (!addedCurrencyLine) {
            lore.add(ChatColor.translateAlternateColorCodes('&', "&aClick &7to upgrade with " + currencyColor + currencyName));
            lore.add(ChatColor.translateAlternateColorCodes('&', "&7Cost: " + currencyColor + formatter.format(totalCost) + " " + currencyName));
        }

        // Create name with the increment
        String name = ChatColor.translateAlternateColorCodes('&',
                plugin.getGuiConfig().getString("enchant-detail.upgrade-options.name", "&a+{increment} Levels")
                        .replace("{increment}", String.valueOf(increment))
                        .replace("{currency}", currencyName));

        ItemStack upgradeItem = createItem(material, name, lore);

        // Add click handler with final variables
        final int finalIncrement = increment;
        final long finalCost = totalCost;

        // Now only handle normal click (no right-click distinction)
        setItem(slot, upgradeItem, event -> {
            handleIncrementalUpgrade(finalIncrement, finalCost, useShards);
        });
    }

    /**
     * Update the calculateTotalCost method to only use the appropriate currency
     */
    private long calculateTotalCost(int currentLevel, int levels, boolean useShards) {
        long totalCost = 0;

        // Base costs from GUI config
        ConfigurationSection costsSection = plugin.getGuiConfig()
                .getConfigurationSection("tool-gui.enchant-costs." + enchantId);

        long baseCost;
        if (costsSection != null) {
            baseCost = useShards ?
                    costsSection.getLong("shards", 1000) :
                    costsSection.getLong("runes", 100);
        } else {
            // Use default costs if not specifically configured
            costsSection = plugin.getGuiConfig()
                    .getConfigurationSection("tool-gui.enchant-costs.default");

            if (costsSection != null) {
                baseCost = useShards ?
                        costsSection.getLong("shards", 1000) :
                        costsSection.getLong("runes", 100);
            } else {
                // Hardcoded defaults as last resort
                baseCost = useShards ? 1000 : 100;
            }
        }

        // Calculate cost for each level
        double costMultiplier = plugin.getGuiConfig().getDouble("tool-gui.cost-multiplier", 1.5);
        for (int i = 0; i < levels; i++) {
            int level = currentLevel + i + 1;
            double multiplier = Math.pow(costMultiplier, level - 1);
            totalCost += (long) (baseCost * multiplier);
        }

        return totalCost;
    }

    /**
     * Add tool display at the bottom
     */
    private void addToolDisplay() {
        int slot = plugin.getGuiConfig().getInt("enchant-detail.tool-display.slot", 31);

        ItemStack displayItem = toolItem.clone();
        setItem(slot, displayItem);
    }

    /**
     * Add back button
     */
    private void addBackButton() {
        int slot = plugin.getGuiConfig().getInt("enchant-detail.back-button.slot", 27);
        Material material = Material.valueOf(
                plugin.getGuiConfig().getString("enchant-detail.back-button.material", "ARROW"));

        String name = ChatColor.translateAlternateColorCodes('&',
                plugin.getGuiConfig().getString("enchant-detail.back-button.name", "&cBack"));

        List<String> lore = plugin.getGuiConfig().getStringList("enchant-detail.back-button.lore")
                .stream()
                .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                .toList();

        ItemStack backItem = createItem(material, name, lore);

        setItem(slot, backItem, event -> {
            // Play navigation sound
            playSound("navigation");

            // Switch back to parent menu (preserves cursor)
            switchTo(parentMenu);
        });
    }

    /**
     * Add message toggle button
     */
    private void addMessageToggle() {
        int slot = plugin.getGuiConfig().getInt("enchant-detail.message-toggle.slot", 19);
        Material enabledMaterial = Material.valueOf(
                plugin.getGuiConfig().getString("enchant-detail.message-toggle.enabled-material", "OAK_SIGN"));
        Material disabledMaterial = Material.valueOf(
                plugin.getGuiConfig().getString("enchant-detail.message-toggle.disabled-material", "OAK_SIGN"));

        String enabledName = ChatColor.translateAlternateColorCodes('&',
                plugin.getGuiConfig().getString("enchant-detail.message-toggle.enabled-name", "&aMessages: &eEnabled"));

        String disabledName = ChatColor.translateAlternateColorCodes('&',
                plugin.getGuiConfig().getString("enchant-detail.message-toggle.disabled-name", "&aMessages: &cDisabled"));

        List<String> enabledLore = plugin.getGuiConfig().getStringList("enchant-detail.message-toggle.enabled-lore")
                .stream()
                .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                .toList();

        List<String> disabledLore = plugin.getGuiConfig().getStringList("enchant-detail.message-toggle.disabled-lore")
                .stream()
                .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                .toList();

        // Create the appropriate item based on current state
        ItemStack toggleItem = createItem(
                messagesEnabled ? enabledMaterial : disabledMaterial,
                messagesEnabled ? enabledName : disabledName,
                messagesEnabled ? enabledLore : disabledLore
        );

        setItem(slot, toggleItem, event -> {
            // Toggle message preference
            messagesEnabled = !messagesEnabled;

            // Update in manager
            if (messagesEnabled) {
                plugin.getToolManager().enableMessages(player.getUniqueId(), enchantId);
            } else {
                plugin.getToolManager().disableMessages(player.getUniqueId(), enchantId);
            }

            // Play sound
            playSound("navigation");

            // Rebuild menu
            build();
        });
    }

    /**
     * Add enchant toggle button
     */
    private void addEnchantToggle() {
        int slot = plugin.getGuiConfig().getInt("enchant-detail.enchant-toggle.slot", 25);
        Material enabledMaterial = Material.valueOf(
                plugin.getGuiConfig().getString("enchant-detail.enchant-toggle.enabled-material", "LIME_DYE"));
        Material disabledMaterial = Material.valueOf(
                plugin.getGuiConfig().getString("enchant-detail.enchant-toggle.disabled-material", "GRAY_DYE"));

        String enabledName = ChatColor.translateAlternateColorCodes('&',
                plugin.getGuiConfig().getString("enchant-detail.enchant-toggle.enabled-name", "&aEnchant: &eEnabled"));

        String disabledName = ChatColor.translateAlternateColorCodes('&',
                plugin.getGuiConfig().getString("enchant-detail.enchant-toggle.disabled-name", "&aEnchant: &cDisabled"));

        List<String> enabledLore = plugin.getGuiConfig().getStringList("enchant-detail.enchant-toggle.enabled-lore")
                .stream()
                .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                .toList();

        List<String> disabledLore = plugin.getGuiConfig().getStringList("enchant-detail.enchant-toggle.disabled-lore")
                .stream()
                .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                .toList();

        // Create the appropriate item based on current state
        ItemStack toggleItem = createItem(
                enchantEnabled ? enabledMaterial : disabledMaterial,
                enchantEnabled ? enabledName : disabledName,
                enchantEnabled ? enabledLore : disabledLore
        );

        setItem(slot, toggleItem, event -> {
            // Toggle enchant preference
            enchantEnabled = !enchantEnabled;

            // Update in manager
            if (enchantEnabled) {
                plugin.getToolManager().enableEnchant(player.getUniqueId(), enchantId, toolItem);
            } else {
                plugin.getToolManager().disableEnchant(player.getUniqueId(), enchantId, toolItem);
            }

            // Play sound
            playSound("navigation");

            // Rebuild menu
            build();
        });
    }

    /**
     * Add max upgrade button
     */
    private void addMaxUpgradeButton() {
        int slot = plugin.getGuiConfig().getInt("enchant-detail.max-upgrade.slot", 22);
        Material material = Material.valueOf(
                plugin.getGuiConfig().getString("enchant-detail.max-upgrade.material", "HOPPER"));

        int currentLevel = GensTool.getEnchantmentLevel(toolItem, enchantId);
        int maxLevel = enchant != null ? enchant.getMaxLevel() : 1;

        // Check if already at max level
        if (currentLevel >= maxLevel) {
            return;
        }

        String name = ChatColor.translateAlternateColorCodes('&',
                plugin.getGuiConfig().getString("enchant-detail.max-upgrade.name", "&aMax Upgrade"));

        List<String> loreConfig = plugin.getGuiConfig().getStringList("enchant-detail.max-upgrade.lore");
        List<String> lore = new ArrayList<>();

        for (String line : loreConfig) {
            line = line.replace("{current_level}", String.valueOf(currentLevel))
                    .replace("{max_level}", String.valueOf(maxLevel));

            lore.add(ChatColor.translateAlternateColorCodes('&', line));
        }

        ItemStack maxItem = createItem(material, name, lore);

        setItem(slot, maxItem, event -> {
            // Handle left/right click for shards/runes
            boolean useShards = !event.isRightClick();
            handleMaxUpgrade(useShards);
        });
    }

    /**
     * Handle incremental upgrade
     */
    private void handleIncrementalUpgrade(int increment, long cost, boolean useShards) {
        if (enchant == null) return;

        int currentLevel = GensTool.getEnchantmentLevel(toolItem, enchantId);
        int maxLevel = enchant.getMaxLevel();

        // String to use in messages
        String currencyName = useShards ? "Shards" : "Runes";

        // Already at max level
        if (currentLevel >= maxLevel) {
            playSound("upgrade-failed");
            sendMessage("max-level", Map.of("{enchant_name}", enchant.getDisplayName()));
            return;
        }

        // Ensure we don't go over max level
        if (currentLevel + increment > maxLevel) {
            increment = maxLevel - currentLevel;
        }

        // Check balance
        GensCoreAPI api = plugin.getGensCoreAPI().getAPI();
        long balance = useShards ?
                api.getShardsBalance(player.getUniqueId()) :
                api.getRunesBalance(player.getUniqueId());

        if (balance < cost) {
            playSound("upgrade-failed");
            sendMessage("insufficient-funds", Map.of(
                    "{currency}", currencyName,
                    "{cost}", formatter.format(cost),
                    "{balance}", formatter.format(balance)
            ));
            return;
        }

        // Deduct currency
        boolean success = useShards ?
                api.removeShards(player.getUniqueId(), cost) :
                api.removeRunes(player.getUniqueId(), cost);

        if (!success) {
            sendMessage("payment-failed", null);
            return;
        }

        // Apply upgrade
        boolean upgradeSuccess = GensTool.addEnchantment(toolItem, enchantId, currentLevel + increment);

        if (!upgradeSuccess) {
            // Refund if failed
            if (useShards) {
                api.addShards(player.getUniqueId(), cost);
            } else {
                api.addRunes(player.getUniqueId(), cost);
            }
            sendMessage("upgrade-failed", null);
            return;
        }

        // Sync player's hand if that's where the tool is
        syncPlayerHand();

        // Show success message
        playSound("upgrade-success");
        sendMessage("upgrade-success", Map.of(
                "{enchant_name}", enchant.getDisplayName(),
                "{level}", GensTool.formatEnchantmentLevel(currentLevel + increment),
                "{currency}", currencyName
        ));

        plugin.getToolPersistenceManager().handleToolUpdate(player, toolItem);

        // Update the parent menu
        if (parentMenu instanceof ToolEnchantMenu) {
            ((ToolEnchantMenu) parentMenu).refreshEnchantmentData();
        }

        // Rebuild the menu to reflect the updated enchantment level
        Bukkit.getScheduler().runTask(plugin, this::build);
    }

    /**
     * Handle max upgrade with available balance
     */
    private void handleMaxUpgrade(boolean useShards) {
        if (enchant == null) return;

        int currentLevel = GensTool.getEnchantmentLevel(toolItem, enchantId);
        int maxLevel = enchant.getMaxLevel();

        // Already at max level
        if (currentLevel >= maxLevel) {
            playSound("upgrade-failed");
            sendMessage("max-level", Map.of("{enchant_name}", enchant.getDisplayName()));
            return;
        }

        // Get player's balance
        GensCoreAPI api = plugin.getGensCoreAPI().getAPI();
        long balance = useShards ?
                api.getShardsBalance(player.getUniqueId()) :
                api.getRunesBalance(player.getUniqueId());

        // Calculate how many levels we can afford
        int affordableLevels = 0;
        long totalCost = 0;

        for (int level = currentLevel + 1; level <= maxLevel; level++) {
            long levelCost = calculateCost(enchantId, level - 1, useShards);

            if (totalCost + levelCost <= balance) {
                totalCost += levelCost;
                affordableLevels++;
            } else {
                break;
            }
        }

        // Can't afford any levels
        if (affordableLevels == 0) {
            playSound("upgrade-failed");
            String currency = useShards ? "Shards" : "Runes";
            long nextLevelCost = calculateCost(enchantId, currentLevel, useShards);
            sendMessage("insufficient-funds", Map.of(
                    "{currency}", currency,
                    "{cost}", formatter.format(nextLevelCost),     // Format with NumberFormatter
                    "{balance}", formatter.format(balance)         // Format with NumberFormatter
            ));
            return;
        }

        // Deduct currency
        boolean success = useShards ?
                api.removeShards(player.getUniqueId(), totalCost) :
                api.removeRunes(player.getUniqueId(), totalCost);

        if (!success) {
            sendMessage("payment-failed", null);
            return;
        }

        // Apply upgrade
        boolean upgradeSuccess = GensTool.addEnchantment(toolItem, enchantId, currentLevel + affordableLevels);

        if (!upgradeSuccess) {
            // Refund if failed
            if (useShards) {
                api.addShards(player.getUniqueId(), totalCost);
            } else {
                api.addRunes(player.getUniqueId(), totalCost);
            }
            sendMessage("upgrade-failed", null);
            return;
        }

        // Sync player's hand if that's where the tool is
        syncPlayerHand();

        // Show success message
        playSound("upgrade-success");
        sendMessage("upgrade-success", Map.of(
                "{enchant_name}", enchant.getDisplayName(),
                "{level}", GensTool.formatEnchantmentLevel(currentLevel + affordableLevels)
        ));

        plugin.getToolPersistenceManager().handleToolUpdate(player, toolItem);

        // Rebuild the menu to reflect the updated enchantment level
        Bukkit.getScheduler().runTask(plugin, this::build);
    }

    /**
     * Calculate the cost for upgrading an enchantment
     */
    private long calculateCost(String enchantId, int currentLevel, boolean isShards) {
        // This should match the calculation in ToolEnchantMenu
        String baseCostPath = isShards ?
                "tool-gui.enchant-costs." + enchantId + ".shards" :
                "tool-gui.enchant-costs." + enchantId + ".runes";

        String defaultPath = isShards ?
                "tool-gui.enchant-costs.default.shards" :
                "tool-gui.enchant-costs.default.runes";

        long baseCost = plugin.getGuiConfig().getLong(baseCostPath,
                plugin.getGuiConfig().getLong(defaultPath, isShards ? 1000L : 100L));

        double costMultiplier = plugin.getGuiConfig().getDouble("tool-gui.cost-multiplier", 1.5);

        // First level costs base amount
        if (currentLevel == 0) return baseCost;

        // Higher levels cost more
        return (long) (baseCost * Math.pow(costMultiplier, currentLevel));
    }

    /**
     * Play a sound to the player
     */
    private void playSound(String key) {
        Sound sound = sounds.get(key);
        if (sound != null) {
            player.playSound(player.getLocation(), sound, 0.5f, 1.0f);
        }
    }

    /**
     * Send a message to the player
     */
    private void sendMessage(String key, Map<String, String> placeholders) {
        String message = messages.get(key);
        if (message == null || message.isEmpty()) return;

        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                message = message.replace(entry.getKey(), entry.getValue());
            }
        }

        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    /**
     * Sync the player's hand if they're holding the tool
     */
    private void syncPlayerHand() {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (GensTool.isGensTool(mainHand) && GensTool.getToolId(mainHand).equals(GensTool.getToolId(toolItem))) {
            player.getInventory().setItemInMainHand(toolItem);
        }
    }
}