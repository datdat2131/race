package me.astrax.race;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Đánh giá và áp dụng buff của chủng tộc. Buff chỉ hoạt động khi người chơi đủ cấp mở khóa VÀ điều kiện đúng.
 * Mỗi giây: áp potion theo điều kiện + hồi máu; trong chiến đấu: hệ số sát thương gây ra/nhận vào, buff khi hạ gục/bị đánh.
 */
public final class BuffManager {

    private final AstraXRace plugin;

    public BuffManager(AstraXRace plugin) {
        this.plugin = plugin;
    }

    // ---------------- Điều kiện ----------------
    private static boolean isNight(World w) {
        long t = w.getTime();
        return t >= 13000 && t < 23000;
    }

    private double healthRatio(Player p) {
        return p.getHealth() / Math.max(1.0, plugin.races().maxHealth(p));
    }

    public boolean conditionMet(Player p, BuffDef b) {
        World w = p.getWorld();
        double thr = b.threshold();
        World.Environment env = w.getEnvironment();
        return switch (b.condition()) {
            case ALWAYS -> true;
            case IN_WATER -> p.isInWater();
            case UNDERGROUND -> env == World.Environment.NORMAL && p.getLocation().getY() <= thr;
            case NIGHT -> env == World.Environment.NORMAL && isNight(w);
            case DAY -> env == World.Environment.NORMAL && !isNight(w) && p.getLocation().getBlock().getLightFromSky() >= 12;
            case LOW_HEALTH -> healthRatio(p) <= thr;
            case FULL_HEALTH -> healthRatio(p) >= thr;
            case IN_NETHER -> env == World.Environment.NETHER;
            case IN_END -> env == World.Environment.THE_END;
            case IN_RAIN -> p.isInRain();
            case SNEAKING -> p.isSneaking();
            case SPRINTING -> p.isSprinting();
            case ON_FIRE -> p.getFireTicks() > 0;
            case HIGH_ALTITUDE -> p.getLocation().getY() >= thr;
        };
    }

    public boolean active(Player p, PlayerRaceData d, BuffDef b) {
        return d.getLevel() >= b.unlockLevel() && conditionMet(p, b);
    }

    // ---------------- Hệ số chiến đấu ----------------
    /** Tích hệ số sát thương GÂY RA của mọi buff đang hoạt động. */
    public double damageMultiplier(Player p, PlayerRaceData d, Race r) {
        double m = 1.0;
        for (BuffDef b : r.getBuffs()) {
            if (b.damageMultiplier() != 1.0 && active(p, d, b)) m *= b.damageMultiplier();
        }
        return m;
    }

    /** Tích hệ số sát thương NHẬN VÀO của mọi buff đang hoạt động (nhỏ hơn 1 = giảm sát thương). */
    public double defenseMultiplier(Player p, PlayerRaceData d, Race r) {
        double m = 1.0;
        for (BuffDef b : r.getBuffs()) {
            if (b.defenseMultiplier() != 1.0 && active(p, d, b)) m *= b.defenseMultiplier();
        }
        return m;
    }

    // ---------------- Chạy mỗi giây ----------------
    public void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isDead()) continue;
            PlayerRaceData d = plugin.races().data(p);
            Race r = plugin.races().raceOf(d);
            if (d == null || r == null) continue;
            double heal = plugin.races().upgradeBonus(d, "regen");
            for (BuffDef b : r.getBuffs()) {
                if (!active(p, d, b)) continue;
                for (EffectSpec spec : b.effects()) spec.applyPermanent(p);
                heal += b.regen();
            }
            if (heal > 0) {
                double max = plugin.races().maxHealth(p);
                if (p.getHealth() < max) p.setHealth(Math.min(max, p.getHealth() + heal));
            }
        }
    }

    // ---------------- Sự kiện ----------------
    /** Gọi khi người chơi hạ gục một thực thể. */
    public void onKill(Player p) {
        PlayerRaceData d = plugin.races().data(p);
        Race r = plugin.races().raceOf(d);
        if (d == null || r == null) return;
        for (BuffDef b : r.getBuffs()) {
            if (b.onKillHeal() <= 0 && b.onKillEffects().isEmpty()) continue;
            if (!active(p, d, b)) continue;
            for (EffectSpec spec : b.onKillEffects()) spec.apply(p);
            if (b.onKillHeal() > 0) {
                double max = plugin.races().maxHealth(p);
                p.setHealth(Math.min(max, p.getHealth() + b.onKillHeal()));
            }
        }
    }

    /** Gọi khi người chơi bị sát thương. */
    public void onHurt(Player p) {
        PlayerRaceData d = plugin.races().data(p);
        Race r = plugin.races().raceOf(d);
        if (d == null || r == null) return;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (BuffDef b : r.getBuffs()) {
            if (b.onHurtChance() <= 0 || b.onHurtEffects().isEmpty()) continue;
            if (!active(p, d, b)) continue;
            if (rnd.nextDouble() <= b.onHurtChance()) {
                for (EffectSpec spec : b.onHurtEffects()) spec.apply(p);
            }
        }
    }
}
