package me.astrax.race;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.logging.Logger;

/** Lao/bật nhảy về hướng đang nhìn. */
public final class DashSkill extends Skill {

    private final double forward;
    private final double up;

    public DashSkill(String id, ConfigurationSection s, Logger log) {
        super(id, s, log);
        forward = params.getDouble("forward", 1.5);
        up = params.getDouble("up", 0.3);
    }

    @Override
    public boolean execute(SkillManager sm, Player caster, double power) {
        Vector dir = caster.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 1.0E-4) {
            dir = new Vector(0, 0, 1);
        } else {
            dir.normalize();
        }
        dir.multiply(forward * (1.0 + (power - 1.0) * 0.5));
        dir.setY(up);
        caster.setVelocity(dir);
        return true;
    }
}
