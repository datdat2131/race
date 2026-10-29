package me.astrax.race;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

/** Lớp cơ sở của mọi skill. Thêm loại skill mới: kế thừa lớp này rồi đăng ký trong SkillManager#buildSkill. */
public abstract class Skill {

    protected final String id;
    protected final String name;
    protected final List<String> description;
    protected final int unlockLevel;
    protected final double cooldown;
    protected final double energyCost;
    protected final String trigger;
    protected final Set<String> tags = new HashSet<>();
    protected final ConfigurationSection params;
    protected final SkillEffects effects;

    protected Skill(String id, ConfigurationSection s, Logger log) {
        this.id = id;
        this.name = s.getString("name", id);
        this.description = s.getStringList("description");
        this.unlockLevel = s.getInt("unlock-level", 1);
        this.cooldown = s.getDouble("cooldown", 10);
        this.energyCost = s.getDouble("energy-cost", 0);
        this.trigger = s.getString("trigger", "COMMAND").toUpperCase(Locale.ROOT);
        for (String t : s.getStringList("tags")) tags.add(t.toUpperCase(Locale.ROOT));
        ConfigurationSection p = s.getConfigurationSection("params");
        this.params = p != null ? p : new MemoryConfiguration();
        this.effects = new SkillEffects(s.getConfigurationSection("effects"), log);
    }

    /**
     * Thực thi skill.
     *
     * @param power hệ số sức mạnh (nâng cấp Skill Power + cấp race)
     * @return false nếu skill không thể dùng (không tốn cooldown/năng lượng)
     */
    public abstract boolean execute(SkillManager sm, Player caster, double power);

    public String getId() { return id; }
    public String getName() { return name; }
    public List<String> getDescription() { return description; }
    public int getUnlockLevel() { return unlockLevel; }
    public double getCooldown() { return cooldown; }
    public double getEnergyCost() { return energyCost; }
    public String getTrigger() { return trigger; }
    public Set<String> getTags() { return tags; }
    public SkillEffects getEffects() { return effects; }
}
