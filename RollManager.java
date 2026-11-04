package me.astrax.race;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Vé roll tộc: dùng vé → quay ngẫu nhiên một chủng tộc theo độ hiếm (COMMON → LEGENDARY) có hệ thống bảo hiểm (pity).
 * Kết quả được quyết định và áp dụng NGAY khi dùng vé (không thể thoát game để né); toàn bộ hoạt ảnh chỉ là trình diễn:
 * GUI vòng quay đổi màu, vòng ma pháp + hạt hút vào quanh người chơi, nhịp tim hồi hộp, rồi nổ hiệu ứng theo độ hiếm.
 */
public final class RollManager {

    private record Rarity(String id, String name, String color, double weight, String sound, boolean broadcast,
                          Material pane, Color rgb, int bursts, boolean lightning) {}

    private static final Material[] RAINBOW = {
            Material.RED_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE, Material.YELLOW_STAINED_GLASS_PANE,
            Material.LIME_STAINED_GLASS_PANE, Material.CYAN_STAINED_GLASS_PANE, Material.LIGHT_BLUE_STAINED_GLASS_PANE,
            Material.BLUE_STAINED_GLASS_PANE, Material.PURPLE_STAINED_GLASS_PANE, Material.MAGENTA_STAINED_GLASS_PANE,
            Material.PINK_STAINED_GLASS_PANE
    };

    private final AstraXRace plugin;
    private final NamespacedKey ticketKey;
    private final Map<UUID, Long> pending = new HashMap<>();
    private final Set<UUID> rolling = new HashSet<>();

    public RollManager(AstraXRace plugin) {
        this.plugin = plugin;
        this.ticketKey = new NamespacedKey(plugin, "roll_ticket");
    }

    private ConfigurationSection rc() {
        ConfigurationSection s = plugin.configs().config().getConfigurationSection("roll");
        return s != null ? s : new MemoryConfiguration();
    }

    private static Color parseColor(String s, Color def) {
        try {
            return Color.fromRGB(Integer.parseInt(s.trim().replace("#", ""), 16));
        } catch (Exception e) {
            return def;
        }
    }

    // ---------------- Độ hiếm ----------------
    private Map<String, Rarity> rarities() {
        Map<String, Rarity> m = new LinkedHashMap<>();
        ConfigurationSection rs = rc().getConfigurationSection("rarities");
        if (rs != null) {
            for (String k : rs.getKeys(false)) {
                ConfigurationSection s = rs.getConfigurationSection(k);
                if (s == null) continue;
                String id = k.toUpperCase(Locale.ROOT);
                Material pane = Material.matchMaterial(s.getString("pane", "WHITE_STAINED_GLASS_PANE"));
                if (pane == null) pane = Material.WHITE_STAINED_GLASS_PANE;
                m.put(id, new Rarity(id, s.getString("name", k), s.getString("color", "&f"),
                        Math.max(0, s.getDouble("weight", 1)), s.getString("sound", "entity.experience_orb.pickup"),
                        s.getBoolean("broadcast", false), pane, parseColor(s.getString("rgb", "#FFFFFF"), Color.WHITE),
                        Math.max(0, s.getInt("fireworks", 0)), s.getBoolean("lightning", false)));
            }
        }
        if (m.isEmpty()) {
            m.put("COMMON", new Rarity("COMMON", "Thường", "&f", 1, "entity.experience_orb.pickup", false,
                    Material.WHITE_STAINED_GLASS_PANE, Color.WHITE, 0, false));
        }
        return m;
    }

    private String rarityKey(Race r, Map<String, Rarity> map) {
        return map.containsKey(r.getRarity()) ? r.getRarity() : map.keySet().iterator().next();
    }

    /** Dòng "Độ hiếm: ..." hiển thị trong GUI. */
    public String rarityLabel(Race r) {
        Map<String, Rarity> m = rarities();
        Rarity ra = m.get(rarityKey(r, m));
        return plugin.configs().raw("rarity-label", "color", ra.color(), "name", ra.name());
    }

    /** Tên độ hiếm (không màu), dùng cho placeholder. */
    public String rarityName(Race r) {
        Map<String, Rarity> m = rarities();
        return m.get(rarityKey(r, m)).name();
    }

