package me.opaque.genstools.enchants.weapons;

import me.opaque.genstools.enchants.CustomEnchant;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.Random;

public class CriticalStrikeEnchant extends CustomEnchant {
    private final Random random = new Random();

    public CriticalStrikeEnchant(String id, String displayName, String description, int maxLevel, boolean isTreasure) {
        super(id, displayName, description, maxLevel, isTreasure);
    }

    @Override
    public boolean handleEffect(Event event, int level) {
        if (!(event instanceof EntityDamageByEntityEvent damageEvent)) {
            return false;
        }

        if (!(damageEvent.getDamager() instanceof Player)) {
            return false;
        }

        double critChance = 0.05 * level; // 5% per level
        double critMultiplier = 1.5 + (0.1 * level); // 1.5x base + 0.1x per level

        if (random.nextDouble() <= critChance) {
            // Apply critical damage
            double originalDamage = damageEvent.getDamage();
            double newDamage = originalDamage * critMultiplier;
            damageEvent.setDamage(newDamage);

            // Visual effect
            Player player = (Player) damageEvent.getDamager();
            player.sendMessage("§c§lCRITICAL HIT! §7(" + String.format("%.1f", newDamage) + " damage)");

            return true;
        }

        return false;
    }

    @Override
    public boolean canHandleEvent(Class<? extends Event> eventClass) {
        return EntityDamageByEntityEvent.class.isAssignableFrom(eventClass);
    }
}