package me.astrax.race;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/** Tạo skill, kiểm tra cooldown/năng lượng, kích hoạt và quản lý trạng thái tạm (không lưu DB). */
public final class SkillManager {

    public static final String TRIGGER_RIGHT = "RIGHT_CLICK";
    public static final String TRIGGER_SHIFT = "SHIFT_RIGHT_CLICK";

    private final AstraXRace plugin;
    private final NamespacedKey itemKey;
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Double> energy = new ConcurrentHashMap<>();
    private final Map<UUID, Long> noFallUntil = new ConcurrentHashMap<>();
    private boolean skillDamage;

    public SkillManager(AstraXRace plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "skill_item");
    }

    // ---------------- Tạo skill ----------------
    public Skill buildSkill(String id, ConfigurationSection sec) {
        String type = sec.getString("type", "").toUpperCase(Locale.ROOT);
        Logger log = plugin.getLogger();
        return switch (type) {
            case "AOE_DAMAGE" -> new AoeDamageSkill(id, sec, log);
            case "SELF_BUFF" -> new SelfBuffSkill(id, sec, log);
            case "HEAL" -> new HealSkill(id, sec, log);
            case "DASH" -> new DashSkill(id, sec, log);
            case "NO_FALL" -> new NoFallSkill(id, sec, log);
            default -> {
                log.warning("Loại skill không hợp lệ '" + type + "' (skill " + id + ")");
                yield null;
            }
        };
    }

    public Skill findByTrigger(Race race, int level, String trigger) {
        Skill fallback = null;
        for (Skill s : race.getSkills()) {
            if (!s.getTrigger().equals(trigger)) continue;
            if (level >= s.getUnlockLevel()) return s;
            if (fallback == null) fallback = s;
        }
        return fallback;
    }

    // ---------------- Kích hoạt ----------------
    private double power(PlayerRaceData d) {
        double perLevel = plugin.configs().config().getDouble("leveling.skill-power-per-level", 0.0);
        return 1.0 + plugin.races().upgradeBonus(d, "skill_power") + perLevel * (d.getLevel() - 1);
    }

    /** Dùng skill, gửi thông báo nếu thất bại. Trả về true nếu dùng thành công. */
    public boolean castAndNotify(Player p, Skill skill) {
        ConfigManager cm = plugin.configs();
        PlayerRaceData d = plugin.races().data(p);
        Race r = plugin.races().raceOf(d);
        if (d == null || r == null) {
            cm.send(p, "no-race");
            return false;
        }
        if (d.getLevel() < skill.getUnlockLevel()) {
            cm.send(p, "skill-locked", "level", String.valueOf(skill.getUnlockLevel()));
            return false;
        }
        boolean bypass = p.hasPermission("astraxrace.bypass");
        long now = System.currentTimeMillis();
        String key = r.getId() + ":" + skill.getId();
        Map<String, Long> cd = cooldowns.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
        Long until = cd.get(key);
        if (!bypass && until != null && until > now) {
            cm.send(p, "skill-cooldown", "seconds", String.valueOf((until - now + 999) / 1000));
            return false;
        }
        boolean useEnergy = energyEnabled() && !bypass && skill.getEnergyCost() > 0;
        if (useEnergy && getEnergy(p) < skill.getEnergyCost()) {
            cm.send(p, "skill-no-energy", "energy", ConfigManager.num(Math.floor(getEnergy(p))),
                    "cost", ConfigManager.num(skill.getEnergyCost()));
            return false;
        }
        if (!skill.execute(this, p, power(d))) return false;

        if (useEnergy) energy.put(p.getUniqueId(), getEnergy(p) - skill.getEnergyCost());
        cd.put(key, now + (long) (skill.getCooldown() * 1000));
        skill.getEffects().play(plugin, p);
        p.sendActionBar(ConfigManager.color(cm.raw("skill-used", "skill", skill.getName())));
        return true;
    }

    public long cooldownRemaining(UUID id, Race r, Skill s) {
        Map<String, Long> cd = cooldowns.get(id);
        if (cd == null) return 0;
        Long until = cd.get(r.getId() + ":" + s.getId());
        if (until == null) return 0;
        return Math.max(0, (until - System.currentTimeMillis() + 999) / 1000);
    }

    // ---------------- Sát thương skill ----------------
    /** Gây sát thương skill: áp điểm yếu/phòng thủ của nạn nhân, bỏ qua bộ xử lý sát thương thường. */
    public void dealDamage(Player caster, LivingEntity target, double amount, Set<String> tags) {
        if (target instanceof Player tp) {
            amount *= plugin.races().incomingMultiplier(tp, tags, EntityDamageEvent.DamageCause.ENTITY_ATTACK);
        }
        skillDamage = true;
        try {
            target.damage(amount, caster);
        } finally {
            skillDamage = false;
        }
    }

    public boolean isSkillDamage() {
        return skillDamage;
    }

    // ---------------- Chống rơi ----------------
    public void grantNoFall(UUID id, int ticks) {
        noFallUntil.put(id, System.currentTimeMillis() + ticks * 50L);
    }

    public boolean hasNoFall(UUID id) {
        Long u = noFallUntil.get(id);
        if (u == null) return false;
        if (u < System.currentTimeMillis()) {
            noFallUntil.remove(id);
            return false;
        }
        return true;
    }

    // ---------------- Năng lượng ----------------
    public boolean energyEnabled() {
        return plugin.configs().config().getBoolean("energy.enabled", true);
    }

    public double maxEnergy(PlayerRaceData d) {
        ConfigurationSection c = plugin.configs().config();
        return c.getDouble("energy.max", 100) + c.getDouble("energy.per-level", 0) * (d.getLevel() - 1);
    }

    public double getEnergy(Player p) {
        PlayerRaceData d = plugin.races().data(p);
        double max = d == null ? 100 : maxEnergy(d);
        return Math.min(max, energy.getOrDefault(p.getUniqueId(), max));
    }

    public void initEnergy(Player p) {
        PlayerRaceData d = plugin.races().data(p);
        energy.put(p.getUniqueId(), d == null ? 100.0 : maxEnergy(d));
    }

    /** Chạy mỗi giây: hồi năng lượng, hiệu ứng thường trực, hiển thị action bar. */
    public void tick() {
        ConfigurationSection cfg = plugin.configs().config();
        boolean enabled = energyEnabled();
        double regen = cfg.getDouble("energy.regen-per-second", 3.0);
        boolean showBar = cfg.getBoolean("energy.show-actionbar-when-holding-item", true);
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerRaceData d = plugin.races().data(p);
            Race r = plugin.races().raceOf(d);
            if (d == null || r == null) continue;
            if (enabled) {
                double max = maxEnergy(d);
                double cur = Math.min(max, energy.getOrDefault(p.getUniqueId(), max) + regen);
                energy.put(p.getUniqueId(), cur);
                if (showBar && isSkillItem(p.getInventory().getItemInMainHand())) {
                    p.sendActionBar(ConfigManager.color(plugin.configs().raw("energy-bar",
                            "energy", ConfigManager.num(Math.floor(cur)), "max", ConfigManager.num(max))));
                }
            }
            for (EffectSpec spec : r.getPermanentEffects()) spec.applyPermanent(p);
            if (!r.getWaterEffects().isEmpty() && p.isInWater()) {
                for (EffectSpec spec : r.getWaterEffects()) spec.applyPermanent(p);
            }
        }
    }

    /** Dọn trạng thái tạm của người chơi (tránh rò rỉ bộ nhớ). */
    public void clear(UUID id) {
        cooldowns.remove(id);
        energy.remove(id);
        noFallUntil.remove(id);
    }

    // ---------------- Vật phẩm skill ----------------
    public ItemStack createSkillItem() {
        ConfigurationSection sec = plugin.configs().config().getConfigurationSection("skill-item");
        if (sec == null) sec = new MemoryConfiguration();
        ItemStack it = plugin.configs().item(sec, null);
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    public boolean isSkillItem(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return false;
        return it.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }

    /** Đưa vật phẩm skill cho người chơi (nếu chưa có, trừ khi force). */
    public void giveSkillItem(Player p, boolean force) {
        if (!plugin.configs().config().getBoolean("skill-item.enabled", true)) return;
        if (!force) {
            for (ItemStack it : p.getInventory().getContents()) {
                if (isSkillItem(it)) return;
            }
        }
        Map<Integer, ItemStack> left = p.getInventory().addItem(createSkillItem());
        for (ItemStack rest : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), rest);
    }
}
