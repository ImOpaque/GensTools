package me.opaque.genstools.manager;

import me.opaque.genstools.GensTools;
import me.opaque.genstools.enchants.CustomEnchant;
import me.opaque.genstools.enchants.EnchantmentCube;
import me.opaque.genstools.tools.GensTool;
import me.opaque.genstools.utils.Utils;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class EnchantmentCubeManager {
    private final GensTools plugin;
    private File configFile;
    private FileConfiguration config;

    private final Map<Integer, CubeTier> cubeTiers = new HashMap<>();
    private final Map<String, String> messages = new HashMap<>();

    public EnchantmentCubeManager(GensTools plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    /**
     * Load the enchantment cubes configuration
     */
    public void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "enchantment_cubes.yml");

        if (!configFile.exists()) {
            try {
                configFile.createNewFile();
                // Save default config
                FileConfiguration defaultConfig = new YamlConfiguration();
                setupDefaultConfig(defaultConfig);
                defaultConfig.save(configFile);
            } catch (IOException e) {
                Utils.logError("Failed to create enchantment_cubes.yml: " + e.getMessage());
                return;
            }
        }

        config = YamlConfiguration.loadConfiguration(configFile);
        loadCubeTiers();
        loadMessages();

        Utils.logInfo("Loaded " + cubeTiers.size() + " enchantment cube tiers");
    }

    /**
     * Setup the default config
     */
    private void setupDefaultConfig(FileConfiguration config) {
        // Tier 1
        config.set("tiers.1.name", "&aBasic Enchantment Cube");
        config.set("tiers.1.material", "EMERALD");
        config.set("tiers.1.model-data", 1001);
        config.set("tiers.1.lore", List.of(
                "&7Apply to any GensTool to add",
                "&7the &f%enchantment% &7enchantment.",
                "&7Success Rate: &a%success_rate%&7%",
                "&7Boost: &a+%boost%&7%"
        ));
        config.set("tiers.1.default-boosts.efficiency", 5.0);
        config.set("tiers.1.default-boosts.unbreaking", 5.0);
        config.set("tiers.1.default-boosts.fortune", 2.5);

        // Tier 2
        config.set("tiers.2.name", "&bAdvanced Enchantment Cube");
        config.set("tiers.2.material", "DIAMOND");
        config.set("tiers.2.model-data", 1002);
        config.set("tiers.2.lore", List.of(
                "&7Apply to any GensTool to add",
                "&7the &f%enchantment% &7enchantment.",
                "&7Success Rate: &b%success_rate%&7%",
                "&7Boost: &b+%boost%&7%"
        ));
        config.set("tiers.2.default-boosts.efficiency", 10.0);
        config.set("tiers.2.default-boosts.unbreaking", 10.0);
        config.set("tiers.2.default-boosts.fortune", 5.0);

        // Messages
        config.set("messages.cube-applied", "&aSuccess! The %cube_name% &ahas applied %boost%% to your %enchantment%&a enchantment!");
        config.set("messages.cube-failed", "&cThe %cube_name% &cshattered upon application! No enchantment was applied.");
        config.set("messages.invalid-tool", "&cThis cube can only be applied to GensTool items!");
        config.set("messages.incompatible-enchant", "&cThis cube cannot be applied to this tool type!");
        config.set("messages.inventory-full", "&cYour inventory is full! Cube has been dropped on the ground.");
    }

    /**
     * Load the cube tiers from config
     */
    private void loadCubeTiers() {
        cubeTiers.clear();

        ConfigurationSection tiersSection = config.getConfigurationSection("tiers");
        if (tiersSection == null) {
            Utils.logWarning("No enchantment cube tiers defined in config!");
            return;
        }

        for (String key : tiersSection.getKeys(false)) {
            ConfigurationSection tierSection = tiersSection.getConfigurationSection(key);
            if (tierSection == null) continue;

            try {
                int tierId = Integer.parseInt(key);
                String name = tierSection.getString("name", "&7Enchantment Cube");
                String materialStr = tierSection.getString("material", "EMERALD");
                Material material = Material.getMaterial(materialStr.toUpperCase());
                if (material == null) {
                    material = Material.EMERALD;
                    Utils.logWarning("Invalid material for cube tier " + tierId + ": " + materialStr);
                }

                int customModelData = tierSection.getInt("model-data", 0);
                List<String> lore = tierSection.getStringList("lore");
                Map<String, Double> defaultBoosts = new HashMap<>();

                ConfigurationSection boostsSection = tierSection.getConfigurationSection("default-boosts");
                if (boostsSection != null) {
                    for (String enchantId : boostsSection.getKeys(false)) {
                        defaultBoosts.put(enchantId, boostsSection.getDouble(enchantId));
                    }
                }

                CubeTier tier = new CubeTier(tierId, name, material, customModelData, lore, defaultBoosts);
                cubeTiers.put(tierId, tier);

            } catch (NumberFormatException e) {
                Utils.logWarning("Invalid tier ID: " + key);
            }
        }
    }

    /**
     * Load the messages from config
     */
    private void loadMessages() {
        messages.clear();

        ConfigurationSection messagesSection = config.getConfigurationSection("messages");
        if (messagesSection == null) {
            Utils.logWarning("No messages defined in enchantment cubes config!");
            return;
        }

        for (String key : messagesSection.getKeys(false)) {
            messages.put(key, messagesSection.getString(key, ""));
        }
    }

    /**
     * Create an enchantment cube item
     */
    public ItemStack createCube(int tier, String enchantId, int successRate, double boost, int amount) {
        CubeTier cubeTier = cubeTiers.get(tier);
        if (cubeTier == null) {
            Utils.logWarning("Attempted to create a cube with unknown tier: " + tier);
            return null;
        }

        CustomEnchant enchant = plugin.getToolManager().getEnchantById(enchantId);
        if (enchant == null) {
            Utils.logWarning("Attempted to create a cube with unknown enchantment: " + enchantId);
            return null;
        }

        // If boost is not specified, use the default boost for this enchant in this tier
        if (boost <= 0) {
            boost = cubeTier.getDefaultBoost(enchantId);
        }

        String cubeId = "cube_" + tier + "_" + enchantId.toLowerCase();
        EnchantmentCube cube = new EnchantmentCube(
                cubeId,
                cubeTier.getName(),
                cubeTier.getMaterial(),
                cubeTier.getCustomModelData(),
                tier,
                successRate,
                enchantId,
                boost,
                cubeTier.getLore()
        );

        return cube.createItemStack(amount);
    }

    /**
     * Give a cube to a player
     */
    public boolean giveCube(Player player, int tier, String enchantId, int successRate, double boost, int amount) {
        ItemStack cubeItem = createCube(tier, enchantId, successRate, boost, amount);
        if (cubeItem == null) {
            return false;
        }

        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(cubeItem);

        // Drop any items that didn't fit in the inventory
        if (!leftover.isEmpty()) {
            for (ItemStack item : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
            player.sendMessage(Utils.colorize(getMessage("inventory-full")));
        }

        return true;
    }

    /**
     * Apply a cube to a tool
     *
     * @param player The player applying the cube
     * @param toolItem The tool to apply the cube to
     * @param cubeItem The cube item being applied
     * @return true if successfully applied, false otherwise
     */
    public boolean applyCube(Player player, ItemStack toolItem, ItemStack cubeItem) {
        // Check if items are valid
        if (!GensTool.isGensTool(toolItem) || !EnchantmentCube.isEnchantmentCube(cubeItem)) {
            player.sendMessage(plugin.getMessageManager().getMessage("cubes.invalid-items"));
            return false;
        }

        // Extract enchantment ID and boost percentage from the cube item
        String enchantId = EnchantmentCube.getEnchantmentId(cubeItem);
        int boostPercentage = EnchantmentCube.getBoostPercentage(cubeItem);

        if (enchantId == null || boostPercentage <= 0) {
            player.sendMessage(plugin.getMessageManager().getMessage("cubes.invalid-cube"));
            return false;
        }

        CustomEnchant enchant = plugin.getToolManager().getEnchantById(enchantId);
        if (enchant == null) {
            player.sendMessage(plugin.getMessageManager().getMessage("cubes.invalid-enchant"));
            return false;
        }

        // Check if the tool has the enchantment
        Map<String, Integer> enchantments;
        try {
            enchantments = GensTool.getEnchantments(toolItem);
        } catch (Exception e) {
            plugin.getLogger().warning("Error getting enchantments when applying cube: " + e.getMessage());
            player.sendMessage(plugin.getMessageManager().getMessage("cubes.error"));
            return false;
        }

        if (!enchantments.containsKey(enchantId)) {
            player.sendMessage(plugin.getMessageManager().getMessage("cubes.missing-enchant")
                    .replace("%enchant%", enchant.getDisplayName()));
            return false;
        }

        // Get tool metadata
        ItemMeta meta = toolItem.getItemMeta();
        if (meta == null) {
            player.sendMessage(plugin.getMessageManager().getMessage("cubes.error"));
            return false;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();

        // Calculate boost as a decimal (e.g., 20% = 0.2)
        double boostDecimal = boostPercentage / 100.0;

        // Check if a multiplier already exists for this enchantment
        NamespacedKey multiplierKey = new NamespacedKey(plugin, "enchant_multiplier_" + enchantId);
        boolean hasExistingMultiplier = container.has(multiplierKey, PersistentDataType.DOUBLE);
        double existingMultiplier = 0.0;

        if (hasExistingMultiplier) {
            existingMultiplier = container.get(multiplierKey, PersistentDataType.DOUBLE);

            // Only allow if the new boost is higher than the existing one
            if (boostDecimal <= existingMultiplier) {
                player.sendMessage(plugin.getMessageManager().getMessage("cubes.already-boosted")
                        .replace("%enchant%", enchant.getDisplayName())
                        .replace("%current%", String.format("%.0f", existingMultiplier * 100))
                        .replace("%new%", String.valueOf(boostPercentage)));
                return false;
            }
        }

        // Also check the consolidated cubes string for consistency
        NamespacedKey cubesKey = new NamespacedKey(plugin, "applied_cubes");
        boolean hasCubeInConsolidatedString = false;
        Map<String, Integer> appliedCubes = new HashMap<>();

        if (container.has(cubesKey, PersistentDataType.STRING)) {
            String cubesData = container.get(cubesKey, PersistentDataType.STRING);
            if (cubesData != null && !cubesData.isEmpty()) {
                // Parse existing cubes
                for (String cube : cubesData.split(",")) {
                    String[] parts = cube.split(":");
                    if (parts.length == 2) {
                        String cubeEnchantId = parts[0];
                        int cubeBoost;
                        try {
                            cubeBoost = Integer.parseInt(parts[1]);
                        } catch (NumberFormatException e) {
                            cubeBoost = 0;
                        }

                        appliedCubes.put(cubeEnchantId, cubeBoost);
                        if (cubeEnchantId.equals(enchantId)) {
                            hasCubeInConsolidatedString = true;
                        }
                    }
                }
            }
        }

        // If checks are inconsistent, log a warning and continue with the higher value
        if (hasExistingMultiplier != hasCubeInConsolidatedString) {
            plugin.getLogger().warning("Inconsistent cube data on tool: multiplier exists=" +
                    hasExistingMultiplier + ", in string=" + hasCubeInConsolidatedString);
        }

        // Get the success rate from the cube
        // Create an EnchantmentCube object from the item to access non-static methods
        EnchantmentCube cube = EnchantmentCube.fromItemStack(cubeItem);
        if (cube != null) {
            int successRate = cube.getSuccessRate();
            if (successRate < 100) {
                // Roll for success based on success rate
                boolean succeeded = ThreadLocalRandom.current().nextInt(100) < successRate;
                if (!succeeded) {
                    // Show failure message
                    player.sendMessage(plugin.getMessageManager().getMessage("cubes.failed")
                            .replace("%cube_name%", getCubeName(cubeItem)));

                    // Play failure sound
                    if (plugin.getConfig().getBoolean("settings.play-sounds", true)) {
                        String soundName = plugin.getConfig().getString("settings.failure-sound", "ENTITY_ITEM_BREAK");
                        try {
                            Sound sound = Sound.valueOf(soundName);
                            player.playSound(player.getLocation(), sound, 1.0F, 0.5F);
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Invalid sound specified in config: " + soundName);
                        }
                    }

                    // Return true if the cube should be consumed on failure, false otherwise
                    return plugin.getConfig().getBoolean("settings.consume-cube-on-failure", true);
                }
            }
        }

        // Apply the new multiplier
        container.set(multiplierKey, PersistentDataType.DOUBLE, boostDecimal);

        // Update the consolidated cubes string
        appliedCubes.put(enchantId, boostPercentage);

        // Rebuild the consolidated string
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Integer> entry : appliedCubes.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            sb.append(entry.getKey()).append(":").append(entry.getValue());
            first = false;
        }

        // Store the updated cubes string
        container.set(cubesKey, PersistentDataType.STRING, sb.toString());
        toolItem.setItemMeta(meta);

        // Update lore to show applied cube
        plugin.getLoreManager().updateToolLore(toolItem);

        // Send success message
        player.sendMessage(plugin.getMessageManager().getMessage("cubes.success")
                .replace("%cube_name%", getCubeName(cubeItem))
                .replace("%boost%", String.valueOf(boostPercentage))
                .replace("%enchant%", enchant.getDisplayName()));

        // Play success sound
        if (plugin.getConfig().getBoolean("settings.play-sounds", true)) {
            String soundName = plugin.getConfig().getString("settings.success-sound", "ENTITY_PLAYER_LEVELUP");
            try {
                Sound sound = Sound.valueOf(soundName);
                player.playSound(player.getLocation(), sound, 1.0F, 1.0F);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid sound specified in config: " + soundName);
            }
        }

        // Make sure to sync player's hand if holding the tool
        syncPlayerHandTool(player, toolItem);

        // Register the tool update with the persistence manager
        plugin.getToolPersistenceManager().handleToolUpdate(player, toolItem);

        return true;
    }

    /**
     * Sync the player's hand if holding this tool
     *
     * @param player The player
     * @param toolItem The tool item
     */
    private void syncPlayerHandTool(Player player, ItemStack toolItem) {
        // Get the tool's unique ID
        String toolId = GensTool.getToolId(toolItem);
        if (toolId == null) return;

        // Check main hand
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (GensTool.isGensTool(mainHand) && toolId.equals(GensTool.getToolId(mainHand))) {
            player.getInventory().setItemInMainHand(toolItem);
        }

        // Check off hand
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (GensTool.isGensTool(offHand) && toolId.equals(GensTool.getToolId(offHand))) {
            player.getInventory().setItemInOffHand(toolItem);
        }
    }

    /**
     * Get the display name of a cube item
     */
    private String getCubeName(ItemStack cube) {
        if (cube == null || !cube.hasItemMeta() || !cube.getItemMeta().hasDisplayName()) {
            return "Cube";
        }
        return cube.getItemMeta().getDisplayName();
    }

    /**
     * Get a message from the MessageManager
     */
    private String getMessage(String key) {
        return plugin.getMessageManager().getMessage("cubes." + key);
    }


    /**
     * Get the plugin's config
     */
    private FileConfiguration getConfig() {
        return plugin.getConfig();
    }

    /**
     * Update the lore to include cube information
     */
    private void updateCubesLore(ItemMeta meta, String cubesData) {
        // Get the existing lore or create a new one
        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
        if (lore == null) lore = new ArrayList<>();

        // Remove existing cubes section if it exists
        int cubesSectionStart = -1;
        int cubesSectionEnd = -1;

        for (int i = 0; i < lore.size(); i++) {
            String line = ChatColor.stripColor(lore.get(i));
            if (line.contains("Applied Cubes:")) {
                cubesSectionStart = i;
            } else if (cubesSectionStart != -1 && line.trim().isEmpty()) {
                cubesSectionEnd = i;
                break;
            }
        }

        if (cubesSectionStart != -1) {
            if (cubesSectionEnd == -1) cubesSectionEnd = lore.size();
            lore.subList(cubesSectionStart, cubesSectionEnd).clear();
        }

        // Add the cubes section
        if (!cubesData.isEmpty()) {
            // Find where to insert (usually after enchantments but before stats)
            int insertIndex = -1;

            // Look for stats section
            for (int i = 0; i < lore.size(); i++) {
                String line = ChatColor.stripColor(lore.get(i));
                if (line.contains("Stats:")) {
                    insertIndex = i;
                    break;
                }
            }

            // If stats section not found, look for a blank line
            if (insertIndex == -1) {
                for (int i = 0; i < lore.size(); i++) {
                    if (lore.get(i).trim().isEmpty()) {
                        insertIndex = i;
                        break;
                    }
                }
            }

            // If still not found, add at the end
            if (insertIndex == -1) {
                insertIndex = lore.size();
            }

            // Insert the cubes section
            List<String> cubesSection = new ArrayList<>();
            cubesSection.add(Utils.colorize("&8❖ &7Applied Cubes:"));

            String[] cubes = cubesData.split(",");
            for (String cube : cubes) {
                String[] parts = cube.split(":");
                if (parts.length == 2) {
                    String enchantId = parts[0];
                    String boostText = parts[1];

                    CustomEnchant enchant = plugin.getToolManager().getEnchantById(enchantId);
                    String enchantName = enchant != null ? enchant.getDisplayName() : enchantId;

                    cubesSection.add(Utils.colorize(" &8• &7" + enchantName + " &a+" + boostText));
                }
            }

            cubesSection.add(""); // Empty line after section

            // Insert the section
            lore.addAll(insertIndex, cubesSection);
        }

        // Set the updated lore
        meta.setLore(lore);
    }

    /**
     * Helper method to apply enchant to tool without relying on problematic methods
     */
    private boolean applyEnchantToTool(Player player, ItemStack toolItem, String enchantId, double boost, int successRate) {
        // Roll for success based on success rate
        boolean success = ThreadLocalRandom.current().nextInt(100) < successRate;

        if (!success) {
            // Show failure effects
            Location loc = player.getLocation().add(0, 1, 0);
            player.getWorld().playSound(loc, Sound.ENTITY_ITEM_BREAK, 1.0f, 0.5f);
            Utils.createRippleEffect(
                    loc,
                    Color.fromRGB(255, 0, 0),  // Red for failure
                    2.0,
                    20,
                    20,
                    plugin
            );
            return false;
        }

        // Get current enchants
        Map<String, Integer> enchants = GensTool.getEnchantments(toolItem);

        // Add or increase the enchantment
        int currentLevel = enchants.getOrDefault(enchantId, 0);
        int newLevel = currentLevel + (int)boost;

        // Apply the enchantment using plugin's tool manager
        boolean result = plugin.getToolManager().addEnchantToTool(toolItem, enchantId, newLevel);

        // Show success effects
        Location loc = player.getLocation().add(0, 1, 0);
        player.getWorld().playSound(loc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
        Utils.createRippleEffect(
                loc,
                Color.fromRGB(0, 255, 0),  // Green for success
                2.0,
                20,
                20,
                plugin
        );

        return result;
    }

    /**
     * Get all available tiers
     */
    public Set<Integer> getAvailableTiers() {
        return cubeTiers.keySet();
    }

    /**
     * Get all available enchants for a specific tier
     */
    public List<String> getAvailableEnchantsForTier(int tier) {
        CubeTier cubeTier = cubeTiers.get(tier);
        if (cubeTier == null) {
            return new ArrayList<>();
        }

        return new ArrayList<>(cubeTier.getDefaultBoosts().keySet());
    }

    /**
     * Helper class to represent a cube tier
     */
    private static class CubeTier {
        private final int id;
        private final String name;
        private final Material material;
        private final int customModelData;
        private final List<String> lore;
        private final Map<String, Double> defaultBoosts;

        public CubeTier(int id, String name, Material material, int customModelData,
                        List<String> lore, Map<String, Double> defaultBoosts) {
            this.id = id;
            this.name = name;
            this.material = material;
            this.customModelData = customModelData;
            this.lore = lore;
            this.defaultBoosts = defaultBoosts;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public Material getMaterial() {
            return material;
        }

        public int getCustomModelData() {
            return customModelData;
        }

        public List<String> getLore() {
            return lore;
        }

        public Map<String, Double> getDefaultBoosts() {
            return defaultBoosts;
        }

        public double getDefaultBoost(String enchantId) {
            return defaultBoosts.getOrDefault(enchantId, 1.0);
        }
    }
}