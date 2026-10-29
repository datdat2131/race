package me.astrax.race;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.logging.Logger;

/** Sát thương diện rộng quanh người dùng: damage, fire, knockback, hiệu ứng lên mục tiêu. */
public final class AoeDamageSkill extends Skill {

    private final double radius;
    private final double damage;
    private final int fireTicks;
    private final double knockback;
    private final boolean affectPlayers;
    private final boolean affectMobs;
    private final List<EffectSpec> targetEffects;

    public AoeDamageSkill(String id, ConfigurationSection s, Logger log) {
        super(id, s, log);
        radius = params.getDouble("radius", 5.0);
        damage = params.getDouble("damage", 6.0);
        fireTicks = params.getInt("fire-ticks", 0);
        knockback = params.getDouble("knockback", 0.0);
        affectPlayers = params.getBoolean("affect-players", true);
        affectMobs = params.getBoolean("affect-mobs", true);
        targetEffects = EffectSpec.parse(params.getList("effects"));
    }

    @Override
    public boolean execute(SkillManager sm, Player caster, double power) {
        Location center = caster.getLocation();
        double dmg = damage * power;
        for (Entity e : caster.getNearbyEntities(radius, radius, radius)) {
            if (!(e instanceof LivingEntity target) || e instanceof ArmorStand) continue;
            if (e.getLocation().distanceSquared(center) > radius * radius) continue;

            if (target instanceof Player tp) {
                if (!affectPlayers || !caster.getWorld().getPVP()) continue;
                if (tp.getGameMode() == GameMode.CREATIVE || tp.getGameMode() == GameMode.SPECTATOR) continue;
            } else if (!affectMobs || !(target instanceof Enemy)) {
                continue;
            }

            sm.dealDamage(caster, target, dmg, tags);
            if (fireTicks > 0) target.setFireTicks(fireTicks);
            for (EffectSpec spec : targetEffects) spec.apply(target);
            if (knockback > 0) {
                Vector v = target.getLocation().toVector().subtract(center.toVector());
                v.setY(0);
                if (v.lengthSquared() > 0) v.normalize();
                v.multiply(knockback).setY(0.35);
                target.setVelocity(v);
            }
        }
        return true;
    }
}
