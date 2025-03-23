package me.opaque.genstools.persistence;

import java.util.UUID;

/**
 * Interface for tool data storage systems
 */
public interface Storage {
    /**
     * Save player tool data
     * @param data The player tool data to save
     */
    void savePlayerData(PlayerToolData data);

    /**
     * Load player tool data
     * @param playerUuid The UUID of the player
     * @return The loaded player tool data, or a new instance if none exists
     */
    PlayerToolData loadPlayerData(UUID playerUuid);

    /**
     * Create a backup of all player data
     * @return true if successful, false otherwise
     */
    boolean createBackup();
}