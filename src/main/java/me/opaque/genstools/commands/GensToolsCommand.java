package me.opaque.genstools.commands;

import me.opaque.genstools.GensTools;
import me.opaque.genstools.enchants.CustomEnchant;
import me.opaque.genstools.tools.GensTool;
import me.opaque.genstools.utils.NumberFormatter;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GensToolsCommand implements CommandExecutor, TabCompleter {

    private final GensTools plugin;

    public GensToolsCommand(GensTools plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "give":
                if (!hasPermission(sender, "genstools.command.give")) return true;

                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /genstools give <player> <tool>");
                    return true;
                }

                Player giveTarget = Bukkit.getPlayer(args[1]);
                if (giveTarget == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found!");
                    return true;
                }

                String toolId = args[2];
                if (plugin.getToolManager().getToolById(toolId) == null) {
                    sender.sendMessage(ChatColor.RED + "Invalid tool ID!");
                    return true;
                }

                plugin.getToolManager().giveTool(giveTarget, toolId);
                sender.sendMessage(ChatColor.GREEN + "Gave " + toolId + " to " + giveTarget.getName());
                return true;

            case "enchant":
                if (!hasPermission(sender, "genstools.command.enchant")) return true;

                if (!(sender instanceof Player enchantPlayer)) {
                    sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
                    return true;
                }

                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /genstools enchant <enchant> <level>");
                    return true;
                }

                String enchantId = args[1];
                int enchantLevel;

                try {
                    enchantLevel = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid level!");
                    return true;
                }

                if (plugin.getToolManager().getEnchantById(enchantId) == null) {
                    sender.sendMessage(ChatColor.RED + "Invalid enchant ID!");
                    return true;
                }

                if (plugin.getToolManager().addEnchantToTool(enchantPlayer.getInventory().getItemInMainHand(), enchantId, enchantLevel)) {
                    sender.sendMessage(ChatColor.GREEN + "Added enchant " + enchantId + " level " + enchantLevel + " to tool!");
                } else {
                    sender.sendMessage(ChatColor.RED + "Failed to add enchant. Make sure you're holding a GensTool.");
                }
                return true;

            case "list":
                if (!hasPermission(sender, "genstools.command.list")) return true;

                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /genstools list <tools|enchants>");
                    return true;
                }

                if (args[1].equalsIgnoreCase("tools")) {
                    sender.sendMessage(ChatColor.GOLD + "=== Available Tools ===");
                    for (String id : plugin.getToolManager().getAllToolIds()) {
                        sender.sendMessage(ChatColor.YELLOW + "- " + id);
                    }
                } else if (args[1].equalsIgnoreCase("enchants")) {
                    sender.sendMessage(ChatColor.GOLD + "=== Available Enchantments ===");
                    for (String id : plugin.getToolManager().getAllEnchantIds()) {
                        sender.sendMessage(ChatColor.YELLOW + "- " + id);
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "Usage: /genstools list <tools|enchants>");
                }
                return true;

            case "setlevel":
                if (!hasPermission(sender, "genstools.command.setlevel")) return true;

                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /genstools setlevel <level> [player]");
                    return true;
                }

                int newLevel;
                try {
                    newLevel = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid level!");
                    return true;
                }

                if (newLevel <= 0 || newLevel > 100) {
                    sender.sendMessage(ChatColor.RED + "Level must be between 1 and 100!");
                    return true;
                }

                Player levelTarget;
                if (args.length >= 3) {
                    levelTarget = Bukkit.getPlayer(args[2]);
                    if (levelTarget == null) {
                        sender.sendMessage(ChatColor.RED + "Player not found!");
                        return true;
                    }
                } else {
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(ChatColor.RED + "Console must specify a player!");
                        return true;
                    }
                    levelTarget = (Player) sender;
                }

                ItemStack levelToolItem = levelTarget.getInventory().getItemInMainHand();
                if (setToolLevel(levelToolItem, newLevel)) {
                    sender.sendMessage(ChatColor.GREEN + "Set tool level to " + newLevel);

                    // Play sound and show effects if not sender
                    if (levelTarget != sender && levelTarget.isOnline()) {
                        levelTarget.sendMessage(ChatColor.GREEN + "Your tool was set to level " + newLevel + " by an admin!");
                        levelTarget.playSound(levelTarget.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "Failed to set level. Make sure a valid GensTool is being held.");
                }
                return true;

            case "setexp":
                if (!hasPermission(sender, "genstools.command.setexp")) return true;

                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /genstools setexp <amount> [player]");
                    return true;
                }

                int expAmount;
                try {
                    expAmount = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid exp amount!");
                    return true;
                }

                if (expAmount < 0) {
                    sender.sendMessage(ChatColor.RED + "EXP amount must be positive!");
                    return true;
                }

                Player expTarget;
                if (args.length >= 3) {
                    expTarget = Bukkit.getPlayer(args[2]);
                    if (expTarget == null) {
                        sender.sendMessage(ChatColor.RED + "Player not found!");
                        return true;
                    }
                } else {
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(ChatColor.RED + "Console must specify a player!");
                        return true;
                    }
                    expTarget = (Player) sender;
                }

                ItemStack expToolItem = expTarget.getInventory().getItemInMainHand();
                if (setToolExp(expToolItem, expAmount)) {
                    sender.sendMessage(ChatColor.GREEN + "Set tool EXP to " + expAmount);

                    // Notify target if not sender
                    if (expTarget != sender && expTarget.isOnline()) {
                        expTarget.sendMessage(ChatColor.GREEN + "Your tool's EXP was set to " + expAmount + " by an admin!");
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "Failed to set EXP. Make sure a valid GensTool is being held.");
                }
                return true;

            case "addexp":
                if (!hasPermission(sender, "genstools.command.addexp")) return true;

                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /genstools addexp <amount> [player]");
                    return true;
                }

                int addExpAmount;
                try {
                    addExpAmount = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid exp amount!");
                    return true;
                }

                if (addExpAmount <= 0) {
                    sender.sendMessage(ChatColor.RED + "EXP amount must be positive!");
                    return true;
                }

                Player addExpTarget;
                if (args.length >= 3) {
                    addExpTarget = Bukkit.getPlayer(args[2]);
                    if (addExpTarget == null) {
                        sender.sendMessage(ChatColor.RED + "Player not found!");
                        return true;
                    }
                } else {
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(ChatColor.RED + "Console must specify a player!");
                        return true;
                    }
                    addExpTarget = (Player) sender;
                }

                ItemStack addExpToolItem = addExpTarget.getInventory().getItemInMainHand();
                boolean leveledUp = addToolExp(addExpToolItem, addExpAmount);

                if (leveledUp) {
                    int currentLevel = getToolLevel(addExpToolItem);

                    sender.sendMessage(ChatColor.GREEN + "Added " + addExpAmount + " EXP to tool, it leveled up to " + currentLevel + "!");

                    // Notify target if not sender
                    if (addExpTarget != sender && addExpTarget.isOnline()) {
                        addExpTarget.sendMessage(ChatColor.GREEN + "An admin added " + addExpAmount + " EXP to your tool, it leveled up to " + currentLevel + "!");
                        addExpTarget.playSound(addExpTarget.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    }
                } else if (addExpToolItem != null) {
                    sender.sendMessage(ChatColor.GREEN + "Added " + addExpAmount + " EXP to tool.");

                    // Notify target if not sender
                    if (addExpTarget != sender && addExpTarget.isOnline()) {
                        addExpTarget.sendMessage(ChatColor.GREEN + "An admin added " + addExpAmount + " EXP to your tool.");
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "Failed to add EXP. Make sure a valid GensTool is being held.");
                }
                return true;

            case "info":
                if (!hasPermission(sender, "genstools.command.info")) return true;

                if (!(sender instanceof Player)) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("general.player-only"));
                    return true;
                }

                Player infoPlayer = (Player) sender;
                ItemStack infoItem = infoPlayer.getInventory().getItemInMainHand();

                if (!GensTool.isGensTool(infoItem)) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("tools.not-holding-tool"));
                    return true;
                }

                String infoToolType = GensTool.getToolId(infoItem);
                int infoToolLevel = GensTool.getToolLevel(infoItem);
                int infoToolExp = GensTool.getToolExp(infoItem);
                int infoRequiredExp = GensTool.calculateRequiredExp(infoToolLevel);

                sender.sendMessage(plugin.getMessageManager().getMessage("commands.info.header"));
                sender.sendMessage(plugin.getMessageManager().getMessage("commands.info.type",
                        "type", infoToolType));
                sender.sendMessage(plugin.getMessageManager().getMessage("commands.info.level",
                        "level", infoToolLevel));

                int infoPercent = (int) Math.round((double) infoToolExp / infoRequiredExp * 100);
                sender.sendMessage(plugin.getMessageManager().getMessage("commands.info.exp",
                        "exp", infoToolExp,
                        "required", infoRequiredExp,
                        "percent", infoPercent));

                // Show enchantments
                sender.sendMessage(plugin.getMessageManager().getMessage("commands.info.enchants-header"));
                Map<String, Integer> infoEnchantments = GensTool.getToolEnchantments(infoItem);

                if (infoEnchantments.isEmpty()) {
                    sender.sendMessage(ChatColor.WHITE + "- None");
                } else {
                    for (Map.Entry<String, Integer> entry : infoEnchantments.entrySet()) {
                        String infoEnchantId = entry.getKey();
                        int infoEnchantLevel = entry.getValue();

                        CustomEnchant infoEnchant = plugin.getToolManager().getEnchantById(infoEnchantId);
                        if (infoEnchant != null) {
                            String infoDisplayName = infoEnchant.getDisplayName();
                            String infoFormattedLevel = GensTool.formatEnchantmentLevel(infoEnchantLevel);

                            sender.sendMessage(plugin.getMessageManager().getMessage("commands.info.enchant-item",
                                    "enchant", infoDisplayName,
                                    "level", infoFormattedLevel));
                        }
                    }
                }
                return true;

            case "reload":
                if (!hasPermission(sender, "genstools.command.reload")) return true;

                plugin.getConfigManager().reloadConfigs();
                plugin.getLoreManager().reloadConfig();

                sender.sendMessage(plugin.getMessageManager().getMessage("reload.success"));
                return true;

            case "help":
                sendHelp(sender);
                return true;

            case "persistence":
                handlePersistenceCommands(sender, args);
                return true;

            default:
                sender.sendMessage(ChatColor.RED + "Unknown command. Type /genstools help for help.");
                return true;
        }
    }

    private void handlePersistenceCommands(CommandSender sender, String[] args) {
        if (args.length < 2) {
            showPersistenceHelp(sender);
            return;
        }

        switch (args[1].toLowerCase()) {
            case "save":
                handleSaveCommand(sender, args);
                break;
            case "reload":
                handleReloadCommand(sender, args);
                break;
            case "backup":
                handleBackupCommand(sender, args);
                break;
            default:
                showPersistenceHelp(sender);
                break;
        }
    }

    private void handleSaveCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("genstools.admin.persistence.save")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return;
        }

        GensTools.getInstance().getToolPersistenceManager().saveAllPendingData();
        sender.sendMessage(ChatColor.GREEN + "All pending tool data has been saved.");
    }

    private void handleReloadCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("genstools.admin.persistence.reload")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return;
        }

        GensTools.getInstance().getToolPersistenceManager().reload();
        sender.sendMessage(ChatColor.GREEN + "Tool persistence system has been reloaded.");
    }

    private void handleBackupCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("genstools.admin.persistence.backup")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return;
        }

        boolean success = GensTools.getInstance().getToolPersistenceManager().getStorage().createBackup();
        if (success) {
            sender.sendMessage(ChatColor.GREEN + "Tool data backup created successfully.");
        } else {
            sender.sendMessage(ChatColor.RED + "Failed to create tool data backup. Check console for details.");
        }
    }

    private void showPersistenceHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== GensTools Persistence Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/genstools persistence save " + ChatColor.GRAY + "- Save all pending tool data");
        sender.sendMessage(ChatColor.YELLOW + "/genstools persistence reload " + ChatColor.GRAY + "- Reload the persistence system");
        sender.sendMessage(ChatColor.YELLOW + "/genstools persistence backup " + ChatColor.GRAY + "- Create a backup of all tool data");
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== GensTools Commands ===");

        if (sender.hasPermission("genstools.command.give"))
            sender.sendMessage(ChatColor.YELLOW + "/genstools give <player> <tool> " + ChatColor.GRAY + "- Give a tool to a player");

        if (sender.hasPermission("genstools.command.enchant"))
            sender.sendMessage(ChatColor.YELLOW + "/genstools enchant <enchant> <level> " + ChatColor.GRAY + "- Add an enchant to held tool");

        if (sender.hasPermission("genstools.command.list"))
            sender.sendMessage(ChatColor.YELLOW + "/genstools list <tools|enchants> " + ChatColor.GRAY + "- List available tools or enchants");

        if (sender.hasPermission("genstools.command.setlevel"))
            sender.sendMessage(ChatColor.YELLOW + "/genstools setlevel <level> [player] " + ChatColor.GRAY + "- Set tool level");

        if (sender.hasPermission("genstools.command.setexp"))
            sender.sendMessage(ChatColor.YELLOW + "/genstools setexp <amount> [player] " + ChatColor.GRAY + "- Set tool EXP");

        if (sender.hasPermission("genstools.command.addexp"))
            sender.sendMessage(ChatColor.YELLOW + "/genstools addexp <amount> [player] " + ChatColor.GRAY + "- Add EXP to tool");

        if (sender.hasPermission("genstools.command.info"))
            sender.sendMessage(ChatColor.YELLOW + "/genstools info " + ChatColor.GRAY + "- Show tool info");

        if (sender.hasPermission("genstools.command.reload"))
            sender.sendMessage(ChatColor.YELLOW + "/genstools reload " + ChatColor.GRAY + "- Reload config");

        sender.sendMessage(ChatColor.YELLOW + "/genstools help " + ChatColor.GRAY + "- Show this help message");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subcommands = new ArrayList<>();

            if (sender.hasPermission("genstools.command.give")) subcommands.add("give");
            if (sender.hasPermission("genstools.command.enchant")) subcommands.add("enchant");
            if (sender.hasPermission("genstools.command.list")) subcommands.add("list");
            if (sender.hasPermission("genstools.command.setlevel")) subcommands.add("setlevel");
            if (sender.hasPermission("genstools.command.setexp")) subcommands.add("setexp");
            if (sender.hasPermission("genstools.command.addexp")) subcommands.add("addexp");
            if (sender.hasPermission("genstools.command.info")) subcommands.add("info");
            if (sender.hasPermission("genstools.command.reload")) subcommands.add("reload");
            subcommands.add("help");

            return filterStartsWith(subcommands, args[0]);
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("give") && sender.hasPermission("genstools.command.give")) {
                return null; // Return player names
            } else if (args[0].equalsIgnoreCase("enchant") && sender.hasPermission("genstools.command.enchant")) {
                // Get all enchant IDs
                return filterStartsWith(
                        new ArrayList<>(plugin.getToolManager().getAllEnchantIds()),
                        args[1]
                );
            } else if (args[0].equalsIgnoreCase("list") && sender.hasPermission("genstools.command.list")) {
                return filterStartsWith(Arrays.asList("tools", "enchants"), args[1]);
            } else if (args[0].equalsIgnoreCase("setlevel") && sender.hasPermission("genstools.command.setlevel")) {
                // Suggest levels 1, 5, 10, 20, 50, 100
                return filterStartsWith(Arrays.asList("1", "5", "10", "20", "50", "100"), args[1]);
            } else if (args[0].equalsIgnoreCase("setexp") && sender.hasPermission("genstools.command.setexp")) {
                // Suggest exp amounts
                return filterStartsWith(Arrays.asList("0", "500", "1000", "5000", "10000"), args[1]);
            } else if (args[0].equalsIgnoreCase("addexp") && sender.hasPermission("genstools.command.addexp")) {
                // Suggest exp amounts
                return filterStartsWith(Arrays.asList("100", "500", "1000", "5000", "10000"), args[1]);
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("give") && sender.hasPermission("genstools.command.give")) {
                // Get all tool IDs
                return filterStartsWith(
                        new ArrayList<>(plugin.getToolManager().getAllToolIds()),
                        args[2]
                );
            } else if (args[0].equalsIgnoreCase("enchant") && sender.hasPermission("genstools.command.enchant")) {
                // For enchant levels, suggest 1-5
                List<String> levels = Arrays.asList("1", "2", "3", "4", "5");
                return filterStartsWith(levels, args[2]);
            } else if ((args[0].equalsIgnoreCase("setlevel") ||
                    args[0].equalsIgnoreCase("setexp") ||
                    args[0].equalsIgnoreCase("addexp")) &&
                    (sender.hasPermission("genstools.command.setlevel") ||
                            sender.hasPermission("genstools.command.setexp") ||
                            sender.hasPermission("genstools.command.addexp"))) {
                return null; // Return player names
            }
        }

        return completions;
    }

    private List<String> filterStartsWith(List<String> list, String prefix) {
        return list.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }

    // Helper methods for accessing tool functionality

    private boolean hasPermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
        return false;
    }

    private boolean isGensTool(ItemStack item) {
        return item != null && item.hasItemMeta() &&
                GensTool.isGensTool(item);
    }

    private String getToolId(ItemStack item) {
        return GensTool.getToolId(item);
    }

    private int getToolLevel(ItemStack item) {
        return GensTool.getLevel(item);
    }

    private int getToolExp(ItemStack item) {
        return GensTool.getExperience(item);
    }

    private Map<String, Integer> getToolEnchantments(ItemStack item) {
        return GensTool.getEnchantments(item);
    }

    private boolean setToolLevel(ItemStack item, int level) {
        if (!isGensTool(item)) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        // Set new level
        container.set(me.opaque.genstools.tools.GensTool.KEY_LEVEL, PersistentDataType.INTEGER, level);

        // Reset experience to 0
        container.set(me.opaque.genstools.tools.GensTool.KEY_EXPERIENCE, PersistentDataType.INTEGER, 0);

        // Update lore
        me.opaque.genstools.tools.GensTool.updateLore(meta, level, 0, calculateRequiredExp(level));
        item.setItemMeta(meta);

        return true;
    }

    private boolean setToolExp(ItemStack item, int exp) {
        if (!isGensTool(item)) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        int level = container.getOrDefault(me.opaque.genstools.tools.GensTool.KEY_LEVEL, PersistentDataType.INTEGER, 1);
        int requiredExp = calculateRequiredExp(level);

        // Cap exp at required amount to prevent issues
        if (exp > requiredExp) {
            exp = requiredExp;
        }

        // Set new exp
        container.set(me.opaque.genstools.tools.GensTool.KEY_EXPERIENCE, PersistentDataType.INTEGER, exp);

        // Update lore
        me.opaque.genstools.tools.GensTool.updateLore(meta, level, exp, requiredExp);
        item.setItemMeta(meta);

        return true;
    }

    private boolean addToolExp(ItemStack item, int amount) {
        if (!isGensTool(item)) {
            return false;
        }

        return me.opaque.genstools.tools.GensTool.addExperience(item, amount);
    }

    private int calculateRequiredExp(int level) {
        return 1000 + (level * 500);
    }

    private String toRoman(int number) {
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(number);
        };
    }
}