    // ---------------- Vé ----------------
    public ItemStack createTicket(int amount) {
        ConfigurationSection sec = plugin.configs().config().getConfigurationSection("ticket");
        if (sec == null) sec = new MemoryConfiguration();
        ItemStack it = plugin.configs().item(sec, null);
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(ticketKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        it.setAmount(Math.max(1, Math.min(64, amount)));
        return it;
    }

    public boolean isTicket(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return false;
        return it.getItemMeta().getPersistentDataContainer().has(ticketKey, PersistentDataType.BYTE);
    }

    public int ticketCount(Player p) {
        int n = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (isTicket(it)) n += it.getAmount();
        }
        return n;
    }

    public void giveTickets(Player p, int amount) {
        int left = Math.max(1, amount);
        while (left > 0) {
            int n = Math.min(64, left);
            Map<Integer, ItemStack> rest = p.getInventory().addItem(createTicket(n));
            for (ItemStack r : rest.values()) p.getWorld().dropItemNaturally(p.getLocation(), r);
            left -= n;
        }
    }

    /** Lấy 1 vé (ưu tiên vé đang cầm). */
    private boolean takeOne(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (isTicket(hand)) {
            hand.setAmount(hand.getAmount() - 1);
            p.getInventory().setItemInMainHand(hand.getAmount() <= 0 ? null : hand);
            return true;
        }
        ItemStack[] c = p.getInventory().getContents();
        for (int i = 0; i < c.length; i++) {
            if (!isTicket(c[i])) continue;
            ItemStack it = c[i];
            it.setAmount(it.getAmount() - 1);
            p.getInventory().setItem(i, it.getAmount() <= 0 ? null : it);
            return true;
        }
        return false;
    }

    // ---------------- Dùng vé ----------------
    public void use(Player p) {
        ConfigManager cm = plugin.configs();
        if (!p.hasPermission("astraxrace.use")) {
            cm.send(p, "no-permission");
            return;
        }
        if (!rc().getBoolean("enabled", true)) {
            cm.send(p, "roll-disabled");
            return;
        }
        PlayerRaceData d = plugin.races().data(p);
        if (d == null) {
            cm.send(p, "data-not-loaded");
            return;
        }
        UUID id = p.getUniqueId();
        if (rolling.contains(id)) {
            cm.send(p, "roll-busy");
            return;
        }
        if (ticketCount(p) <= 0) {
            cm.send(p, "roll-no-ticket");
            return;
        }
        // Đã có tộc: xác nhận bằng cách dùng vé lần nữa trong vài giây
        if (d.getRace() != null && rc().getBoolean("confirm-when-has-race", true)
                && !p.hasPermission("astraxrace.bypass")) {
            long now = System.currentTimeMillis();
            Long until = pending.get(id);
            if (until == null || now > until) {
                int secs = Math.max(3, rc().getInt("confirm-seconds", 8));
                pending.put(id, now + secs * 1000L);
                Race cur = plugin.races().raceOf(d);
                cm.send(p, "roll-confirm", "race", cur == null ? d.getRace() : cur.getDisplayName(),
                        "seconds", String.valueOf(secs));
                return;
            }
        }
        pending.remove(id);

        Map<String, Rarity> all = rarities();
        Race result = pick(d, plugin.races().raceOf(d), all);
        if (result == null) {
            cm.send(p, "roll-disabled");
            return;
        }
        if (!takeOne(p)) {
            cm.send(p, "roll-no-ticket");
            return;
        }
        Rarity ra = all.get(rarityKey(result, all));
        plugin.races().choose(p, result, true);
        rolling.add(id);
        animate(p, result, ra);
    }

