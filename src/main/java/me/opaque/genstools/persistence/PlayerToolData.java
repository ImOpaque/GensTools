package me.opaque.genstools.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents all tool data for a player
 */
public class PlayerToolData {
    private final UUID playerUuid;
    private final List<SavedToolData> tools;

    public PlayerToolData(UUID playerUuid) {
        this.playerUuid = playerUuid;
        this.tools = new ArrayList<>();
    }

    /**
     * Add or update a tool in the player's data
     * @param toolData The tool data to add/update
     */
    public void addOrUpdateTool(SavedToolData toolData) {
        // Check if tool already exists
        SavedToolData existing = getToolByUniqueId(toolData.getUniqueId());

        if (existing != null) {
            // Update existing tool
            existing.setLevel(toolData.getLevel());
            existing.setExperience(toolData.getExperience());

            // Update enchantments
            for (String enchantId : toolData.getEnchantments().keySet()) {
                existing.setEnchantment(enchantId, toolData.getEnchantments().get(enchantId));
            }
        } else {
            // Add new tool
            tools.add(toolData);
        }
    }

    /**
     * Get a tool by its unique ID
     * @param uniqueId The unique ID of the tool
     * @return The tool data, or null if not found
     */
    public SavedToolData getToolByUniqueId(String uniqueId) {
        for (SavedToolData tool : tools) {
            if (tool.getUniqueId().equals(uniqueId)) {
                return tool;
            }
        }
        return null;
    }

    /**
     * Get all tools for this player
     * @return The list of tools
     */
    public List<SavedToolData> getTools() {
        return new ArrayList<>(tools);
    }

    /**
     * Get the player UUID
     * @return The player UUID
     */
    public UUID getPlayerUuid() {
        return playerUuid;
    }

    /**
     * Remove a tool by its unique ID
     * @param uniqueId The unique ID of the tool to remove
     * @return true if removed, false if not found
     */
    public boolean removeTool(String uniqueId) {
        return tools.removeIf(tool -> tool.getUniqueId().equals(uniqueId));
    }
}