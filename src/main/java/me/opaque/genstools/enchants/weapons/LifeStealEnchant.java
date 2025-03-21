package me.opaque.genstools.enchants.weapons;

import me.opaque.genstools.enchants.CustomEnchant;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class LifeStealEnchant extends CustomEnchant {

    public LifeStealEnchant(String id, String displayName, String description, int maxLevel, boolean isTreasure) {
        super(id, displayName, description, maxLevel, isTreasure);
    }

    @Override
    public boolean handleEffect(Event event, int level) {
        if (!(event instanceof EntityDamageByEntityEvent damageEvent)) {
            return false;
        }

        if (!(damageEvent.getDamager() instanceof Player player)) {
            return false;
        }

        if (!(damageEvent.getEntity() instanceof LivingEntity)) {
            return false;
        }

        // Calculate life steal amount (percentage of damage dealt)
        double healPercentage = 0.03 * level; // 3% per level
        double damageDealt = damageEvent.getFinalDamage();
        double healAmount = damageDealt * healPercentage;

        // Apply healing
        double maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        double newHealth = Math.min(player.getHealth() + healAmount, maxHealth);
        player.setHealth(newHealth);

        // Visual effect (only if significant healing occurred)
        if (healAmount >= 1.0) {
            player.sendMessage("§a§lLIFE STEAL! §7Healed for " + String.format("%.1f", healAmount) + " health");
        }

        return true;
    }

    @Override
    public boolean canHandleEvent(Class<? extends Event> eventClass) {
        return EntityDamageByEntityEvent.class.isAssignableFrom(eventClass);
    }
}