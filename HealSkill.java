package me.astrax.race;

import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.logging.Logger;

/**
 * Hồi máu bản thân và người chơi xung quanh.
 * params: amount, radius (0 = chỉ bản thân), cleanse (hiệu ứng xấu cần xóa), effects (buff áp lên người được hồi).
 */
public final class HealSkill extends Skill {

    private final double amount;
    private final double radius;
    private final List<String> cleanse;
    private final List<EffectSpec> buffs;

    public HealSkill(String id, ConfigurationSection s, Logger log) {
        super(id, s, log);
        amount = params.getDouble("amount", 6.0);
        radius = params.getDouble("radius", 0.0);
        cleanse = params.getStringList("cleanse");
        buffs = EffectSpec.parse(params.getList("effects"));
    }

    @Override
    public boolean execute(SkillManager sm, Player caster, double power) {
        double amt = amount * power;
        heal(caster, amt);
        if (radius > 0) {
            for (Entity e : caster.getNearbyEntities(radius, radius, radius)) {
                if (!(e instanceof Player p) || p.getGameMode() == GameMode.SPECTATOR) continue;
                if (p.getLocation().distanceSquared(caster.getLocation()) > radius * radius) continue;
                heal(p, amt);
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
        for (EffectSpec spec : buffs) spec.apply(p);
        effects.impact(p.getLocation());
    }
}
