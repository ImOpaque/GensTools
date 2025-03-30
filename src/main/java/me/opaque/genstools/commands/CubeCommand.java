package me.opaque.genstools.commands;

import me.opaque.genstools.GensTools;
import me.opaque.genstools.manager.EnchantmentCubeManager;
import me.opaque.genstools.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CubeCommand {
    private final GensTools plugin;
    private final EnchantmentCubeManager cubeManager;

    public CubeCommand(GensTools plugin) {
        this.plugin = plugin;
        this.cubeManager = plugin.getEnchantmentCubeManager();
    }

    /**
     * Handle the givecube command
     */
    public boolean handleGiveCube(CommandSender sender, String[] args) {
        if (args.length < 6) {
            sendUsage(sender);
            return true;
        }

        // Parse arguments
        Player targetPlayer = Bukkit.getPlayer(args[1]);
        if (targetPlayer == null) {
            Utils.sendMessage(sender, "&cPlayer " + args[1] + " not found.");
            return true;
        }

        int tier, successRate, amount;
        double boost;

        try {
            tier = Integer.parseInt(args[2]);
            successRate = Integer.parseInt(args[4]);
            boost = Double.parseDouble(args[5]);
            amount = args.length > 6 ? Integer.parseInt(args[6]) : 1;
        } catch (NumberFormatException e) {
            Utils.sendMessage(sender, "&cInvalid number format. Please use numbers for tier, success rate, boost, and amount.");
            return true;
        }

        String enchantId = args[3].toLowerCase();

        // Give the cube to the player
        boolean success = cubeManager.giveCube(targetPlayer, tier, enchantId, successRate, boost, amount);

        if (success) {
            Utils.sendMessage(sender, "&aGave " + targetPlayer.getName() + " " + amount + " enchantment cube(s).");

            if (sender != targetPlayer) {
                Utils.sendMessage(targetPlayer, "&aYou received " + amount + " enchantment cube(s).");
            }
        } else {
            Utils.sendMessage(sender, "&cFailed to give enchantment cube. Check the tier and enchantment ID.");
        }

        return true;
    }

    /**
     * Send command usage to sender
     */
    private void sendUsage(CommandSender sender) {
        Utils.sendMessage(sender, "&cUsage: /genstools givecube <player> <tier> <enchant> <success> <boost> [amount]");
    }
}