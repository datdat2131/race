package me.astrax.race;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Một buff của chủng tộc (đọc từ races.yml, mục buffs). Buff tự động hoạt động khi ĐIỀU KIỆN đúng và người chơi
 * đã đạt cấp mở khóa – không cần bấm gì cả:
 * <pre>
 * buffs:
 *   sea-blessing:
 *     name: '&bPhước lành đại dương'
 *     description: ['Dưới nước: thở được, bơi nhanh.']
 *     condition: IN_WATER            # ALWAYS IN_WATER UNDERGROUND NIGHT DAY LOW_HEALTH FULL_HEALTH IN_NETHER IN_END
 *                                    # IN_RAIN SNEAKING SPRINTING ON_FIRE HIGH_ALTITUDE
 *     threshold: 0.35                # LOW_HEALTH/FULL_HEALTH: tỉ lệ máu; UNDERGROUND: Y tối đa; HIGH_ALTITUDE: Y tối thiểu
 *     unlock-level: 1
 *     effects: [{type: WATER_BREATHING, duration: 40, amplifier: 0}]   # potion áp liên tục khi điều kiện đúng
 *     damage-multiplier: 1.2         # nhân sát thương GÂY RA
 *     defense-multiplier: 0.9        # nhân sát thương NHẬN VÀO (0.9 = giảm 10%)
 *     regen: 0.5                     # HP hồi mỗi giây
 *     on-kill: {heal: 2.0, effects: [{type: STRENGTH, duration: 100, amplifier: 0}]}
 *     on-hurt: {chance: 0.25, effects: [{type: RESISTANCE, duration: 80, amplifier: 1}]}
 * </pre>
 */
public final class BuffDef {

    public enum Condition {
        ALWAYS, IN_WATER, UNDERGROUND, NIGHT, DAY, LOW_HEALTH, FULL_HEALTH, IN_NETHER, IN_END, IN_RAIN,
        SNEAKING, SPRINTING, ON_FIRE, HIGH_ALTITUDE
    }

    private final String id;
    private final String name;
    private final List<String> description;
    private final Condition condition;
    private final double threshold;
    private final int unlockLevel;
    private final List<EffectSpec> effects;
    private final double damageMultiplier;
    private final double defenseMultiplier;
    private final double regen;
    private final double onKillHeal;
    private final List<EffectSpec> onKillEffects;
    private final double onHurtChance;
    private final List<EffectSpec> onHurtEffects;

    public BuffDef(String id, ConfigurationSection s, java.util.logging.Logger log) {
        this.id = id;
        this.name = s.getString("name", id);
        this.description = s.getStringList("description");
        Condition c;
        try {
            c = Condition.valueOf(s.getString("condition", "ALWAYS").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warning("Buff '" + id + "': condition không hợp lệ (" + s.getString("condition") + "), dùng ALWAYS.");
            c = Condition.ALWAYS;
        }
        this.condition = c;
        double defThreshold = switch (c) {
            case UNDERGROUND -> 50.0;
            case LOW_HEALTH -> 0.35;
            case FULL_HEALTH -> 0.9;
            case HIGH_ALTITUDE -> 120.0;
            default -> 0.0;
        };
        this.threshold = s.getDouble("threshold", defThreshold);
        this.unlockLevel = Math.max(1, s.getInt("unlock-level", 1));
        this.effects = EffectSpec.parse(s.getList("effects"));
        this.damageMultiplier = s.getDouble("damage-multiplier", 1.0);
        this.defenseMultiplier = s.getDouble("defense-multiplier", 1.0);
        this.regen = s.getDouble("regen", 0.0);
        this.onKillHeal = s.getDouble("on-kill.heal", 0.0);
        this.onKillEffects = EffectSpec.parse(s.getList("on-kill.effects"));
        this.onHurtChance = s.getDouble("on-hurt.chance", 0.0);
        this.onHurtEffects = EffectSpec.parse(s.getList("on-hurt.effects"));
    }

    public String id() { return id; }
    public String name() { return name; }
    public List<String> description() { return Collections.unmodifiableList(description); }
    public Condition condition() { return condition; }
    public double threshold() { return threshold; }
    public int unlockLevel() { return unlockLevel; }
    public List<EffectSpec> effects() { return effects; }
    public double damageMultiplier() { return damageMultiplier; }
    public double defenseMultiplier() { return defenseMultiplier; }
    public double regen() { return regen; }
    public double onKillHeal() { return onKillHeal; }
    public List<EffectSpec> onKillEffects() { return onKillEffects; }
    public double onHurtChance() { return onHurtChance; }
    public List<EffectSpec> onHurtEffects() { return onHurtEffects; }
}
