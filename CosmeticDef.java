package me.astrax.race;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Cosmetic nhận dạng của một race (cánh, vật phẩm đi theo, đuôi, hiệu ứng particle), đọc từ races.yml:
 * <pre>
 * cosmetic:
 *   parts:
 *     wings: {type: FIXED, item: ELYTRA, mirror: true, ...}
 *   particles:
 *     trail: {type: PORTAL, ...}
 * </pre>
 */
public final class CosmeticDef {

    /** FIXED = gắn cố định sau lưng (cánh...), FOLLOWER = bay lơ lửng đi theo, TAIL = chuỗi đốt như đuôi rắn. */
    public enum Type { FIXED, FOLLOWER, TAIL }

    public record Flap(double amplitude, double speed, double airMultiplier) {}

    public record ParticleDef(Particle particle, int count, double spread, double speed, int interval,
                              String at, boolean movingOnly) {}

    public static final class Part {
        public final String name;
        public final Type type;
        public final Material material;
        public final Material endMaterial;      // vật phẩm của đốt cuối (TAIL), có thể null
        public final String itemModel;          // item-model (resource pack, 1.21.2+), có thể null
        public final int customModelData;       // -1 = không dùng
        public final boolean mirror;
        public final boolean ownerVisible;
        public final float scale;
        public final float endScale;
        public final double side;
        public final double up;
        public final double back;
        public final double tilt;               // xoay trong mặt phẳng (độ)
        public final double spread;             // góc mở quanh trục dọc (độ)
        public final double pitch;              // xoay quanh trục ngang (độ), dùng cho vòng hào quang...
        public final double pivot;              // 1.0 = xoay quanh mép trong (cánh), 0 = quanh tâm
        public final double followSpeed;
        public final double bobAmplitude;
        public final double bobSpeed;
        public final int segments;
        public final double spacing;
        public final double wiggle;
        public final Flap flap;                 // null = không vỗ
        public final Display.Billboard billboard;

        private Part(String name, Type type, Material material, Material endMaterial,
                     Display.Billboard billboard, ConfigurationSection s) {
            this.name = name;
            this.type = type;
            this.material = material;
            this.endMaterial = endMaterial;
            this.billboard = billboard;
            this.itemModel = s.getString("item-model");
            this.customModelData = s.getInt("custom-model-data", -1);
            this.mirror = s.getBoolean("mirror", false);
            this.ownerVisible = s.getBoolean("owner-visible", true);
            this.scale = (float) s.getDouble("scale", 1.0);
            this.endScale = (float) s.getDouble("end-scale", this.scale);
            this.side = s.getDouble("side", 0.0);
            this.up = s.getDouble("up", 1.2);
            this.back = s.getDouble("back", 0.25);
            this.tilt = s.getDouble("tilt", 0.0);
            this.spread = s.getDouble("spread", 0.0);
            this.pitch = s.getDouble("pitch", 0.0);
            this.pivot = s.getDouble("pivot", 0.0);
            this.followSpeed = Math.max(0.01, Math.min(1.0, s.getDouble("follow-speed", 0.25)));
            this.bobAmplitude = s.getDouble("bob.amplitude", 0.0);
            this.bobSpeed = s.getDouble("bob.speed", 0.15);
            this.segments = Math.max(1, Math.min(24, s.getInt("segments", 6)));
            this.spacing = Math.max(0.05, s.getDouble("spacing", 0.3));
            this.wiggle = s.getDouble("wiggle", 0.0);
            this.flap = s.contains("flap.amplitude")
                    ? new Flap(s.getDouble("flap.amplitude"), s.getDouble("flap.speed", 0.2),
                    s.getDouble("flap.air-multiplier", 1.0))
                    : null;
        }

        static Part parse(String name, ConfigurationSection s, Logger log) {
            Type type;
            try {
                type = Type.valueOf(s.getString("type", "FIXED").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                log.warning("Cosmetic '" + name + "': type không hợp lệ (FIXED | FOLLOWER | TAIL).");
                return null;
            }
            Material m = Material.matchMaterial(s.getString("item", ""));
            if (m == null || !m.isItem()) {
                log.warning("Cosmetic '" + name + "': item không hợp lệ: " + s.getString("item"));
                return null;
            }
            Material end = s.contains("end-item") ? Material.matchMaterial(s.getString("end-item", "")) : null;
            Display.Billboard bb;
            try {
                bb = Display.Billboard.valueOf(s.getString("billboard",
                        type == Type.FIXED ? "FIXED" : "CENTER").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                bb = Display.Billboard.FIXED;
            }
            return new Part(name, type, m, end, bb, s);
        }
    }

    private final List<Part> parts = new ArrayList<>();
    private final List<ParticleDef> particles = new ArrayList<>();

    public CosmeticDef(ConfigurationSection sec, Logger log) {
        ConfigurationSection ps = sec.getConfigurationSection("parts");
        if (ps != null) {
            for (String key : ps.getKeys(false)) {
                ConfigurationSection s = ps.getConfigurationSection(key);
                if (s == null) continue;
                Part p = Part.parse(key, s, log);
                if (p != null) parts.add(p);
            }
        }
        ConfigurationSection pa = sec.getConfigurationSection("particles");
        if (pa != null) {
            for (String key : pa.getKeys(false)) {
                ConfigurationSection s = pa.getConfigurationSection(key);
                if (s == null) continue;
                Particle particle;
                try {
                    particle = Particle.valueOf(s.getString("type", "").toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    log.warning("Cosmetic particle '" + key + "': type không hợp lệ: " + s.getString("type"));
                    continue;
                }
                if (particle.getDataType() != Void.class) {
                    log.warning("Cosmetic particle '" + key + "': " + particle + " cần dữ liệu đi kèm, không hỗ trợ.");
                    continue;
                }
                particles.add(new ParticleDef(particle, Math.max(1, s.getInt("count", 3)),
                        s.getDouble("spread", 0.3), s.getDouble("speed", 0.02),
                        Math.max(1, s.getInt("interval", 10)),
                        s.getString("at", "BACK").toUpperCase(Locale.ROOT),
                        s.getBoolean("moving-only", false)));
            }
        }
    }

    public List<Part> getParts() { return Collections.unmodifiableList(parts); }
    public List<ParticleDef> getParticles() { return Collections.unmodifiableList(particles); }
    public boolean isEmpty() { return parts.isEmpty() && particles.isEmpty(); }
}
