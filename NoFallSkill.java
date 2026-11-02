package me.astrax.race;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.logging.Logger;

/**
 * Miễn sát thương rơi trong một khoảng thời gian.
 * params: duration-ticks, slow-falling, effects (buff thêm lên bản thân).
 */
public final class NoFallSkill extends Skill {

    private final int durationTicks;
    private final boolean slowFalling;
    private final List<EffectSpec> buffs;

    public NoFallSkill(String id, ConfigurationSection s, Logger log) {
        super(id, s, log);
        durationTicks = params.getInt("duration-ticks", 100);
        slowFalling = params.getBoolean("slow-falling", true);
        buffs = EffectSpec.parse(params.getList("effects"));
    }

    @Override
    public boolean execute(SkillManager sm, Player caster, double power) {
        sm.grantNoFall(caster.getUniqueId(), durationTicks);
        if (slowFalling) {
            PotionEffectType t = EffectSpec.potion("slow_falling");
            if (t != null) {
                caster.addPotionEffect(new PotionEffect(t, durationTicks, 0, false, false, true));
            }
        }
        for (EffectSpec spec : buffs) spec.apply(caster);
        caster.setFallDistance(0f);
        return true;
    }
}
