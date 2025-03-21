package me.opaque.genstools.utils;

import me.opaque.genstools.GensTools;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Utils {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static String PREFIX;
    private static boolean DEBUG_MODE = false;
    private static NumberFormatter numberFormatter;
    private static GensTools plugin;

    /**
     * Initialize the Utils class with the plugin instance
     */
    public static void initialize(GensTools pl) {
        plugin = pl;
        numberFormatter = new NumberFormatter(plugin);
        PREFIX = plugin.getConfig().getString("settings.prefix");
    }

    /**
     * Reload the utils
     */
    public static void reload() {
        numberFormatter.reload();
    }

    /**
     * Format a number using the configured suffixes
     */
    public static String formatNumber(long number) {
        return numberFormatter.format(number);
    }

    /**
     * Format a number using the configured suffixes
     */
    public static String formatNumber(double number) {
        return numberFormatter.format(number);
    }

    /**
     * Sets the plugin prefix
     * @param prefix The prefix to set
     */
    public static void setPrefix(String prefix) {
        PREFIX = prefix;
    }

    /**
     * Sets the debug mode
     * @param debugMode Whether debug mode is enabled
     */
    public static void setDebugMode(boolean debugMode) {
        DEBUG_MODE = debugMode;
    }

    /**
     * Applies color codes to a message
     * @param message The message to colorize
     * @return The colorized message
     */
    public static String colorize(String message) {
        if (message == null) return "";

        // Convert hex colors
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String color = matcher.group(1);
            matcher.appendReplacement(buffer, ChatColor.of("#" + color).toString());
        }

        matcher.appendTail(buffer);

        // Convert standard color codes
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    /**
     * Sends a message to a player with the plugin prefix
     * @param player The player to send the message to
     * @param message The message to send
     */
    public static void sendMessage(Player player, String message) {
        player.sendMessage(colorize(PREFIX + message));
        //player.sendMessage(PlaceholderUtil.setPlaceholders(player, Utils.colorize(PREFIX + message)));
    }

    /**
     * Sends a message to a command sender with the plugin prefix
     * @param sender The command sender to send the message to
     * @param message The message to send
     */
    public static void sendMessage(CommandSender sender, String message) {
        String colorizedMessage = colorize(PREFIX + message);

        // Apply placeholders if sender is a player
        /*if (sender instanceof Player) {
            colorizedMessage = PlaceholderUtil.setPlaceholders((Player) sender, colorizedMessage);
        }*/

        sender.sendMessage(colorizedMessage);
    }

    /**
     * Logs an info message to the console
     * @param message The message to log
     */
    public static void logInfo(String message) {
        Bukkit.getLogger().info(colorize("[GensCore] &b" + message));
    }

    /**
     * Logs a warning message to the console
     * @param message The message to log
     */
    public static void logWarning(String message) {
        Bukkit.getLogger().warning(colorize("[GensCore] &4" + message));
    }

    /**
     * Logs an error message to the console
     * @param message The message to log
     */
    public static void logError(String message) {
        Bukkit.getLogger().severe(colorize("[GensCore] " + message));
    }

    /**
     * Logs a debug message to the console if debug mode is enabled
     * @param message The message to log
     */
    public static void logDebug(String message) {
        if (DEBUG_MODE) {
            Bukkit.getLogger().info(colorize("[GensCore] - [DEBUG] &6" + message));
        }
    }

    /**
     * Spawns redstone particles in a ripple effect at the specified location.
     *
     * @param center The center location of the ripple
     * @param color The color of the particles (RGB format)
     * @param maxRadius The maximum radius of the ripple
     * @param particlesPerCircle Number of particles in each circle
     * @param duration Duration of the animation in ticks
     * @param plugin Your plugin instance for scheduling
     */
    public static void createRippleEffect(Location center, Color color, double maxRadius, int particlesPerCircle, int duration, Plugin plugin) {
        double radiusIncrement = maxRadius / duration;

        new BukkitRunnable() {
            double currentRadius = 0;
            int tick = 0;

            @Override
            public void run() {
                if (tick >= duration) {
                    this.cancel();
                    return;
                }

                // Calculate the dust options for colored redstone particles
                Particle.DustOptions dustOptions = new Particle.DustOptions(color, 1.0F);

                // Spawn particles in a circle
                for (int i = 0; i < particlesPerCircle; i++) {
                    double angle = 2 * Math.PI * i / particlesPerCircle;
                    double x = center.getX() + currentRadius * Math.cos(angle);
                    double y = center.getY();
                    double z = center.getZ() + currentRadius * Math.sin(angle);

                    Location particleLocation = new Location(center.getWorld(), x, y, z);
                    center.getWorld().spawnParticle(Particle.REDSTONE, particleLocation, 1, 0, 0, 0, 0, dustOptions);
                }

                currentRadius += radiusIncrement;
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /*
     * Formats a number with commas for readability
     * @param number The number to format
     * @return The formatted number
     */
    public static String formatGenNumber(long number) {
        return String.format("%,d", number);
    }

    /*
     * Formats a number with commas and truncates decimals for readability
     * @param number The number to format
     * @return The formatted number
     */
    public static String formatGenNumber(double number) {
        if (number == (long) number) {
            return String.format("%,d", (long) number);
        } else {
            return String.format("%,.2f", number);
        }
    }

    /**
     * Applies a multiplier to a value
     * @param value The base value
     * @param multiplier The multiplier
     * @return The value after applying the multiplier
     */
    public static double applyMultiplier(double value, double multiplier) {
        return value * multiplier;
    }

}
