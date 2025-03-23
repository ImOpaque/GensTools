package me.opaque.genstools;

import me.opaque.genscore.GensCore;
import me.opaque.genstools.commands.GensToolsCommand;
import me.opaque.genstools.enchants.EnchantmentApplicability;
import me.opaque.genstools.gui.MenuManager;
import me.opaque.genstools.listeners.PersistenceListener;
import me.opaque.genstools.listeners.ToolEventListener;
import me.opaque.genstools.manager.ConfigManager;
import me.opaque.genstools.manager.ToolManager;
import me.opaque.genstools.persistence.ToolPersistenceManager;
import me.opaque.genstools.utils.LoreManager;
import me.opaque.genstools.utils.MessageManager;
import me.opaque.genstools.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import static com.edwardbelt.edlicense.Main.checkLicense;

public class GensTools extends JavaPlugin {
    private static GensTools instance;
    private ToolManager toolManager;
    private ConfigManager configManager;
    private MessageManager messageManager;
    private GensCore gensCoreAPI;
    private MenuManager menuManager;
    private LoreManager loreManager;
    private ToolPersistenceManager toolPersistenceManager;

    @Override
    public void onEnable() {
        instance = this;

        // Check if GensCore is loaded
        if (Bukkit.getPluginManager().getPlugin("GensCore") != null) {
            // Get the API
            gensCoreAPI = (GensCore) Bukkit.getPluginManager().getPlugin("GensCore");
            getLogger().info("Successfully hooked into GensCore!");
        } else {
            getLogger().warning("GensCore not found! Functionality will be limited.");
        }

        // Initialize the managers in the correct order
        toolManager = new ToolManager(this);
        loreManager = new LoreManager(this);
        configManager = new ConfigManager(this);
        messageManager = new MessageManager(this);
        menuManager = new MenuManager(this);

        // Initialize persistence system
        toolPersistenceManager = new ToolPersistenceManager(this);

        EnchantmentApplicability.initialize();

        // Register commands
        getCommand("genstools").setExecutor(new GensToolsCommand(this));

        // Register event listeners
        getServer().getPluginManager().registerEvents(new ToolEventListener(this), this);
        getServer().getPluginManager().registerEvents(new PersistenceListener(this), this);

        getLogger().info(Utils.colorize("&9&lGensTools has been enabled!"));
    }

    @Override
    public void onDisable() {
        // Shutdown persistence system
        if (toolPersistenceManager != null) {
            toolPersistenceManager.shutdown();
        }

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

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public GensCore getGensCoreAPI() {
        return gensCoreAPI;
    }

    public MenuManager getMenuManager() {
        return menuManager;
    }

    public FileConfiguration getGuiConfig() {
        return menuManager.getGuiConfig();
    }

    public LoreManager getLoreManager() {
        return loreManager;
    }

    public ToolPersistenceManager getToolPersistenceManager() {
        return toolPersistenceManager;
    }
}