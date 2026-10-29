package me.astrax.race;

import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.logging.Logger;

/** Hồi máu bản thân và (tùy chọn) người chơi xung quanh, kèm giải trừ hiệu ứng xấu. */
public final class HealSkill extends Skill {

    private final double amount;
    private final double radius;
    private final List<String> cleanse;

    public HealSkill(String id, ConfigurationSection s, Logger log) {
        super(id, s, log);
        amount = params.getDouble("amount", 6.0);
        radius = params.getDouble("radius", 0.0);
        cleanse = params.getStringList("cleanse");
    }

    @Override
    public boolean execute(SkillManager sm, Player caster, double power) {
        double amt = amount * power;
        heal(caster, amt);
        if (radius > 0) {
            for (Entity e : caster.getNearbyEntities(radius, radius, radius)) {
                if (!(e instanceof Player p) || p.getGameMode() == GameMode.SPECTATOR) continue;
                heal(p, amt);
                p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 2, 0), 3, 0.3, 0.3, 0.3, 0);
            }
        }
        return true;
    }

    private void heal(Player p, double amt) {
        Attribute a = RaceManager.attribute("max_health");
        AttributeInstance inst = a == null ? null : p.getAttribute(a);
        double max = inst == null ? 20.0 : inst.getValue();
        p.setHealth(Math.min(max, p.getHealth() + amt));
        for (String n : cleanse) {
            PotionEffectType t = EffectSpec.potion(n);
            if (t != null) p.removePotionEffect(t);
        }
    }
}
