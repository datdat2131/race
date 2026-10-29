package me.astrax.race;

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

import java.util.Locale;
import java.util.logging.Logger;

/** Hiệu ứng hình ảnh/âm thanh/animation của skill, đọc từ YAML. */
public final class SkillEffects {

    private final Particle particle;
    private final int count;
    private final double spread;
    private final double speed;
    private final Color color;

    private final String sound;
    private final float volume;
    private final float pitch;

    private final String animType;
    private final Particle animParticle;
    private final double radius;
    private final double height;
    private final int duration;

    public SkillEffects(ConfigurationSection s, Logger log) {
        if (s == null) s = new MemoryConfiguration();
        particle = parse(s.getString("particle.type"), log);
        count = s.getInt("particle.count", 20);
        spread = s.getDouble("particle.spread", 0.5);
        speed = s.getDouble("particle.speed", 0.02);
        color = parseColor(s.getString("particle.color", "255,60,60"));

        sound = s.getString("sound.key");
        volume = (float) s.getDouble("sound.volume", 1.0);
        pitch = (float) s.getDouble("sound.pitch", 1.0);

        animType = s.getString("animation.type", "NONE").toUpperCase(Locale.ROOT);
        animParticle = parse(s.getString("animation.particle"), log);
        radius = s.getDouble("animation.radius", 3.0);
        height = s.getDouble("animation.height", 2.0);
        duration = s.getInt("animation.duration", 20);
    }

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

    /** Chuẩn hóa khoá âm thanh: ENTITY_BLAZE_SHOOT -> entity.blaze.shoot; khoá có dấu chấm giữ nguyên. */
    public static String soundKey(String s) {
        if (s.contains(".") || s.contains(":")) return s.toLowerCase(Locale.ROOT);
        return s.toLowerCase(Locale.ROOT).replace('_', '.');
    }

    public void play(JavaPlugin plugin, Player caster) {
        Location loc = caster.getLocation();
        if (sound != null) {
            loc.getWorld().playSound(loc, soundKey(sound), SoundCategory.PLAYERS, volume, pitch);
        }
        if (particle != null) {
            spawn(loc.clone().add(0, 1, 0), particle, count, spread, speed);
        }
        if (animParticle != null && !animType.equals("NONE") && duration > 0) {
            animate(plugin, caster);
        }
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

    private void animate(JavaPlugin plugin, Player caster) {
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (!caster.isOnline() || tick >= duration) {
                    cancel();
                    return;
                }
                Location c = caster.getLocation();
                double prog = (double) (tick + 1) / duration;
                switch (animType) {
                    case "RING" -> {
                        double r = radius * prog;
                        for (int i = 0; i < 24; i++) {
                            double a = 2 * Math.PI * i / 24;
                            spawn(c.clone().add(Math.cos(a) * r, 0.2, Math.sin(a) * r), animParticle, 1, 0, 0);
                        }
                    }
                    case "SPIRAL" -> {
                        for (int k = 0; k < 2; k++) {
                            double a = tick * 0.5 + k * Math.PI;
                            spawn(c.clone().add(Math.cos(a) * radius, height * prog, Math.sin(a) * radius),
                                    animParticle, 1, 0, 0);
                        }
                    }
                    case "BURST" -> {
                        double r = radius * prog;
                        for (int i = 0; i < 20; i++) {
                            double phi = Math.acos(1 - 2.0 * (i + 0.5) / 20);
                            double th = Math.PI * (1 + Math.sqrt(5)) * (i + 0.5);
                            spawn(c.clone().add(r * Math.sin(phi) * Math.cos(th), 1 + r * Math.cos(phi),
                                    r * Math.sin(phi) * Math.sin(th)), animParticle, 1, 0, 0);
                        }
                    }
                    default -> cancel();
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