    // ---------------- Thuật toán roll ----------------
    private Race pick(PlayerRaceData d, Race current, Map<String, Rarity> all) {
        List<Race> pool = new ArrayList<>(plugin.races().getRaces());
        if (pool.isEmpty()) return null;
        if (rc().getBoolean("exclude-current", true) && current != null && pool.size() > 1) pool.remove(current);

        Map<String, List<Race>> byRarity = new LinkedHashMap<>();
        for (Race r : pool) byRarity.computeIfAbsent(rarityKey(r, all), k -> new ArrayList<>()).add(r);

        List<String> order = new ArrayList<>(all.keySet());
        int epicRank = order.indexOf("EPIC");
        int pityEpic = rc().getInt("pity.epic", 0);
        int pityLegend = rc().getInt("pity.legendary", 0);

        String chosen = null;
        if (pityLegend > 0 && d.getPityLegend() + 1 >= pityLegend && byRarity.containsKey("LEGENDARY")) {
            chosen = "LEGENDARY";
        } else if (pityEpic > 0 && epicRank >= 0 && d.getPityEpic() + 1 >= pityEpic) {
            chosen = weighted(all, byRarity, order, epicRank);
        }
        if (chosen == null) chosen = weighted(all, byRarity, order, 0);
        if (chosen == null) return null;

        List<Race> list = byRarity.get(chosen);
        Race result = list.get(ThreadLocalRandom.current().nextInt(list.size()));

        int rank = order.indexOf(chosen);
        d.setPityEpic(epicRank >= 0 && rank >= epicRank ? 0 : d.getPityEpic() + 1);
        d.setPityLegend("LEGENDARY".equals(chosen) ? 0 : d.getPityLegend() + 1);
        return result;
    }

    /** Chọn độ hiếm theo trọng số, chỉ xét các độ hiếm có tộc và có thứ hạng >= minRank. */
    private String weighted(Map<String, Rarity> all, Map<String, List<Race>> byRarity, List<String> order, int minRank) {
        double sum = 0;
        for (int i = minRank; i < order.size(); i++) {
            String k = order.get(i);
            if (byRarity.containsKey(k)) sum += all.get(k).weight();
        }
        if (sum <= 0) return null;
        double roll = ThreadLocalRandom.current().nextDouble() * sum;
        for (int i = minRank; i < order.size(); i++) {
            String k = order.get(i);
            if (!byRarity.containsKey(k)) continue;
            roll -= all.get(k).weight();
            if (roll <= 0) return k;
        }
        return null;
    }

    // ---------------- Hoạt ảnh vòng quay ----------------
    private boolean effectsOn() {
        return rc().getBoolean("effects.enabled", true);
    }

    private double density() {
        return Math.max(0.2, Math.min(3.0, rc().getDouble("effects.density", 1.0)));
    }

    private static int delayOf(int k, int steps) {
        double f = (double) k / steps;
        return 2 + (int) Math.round(f * f * f * 9);
    }

    private void animate(Player p, Race result, Rarity ra) {
        int steps = Math.max(10, Math.min(60, rc().getInt("animation-steps", 26)));
        int suspense = Math.max(0, Math.min(60, rc().getInt("effects.suspense-ticks", 14)));
        int total = suspense;
        for (int k = 0; k < steps; k++) total += delayOf(k, steps);

        List<Race> pool = new ArrayList<>(plugin.races().getRaces());
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        List<Race> seq = new ArrayList<>();
        for (int i = 0; i < steps + 9; i++) seq.add(pool.get(rnd.nextInt(pool.size())));
        seq.set(steps + 4, result);

        RaceGuiHolder h = new RaceGuiHolder(RaceGuiHolder.Type.ROLL, null);
        Inventory inv = Bukkit.createInventory(h, 27, ConfigManager.color(rc().getString("title", "&8Roll Chủng Tộc")));
        h.setInventory(inv);
        ConfigurationSection fs = plugin.configs().config().getConfigurationSection("gui.filler");
        ItemStack filler = plugin.configs().item(fs != null ? fs : new MemoryConfiguration(), null);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
        inv.setItem(4, plugin.configs().simple(Material.HOPPER, "&e▼", List.of(), false));
        inv.setItem(22, plugin.configs().simple(Material.AMETHYST_SHARD, "&e▲", List.of(), false));
        p.openInventory(inv);

        p.playSound(p.getLocation(), "block.beacon.activate", 1.0f, 1.2f);
        if (effectsOn()) startSpinEffects(p, total, suspense);
        step(p, inv, seq, result, ra, steps, 0, suspense);
    }

    private ItemStack reelItem(Race r, Map<String, Rarity> all, boolean glow) {
        Rarity ra = all.get(rarityKey(r, all));
        return plugin.configs().simple(r.getIcon(), r.getDisplayName(), List.of(ra.color() + ra.name()), glow);
    }

