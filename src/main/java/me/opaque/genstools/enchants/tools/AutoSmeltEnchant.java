package me.opaque.genstools.enchants.tools;

import me.opaque.genstools.enchants.CustomEnchant;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

public class AutoSmeltEnchant extends CustomEnchant {

    public AutoSmeltEnchant(String id, String displayName, String description, int maxLevel, boolean isTreasure, CurrencyType currencyType) {
        super(id, displayName, description, maxLevel, isTreasure, currencyType);
    }

    // Constructor for backward compatibility
    public AutoSmeltEnchant(String id, String displayName, String description, int maxLevel, boolean isTreasure) {
        this(id, displayName, description, maxLevel, isTreasure, CurrencyType.SHARDS);
    }

    @Override
    public boolean handleEffect(Event event, int level) {
        if (!(event instanceof BlockBreakEvent blockBreakEvent)) {
            return false;
        }

        Block block = blockBreakEvent.getBlock();
        Player player = blockBreakEvent.getPlayer();

        // Get smelted version of the drop
        ItemStack smeltedDrop = getSmeltedDrop(block.getType(), level);
        if (smeltedDrop != null) {
            // Cancel the default drops
            blockBreakEvent.setDropItems(false);

            // Drop at block location if inventory is full
            if (player.getInventory().firstEmpty() == -1) {
                player.getWorld().dropItemNaturally(block.getLocation(), smeltedDrop);
            } else {
                player.getInventory().addItem(smeltedDrop);
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean canHandleEvent(Class<? extends Event> eventClass) {
        return eventClass == BlockBreakEvent.class;
    }

    private ItemStack getSmeltedDrop(Material blockType, int level) {
        return switch (blockType) {
            case IRON_ORE -> new ItemStack(Material.IRON_INGOT, 1 + (level > 3 ? 1 : 0));
            case GOLD_ORE -> new ItemStack(Material.GOLD_INGOT, 1 + (level > 3 ? 1 : 0));
            case COPPER_ORE -> new ItemStack(Material.COPPER_INGOT, 1 + (level > 2 ? 1 : 0));
            case ANCIENT_DEBRIS -> new ItemStack(Material.NETHERITE_SCRAP, 1);
            case SAND -> new ItemStack(Material.GLASS, 1);
            case COBBLESTONE -> new ItemStack(Material.STONE, 1);
            case CLAY -> new ItemStack(Material.TERRACOTTA, 1);
            case NETHERRACK -> new ItemStack(Material.NETHER_BRICK, 1);
            case CACTUS -> new ItemStack(Material.GREEN_DYE, 1 + (level > 2 ? 1 : 0));
            case RAW_IRON_BLOCK -> new ItemStack(Material.IRON_BLOCK, 1);
            case RAW_GOLD_BLOCK -> new ItemStack(Material.GOLD_BLOCK, 1);
            case RAW_COPPER_BLOCK -> new ItemStack(Material.COPPER_BLOCK, 1);
            case DEEPSLATE_IRON_ORE -> new ItemStack(Material.IRON_INGOT, 1 + (level > 2 ? 1 : 0));
            case DEEPSLATE_GOLD_ORE -> new ItemStack(Material.GOLD_INGOT, 1 + (level > 2 ? 1 : 0));
            case DEEPSLATE_COPPER_ORE -> new ItemStack(Material.COPPER_INGOT, 1 + (level > 2 ? 1 : 0));
            default -> null;
        };
    }
}