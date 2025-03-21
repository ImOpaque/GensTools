package me.opaque.genstools.utils;

import me.opaque.genstools.GensTools;
import org.bukkit.configuration.ConfigurationSection;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NumberFormatter {

    private final GensTools plugin;
    private final List<NumberSuffix> suffixes = new ArrayList<>();
    private boolean enabled = true;
    private static final DecimalFormat decimalFormat = new DecimalFormat("#,##0.##");
    private static final Pattern SUFFIX_PATTERN = Pattern.compile("([a-zA-Z]+)-(\\d+)");

    public NumberFormatter(GensTools plugin) {
        this.plugin = plugin;
        loadFromConfig();
    }

    /**
     * Reload the number formatter from config
     */
    public void reload() {
        loadFromConfig();
    }

    /**
     * Load suffixes from configuration
     */
    private void loadFromConfig() {
        suffixes.clear();

        ConfigurationSection config = plugin.getConfig().getConfigurationSection("settings.number-format");
        if (config == null) {
            // Use defaults if section doesn't exist
            setupDefaults();
            return;
        }

        enabled = config.getBoolean("enabled", true);
        List<String> suffixList = config.getStringList("suffixes");

        if (suffixList.isEmpty()) {
            setupDefaults();
            return;
        }

        for (String suffixEntry : suffixList) {
            Matcher matcher = SUFFIX_PATTERN.matcher(suffixEntry);
            if (matcher.matches()) {
                String suffix = matcher.group(1);
                long value = Long.parseLong(matcher.group(2));
                suffixes.add(new NumberSuffix(suffix, value));
            } else {
                plugin.getLogger().warning("Invalid number suffix format: " + suffixEntry);
            }
        }

        // Sort by value in descending order (largest values first)
        Collections.sort(suffixes, Comparator.comparingLong(NumberSuffix::getValue).reversed());
    }

    /**
     * Setup default suffixes
     */
    private void setupDefaults() {
        suffixes.add(new NumberSuffix("k", 1_000));
        suffixes.add(new NumberSuffix("m", 1_000_000));
        suffixes.add(new NumberSuffix("b", 1_000_000_000));
        suffixes.add(new NumberSuffix("t", 1_000_000_000_000L));
        suffixes.add(new NumberSuffix("q", 1_000_000_000_000_000L));
    }

    /**
     * Format a number using the configured suffixes
     * @param number The number to format
     * @return The formatted number string
     */
    public String format(long number) {
        if (!enabled || number < 1000) {
            return decimalFormat.format(number);
        }

        for (NumberSuffix suffix : suffixes) {
            if (Math.abs(number) >= suffix.getValue()) {
                double value = (double) number / suffix.getValue();
                return decimalFormat.format(value) + suffix.getSuffix();
            }
        }

        return decimalFormat.format(number);
    }

    /**
     * Format a number using the configured suffixes
     * @param number The number to format
     * @return The formatted number string
     */
    public String format(double number) {
        if (!enabled || Math.abs(number) < 1000) {
            return decimalFormat.format(number);
        }

        for (NumberSuffix suffix : suffixes) {
            if (Math.abs(number) >= suffix.getValue()) {
                double value = number / suffix.getValue();
                return decimalFormat.format(value) + suffix.getSuffix();
            }
        }

        return decimalFormat.format(number);
    }

    /**
     * Check if formatting is enabled
     * @return true if enabled, false otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Inner class to represent a number suffix
     */
    private static class NumberSuffix {
        private final String suffix;
        private final long value;

        public NumberSuffix(String suffix, long value) {
            this.suffix = suffix;
            this.value = value;
        }

        public String getSuffix() {
            return suffix;
        }

        public long getValue() {
            return value;
        }
    }
}