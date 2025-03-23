package me.opaque.genstools.gui;

import me.opaque.genstools.GensTools;
import me.opaque.genstools.enchants.EnchantmentApplicability;
import me.opaque.genstools.tools.GensTool;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * Manager for all GensTools GUI menus
 * Handles events and configuration
 */
public class MenuManager implements Listener {
    private final GensTools plugin;
    private FileConfiguration guiConfig;
    private boolean enabled = true;

    public MenuManager(GensTools plugin) {
        this.plugin = plugin;
        loadConfig();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Load GUI configuration
     */
    public void loadConfig() {
        File file = new File(plugin.getDataFolder(), "gui_config.yml");

        // Create default config if it doesn't exist
        if (!file.exists()) {
            createDefaultConfig(file);
        }

        guiConfig = YamlConfiguration.loadConfiguration(file);
        enabled = guiConfig.getBoolean("tool-gui.enabled", true);

        // Initialize the enchantment applicability system
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            EnchantmentApplicability.initialize();
        }, 1L); // Slight delay to ensure all enchants are loaded
    }

    /**
     * Create a default GUI configuration
     */
    private void createDefaultConfig(File file) {
        FileConfiguration config = new YamlConfiguration();

        // General settings
        config.set("tool-gui.enabled", true);
        config.set("tool-gui.title", "&6Tool Enchantments");

        // Materials
        config.set("tool-gui.filler-material", "GRAY_STAINED_GLASS_PANE");
        config.set("tool-gui.upgrade-shards-material", "EMERALD");
        config.set("tool-gui.upgrade-runes-material", "AMETHYST_SHARD");
        config.set("tool-gui.max-level-material", "BARRIER");
        config.set("tool-gui.nav-prev-material", "ARROW");
        config.set("tool-gui.nav-next-material", "ARROW");

        // Cost system
        config.set("tool-gui.cost-multiplier", 1.5);

        // Sample enchant costs
        config.set("tool-gui.enchant-costs.efficiency.shards", 1000);
        config.set("tool-gui.enchant-costs.efficiency.runes", 100);
        config.set("tool-gui.enchant-costs.fortune.shards", 2000);
        config.set("tool-gui.enchant-costs.fortune.runes", 200);
        config.set("tool-gui.enchant-costs.sharpness.shards", 1000);
        config.set("tool-gui.enchant-costs.sharpness.runes", 100);
        config.set("tool-gui.enchant-costs.critical_strike.shards", 2000);
        config.set("tool-gui.enchant-costs.critical_strike.runes", 200);
        config.set("tool-gui.enchant-costs.shard_greed.shards", 5000);
        config.set("tool-gui.enchant-costs.shard_greed.runes", 500);

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save GUI configuration: " + e.getMessage());
        }
    }

    /**
     * Get the GUI configuration
     */
    public FileConfiguration getGuiConfig() {
        return guiConfig;
    }

    /**
     * Handle right-click to open the tool menu
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!enabled) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();

        // Allow normal tool use when sneaking
        if (player.isSneaking()) return;

        ItemStack item = event.getItem();
        if (item == null || !GensTool.isGensTool(item)) return;

        // Prevent normal interaction
        event.setCancelled(true);

        // Open the tool menu
        new ToolEnchantMenu(plugin, player, item).open();
    }

    /**
     * Handle inventory clicks in menus
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Menu menu = Menu.getOpenMenu(player.getUniqueId());
        if (menu != null && event.getView().getTopInventory().equals(menu.inventory)) {
            menu.handleClick(event);
        }
    }

    /**
     * Handle inventory close
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        Menu menu = Menu.getOpenMenu(player.getUniqueId());
        if (menu != null && event.getInventory().equals(menu.inventory)) {
            Menu.removeOpenMenu(player.getUniqueId());
        }
    }

    /**
     * Remove menu tracking when player quits
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Menu.removeOpenMenu(event.getPlayer().getUniqueId());
    }
}