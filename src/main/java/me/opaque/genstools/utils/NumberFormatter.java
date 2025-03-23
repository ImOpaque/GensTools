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
     * Format a number using the configured suffixes
     * @param number The number to format
     * @return The formatted number string
     */
    public String format(int number) {
        return format((long) number);
    }

    /**
     * Formats a number with commas and decimal places
     *
     * @param number The number to format
     * @param decimalPlaces The number of decimal places to show
     * @return A formatted string
     */
    public String format(double number, int decimalPlaces) {
        if (!enabled || Math.abs(number) < 1000) {
            StringBuilder pattern = new StringBuilder("#,##0");
            if (decimalPlaces > 0) {
                pattern.append(".");
                for (int i = 0; i < decimalPlaces; i++) {
                    pattern.append("0");
                }
            }

            DecimalFormat customFormat = new DecimalFormat(pattern.toString());
            return customFormat.format(number);
        }

        for (NumberSuffix suffix : suffixes) {
            if (Math.abs(number) >= suffix.getValue()) {
                double value = number / suffix.getValue();

                StringBuilder pattern = new StringBuilder("#,##0");
                if (decimalPlaces > 0) {
                    pattern.append(".");
                    for (int i = 0; i < decimalPlaces; i++) {
                        pattern.append("0");
                    }
                }

                DecimalFormat customFormat = new DecimalFormat(pattern.toString());
                return customFormat.format(value) + suffix.getSuffix();
            }
        }

        StringBuilder pattern = new StringBuilder("#,##0");
        if (decimalPlaces > 0) {
            pattern.append(".");
            for (int i = 0; i < decimalPlaces; i++) {
                pattern.append("0");
            }
        }

        DecimalFormat customFormat = new DecimalFormat(pattern.toString());
        return customFormat.format(number);
    }

    /**
     * Formats a progress bar
     *
     * @param current The current value
     * @param max The maximum value
     * @param totalBars The total number of bars
     * @param barChar The character to use for filled bars
     * @param emptyChar The character to use for empty bars
     * @return A formatted progress bar
     */
    public String formatProgressBar(int current, int max, int totalBars, char barChar, char emptyChar) {
        int bars = (int) Math.round((double) current / max * totalBars);

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < totalBars; i++) {
            builder.append(i < bars ? barChar : emptyChar);
        }

        return builder.toString();
    }

    /**
     * Check if formatting is enabled
     * @return true if enabled, false otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Get a static instance of the standard formatter
     * This is useful for static contexts where the plugin instance isn't available
     *
     * @return A DecimalFormat with comma separator
     */
    public static DecimalFormat getStandardFormatter() {
        return decimalFormat;
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