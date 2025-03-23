package me.opaque.genstools.utils;

import me.opaque.genstools.GensTools;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageManager {

    private final GensTools plugin;
    private FileConfiguration messagesConfig;
    private File messagesFile;
    private final Map<String, String> messages = new HashMap<>();

    // Pattern for RGB color codes like &#RRGGBB
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    // Pattern for placeholders like {placeholder}
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([^}]+)\\}");

    public MessageManager(GensTools plugin) {
        this.plugin = plugin;
        loadMessages();
    }

    /**
     * Load messages from configuration
     */
    public void loadMessages() {
        if (messagesFile == null) {
            messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        }

        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        // Compare with internal defaults and add missing keys
        InputStream defaultStream = plugin.getResource("messages.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream));

            boolean needsSave = false;
            for (String key : defaultConfig.getKeys(true)) {
                if (!messagesConfig.contains(key)) {
                    messagesConfig.set(key, defaultConfig.get(key));
                    needsSave = true;
                }
            }

            if (needsSave) {
                try {
                    messagesConfig.save(messagesFile);
                } catch (IOException e) {
                    plugin.getLogger().severe("Could not save messages.yml file: " + e.getMessage());
                }
            }
        }

        // Load all messages into memory
        messages.clear();
        for (String key : messagesConfig.getKeys(true)) {
            if (messagesConfig.isString(key)) {
                messages.put(key, messagesConfig.getString(key));
            }
        }
    }

    /**
     * Get a message from the config, with color codes translated
     *
     * @param key The message key
     * @return The formatted message
     */
    public String getMessage(String key) {
        String message = messages.getOrDefault(key, "Missing message: " + key);
        return formatMessage(message);
    }

    /**
     * Get a message with placeholders replaced
     *
     * @param key The message key
     * @param replacements Pairs of placeholder and value (e.g., "player", "Steve")
     * @return The formatted message with placeholders replaced
     */
    public String getMessage(String key, Object... replacements) {
        String message = getMessage(key);

        // Replace placeholders
        if (replacements.length % 2 == 0) {
            for (int i = 0; i < replacements.length; i += 2) {
                String placeholder = String.valueOf(replacements[i]);
                String value = String.valueOf(replacements[i + 1]);
                message = message.replace("{" + placeholder + "}", value);
            }
        }

        return message;
    }

    /**
     * Send a message to a player
     *
     * @param player The player to send the message to
     * @param key The message key
     * @param replacements Pairs of placeholder and value
     */
    public void sendMessage(Player player, String key, Object... replacements) {
        String message = getMessage(key, replacements);
        if (!message.isEmpty()) {
            player.sendMessage(message);
        }
    }

    /**
     * Format a message with color codes and hex colors
     *
     * @param message The message to format
     * @return The formatted message
     */
    public String formatMessage(String message) {
        if (message == null) return "";

        // Replace hex colors (&#RRGGBB)
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String hex = matcher.group(1);
            matcher.appendReplacement(buffer, net.md_5.bungee.api.ChatColor.of("#" + hex).toString());
        }

        matcher.appendTail(buffer);
        message = buffer.toString();

        // Replace standard color codes
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * Reload messages from disk
     */
    public void reload() {
        loadMessages();
    }
}