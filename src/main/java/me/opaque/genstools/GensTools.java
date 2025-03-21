package me.opaque.genstools;

import me.opaque.genstools.commands.GensToolsCommand;
import me.opaque.genstools.listeners.ToolEventListener;
import me.opaque.genstools.manager.ConfigManager;
import me.opaque.genstools.manager.ToolManager;
import org.bukkit.plugin.java.JavaPlugin;

public class GensTools extends JavaPlugin {
    private static GensTools instance;
    private ToolManager toolManager;
    private ConfigManager configManager;

    @Override
    public void onEnable() {
        instance = this;

        // Initialize the managers in the correct order
        toolManager = new ToolManager(this);
        configManager = new ConfigManager(this);

        // Register commands
        getCommand("genstools").setExecutor(new GensToolsCommand(this));

        // Register event listeners
        getServer().getPluginManager().registerEvents(new ToolEventListener(this), this);

        getLogger().info("GensTools has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("GensTools has been disabled!");
    }

    public static GensTools getInstance() {
        return instance;
    }

    public ToolManager getToolManager() {
        return toolManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }
}
