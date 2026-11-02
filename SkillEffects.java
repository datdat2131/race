package me.astrax.race;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hiệu ứng hình ảnh/âm thanh của skill, đọc từ YAML (mục effects của skill):
 * <pre>
 * effects:
 *   sounds:     {cast: {key: entity.blaze.shoot, volume: 1, pitch: 0.7, delay: 0}, ...}
 *   particle:   {type: FLAME, count: 40, spread: 1.0, speed: 0.05}     # nổ nhỏ lúc tung skill
 *   animations: {ring: {type: SHOCKWAVE, particle: FLAME, radius: 6, duration: 14, count: 36, delay: 0}, ...}
 *   impact:     {particle: {...}, sound: {...}}                        # tại mỗi mục tiêu bị trúng
 *   landing:    {particle: {...}, sound: {...}}                        # (tùy chọn) hiệu ứng khi đáp đất
 * </pre>
 * Loại animation: RING, SHOCKWAVE, SPIRAL, VORTEX, PILLAR, BURST, DOME, TRAIL.
 * Vẫn hỗ trợ cú pháp cũ: sound {key..} và animation {type..}.
 */
public final class SkillEffects {

    private record SoundDef(String key, float volume, float pitch, int delay) {}

    private record Layer(String type, Particle particle, double radius, double height, int duration, int count, int delay) {}

    private record Hit(Particle particle, int count, double spread, double speed, SoundDef sound) {}

    private final Particle particle;
    private final int count;
    private final double spread;
    private final double speed;
    private final Color color;
    private final List<SoundDef> sounds = new ArrayList<>();
    private final List<Layer> layers = new ArrayList<>();
    private final Hit impact;
    private final Hit landing;

    public SkillEffects(ConfigurationSection s, Logger log) {
        if (s == null) s = new MemoryConfiguration();
        particle = parse(s.getString("particle.type"), log);
        count = s.getInt("particle.count", 20);
        spread = s.getDouble("particle.spread", 0.5);
        speed = s.getDouble("particle.speed", 0.02);
        color = parseColor(s.getString("particle.color", "255,60,60"));

        // âm thanh (cú pháp cũ + nhiều lớp)
        if (s.getString("sound.key") != null) {
            sounds.add(new SoundDef(s.getString("sound.key"), (float) s.getDouble("sound.volume", 1.0),
                    (float) s.getDouble("sound.pitch", 1.0), 0));
        }
        ConfigurationSection ss = s.getConfigurationSection("sounds");
        if (ss != null) {
            for (String k : ss.getKeys(false)) {
                ConfigurationSection x = ss.getConfigurationSection(k);
                if (x == null || x.getString("key") == null) continue;
                sounds.add(new SoundDef(x.getString("key"), (float) x.getDouble("volume", 1.0),
                        (float) x.getDouble("pitch", 1.0), Math.max(0, x.getInt("delay", 0))));
            }
        }

        // animation (cú pháp cũ + nhiều lớp)
        if (s.getString("animation.type") != null) {
            Layer l = parseLayer(s.getConfigurationSection("animation"), log);
            if (l != null) layers.add(l);
        }
        ConfigurationSection as = s.getConfigurationSection("animations");
        if (as != null) {
            for (String k : as.getKeys(false)) {
                Layer l = parseLayer(as.getConfigurationSection(k), log);
                if (l != null) layers.add(l);
            }
        }

        impact = parseHit(s, "impact", log);
        landing = parseHit(s, "landing", log);
    }

    // ---------------- Phân tích cấu hình ----------------
    private static Particle parse(String name, Logger log) {
        if (name == null || name.equalsIgnoreCase("NONE")) return null;
        try {
            return Particle.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warning("Particle không hợp lệ: " + name);
            return null;
        }
    }

