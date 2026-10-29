package me.astrax.race;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.logging.Logger;

/** Tự buff bản thân bằng danh sách hiệu ứng (và hồi máu tùy chọn). */
public final class SelfBuffSkill extends Skill {

    private final List<EffectSpec> buffs;
    private final double heal;

    public SelfBuffSkill(String id, ConfigurationSection s, Logger log) {
        super(id, s, log);
        buffs = EffectSpec.parse(params.getList("effects"));
        heal = params.getDouble("heal", 0.0);
    }

    @Override
    public boolean execute(SkillManager sm, Player caster, double power) {
        for (EffectSpec spec : buffs) spec.apply(caster);
        if (heal > 0) {
            Attribute a = RaceManager.attribute("max_health");
            AttributeInstance inst = a == null ? null : caster.getAttribute(a);
            double max = inst == null ? 20.0 : inst.getValue();
            caster.setHealth(Math.min(max, caster.getHealth() + heal * power));
        }
        return true;
    }
}
