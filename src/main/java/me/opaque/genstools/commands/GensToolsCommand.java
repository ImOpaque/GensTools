package me.opaque.genstools.commands;

import me.opaque.genstools.GensTools;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /genstools give <player> <tool>");
                    return true;
                }

                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found!");
                    return true;
                }

                String toolId = args[2];
                if (plugin.getToolManager().getToolById(toolId) == null) {
                    sender.sendMessage(ChatColor.RED + "Invalid tool ID!");
                    return true;
                }

                plugin.getToolManager().giveTool(target, toolId);
                sender.sendMessage(ChatColor.GREEN + "Gave " + toolId + " to " + target.getName());
                return true;

            case "enchant":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
                    return true;
                }

                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /genstools enchant <enchant> <level>");
                    return true;
                }

                Player player = (Player) sender;
                String enchantId = args[1];
                int level;

                try {
                    level = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid level!");
                    return true;
                }

                if (plugin.getToolManager().getEnchantById(enchantId) == null) {
                    sender.sendMessage(ChatColor.RED + "Invalid enchant ID!");
                    return true;
                }

                plugin.getToolManager().addEnchantToTool(player.getInventory().getItemInMainHand(), enchantId, level);
                sender.sendMessage(ChatColor.GREEN + "Added enchant " + enchantId + " level " + level + " to tool!");
                return true;

            case "help":
                sendHelp(sender);
                return true;

            default:
                sender.sendMessage(ChatColor.RED + "Unknown command. Type /genstools help for help.");
                return true;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== GensTools Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/genstools give <player> <tool> " + ChatColor.GRAY + "- Give a tool to a player");
        sender.sendMessage(ChatColor.YELLOW + "/genstools enchant <enchant> <level> " + ChatColor.GRAY + "- Add an enchant to held tool");
        sender.sendMessage(ChatColor.YELLOW + "/genstools help " + ChatColor.GRAY + "- Show this help message");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subcommands = Arrays.asList("give", "enchant", "help");
            return filterStartsWith(subcommands, args[0]);
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("give")) {
                return null; // Return player names
            } else if (args[0].equalsIgnoreCase("enchant")) {
                // Get all enchant IDs
                return filterStartsWith(
                        new ArrayList<>(plugin.getToolManager().getAllEnchantIds()),
                        args[1]
                );
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("give")) {
                // Get all tool IDs
                return filterStartsWith(
                        new ArrayList<>(plugin.getToolManager().getAllToolIds()),
                        args[2]
                );
            } else if (args[0].equalsIgnoreCase("enchant")) {
                // For enchant levels, suggest 1-5
                List<String> levels = Arrays.asList("1", "2", "3", "4", "5");
                return filterStartsWith(levels, args[2]);
            }
        }

        return completions;
    }

    private List<String> filterStartsWith(List<String> list, String prefix) {
        return list.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }
}