    /** Viền GUI chạy màu cầu vồng; truyền pane != null để tô một màu cố định. */
    private void paintBorder(Inventory inv, int phase, Material fixed) {
        for (int i = 0; i < 9; i++) {
            if (i != 4) inv.setItem(i, pane(fixed != null ? fixed : RAINBOW[(phase + i) % RAINBOW.length]));
            if (i != 4) inv.setItem(18 + i, pane(fixed != null ? fixed : RAINBOW[(phase + 9 - i) % RAINBOW.length]));
        }
    }

    private ItemStack pane(Material m) {
        return plugin.configs().simple(m, " ", List.of(), false);
    }

    private void step(Player p, Inventory inv, List<Race> seq, Race result, Rarity ra, int steps, int k, int suspense) {
        if (!p.isOnline()) {
            rolling.remove(p.getUniqueId());
            return;
        }
        Map<String, Rarity> all = rarities();
        for (int i = 0; i < 9; i++) {
            inv.setItem(9 + i, reelItem(seq.get(k + i), all, i == 4 && k == steps));
        }
        paintBorder(inv, k, null);
        Race center = seq.get(k + 4);
        p.sendActionBar(ConfigManager.color(plugin.configs().raw("roll-spin", "race", center.getDisplayName())));
        if (k < steps) {
            float prog = (float) k / steps;
            p.playSound(p.getLocation(), "block.note_block.hat", 0.8f, 0.8f + 1.2f * prog);
            if (k % 2 == 0) p.playSound(p.getLocation(), "block.note_block.chime", 0.5f, 0.6f + 1.4f * prog);
            Bukkit.getScheduler().runTaskLater(plugin, () -> step(p, inv, seq, result, ra, steps, k + 1, suspense),
                    delayOf(k, steps));
        } else {
            // Giây phút hồi hộp: dừng lại, nhịp tim dồn dập rồi mới lộ kết quả
            p.playSound(p.getLocation(), "entity.warden.heartbeat", 1.2f, 1.0f);
            Bukkit.getScheduler().runTaskLater(plugin, () -> reveal(p, inv, result, ra), Math.max(1, suspense));
        }
    }

