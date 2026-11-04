package me.astrax.race;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/** Định nghĩa một chủng tộc, đọc hoàn toàn từ races.yml: chỉ số, nội tại (passives), buff theo điều kiện, cosmetic. */
public final class Race {

    private final String id;
    private final String displayName;
    private final Material icon;
    private final int guiSlot;
    private final String rarity;
    private final List<String> description;
    private final List<String> strengths;
    private final List<String> weaknesses;

    private final double health, speed, strength, jump, knockback, critical;
    private final double healthPerLevel, speedPerLevel, strengthPerLevel;

    private final double expBonus, vanillaExpBonus;
    private final double meleeMultiplier, bowMultiplier;
    private final List<EffectSpec> onHit;
    private final Set<String> immuneEffects = new HashSet<>();
    private final Set<String> damageTags = new HashSet<>();
    private final Map<String, Double> damageTaken = new HashMap<>();
    private final Map<String, Double> weakTo = new HashMap<>();
    private final List<BuffDef> buffs = new ArrayList<>();
    private final CosmeticDef cosmetic;

    public Race(String id, ConfigurationSection s, Logger log) {
        this.id = id;
        this.displayName = s.getString("display-name", id);
        Material m = Material.matchMaterial(s.getString("icon", "PLAYER_HEAD"));
        this.icon = m != null ? m : Material.PLAYER_HEAD;
        this.guiSlot = s.getInt("gui-slot", -1);
        this.rarity = s.getString("rarity", "COMMON").toUpperCase(Locale.ROOT);
        this.description = s.getStringList("description");
        this.strengths = s.getStringList("strengths");
        this.weaknesses = s.getStringList("weaknesses");

        health = s.getDouble("stats.health", 20.0);
        speed = s.getDouble("stats.speed", 1.0);
        strength = s.getDouble("stats.strength", 1.0);
        jump = s.getDouble("stats.jump", 0.0);
        knockback = s.getDouble("stats.knockback-resistance", 0.0);
        critical = s.getDouble("stats.critical-chance", 0.0);
        healthPerLevel = s.getDouble("growth.health", 0.0);
        speedPerLevel = s.getDouble("growth.speed", 0.0);
        strengthPerLevel = s.getDouble("growth.strength", 0.0);

        expBonus = s.getDouble("passives.exp-bonus", 0.0);
        vanillaExpBonus = s.getDouble("passives.vanilla-exp-bonus", 0.0);
        meleeMultiplier = s.getDouble("passives.melee-damage-multiplier", 1.0);
        bowMultiplier = s.getDouble("passives.bow-damage-multiplier", 1.0);
        onHit = EffectSpec.parse(s.getList("passives.on-hit"));

        for (String e : s.getStringList("passives.immune-effects")) immuneEffects.add(e.toUpperCase(Locale.ROOT));
        for (String t : s.getStringList("passives.damage-tags")) damageTags.add(t.toUpperCase(Locale.ROOT));
        readMap(s.getConfigurationSection("passives.damage-taken"), damageTaken);
        readMap(s.getConfigurationSection("passives.weak-to"), weakTo);

        ConfigurationSection bs = s.getConfigurationSection("buffs");
        if (bs != null) {
            for (String key : bs.getKeys(false)) {
                ConfigurationSection b = bs.getConfigurationSection(key);
                if (b != null) buffs.add(new BuffDef(key.toLowerCase(Locale.ROOT), b, log));
            }
        }

        ConfigurationSection cs = s.getConfigurationSection("cosmetic");
        cosmetic = cs == null ? null : new CosmeticDef(cs, log);
    }

    private static void readMap(ConfigurationSection sec, Map<String, Double> out) {
        if (sec == null) return;
        for (String k : sec.getKeys(false)) out.put(k.toUpperCase(Locale.ROOT), sec.getDouble(k));
    }

    public double damageTakenMultiplier(EntityDamageEvent.DamageCause cause) {
        return damageTaken.getOrDefault(cause.name(), 1.0);
    }

    public double weakness(String tag) {
        return weakTo.getOrDefault(tag, 1.0);
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public Material getIcon() { return icon; }
    public int getGuiSlot() { return guiSlot; }
    public String getRarity() { return rarity; }
    public List<String> getDescription() { return description; }
    public List<String> getStrengths() { return strengths; }
    public List<String> getWeaknesses() { return weaknesses; }

    public double getHealth() { return health; }
    public double getSpeed() { return speed; }
    public double getStrength() { return strength; }
    public double getJump() { return jump; }
    public double getKnockback() { return knockback; }
    public double getCritical() { return critical; }
    public double getHealthPerLevel() { return healthPerLevel; }
    public double getSpeedPerLevel() { return speedPerLevel; }
    public double getStrengthPerLevel() { return strengthPerLevel; }

    public double getExpBonus() { return expBonus; }
    public double getVanillaExpBonus() { return vanillaExpBonus; }
    public double getMeleeMultiplier() { return meleeMultiplier; }
    public double getBowMultiplier() { return bowMultiplier; }
    public List<EffectSpec> getOnHit() { return onHit; }
    public Set<String> getImmuneEffects() { return immuneEffects; }
    public Set<String> getDamageTags() { return Collections.unmodifiableSet(damageTags); }
    public CosmeticDef getCosmetic() { return cosmetic; }

    public List<BuffDef> getBuffs() { return Collections.unmodifiableList(buffs); }
}
