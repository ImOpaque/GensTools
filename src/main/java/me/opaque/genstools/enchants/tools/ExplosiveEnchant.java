package me.opaque.genstools.enchants.tools;

import me.opaque.genstools.enchants.CustomEnchant;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ExplosiveEnchant extends CustomEnchant {
    private final Random random = new Random();

    public ExplosiveEnchant(String id, String displayName, String description, int maxLevel, boolean isTreasure, CurrencyType currencyType) {
        super(id, displayName, description, maxLevel, isTreasure, currencyType);
    }

    // Constructor for backward compatibility
    public ExplosiveEnchant(String id, String displayName, String description, int maxLevel, boolean isTreasure) {
        this(id, displayName, description, maxLevel, isTreasure, CurrencyType.SHARDS);
    }

    @Override
    public boolean handleEffect(Event event, int level) {
        if (!(event instanceof BlockBreakEvent blockBreakEvent)) {
            return false;
        }

        Player player = blockBreakEvent.getPlayer();
        Block block = blockBreakEvent.getBlock();

        // Calculate explosion chance and radius based on level
        double chance = 0.1 + (level * 0.1); // 10% base + 10% per level
        int radius = 1 + (level / 2); // 1 block base radius + 1 for every 2 levels

        // Check if explosion triggers
        if (random.nextDouble() <= chance) {
            // Get blocks in the radius
            List<Block> blocksToBreak = getBlocksInRadius(block, radius);

            // Break each block and give drops to the player
            for (Block nearbyBlock : blocksToBreak) {
                // Skip air blocks, bedrock, etc.
                if (nearbyBlock.getType() == Material.AIR ||
                        nearbyBlock.getType() == Material.BEDROCK ||
                        nearbyBlock.getType() == Material.BARRIER) {
                    continue;
                }

                // Skip the original block as it's already being broken
                if (nearbyBlock.equals(block)) {
                    continue;
                }

                // Get block drops and add them to the player's inventory
                for (ItemStack drop : nearbyBlock.getDrops(player.getInventory().getItemInMainHand())) {
                    // Drop items at original block location if inventory is full
                    if (player.getInventory().firstEmpty() == -1) {
                        player.getWorld().dropItemNaturally(block.getLocation(), drop);
                    } else {
                        player.getInventory().addItem(drop);
                    }
                }

                // Break the block
                nearbyBlock.setType(Material.AIR);
            }

            // Visual effect
            player.getWorld().createExplosion(block.getLocation(), 0F, false, false);
            return true;
        }

        return false;
    }

    @Override
    public boolean canHandleEvent(Class<? extends Event> eventClass) {
        return eventClass == BlockBreakEvent.class;
    }

    private List<Block> getBlocksInRadius(Block center, int radius) {
        List<Block> blocks = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    // Skip blocks too far away (for a more spherical explosion)
                    if (x*x + y*y + z*z > radius*radius) {
                        continue;
                    }

                    Block relative = center.getRelative(x, y, z);
                    blocks.add(relative);
                }
            }
        }
        return blocks;
    }
}