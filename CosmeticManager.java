package me.astrax.race;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cosmetic nhận dạng của từng race: cánh, vật phẩm bay theo sau lưng, đuôi nhiều đốt, particle.
 * Dùng ItemDisplay (không lưu xuống đĩa, tự dọn khi người chơi thoát / đổi race / tắt plugin).
 * Chỉ chạy trên luồng chính.
 */
public final class CosmeticManager {

    private static final class Live {
        final CosmeticDef.Part part;
        final ItemDisplay display;
        final double sign;
        final int index;
        final Live prev;
        final float scale;
        final double phase;
        Vector pos;
        boolean hidden;
        boolean fresh = true;

        Live(CosmeticDef.Part part, ItemDisplay display, double sign, int index, Live prev, float scale, double phase) {
            this.part = part;
            this.display = display;
            this.sign = sign;
            this.index = index;
            this.prev = prev;
            this.scale = scale;
            this.phase = phase;
        }
    }

    private static final class CosmeticSet {
        final Race race;
        final List<Live> live = new ArrayList<>();
        Location last;
        boolean moved;

        CosmeticSet(Race race) {
            this.race = race;
        }
    }

    private final AstraXRace plugin;
    private final Map<UUID, CosmeticSet> sets = new HashMap<>();
    private BukkitTask task;
    private long tickCounter;

    private int updateTicks = 2;
    private float viewRange = 0.6f;
    private boolean hideGliding = true;
    private boolean hideInvisible = true;
    private double sneakDrop = 0.2;

    public CosmeticManager(AstraXRace plugin) {
        this.plugin = plugin;
    }

