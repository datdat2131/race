package me.astrax.race;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.logging.Logger;

/**
 * Sát thương diện rộng quanh người dùng.
 * params: radius, damage (MỖI ĐỢT), waves (số đợt), wave-interval (tick giữa các đợt), follow (đợt sau bám theo người dùng),
 * fire-ticks, knockback, lift, pull (kéo vào tâm thay vì đẩy ra), lightning (hiệu ứng sét tại mục tiêu),
 * affect-players, affect-mobs, effects (hiệu ứng potion lên mục tiêu).
 */
public final class AoeDamageSkill extends Skill {

    private final double radius;
    private final double damage;
    private final int waves;
    private final int waveInterval;
    private final boolean follow;
    private final int fireTicks;
    private final double knockback;
    private final double lift;
    private final boolean pull;
    private final boolean lightning;
    private final boolean affectPlayers;
    private final boolean affectMobs;
    private final List<EffectSpec> targetEffects;

    public AoeDamageSkill(String id, ConfigurationSection s, Logger log) {
        super(id, s, log);
        radius = params.getDouble("radius", 5.0);
        damage = params.getDouble("damage", 6.0);
        waves = Math.max(1, Math.min(12, params.getInt("waves", 1)));
        waveInterval = Math.max(1, params.getInt("wave-interval", 6));
        follow = params.getBoolean("follow", false);
        fireTicks = params.getInt("fire-ticks", 0);
        knockback = params.getDouble("knockback", 0.0);
        lift = params.getDouble("lift", knockback > 0 ? 0.35 : 0.0);
        pull = params.getBoolean("pull", false);
        lightning = params.getBoolean("lightning", false);
        affectPlayers = params.getBoolean("affect-players", true);
        affectMobs = params.getBoolean("affect-mobs", true);
        targetEffects = EffectSpec.parse(params.getList("effects"));
    }

    @Override
    public boolean execute(SkillManager sm, Player caster, double power) {
        final Location origin = caster.getLocation().clone();
        final double dmg = damage * power;
        strike(sm, caster, origin, dmg);
        for (int i = 1; i < waves; i++) {
            Bukkit.getScheduler().runTaskLater(sm.plugin(), () -> {
                if (!caster.isOnline() || caster.isDead()) return;
                Location c = follow ? caster.getLocation() : origin;
                if (c.getWorld() != caster.getWorld()) return;
                strike(sm, caster, c, dmg);
            }, (long) i * waveInterval);
        }
        return true;
    }

    private void strike(SkillManager sm, Player caster, Location center, double dmg) {
        World w = center.getWorld();
        if (w == null) return;
        for (Entity e : w.getNearbyEntities(center, radius, radius, radius)) {
            if (!(e instanceof LivingEntity target)) continue;
            if (e.getLocation().distanceSquared(center) > radius * radius) continue;
            if (!canHit(caster, target, affectPlayers, affectMobs)) continue;

            sm.dealDamage(caster, target, dmg, tags);
            if (fireTicks > 0) target.setFireTicks(fireTicks);
            for (EffectSpec spec : targetEffects) spec.apply(target);

            if (knockback > 0 || lift > 0) {
                Vector v = pull
                        ? center.toVector().subtract(target.getLocation().toVector())
                        : target.getLocation().toVector().subtract(center.toVector());
                v.setY(0);
                if (v.lengthSquared() > 1.0E-6) v.normalize();
                v.multiply(knockback).setY(lift);
                target.setVelocity(v);
            }
            if (lightning) w.strikeLightningEffect(target.getLocation());
            effects.impact(target.getLocation());
        }
    }
}