    // ---------------- Hiệu ứng trong lúc quay ----------------
    private static void dust(World w, double x, double y, double z, Color c, float size) {
        w.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, new Particle.DustOptions(c, size));
    }

    private static Color lerp(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        return Color.fromRGB((int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    /** Vòng ma pháp xoay nhanh dần + xoắn ốc + hạt hút vào quanh người chơi; co lại và đập theo nhịp tim ở cuối. */
    private void startSpinEffects(Player p, int total, int suspense) {
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || t > total) {
                    cancel();
                    return;
                }
                spinTick(p, t, total, suspense);
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void spinTick(Player p, int t, int total, int suspense) {
        World w = p.getWorld();
        Location c = p.getLocation();
        double dens = density();
        double prog = Math.min(1.0, (double) t / Math.max(1, total));
        boolean heart = suspense > 0 && t >= total - suspense;
        double pulse = heart ? 0.25 * Math.abs(Math.sin((t - (total - suspense)) * 0.5)) : 0;
        Color col = lerp(Color.fromRGB(0x8A2BE2), Color.fromRGB(0xFFE9A8), prog);
        float size = (float) (1.0 + prog * 0.8);
        double rot = t * (0.12 + 0.6 * prog);
        double r = 2.4 - 1.2 * prog + pulse;

        int n = Math.max(12, (int) Math.round(26 * dens));
        for (int i = 0; i < n; i++) {
            double a = rot + 2 * Math.PI * i / n;
            dust(w, c.getX() + Math.cos(a) * r, c.getY() + 0.1, c.getZ() + Math.sin(a) * r, col, size);
        }
        // vòng trong quay ngược
        int m = Math.max(8, (int) Math.round(14 * dens));
        for (int i = 0; i < m; i++) {
            double a = -rot * 1.3 + 2 * Math.PI * i / m;
            dust(w, c.getX() + Math.cos(a) * r * 0.55, c.getY() + 0.12, c.getZ() + Math.sin(a) * r * 0.55,
                    Color.fromRGB(0xFFFFFF), 0.8f);
        }
        // ngôi sao 6 cánh (vẽ thưa)
        if (t % 3 == 0) {
            for (int i = 0; i < 6; i++) {
                double a1 = rot * 0.7 + 2 * Math.PI * i / 6, a2 = rot * 0.7 + 2 * Math.PI * ((i + 2) % 6) / 6;
                for (int s = 0; s <= 5; s++) {
                    double f = s / 5.0;
                    double x = Math.cos(a1) * r * 0.85 * (1 - f) + Math.cos(a2) * r * 0.85 * f;
                    double z = Math.sin(a1) * r * 0.85 * (1 - f) + Math.sin(a2) * r * 0.85 * f;
                    dust(w, c.getX() + x, c.getY() + 0.1, c.getZ() + z, col, 0.9f);
                }
            }
        }
        // xoắn ốc bay lên
        double y = 2.4 * ((t * 0.045) % 1.0);
        for (int k = 0; k < 3; k++) {
            double a = rot * 1.5 + k * 2 * Math.PI / 3;
            dust(w, c.getX() + Math.cos(a) * 1.0, c.getY() + y, c.getZ() + Math.sin(a) * 1.0, Color.fromRGB(0xFFFFFF), 1.0f);
        }
        // hạt sáng bay hút vào người chơi
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int conv = Math.max(2, (int) Math.round(6 * dens * (0.5 + prog)));
        for (int i = 0; i < conv; i++) {
            double ang = rnd.nextDouble() * 2 * Math.PI;
            double el = rnd.nextDouble() * 1.2 - 0.2;
            double dist = 4.5 * (1 - 0.5 * prog);
            double x = c.getX() + Math.cos(ang) * dist, yy = c.getY() + 1 + el * 2, z = c.getZ() + Math.sin(ang) * dist;
            w.spawnParticle(Particle.END_ROD, x, yy, z, 0, (c.getX() - x) * 0.12, (c.getY() + 1 - yy) * 0.12,
                    (c.getZ() - z) * 0.12, 1.0);
        }
        // âm thanh năng lượng dâng lên + nhịp tim ở cuối
        if (t % 8 == 0) p.playSound(c, "block.beacon.ambient", 0.7f, (float) (0.5 + 1.3 * prog));
        if (heart && (t - (total - suspense)) % 6 == 0) p.playSound(c, "entity.warden.heartbeat", 1.2f, 1.0f);
    }

    // ---------------- Lộ kết quả ----------------
    private void reveal(Player p, Inventory inv, Race result, Rarity ra) {
        if (!p.isOnline()) {
            rolling.remove(p.getUniqueId());
            return;
        }
        ConfigManager cm = plugin.configs();
        String rarityText = ra.color() + ra.name();

        // GUI: viền đổi sang màu độ hiếm, biểu ngữ độ hiếm
        paintBorder(inv, 0, ra.pane());
        inv.setItem(4, cm.simple(Material.NETHER_STAR, ra.color() + "★ " + ra.name().toUpperCase(Locale.ROOT) + " ★",
                List.of(result.getDisplayName()), true));
        inv.setItem(22, cm.simple(Material.NETHER_STAR, ra.color() + "★ " + ra.name().toUpperCase(Locale.ROOT) + " ★",
                List.of(result.getDisplayName()), true));
        inv.setItem(13, reelItem(result, rarities(), true));

        // Âm thanh + tiêu đề
        p.playSound(p.getLocation(), ConfigManager.soundKey(ra.sound()), 1.0f, 1.0f);
        p.showTitle(Title.title(ConfigManager.color(cm.raw("roll-title", "race", result.getDisplayName())),
                ConfigManager.color(cm.raw("roll-subtitle", "rarity", rarityText))));
        cm.send(p, "roll-result", "race", result.getDisplayName(), "rarity", rarityText);
        if (ra.broadcast()) {
            Bukkit.broadcast(cm.comp("roll-broadcast", "player", p.getName(), "race", result.getDisplayName(),
                    "rarity", rarityText));
        }
        if (effectsOn()) revealEffects(p, ra);

        long close = Math.max(10, rc().getLong("close-delay-ticks", 60));
        UUID id = p.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            rolling.remove(id);
            if (p.isOnline() && p.getOpenInventory().getTopInventory().getHolder() instanceof RaceGuiHolder hh
                    && hh.getType() == RaceGuiHolder.Type.ROLL) {
                p.closeInventory();
            }
        }, close);
    }

    /** Nổ hiệu ứng theo độ hiếm: cầu nổ, sóng xung kích, cột sáng, pháo hoa (particle), sét (chỉ hiệu ứng). */
    private void revealEffects(Player p, Rarity ra) {
        double dens = density();
        Color col = ra.rgb();
        int power = ra.bursts();                       // độ hoành tráng ~ số đợt pháo hoa

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || t >= 18) {
                    cancel();
                    return;
                }
                World w = p.getWorld();
                Location c = p.getLocation();
                double e = 1 - Math.pow(1 - (t + 1) / 18.0, 2);
                // cầu nổ
                double sr = (2.5 + power * 0.4) * e;
                int n = (int) Math.round(46 * dens);
                for (int i = 0; i < n; i++) {
                    double phi = Math.acos(1 - 2.0 * (i + 0.5) / n);
                    double th = Math.PI * (1 + Math.sqrt(5)) * (i + 0.5);
                    dust(w, c.getX() + sr * Math.sin(phi) * Math.cos(th), c.getY() + 1 + sr * Math.cos(phi),
                            c.getZ() + sr * Math.sin(phi) * Math.sin(th), col, 1.6f);
                }
                // sóng xung kích dưới chân
                double wr = (4.0 + power * 0.6) * e;
                int wn = (int) Math.round(40 * dens);
                for (int i = 0; i < wn; i++) {
                    double a = 2 * Math.PI * i / wn;
                    dust(w, c.getX() + Math.cos(a) * wr, c.getY() + 0.15, c.getZ() + Math.sin(a) * wr, col, 1.4f);
                }
                // cột sáng (từ rare trở lên)
                if (power > 0) {
                    double h = (5 + power) * e;
                    for (double yy = 0; yy <= h; yy += 0.5) {
                        for (int i = 0; i < 6; i++) {
                            double a = 2 * Math.PI * i / 6 + t * 0.4 + yy;
                            w.spawnParticle(Particle.END_ROD, c.getX() + Math.cos(a) * 0.5, c.getY() + yy,
                                    c.getZ() + Math.sin(a) * 0.5, 0, 0, 0.05, 0, 1.0);
                        }
                    }
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Pháo hoa bằng particle (an toàn: không gây sát thương như Firework thật)
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int b = 0; b < power; b++) {
            double ox = rnd.nextDouble(-3, 3), oz = rnd.nextDouble(-3, 3), oy = 1.5 + rnd.nextDouble(0, 3);
            float pitch = 0.9f + (float) rnd.nextDouble(0, 0.4);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;
                Location l = p.getLocation().add(ox, oy, oz);
                p.getWorld().spawnParticle(Particle.FIREWORK, l, 30, 0.4, 0.4, 0.4, 0.15);
                p.getWorld().spawnParticle(Particle.DUST, l, 16, 0.5, 0.5, 0.5, 0,
                        new Particle.DustOptions(col, 1.8f));
                p.playSound(l, "entity.firework_rocket.blast", 1.0f, pitch);
                p.playSound(l, "entity.firework_rocket.twinkle", 1.0f, pitch);
            }, 3L + b * 4L);
        }

        // Sét giáng (chỉ hiệu ứng, không sát thương/cháy) cho độ hiếm cao nhất
        if (ra.lightning()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;
                Location c = p.getLocation();
                for (int i = 0; i < 3; i++) {
                    Location l = c.clone().add(rnd.nextDouble(-4, 4), 0, rnd.nextDouble(-4, 4));
                    p.getWorld().strikeLightningEffect(l);
                }
                p.playSound(c, "entity.lightning_bolt.thunder", 1.0f, 1.0f);
                p.playSound(c, "entity.ender_dragon.growl", 0.8f, 1.2f);
            }, 2L);
        }
    }

    // ---------------- Dọn dẹp ----------------
    public void onQuit(Player p) {
        pending.remove(p.getUniqueId());
        rolling.remove(p.getUniqueId());
    }

    public void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof RaceGuiHolder hh
                    && hh.getType() == RaceGuiHolder.Type.ROLL) {
                p.closeInventory();
            }
        }
        pending.clear();
        rolling.clear();
    }
}