    // ---------------- Vòng đời ----------------
    /** Đọc cấu hình và (khởi động lại) tác vụ cập nhật. */
    public void start() {
        var c = plugin.configs().config();
        updateTicks = Math.max(1, Math.min(10, c.getInt("cosmetic.update-ticks", 2)));
        viewRange = (float) c.getDouble("cosmetic.view-range", 0.6);
        hideGliding = c.getBoolean("cosmetic.hide-when-gliding", true);
        hideInvisible = c.getBoolean("cosmetic.hide-when-invisible", true);
        sneakDrop = c.getDouble("cosmetic.sneak-drop", 0.2);
        if (task != null) task.cancel();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, updateTicks);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
        removeAll();
    }

    public void refreshAll() {
        for (Player p : Bukkit.getOnlinePlayers()) refresh(p);
    }

    public void removeAll() {
        for (CosmeticSet s : sets.values()) despawn(s);
        sets.clear();
    }

    public void remove(Player p) {
        CosmeticSet s = sets.remove(p.getUniqueId());
        if (s != null) despawn(s);
    }

    private void despawn(CosmeticSet s) {
        for (Live l : s.live) l.display.remove();
    }

    /** Dựng lại cosmetic của người chơi theo race hiện tại. */
    public void refresh(Player p) {
        remove(p);
        if (!plugin.configs().config().getBoolean("cosmetic.enabled", true)) return;
        Race race = plugin.races().raceOf(p);
        if (race == null) return;
        CosmeticDef def = race.getCosmetic();
        if (def == null || def.isEmpty()) return;

        CosmeticSet set = new CosmeticSet(race);
        Location base = p.getLocation();
        double phase = 0;
        for (CosmeticDef.Part part : def.getParts()) {
            if (part.type == CosmeticDef.Type.TAIL) {
                Live prev = null;
                for (int i = 0; i < part.segments; i++) {
                    boolean last = i == part.segments - 1;
                    Material mat = last && part.endMaterial != null ? part.endMaterial : part.material;
                    float t = part.segments > 1 ? (float) i / (part.segments - 1) : 0f;
                    float sc = part.scale + (part.endScale - part.scale) * t;
                    Live l = create(p, part, mat, base, 1.0, i, prev, sc, phase++);
                    set.live.add(l);
                    prev = l;
                }
            } else {
                set.live.add(create(p, part, part.material, base, 1.0, 0, null, part.scale, phase += 1.7));
                if (part.mirror) {
                    set.live.add(create(p, part, part.material, base, -1.0, 0, null, part.scale, phase));
                }
            }
        }
        sets.put(p.getUniqueId(), set);
    }

    // ---------------- Tạo entity ----------------
    @SuppressWarnings("deprecation")
    private ItemStack buildItem(CosmeticDef.Part part, Material mat) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            try {
                if (part.itemModel != null && !part.itemModel.isBlank()) {
                    meta.setItemModel(NamespacedKey.fromString(part.itemModel));
                }
                if (part.customModelData >= 0) meta.setCustomModelData(part.customModelData);
            } catch (LinkageError ignored) {
                // phiên bản server cũ không có API này: dùng icon mặc định
            }
            it.setItemMeta(meta);
        }
        return it;
    }

    private Live create(Player p, CosmeticDef.Part part, Material mat, Location at, double sign, int index,
                        Live prev, float scale, double phase) {
        ItemStack stack = buildItem(part, mat);
        ItemDisplay d = at.getWorld().spawn(at, ItemDisplay.class, disp -> {
            disp.setItemStack(stack);
            disp.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            disp.setBillboard(part.billboard);
            disp.setPersistent(false);
            disp.setInvulnerable(true);
            disp.setGravity(false);
            disp.setShadowRadius(0f);
            disp.setShadowStrength(0f);
            disp.setViewRange(viewRange);
            disp.setInterpolationDuration(updateTicks);
            disp.setTeleportDuration(0);
        });
        if (!part.ownerVisible) p.hideEntity(plugin, d);
        Live l = new Live(part, d, sign, index, prev, scale, phase);
        writeTransform(l, 0.0);
        return l;
    }

    /** Ghi transform: scale + xoay (tilt/spread/pitch + vỗ cánh) + dịch chuyển để xoay quanh mép trong. */
    private void writeTransform(Live l, double flapOffset) {
        CosmeticDef.Part pt = l.part;
        double sign = l.sign;
        float sc = l.scale;
        Quaternionf rot = new Quaternionf()
                .rotationY((float) Math.toRadians(sign * (pt.spread + flapOffset)))
                .rotateZ((float) Math.toRadians(sign * pt.tilt))
                .rotateX((float) Math.toRadians(pt.pitch));
        Vector3f translation = new Vector3f((float) (0.5 * sign * sc * pt.pivot), 0f, 0f);
        rot.transform(translation);
        if (pt.type == CosmeticDef.Type.FIXED) translation.add((float) (sign * pt.side), 0f, 0f);
        Vector3f scale = new Vector3f((float) (sign * sc), sc, sc);
        l.display.setTransformation(new Transformation(translation, rot, scale, new Quaternionf()));
        l.display.setInterpolationDelay(0);
    }

    // ---------------- Cập nhật mỗi tick ----------------
    private void tick() {
        tickCounter += updateTicks;
        List<UUID> remove = new ArrayList<>();
        List<UUID> rebuild = new ArrayList<>();
        for (Map.Entry<UUID, CosmeticSet> e : sets.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            CosmeticSet s = e.getValue();
            if (p == null || !p.isOnline()) {
                remove.add(e.getKey());
                continue;
            }
            boolean broken = false;
            for (Live l : s.live) {
                if (!l.display.isValid()) {
                    broken = true;
                    break;
                }
            }
            if (broken) {
                rebuild.add(e.getKey());
                continue;
            }
            update(p, s);
        }
        for (UUID id : remove) {
            CosmeticSet s = sets.remove(id);
            if (s != null) despawn(s);
        }
        for (UUID id : rebuild) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) refresh(p);
            else {
                CosmeticSet s = sets.remove(id);
                if (s != null) despawn(s);
            }
        }
    }

    private boolean shouldHide(Player p) {
        if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR) return true;
        if (hideGliding && p.isGliding()) return true;
        return hideInvisible && p.isInvisible();
    }

    private void update(Player p, CosmeticSet set) {
        Location base = p.getLocation();
        World w = base.getWorld();
        double yaw = Math.toRadians(base.getYaw());
        Vector f = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));    // hướng nhìn ngang
        Vector r = new Vector(-Math.cos(yaw), 0, -Math.sin(yaw));   // bên phải người chơi
        Vector basePos = base.toVector();
        double drop = p.isSneaking() ? sneakDrop : 0.0;
        boolean hide = shouldHide(p);
        boolean air = !p.isOnGround();

        set.moved = set.last == null || set.last.getWorld() != w || set.last.distanceSquared(base) > 0.01;
        set.last = base.clone();

        for (Live l : set.live) {
            CosmeticDef.Part pt = l.part;
            if (l.display.getWorld() != w) l.pos = null;

            Vector anchor = basePos.clone().add(new Vector(0, pt.up - drop, 0)).subtract(f.clone().multiply(pt.back));
            Vector leaderPos = null;
            switch (pt.type) {
                case FIXED -> l.pos = anchor;
                case FOLLOWER -> {
                    Vector target = anchor.clone().add(r.clone().multiply(pt.side * l.sign));
                    if (l.pos == null || l.pos.distanceSquared(target) > 100) {
                        l.pos = target;
                    } else {
                        l.pos.add(target.clone().subtract(l.pos).multiply(pt.followSpeed));
                    }
                }
                case TAIL -> {
                    leaderPos = l.prev == null ? anchor : l.prev.pos;
                    if (l.pos == null || l.pos.distanceSquared(leaderPos) > 144) {
                        l.pos = leaderPos.clone().subtract(f.clone().multiply(pt.spacing));
                    } else {
                        Vector d = l.pos.clone().subtract(leaderPos);
                        double len = d.length();
                        if (len > pt.spacing) {
                            d.multiply(pt.spacing / len);
                            l.pos = leaderPos.clone().add(d);
                        }
                    }
                }
            }

            double x = l.pos.getX();
            double y = l.pos.getY();
            double z = l.pos.getZ();
            if (pt.type == CosmeticDef.Type.FOLLOWER && pt.bobAmplitude != 0) {
                y += Math.sin((tickCounter + l.phase * 7.0) * pt.bobSpeed) * pt.bobAmplitude;
            }
            if (pt.type == CosmeticDef.Type.TAIL && pt.wiggle != 0 && leaderPos != null) {
                Vector d = l.pos.clone().subtract(leaderPos);
                double len = Math.sqrt(d.getX() * d.getX() + d.getZ() * d.getZ());
                if (len > 1.0E-6) {
                    double amp = pt.wiggle * (set.moved ? 1.0 : 0.3);
                    double off = Math.sin(tickCounter * 0.25 - l.index * 0.9) * amp;
                    x += -d.getZ() / len * off;
                    z += d.getX() / len * off;
                }
            }

            Location loc = new Location(w, x, y, z, base.getYaw(), 0f);
            ItemDisplay disp = l.display;
            boolean jump = disp.getWorld() != w || disp.getLocation().distanceSquared(loc) > 64;
            if (l.fresh || jump) disp.setTeleportDuration(0);
            disp.teleport(loc);
            if (l.fresh || jump) {
                disp.setTeleportDuration(updateTicks);
                l.fresh = false;
            }

            if (l.hidden != hide) {
                disp.setViewRange(hide ? 0f : viewRange);
                l.hidden = hide;
            }
            if (!hide && pt.type == CosmeticDef.Type.FIXED && pt.flap != null) {
                double speed = pt.flap.speed() * (air ? pt.flap.airMultiplier() : 1.0);
                writeTransform(l, pt.flap.amplitude() * Math.sin(tickCounter * speed));
            }
        }

        if (!hide) emitParticles(set, w, f, basePos);
    }

    private void emitParticles(CosmeticSet set, World w, Vector f, Vector basePos) {
        CosmeticDef def = set.race.getCosmetic();
        if (def == null) return;
        for (CosmeticDef.ParticleDef pd : def.getParticles()) {
            if (pd.movingOnly() && !set.moved) continue;
            if (tickCounter % Math.max(pd.interval(), updateTicks) >= updateTicks) continue;
            Vector pos = null;
            if (pd.at().equals("TAIL_END")) {
                for (int i = set.live.size() - 1; i >= 0; i--) {
                    Live l = set.live.get(i);
                    if (l.part.type == CosmeticDef.Type.TAIL && l.pos != null) {
                        pos = l.pos;
                        break;
                    }
                }
            }
            if (pos == null) pos = basePos.clone().add(new Vector(0, 1.2, 0)).subtract(f.clone().multiply(0.3));
            w.spawnParticle(pd.particle(), pos.getX(), pos.getY(), pos.getZ(), pd.count(),
                    pd.spread(), pd.spread(), pd.spread(), pd.speed());
        }
    }
}