    private static Color parseColor(String s) {
        try {
            String[] p = s.split(",");
            return Color.fromRGB(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()));
        } catch (Exception e) {
            return Color.RED;
        }
    }

    private static Layer parseLayer(ConfigurationSection a, Logger log) {
        if (a == null) return null;
        Particle p = parse(a.getString("particle"), log);
        if (p == null) return null;
        String type = a.getString("type", "RING").toUpperCase(Locale.ROOT);
        return new Layer(type, p, a.getDouble("radius", 3.0), a.getDouble("height", 2.0),
                Math.max(1, a.getInt("duration", 20)), Math.max(1, a.getInt("count", 0)),
                Math.max(0, a.getInt("delay", 0)));
    }

    private static Hit parseHit(ConfigurationSection s, String path, Logger log) {
        ConfigurationSection h = s.getConfigurationSection(path);
        if (h == null) return null;
        Particle p = parse(h.getString("particle.type"), log);
        SoundDef sd = h.getString("sound.key") == null ? null
                : new SoundDef(h.getString("sound.key"), (float) h.getDouble("sound.volume", 1.0),
                (float) h.getDouble("sound.pitch", 1.0), 0);
        if (p == null && sd == null) return null;
        return new Hit(p, Math.max(1, h.getInt("particle.count", 8)), h.getDouble("particle.spread", 0.4),
                h.getDouble("particle.speed", 0.05), sd);
    }

    /** Chuẩn hóa khoá âm thanh: ENTITY_BLAZE_SHOOT -> entity.blaze.shoot; khoá có dấu chấm giữ nguyên. */
    public static String soundKey(String s) {
        if (s.contains(".") || s.contains(":")) return s.toLowerCase(Locale.ROOT);
        return s.toLowerCase(Locale.ROOT).replace('_', '.');
    }

    // ---------------- Phát hiệu ứng ----------------
    public void play(JavaPlugin plugin, Player caster) {
        Location loc = caster.getLocation();
        for (SoundDef sd : sounds) {
            if (sd.delay() <= 0) {
                playSound(loc, sd);
            } else {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (caster.isOnline()) playSound(caster.getLocation(), sd);
                }, sd.delay());
            }
        }
        if (particle != null) spawn(loc.clone().add(0, 1, 0), particle, count, spread, speed);
        for (Layer l : layers) animate(plugin, caster, l);
    }

    /** Hiệu ứng tại mỗi mục tiêu bị trúng. */
    public void impact(Location l) {
        playHit(impact, l);
    }

    /** Hiệu ứng khi đáp đất (tùy chọn). */
    public void landing(Location l) {
        playHit(landing, l);
    }

    private void playHit(Hit h, Location l) {
        if (h == null || l.getWorld() == null) return;
        if (h.particle() != null) spawn(l.clone().add(0, 1, 0), h.particle(), h.count(), h.spread(), h.speed());
        if (h.sound() != null) playSound(l, h.sound());
    }

    private void playSound(Location loc, SoundDef sd) {
        World w = loc.getWorld();
        if (w == null) return;
        w.playSound(loc, soundKey(sd.key()), SoundCategory.PLAYERS, sd.volume(), sd.pitch());
    }

    private void spawn(Location l, Particle p, int n, double off, double spd) {
        World w = l.getWorld();
        if (w == null) return;
        if (p == Particle.DUST) {
            w.spawnParticle(p, l, n, off, off, off, 0, new Particle.DustOptions(color, 1.5f));
        } else if (p.getDataType() == Void.class) {
            w.spawnParticle(p, l, n, off, off, off, spd);
        }
    }

    private void animate(JavaPlugin plugin, Player caster, Layer layer) {
        new BukkitRunnable() {
            int t = -layer.delay();

            @Override
            public void run() {
                if (!caster.isOnline()) {
                    cancel();
                    return;
                }
                if (t < 0) {
                    t++;
                    return;
                }
                if (t >= layer.duration()) {
                    cancel();
                    return;
                }
                draw(layer, caster.getLocation(), (double) (t + 1) / layer.duration(), t);
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void ring(Location c, Particle p, double r, double y, int n) {
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            spawn(c.clone().add(Math.cos(a) * r, y, Math.sin(a) * r), p, 1, 0, 0);
        }
    }

    private void draw(Layer L, Location c, double prog, int t) {
        Particle p = L.particle();
        int n = L.count();
        switch (L.type()) {
            case "RING" -> ring(c, p, L.radius() * prog, 0.2, n > 0 ? n : 24);
            case "SHOCKWAVE" -> {
                int pts = n > 0 ? n : 36;
                ring(c, p, L.radius() * prog, 0.1, pts);
                ring(c, p, L.radius() * prog * 0.75, 0.1, pts);
            }
            case "SPIRAL" -> {
                int strands = n > 0 ? n : 2;
                for (int k = 0; k < strands; k++) {
                    double a = t * 0.5 + k * 2 * Math.PI / strands;
                    spawn(c.clone().add(Math.cos(a) * L.radius(), L.height() * prog, Math.sin(a) * L.radius()), p, 1, 0, 0);
                }
            }
            case "VORTEX" -> {
                int strands = n > 0 ? n : 3;
                for (int k = 0; k < strands; k++) {
                    double a = t * 0.6 + k * 2 * Math.PI / strands;
                    double r = L.radius() * prog;
                    spawn(c.clone().add(Math.cos(a) * r, L.height() * prog, Math.sin(a) * r), p, 1, 0, 0);
                }
            }
            case "PILLAR" -> {
                double y = L.height() * prog;
                ring(c, p, L.radius(), y, n > 0 ? n : 12);
                ring(c, p, L.radius() * 0.6, y * 0.5, Math.max(6, (n > 0 ? n : 12) / 2));
            }
            case "BURST" -> {
                int pts = n > 0 ? n : 24;
                double r = L.radius() * prog;
                for (int i = 0; i < pts; i++) {
                    double phi = Math.acos(1 - 2.0 * (i + 0.5) / pts);
                    double th = Math.PI * (1 + Math.sqrt(5)) * (i + 0.5);
                    spawn(c.clone().add(r * Math.sin(phi) * Math.cos(th), 1 + r * Math.cos(phi),
                            r * Math.sin(phi) * Math.sin(th)), p, 1, 0, 0);
                }
            }
            case "DOME" -> {
                int pts = n > 0 ? n : 30;
                double r = L.radius() * prog;
                for (int i = 0; i < pts; i++) {
                    double phi = Math.acos(1 - (i + 0.5) / pts);
                    double th = Math.PI * (1 + Math.sqrt(5)) * (i + 0.5);
                    spawn(c.clone().add(r * Math.sin(phi) * Math.cos(th), r * Math.cos(phi),
                            r * Math.sin(phi) * Math.sin(th)), p, 1, 0, 0);
                }
            }
            case "TRAIL" -> {
                ThreadLocalRandom rnd = ThreadLocalRandom.current();
                int pts = n > 0 ? n : 4;
                for (int i = 0; i < pts; i++) {
                    spawn(c.clone().add((rnd.nextDouble() - 0.5) * L.radius() * 2, 0.2 + rnd.nextDouble() * 1.4,
                            (rnd.nextDouble() - 0.5) * L.radius() * 2), p, 1, 0, 0.01);
                }
            }
            default -> {
            }
        }
    }
}
