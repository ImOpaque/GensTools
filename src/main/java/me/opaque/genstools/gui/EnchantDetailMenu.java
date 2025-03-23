package me.opaque.genstools.gui;

import me.opaque.genscore.hooks.GensCoreAPI;
import me.opaque.genstools.GensTools;
import me.opaque.genstools.enchants.CustomEnchant;
import me.opaque.genstools.tools.GensTool;
import me.opaque.genstools.utils.NumberFormatter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
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

        String name = ChatColor.translateAlternateColorCodes('&',
                plugin.getGuiConfig().getString("enchant-detail.enchant-info.name", "&6{enchant_name} {level}")
                        .replace("{enchant_name}", enchant.getDisplayName())
                        .replace("{level}", currentLevel > 0 ? GensTool.formatEnchantmentLevel(currentLevel) : "")
                        .replace("{current_level}", String.valueOf(currentLevel))
                        .replace("{max_level}", String.valueOf(maxLevel)));

        List<String> loreConfig = plugin.getGuiConfig().getStringList("enchant-detail.enchant-info.lore");
        List<String> lore = new ArrayList<>();

        for (String line : loreConfig) {
            line = line.replace("{enchant_description}", enchant.getDescription())
                    .replace("{enchant_name}", enchant.getDisplayName())
                    .replace("{current_level}", String.valueOf(currentLevel))
                    .replace("{max_level}", String.valueOf(maxLevel))
                    .replace("{level}", currentLevel > 0 ? GensTool.formatEnchantmentLevel(currentLevel) : "");

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

        // Calculate cost for this increment
        final long totalShardCost = calculateTotalCost(currentLevel, increment, true);
        final long totalRuneCost = calculateTotalCost(currentLevel, increment, false);

        // Create the lore
        List<String> loreConfig = plugin.getGuiConfig().getStringList("enchant-detail.upgrade-options.lore");
        List<String> lore = new ArrayList<>();

        for (String line : loreConfig) {
            line = line.replace("{increment}", String.valueOf(increment))
                    .replace("{shard_cost}", formatter.format(totalShardCost)) // Format with NumberFormatter
                    .replace("{rune_cost}", formatter.format(totalRuneCost))   // Format with NumberFormatter
                    .replace("{new_level}", String.valueOf(currentLevel + increment))
                    .replace("{max_level}", String.valueOf(maxLevel));

            lore.add(ChatColor.translateAlternateColorCodes('&', line));
        }

        // Create name with the increment
        String name = ChatColor.translateAlternateColorCodes('&',
                plugin.getGuiConfig().getString("enchant-detail.upgrade-options.name", "&a+{increment} Levels")
                        .replace("{increment}", String.valueOf(increment)));

        ItemStack upgradeItem = createItem(material, name, lore);

        // Add click handler with final variables
        final int finalIncrement = increment; // This is redundant as increment is already final, but making it explicit
        setItem(slot, upgradeItem, event -> {
            // Handle left/right click for shards/runes
            boolean useShards = !event.isRightClick();
            handleIncrementalUpgrade(finalIncrement, useShards ? totalShardCost : totalRuneCost, useShards);
        });
    }

    /**
     * Calculate total cost for multiple level upgrades
     */
    private long calculateTotalCost(int currentLevel, int increment, boolean useShards) {
        long totalCost = 0;

        for (int level = currentLevel + 1; level <= currentLevel + increment; level++) {
            long levelCost = calculateCost(enchantId, level - 1, useShards);
            totalCost += levelCost;
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
            String currency = useShards ? "Shards" : "Runes";
            sendMessage("insufficient-funds", Map.of(
                    "{currency}", currency,
                    "{cost}", formatter.format(cost),          // Format with NumberFormatter
                    "{balance}", formatter.format(balance)     // Format with NumberFormatter
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
                "{level}", GensTool.formatEnchantmentLevel(currentLevel + increment)
        ));

        plugin.getToolPersistenceManager().handleToolUpdate(player, toolItem);

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