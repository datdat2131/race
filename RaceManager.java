package me.astrax.race;

import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/** Quản lý chủng tộc, dữ liệu người chơi (cache), cấp độ, nâng cấp và chỉ số. */
public final class RaceManager {

    public static final List<String> UPGRADES =
            List.of("strength", "defense", "speed", "health", "critical", "skill_power");

    /** {tên attribute (không prefix), key modifier} */
    private static final String[][] ATTRS = {
            {"max_health", "race_health"},
            {"movement_speed", "race_speed"},
            {"jump_strength", "race_jump"},
            {"knockback_resistance", "race_kb"}
    };

    public enum ChooseResult { OK, SAME, NO_CHANGES, NO_DATA }

    public enum UpgradeResult { OK, NO_RACE, MAXED, NO_POINTS, INVALID }

    private final AstraXRace plugin;
    private final Map<String, Race> races = new LinkedHashMap<>();
    private final Map<UUID, PlayerRaceData> cache = new ConcurrentHashMap<>();

    public RaceManager(AstraXRace plugin) {
        this.plugin = plugin;
    }

    private ConfigurationSection cfg() {
        return plugin.configs().config();
    }

    // ---------------- Race ----------------
    public void loadRaces() {
        Map<String, Race> loaded = new LinkedHashMap<>();
        ConfigurationSection sec = plugin.configs().races().getConfigurationSection("races");
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                ConfigurationSection rs = sec.getConfigurationSection(id);
                if (rs == null) continue;
                try {
                    String rid = id.toLowerCase(Locale.ROOT);
                    loaded.put(rid, new Race(rid, rs, plugin.skills(), plugin.getLogger()));
                } catch (Exception e) {
                    plugin.getLogger().warning("Không thể nạp race '" + id + "': " + e.getMessage());
                }
            }
        }
        races.clear();
        races.putAll(loaded);
        plugin.getLogger().info("Đã nạp " + races.size() + " chủng tộc.");
    }

    public Race getRace(String id) {
        return id == null ? null : races.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<Race> getRaces() {
        return races.values();
    }

    public Race raceOf(PlayerRaceData d) {
        return d == null ? null : getRace(d.getRace());
    }

    public Race raceOf(Player p) {
        return raceOf(data(p));
    }

    // ---------------- Dữ liệu người chơi ----------------
    public PlayerRaceData data(Player p) {
        return cache.get(p.getUniqueId());
    }

    private PlayerRaceData newData(UUID id, String name) {
        PlayerRaceData d = new PlayerRaceData(id, name);
        d.setPoints(cfg().getInt("leveling.starting-points", 0));
        d.setDirty(false);
        return d;
    }

    private PlayerRaceData loadOrCreate(UUID id, String name) {
        try {
            PlayerRaceData d = plugin.storage().load(id, name);
            if (d != null) {
                d.setName(name);
                return d;
            }
            return newData(id, name);
        } catch (Exception e) {
            plugin.getLogger().severe("Không thể tải dữ liệu của " + name + ": " + e.getMessage());
            PlayerRaceData d = newData(id, name);
            d.setPersistable(false);   // tránh ghi đè dữ liệu thật bằng dữ liệu rỗng
            return d;
        }
    }

    /** Gọi từ AsyncPlayerPreLoginEvent: tải trước dữ liệu (luồng bất đồng bộ). */
    public void preload(UUID id, String name) {
        if (cache.containsKey(id)) return;
        cache.putIfAbsent(id, loadOrCreate(id, name));
    }

    public PlayerRaceData ensureData(Player p) {
        PlayerRaceData d = cache.get(p.getUniqueId());
        if (d == null) {
            cache.putIfAbsent(p.getUniqueId(), loadOrCreate(p.getUniqueId(), p.getName()));
            d = cache.get(p.getUniqueId());
        }
        return d;
    }

    /** Gắn người chơi: nạp dữ liệu, áp chỉ số, khởi tạo năng lượng. */
    public void attach(Player p) {
        ensureData(p);
        applyAttributes(p);
        plugin.skills().initEnergy(p);
        plugin.cosmetics().refresh(p);
    }

    /** Gỡ người chơi khi thoát: gỡ modifier, lưu dữ liệu, dọn cache. */
    public void detach(Player p) {
        plugin.cosmetics().remove(p);
        clearAttributes(p);
        PlayerRaceData d = cache.remove(p.getUniqueId());
        if (d != null && d.isPersistable()) plugin.storage().saveAsync(d.copy());
        plugin.skills().clear(p.getUniqueId());
    }

    public void save(PlayerRaceData d) {
        d.setDirty(false);
        if (d.isPersistable()) plugin.storage().saveAsync(d.copy());
    }

    public void saveDirty() {
        for (PlayerRaceData d : cache.values()) {
            if (d.isDirty()) save(d);
        }
    }

    public List<PlayerRaceData> snapshots() {
        List<PlayerRaceData> list = new ArrayList<>();
        for (PlayerRaceData d : cache.values()) {
            if (d.isPersistable()) list.add(d.copy());
        }
        return list;
    }

    // ---------------- Cấp độ ----------------
    public int maxLevel() {
        return Math.max(1, cfg().getInt("leveling.max-level", 50));
    }

    public long requiredExp(int level) {
        double base = cfg().getDouble("leveling.exp-base", 100);
        double growth = cfg().getDouble("leveling.exp-growth", 1.12);
        return Math.max(1, Math.round(base * Math.pow(growth, Math.max(0, level - 1))));
    }

    /** Cộng Race EXP, tự động lên cấp. applyBonus: có nhân nội tại exp-bonus của race không. */
    public void addExp(Player p, long amount, boolean applyBonus) {
        PlayerRaceData d = data(p);
        Race r = raceOf(d);
        if (d == null || r == null || amount <= 0) return;
        if (d.getLevel() >= maxLevel()) return;
        if (applyBonus) amount = Math.round(amount * (1.0 + r.getExpBonus()));

        d.setExp(d.getExp() + amount);
        boolean leveled = false;
        while (d.getLevel() < maxLevel() && d.getExp() >= requiredExp(d.getLevel())) {
            d.setExp(d.getExp() - requiredExp(d.getLevel()));
            d.setLevel(d.getLevel() + 1);
            d.setPoints(d.getPoints() + cfg().getInt("leveling.points-per-level", 1));
            onLevelUp(p, d, r);
            leveled = true;
        }
        if (d.getLevel() >= maxLevel()) d.setExp(0);
        if (leveled) applyAttributes(p);
    }

    private void onLevelUp(Player p, PlayerRaceData d, Race r) {
        ConfigManager cm = plugin.configs();
        cm.send(p, "level-up", "level", String.valueOf(d.getLevel()),
                "points", String.valueOf(cfg().getInt("leveling.points-per-level", 1)));
        p.showTitle(Title.title(ConfigManager.color(cm.raw("level-up-title")),
                ConfigManager.color(cm.raw("level-up-subtitle", "level", String.valueOf(d.getLevel())))));
        String snd = cfg().getString("leveling.level-up-sound", "entity.player.levelup");
        if (snd != null && !snd.isEmpty()) p.playSound(p.getLocation(), SkillEffects.soundKey(snd), 1f, 1f);
        for (Skill s : r.getSkills()) {
            if (s.getUnlockLevel() == d.getLevel()) {
                cm.send(p, "skill-unlocked", "skill", s.getName());
            }
        }
    }

    // ---------------- Chọn / đặt lại race ----------------
    public String changesLeft(PlayerRaceData d) {
        int max = cfg().getInt("race.max-changes", 1);
        if (max < 0) return "∞";
        return String.valueOf(Math.max(0, max - (d == null ? 0 : d.getChangesUsed())));
    }

    private void resetProgress(PlayerRaceData d) {
        d.setLevel(1);
        d.setExp(0);
        d.setPoints(cfg().getInt("leveling.starting-points", 0));
        d.clearUpgrades();
    }

    public ChooseResult choose(Player p, Race race, boolean admin) {
        PlayerRaceData d = data(p);
        if (d == null) return ChooseResult.NO_DATA;
        boolean hadRace = d.getRace() != null;
        if (!admin) {
            if (race.getId().equals(d.getRace())) return ChooseResult.SAME;
            if (hadRace) {
                int max = cfg().getInt("race.max-changes", 1);
                if (!p.hasPermission("astraxrace.bypass") && max >= 0 && d.getChangesUsed() >= max) {
                    return ChooseResult.NO_CHANGES;
                }
                d.setChangesUsed(d.getChangesUsed() + 1);
            }
        }
        boolean differs = !race.getId().equals(d.getRace());
        d.setRace(race.getId());
        if (differs && hadRace && cfg().getBoolean("race.reset-progress-on-change", true)) resetProgress(d);
        applyAttributes(p);
        plugin.skills().initEnergy(p);
        plugin.skills().giveSkillItem(p, false);
        plugin.cosmetics().refresh(p);
        save(d);
        return ChooseResult.OK;
    }

    public void resetPlayer(Player p) {
        PlayerRaceData d = ensureData(p);
        d.setRace(null);
        resetProgress(d);
        d.setChangesUsed(0);
        plugin.cosmetics().remove(p);
        clearAttributes(p);
        save(d);
    }

    // ---------------- Nâng cấp ----------------
    public int upgradeMax(String attr) { return cfg().getInt("upgrades." + attr + ".max-level", 10); }
    public int upgradeCost(String attr) { return Math.max(1, cfg().getInt("upgrades." + attr + ".cost", 1)); }
    public double upgradePer(String attr) { return cfg().getDouble("upgrades." + attr + ".per-level", 0.0); }

    public double upgradeBonus(PlayerRaceData d, String attr) {
        return d == null ? 0.0 : d.getUpgrade(attr) * upgradePer(attr);
    }

    public UpgradeResult upgrade(Player p, String attr) {
        if (!UPGRADES.contains(attr)) return UpgradeResult.INVALID;
        PlayerRaceData d = data(p);
        if (d == null || raceOf(d) == null) return UpgradeResult.NO_RACE;
        if (d.getUpgrade(attr) >= upgradeMax(attr)) return UpgradeResult.MAXED;
        int cost = upgradeCost(attr);
        if (d.getPoints() < cost) return UpgradeResult.NO_POINTS;
        d.setPoints(d.getPoints() - cost);
        d.setUpgrade(attr, d.getUpgrade(attr) + 1);
        applyAttributes(p);
        save(d);
        return UpgradeResult.OK;
    }

    // ---------------- Chỉ số ----------------
    public double healthOf(PlayerRaceData d, Race r) {
        return r.getHealth() + (d.getLevel() - 1) * r.getHealthPerLevel() + upgradeBonus(d, "health");
    }

    public double speedOf(PlayerRaceData d, Race r) {
        return r.getSpeed() + (d.getLevel() - 1) * r.getSpeedPerLevel() + upgradeBonus(d, "speed");
    }

    public double strengthOf(PlayerRaceData d, Race r) {
        return r.getStrength() + (d.getLevel() - 1) * r.getStrengthPerLevel() + upgradeBonus(d, "strength");
    }

    public double critOf(PlayerRaceData d, Race r) {
        return Math.min(1.0, r.getCritical() + upgradeBonus(d, "critical"));
    }

    // ---------------- Attribute ----------------
    /** Tìm Attribute theo tên, hỗ trợ cả key mới (1.21.3+) lẫn cũ ("generic."). */
    public static Attribute attribute(String name) {
        Attribute a = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(name));
        if (a == null) a = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic." + name));
        return a;
    }

    public double maxHealth(Player p) {
        Attribute a = attribute("max_health");
        AttributeInstance inst = a == null ? null : p.getAttribute(a);
        return inst == null ? 20.0 : inst.getValue();
    }

    private void addMod(Player p, String attrName, String keyName, double amount, AttributeModifier.Operation op) {
        if (amount == 0.0) return;
        Attribute a = attribute(attrName);
        AttributeInstance inst = a == null ? null : p.getAttribute(a);
        if (inst == null) return;
        inst.addModifier(new AttributeModifier(new NamespacedKey(plugin, keyName), amount, op));
    }

    public void clearAttributes(Player p) {
        for (String[] pair : ATTRS) {
            Attribute a = attribute(pair[0]);
            AttributeInstance inst = a == null ? null : p.getAttribute(a);
            if (inst == null) continue;
            NamespacedKey key = new NamespacedKey(plugin, pair[1]);
            for (AttributeModifier m : new ArrayList<>(inst.getModifiers())) {
                if (key.equals(m.getKey())) inst.removeModifier(m);
            }
        }
        double max = maxHealth(p);
        if (p.getHealth() > max) p.setHealth(max);
    }

    /** Tính lại và áp toàn bộ chỉ số (máu, tốc độ, nhảy, kháng knockback) theo race/level/nâng cấp. */
    public void applyAttributes(Player p) {
        clearAttributes(p);
        PlayerRaceData d = data(p);
        Race r = raceOf(d);
        if (d == null || r == null) return;
        addMod(p, "max_health", "race_health", healthOf(d, r) - 20.0, AttributeModifier.Operation.ADD_NUMBER);
        addMod(p, "movement_speed", "race_speed", speedOf(d, r) - 1.0, AttributeModifier.Operation.ADD_SCALAR);
        addMod(p, "jump_strength", "race_jump", r.getJump(), AttributeModifier.Operation.ADD_SCALAR);
        addMod(p, "knockback_resistance", "race_kb", r.getKnockback(), AttributeModifier.Operation.ADD_NUMBER);

        for (String n : r.getImmuneEffects()) {
            PotionEffectType t = EffectSpec.potion(n);
            if (t != null) p.removePotionEffect(t);
        }
        double max = maxHealth(p);
        if (p.getHealth() > max) p.setHealth(max);
    }

    // ---------------- Chiến đấu ----------------
    /** Hệ số sát thương người chơi gây ra (sức mạnh, nội tại, chí mạng). */
    public double outgoingMultiplier(Player attacker, boolean melee, boolean arrow) {
        PlayerRaceData d = data(attacker);
        Race r = raceOf(d);
        if (d == null || r == null) return 1.0;
        double m = strengthOf(d, r);
        if (melee) m *= r.getMeleeMultiplier();
        if (arrow) m *= r.getBowMultiplier();
        if (attacker.getLocation().getY() <= r.getUndergroundMaxY()) m *= r.getUndergroundMultiplier();
        if (r.getWaterDamageMultiplier() != 1.0 && attacker.isInWater()) m *= r.getWaterDamageMultiplier();
        if (r.getLowHealthThreshold() > 0) {
            double ratio = attacker.getHealth() / Math.max(1.0, maxHealth(attacker));
            if (ratio <= r.getLowHealthThreshold()) m *= r.getLowHealthMultiplier();
        }
        double crit = critOf(d, r);
        if (crit > 0 && ThreadLocalRandom.current().nextDouble() < crit) {
            m *= cfg().getDouble("combat.critical-multiplier", 1.5);
            Location l = attacker.getLocation().add(0, 1.5, 0);
            attacker.getWorld().spawnParticle(Particle.CRIT, l, 12, 0.4, 0.4, 0.4, 0.2);
            String snd = cfg().getString("combat.critical-sound", "");
            if (snd != null && !snd.isEmpty()) attacker.playSound(attacker.getLocation(), SkillEffects.soundKey(snd), 1f, 1f);
        }
        return m;
    }

    /** Hệ số sát thương người chơi nhận vào (điểm yếu, kháng, phòng thủ). */
    public double incomingMultiplier(Player victim, Set<String> attackerTags, EntityDamageEvent.DamageCause cause) {
        PlayerRaceData d = data(victim);
        Race r = raceOf(d);
        if (d == null || r == null) return 1.0;
        double m = r.damageTakenMultiplier(cause);
        for (String tag : attackerTags) m *= r.weakness(tag);
        m *= Math.max(0.1, 1.0 - upgradeBonus(d, "defense"));
        return m;
    }
}
