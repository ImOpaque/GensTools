package me.opaque.genstools.persistence;

import me.opaque.genstools.GensTools;
import me.opaque.genstools.tools.GensTool;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class ToolPersistenceManager {
    private final GensTools plugin;
    private final Storage storage;

    // Cache of loaded player tool data
    private final Map<UUID, PlayerToolData> playerToolCache = new ConcurrentHashMap<>();

    // Track modified tools that need saving
    private final Set<UUID> pendingSaves = Collections.synchronizedSet(new HashSet<>());

    // Key for storing unique tool IDs
    private final NamespacedKey KEY_UNIQUE_ID;

    // Auto-save task
    private BukkitTask autoSaveTask;

    // Settings
    private int autoSaveInterval;
    private boolean debugMode;

    public ToolPersistenceManager(GensTools plugin) {
        this.plugin = plugin;
        this.KEY_UNIQUE_ID = new NamespacedKey(plugin, "tool_unique_id");

        // Load configuration
        loadConfiguration();

        // Initialize storage system
        this.storage = new YamlStorage(plugin);

        // Create data directory if it doesn't exist
        File dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        // Start auto-save task
        startAutoSaveTask();
    }

    /**
     * Get the storage implementation
     * @return The storage implementation
     */
    public Storage getStorage() {
        return storage;
    }

    /**
     * Load configuration settings for the persistence system
     */
    private void loadConfiguration() {
        // Load from config.yml
        this.autoSaveInterval = plugin.getConfigManager().getConfig().getInt("persistence.auto-save-interval", 300);
        this.debugMode = plugin.getConfigManager().getConfig().getBoolean("persistence.debug-mode", false);
    }

    /**
     * Start the auto-save task
     */
    private void startAutoSaveTask() {
        // Cancel any existing task
        stopAutoSaveTask();

        // Start new task
        if (autoSaveInterval > 0) {
            autoSaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
                    this::saveAllPendingData,
                    autoSaveInterval * 20L,
                    autoSaveInterval * 20L);

            if (debugMode) {
                plugin.getLogger().info("Started auto-save task with interval: " + autoSaveInterval + " seconds");
            }
        }
    }

    /**
     * Stop the auto-save task
     */
    private void stopAutoSaveTask() {
        if (autoSaveTask != null && !autoSaveTask.isCancelled()) {
            autoSaveTask.cancel();
            autoSaveTask = null;
        }
    }

    /**
     * Generate a unique ID for a tool
     * @return A unique identifier string
     */
    private String generateUniqueId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Get or create a unique ID for a tool
     * @param item The tool item
     * @return The unique ID
     */
    public String getOrCreateUniqueId(ItemStack item) {
        if (!GensTool.isGensTool(item) || item.getItemMeta() == null) {
            return null;
        }

        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        String uniqueId = container.get(KEY_UNIQUE_ID, PersistentDataType.STRING);

        if (uniqueId == null) {
            // Create new unique ID
            uniqueId = generateUniqueId();

            // Save it to the item
            setUniqueId(item, uniqueId);

            if (debugMode) {
                plugin.getLogger().info("Created new unique ID for tool: " + uniqueId);
            }
        }

        return uniqueId;
    }

    /**
     * Set a unique ID on a tool
     * @param item The tool item
     * @param uniqueId The unique ID to set
     */
    private void setUniqueId(ItemStack item, String uniqueId) {
        if (!GensTool.isGensTool(item) || item.getItemMeta() == null) {
            return;
        }

        try {
            // Update the item's metadata
            var meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(KEY_UNIQUE_ID, PersistentDataType.STRING, uniqueId);
            item.setItemMeta(meta);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to set unique ID on tool", e);
        }
    }

    /**
     * Register a new tool with the persistence system
     * @param player The owner of the tool
     * @param item The tool item
     */
    public void registerTool(Player player, ItemStack item) {
        if (!GensTool.isGensTool(item)) {
            return;
        }

        // Ensure the tool has a unique ID
        String uniqueId = getOrCreateUniqueId(item);
        if (uniqueId == null) {
            return;
        }

        // Create tool data
        SavedToolData toolData = createToolData(item);

        // Add to player's tool data
        PlayerToolData playerData = getPlayerData(player.getUniqueId());
        playerData.addOrUpdateTool(toolData);

        // Mark for saving
        markPlayerForSave(player.getUniqueId());

        if (debugMode) {
            plugin.getLogger().info("Registered tool for player " + player.getName() + ": " + toolData.getToolId());
        }
    }

    /**
     * Creates tool data from an item
     * @param item The tool item
     * @return SavedToolData object
     */
    private SavedToolData createToolData(ItemStack item) {
        if (!GensTool.isGensTool(item)) {
            return null;
        }

        String toolId = GensTool.getToolId(item);
        int level = GensTool.getLevel(item);
        int exp = GensTool.getExperience(item);
        String uniqueId = getOrCreateUniqueId(item);
        Map<String, Integer> enchants = GensTool.getEnchantments(item);

        return new SavedToolData(uniqueId, toolId, level, exp, enchants);
    }

    /**
     * Get player tool data, loading from storage if needed
     * @param playerUuid The player UUID
     * @return PlayerToolData object
     */
    private PlayerToolData getPlayerData(UUID playerUuid) {
        // Check cache first
        if (playerToolCache.containsKey(playerUuid)) {
            return playerToolCache.get(playerUuid);
        }

        // Load from storage
        PlayerToolData data = storage.loadPlayerData(playerUuid);
        if (data == null) {
            // Create new data if none exists
            data = new PlayerToolData(playerUuid);
        }

        // Add to cache
        playerToolCache.put(playerUuid, data);
        return data;
    }

    /**
     * Mark a player for pending save
     * @param playerUuid The player UUID
     */
    private void markPlayerForSave(UUID playerUuid) {
        pendingSaves.add(playerUuid);
    }

    /**
     * Save data for a specific player
     * @param playerUuid The player UUID
     */
    public void savePlayerData(UUID playerUuid) {
        // Check if player has data in cache
        if (!playerToolCache.containsKey(playerUuid)) {
            return;
        }

        // Get the data
        PlayerToolData data = playerToolCache.get(playerUuid);

        // Save asynchronously
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                storage.savePlayerData(data);

                if (debugMode) {
                    plugin.getLogger().info("Saved tool data for player: " + playerUuid);
                }

                // Remove from pending saves
                pendingSaves.remove(playerUuid);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save tool data for player: " + playerUuid, e);
            }
        });
    }

    /**
     * Save all pending player data
     */
    public void saveAllPendingData() {
        if (pendingSaves.isEmpty()) {
            return;
        }

        if (debugMode) {
            plugin.getLogger().info("Saving tool data for " + pendingSaves.size() + " players");
        }

        // Create a copy to avoid concurrent modification
        List<UUID> playersToSave = new ArrayList<>(pendingSaves);

        // Save each player's data
        for (UUID playerUuid : playersToSave) {
            savePlayerData(playerUuid);
        }
    }

    /**
     * Update a tool from storage based on its unique ID
     * @param player The player who owns the tool
     * @param item The tool item to update
     * @return true if the tool was updated, false otherwise
     */
    public boolean updateToolFromStorage(Player player, ItemStack item) {
        if (!GensTool.isGensTool(item)) {
            return false;
        }

        // Get the unique ID
        String uniqueId = getOrCreateUniqueId(item);
        if (uniqueId == null) {
            return false;
        }

        // Get player data
        PlayerToolData playerData = getPlayerData(player.getUniqueId());

        // Find the tool
        SavedToolData toolData = playerData.getToolByUniqueId(uniqueId);
        if (toolData == null) {
            // Tool not found in storage
            return false;
        }

        // Update the item from saved data
        applyToolData(item, toolData);

        if (debugMode) {
            plugin.getLogger().info("Updated tool from storage for player " + player.getName() +
                    ": " + toolData.getToolId() + " (Level: " + toolData.getLevel() +
                    ", Enchants: " + toolData.getEnchantments().size() + ")");
        }

        return true;
    }

    /**
     * Apply saved tool data to an item
     * @param item The tool item to update
     * @param toolData The saved tool data
     */
    private void applyToolData(ItemStack item, SavedToolData toolData) {
        // Set level
        GensTool.setLevel(item, toolData.getLevel());

        // Set experience
        GensTool.setExperience(item, toolData.getExperience());

        // Clear and re-add enchantments
        Map<String, Integer> currentEnchants = GensTool.getEnchantments(item);

        // First remove existing enchantments
        for (String enchantId : new ArrayList<>(currentEnchants.keySet())) {
            GensTool.removeEnchantment(item, enchantId, false);
        }

        // Then add saved enchantments
        for (Map.Entry<String, Integer> entry : toolData.getEnchantments().entrySet()) {
            GensTool.addEnchantment(item, entry.getKey(), entry.getValue(), false);
        }

        // Update lore once at the end
        GensTool.updateEnchantmentLore(item);
    }

    /**
     * Called when a player joins the server
     * @param player The player who joined
     */
    public void handlePlayerJoin(Player player) {
        // Load player data (this will cache it for future use)
        getPlayerData(player.getUniqueId());

        if (debugMode) {
            plugin.getLogger().info("Loaded tool data for player: " + player.getName());
        }
    }

    /**
     * Called when a player quits the server
     * @param player The player who quit
     */
    public void handlePlayerQuit(Player player) {
        // Save player data
        savePlayerData(player.getUniqueId());

        // Remove from cache after a delay
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            playerToolCache.remove(player.getUniqueId());

            if (debugMode) {
                plugin.getLogger().info("Removed cached tool data for player: " + player.getName());
            }
        }, 100L); // 5 second delay
    }

    /**
     * Called when a tool is updated (enchantments, level, etc.)
     * @param player The player who owns the tool
     * @param item The tool that was updated
     */
    public void handleToolUpdate(Player player, ItemStack item) {
        if (!GensTool.isGensTool(item)) {
            return;
        }

        // Update cached data
        SavedToolData toolData = createToolData(item);
        if (toolData == null) {
            return;
        }

        // Update in player data
        PlayerToolData playerData = getPlayerData(player.getUniqueId());
        playerData.addOrUpdateTool(toolData);

        // Mark for saving
        markPlayerForSave(player.getUniqueId());

        if (debugMode) {
            plugin.getLogger().info("Updated tool data for player " + player.getName() +
                    ": " + toolData.getToolId());
        }
    }

    /**
     * Save all player data (used on server shutdown)
     */
    public void saveAllData() {
        plugin.getLogger().info("Saving all tool data...");

        // Save all players in cache
        for (UUID playerUuid : new ArrayList<>(playerToolCache.keySet())) {
            try {
                PlayerToolData data = playerToolCache.get(playerUuid);
                storage.savePlayerData(data);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save tool data for player: " + playerUuid, e);
            }
        }

        plugin.getLogger().info("All tool data saved successfully.");
    }

    /**
     * Reload the persistence system
     */
    public void reload() {
        // Save all pending data
        saveAllPendingData();

        // Reload configuration
        loadConfiguration();

        // Restart auto-save task with new settings
        startAutoSaveTask();

        // Clear caches
        playerToolCache.clear();

        plugin.getLogger().info("Tool persistence system reloaded.");
    }

    /**
     * Shutdown the persistence system
     */
    public void shutdown() {
        // Stop auto-save task
        stopAutoSaveTask();

        // Save all data
        saveAllData();

        // Clear caches
        playerToolCache.clear();
        pendingSaves.clear();
    }
}