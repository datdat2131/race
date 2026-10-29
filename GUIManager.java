package me.astrax.race;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Xây dựng và xử lý các GUI: chọn race, chi tiết race, nâng cấp. Toàn bộ giao diện đọc từ config.yml. */
public final class GUIManager {

    private final AstraXRace plugin;

    public GUIManager(AstraXRace plugin) {
        this.plugin = plugin;
    }

    private ConfigManager cm() {
        return plugin.configs();
    }

    private ConfigurationSection sec(String path) {
        ConfigurationSection s = cm().config().getConfigurationSection(path);
        return s != null ? s : new MemoryConfiguration();
    }

    private Inventory create(RaceGuiHolder holder, String title, int rows) {
        int r = Math.max(1, Math.min(6, rows));
        Inventory inv = Bukkit.createInventory(holder, r * 9, ConfigManager.color(title));
        holder.setInventory(inv);
        ItemStack filler = cm().item(sec("gui.filler"), null);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
        return inv;
    }

    private void place(Inventory inv, RaceGuiHolder h, int slot, ItemStack item, String action) {
        if (slot < 0 || slot >= inv.getSize()) return;
        inv.setItem(slot, item);
        if (action != null) h.setAction(slot, action);
        else h.setAction(slot, "none");
    }

    private static String pct(double v) {
        return ConfigManager.num(v * 100.0);
    }

    // ---------------- GUI chọn race ----------------
    public void openMain(Player p) {
        ConfigurationSection g = sec("gui.main");
        RaceGuiHolder h = new RaceGuiHolder(RaceGuiHolder.Type.MAIN, null);
        Inventory inv = create(h, g.getString("title", "&8Chọn Chủng tộc"), g.getInt("rows", 3));
        PlayerRaceData d = plugin.races().data(p);

        int next = 10;
        for (Race r : plugin.races().getRaces()) {
            int slot = r.getGuiSlot();
            if (slot < 0 || slot >= inv.getSize() || h.hasAction(slot)) {
                while (next < inv.getSize() && h.hasAction(next)) next++;
                slot = next;
            }
            boolean current = d != null && r.getId().equals(d.getRace());
            List<String> lore = new ArrayList<>(r.getDescription());
            lore.add(" ");
            lore.add(g.getString(current ? "status-current" : "status-other", ""));
            lore.addAll(g.getStringList("lore"));
            place(inv, h, slot, cm().simple(r.getIcon(), r.getDisplayName(), lore, current), "race:" + r.getId());
        }
        ConfigurationSection close = sec("gui.main.close");
        place(inv, h, close.getInt("slot", inv.getSize() - 5), cm().item(close, null), "close");
        p.openInventory(inv);
    }

    // ---------------- GUI chi tiết race ----------------
    public void openDetail(Player p, Race r) {
        ConfigurationSection g = sec("gui.detail");
        RaceGuiHolder h = new RaceGuiHolder(RaceGuiHolder.Type.DETAIL, r.getId());
        Inventory inv = create(h, ConfigManager.replace(g.getString("title", "&8{race}"), "race", r.getDisplayName()),
                g.getInt("rows", 5));
        PlayerRaceData d = plugin.races().data(p);

        place(inv, h, g.getInt("info-slot", 4), cm().simple(r.getIcon(), r.getDisplayName(), r.getDescription(), false), null);

        ConfigurationSection st = sec("gui.detail.stats");
        place(inv, h, st.getInt("slot", 19), cm().item(st, null,
                "health", ConfigManager.num(r.getHealth()),
                "hearts", ConfigManager.num(r.getHealth() / 2.0),
                "speed", pct(r.getSpeed()),
                "strength", pct(r.getStrength()),
                "jump", pct(r.getJump()),
                "knockback", pct(r.getKnockback()),
                "critical", pct(r.getCritical())), null);

        ConfigurationSection sk = sec("gui.detail.skills");
        List<String> skillLore = new ArrayList<>();
        for (Skill s : r.getSkills()) {
            skillLore.add(ConfigManager.replace(sk.getString("line", "&e{name}"),
                    "name", s.getName(),
                    "level", String.valueOf(s.getUnlockLevel()),
                    "cooldown", ConfigManager.num(s.getCooldown()),
                    "trigger", s.getTrigger()));
            for (String line : s.getDescription()) skillLore.add(sk.getString("desc-prefix", "  &7") + line);
        }
        place(inv, h, sk.getInt("slot", 21), cm().item(sk, skillLore), null);

        ConfigurationSection su = sec("gui.detail.strengths");
        List<String> strong = new ArrayList<>();
        for (String s : r.getStrengths()) strong.add(su.getString("prefix", "&a+ &7") + s);
        place(inv, h, su.getInt("slot", 23), cm().item(su, strong), null);

        ConfigurationSection sw = sec("gui.detail.weaknesses");
        List<String> weak = new ArrayList<>();
        for (String s : r.getWeaknesses()) weak.add(sw.getString("prefix", "&c- &7") + s);
        place(inv, h, sw.getInt("slot", 25), cm().item(sw, weak), null);

        ConfigurationSection back = sec("gui.detail.back");
        place(inv, h, back.getInt("slot", 36), cm().item(back, null), "back");

        ConfigurationSection confirm = sec("gui.detail.confirm");
        place(inv, h, confirm.getInt("slot", 40), cm().item(confirm, null,
                "race", r.getDisplayName(),
                "changes_left", plugin.races().changesLeft(d)), "confirm:" + r.getId());

        ConfigurationSection close = sec("gui.detail.close");
        place(inv, h, close.getInt("slot", 44), cm().item(close, null), "close");
        p.openInventory(inv);
    }

    // ---------------- GUI nâng cấp ----------------
    public void openUpgrade(Player p) {
        PlayerRaceData d = plugin.races().data(p);
        Race r = plugin.races().raceOf(d);
        if (d == null || r == null) {
            cm().send(p, "no-race");
            return;
        }
        ConfigurationSection g = sec("gui.upgrade");
        RaceGuiHolder h = new RaceGuiHolder(RaceGuiHolder.Type.UPGRADE, r.getId());
        Inventory inv = create(h, g.getString("title", "&8Nâng cấp"), g.getInt("rows", 3));

        ConfigurationSection info = sec("gui.upgrade.info");
        place(inv, h, info.getInt("slot", 4), cm().item(info, null,
                "level", String.valueOf(d.getLevel()),
                "race", r.getDisplayName(),
                "exp", String.valueOf(d.getExp()),
                "required", d.getLevel() >= plugin.races().maxLevel() ? "MAX" : String.valueOf(plugin.races().requiredExp(d.getLevel())),
                "points", String.valueOf(d.getPoints())), null);

        for (String attr : RaceManager.UPGRADES) {
            ConfigurationSection us = sec("upgrades." + attr);
            int level = d.getUpgrade(attr);
            int max = plugin.races().upgradeMax(attr);
            double mult = us.getDouble("display-multiplier", 1.0);
            String unit = us.getString("unit", "");
            boolean maxed = level >= max;
            List<String> extra = new ArrayList<>();
            extra.add(" ");
            extra.add(g.getString(maxed ? "maxed-line" : "click-line", ""));
            place(inv, h, us.getInt("slot", 10), cm().item(us, extra,
                    "desc", us.getString("description", ""),
                    "level", String.valueOf(level),
                    "max", String.valueOf(max),
                    "bonus", ConfigManager.num(plugin.races().upgradeBonus(d, attr) * mult) + unit,
                    "per", ConfigManager.num(plugin.races().upgradePer(attr) * mult) + unit,
                    "cost", String.valueOf(plugin.races().upgradeCost(attr))), "upgrade:" + attr);
        }
        ConfigurationSection close = sec("gui.upgrade.close");
        place(inv, h, close.getInt("slot", inv.getSize() - 5), cm().item(close, null), "close");
        p.openInventory(inv);
    }

    // ---------------- Xử lý click ----------------
    public void handleClick(Player p, String action) {
        if (action == null || action.equals("none")) return;
        if (action.equals("close")) {
            p.closeInventory();
        } else if (action.equals("back")) {
            openMain(p);
        } else if (action.startsWith("race:")) {
            Race r = plugin.races().getRace(action.substring(5));
            if (r != null) openDetail(p, r);
        } else if (action.startsWith("confirm:")) {
            confirm(p, action.substring(8));
        } else if (action.startsWith("upgrade:")) {
            upgrade(p, action.substring(8));
        }
    }

    private void confirm(Player p, String raceId) {
        Race r = plugin.races().getRace(raceId);
        if (r == null) {
            cm().send(p, "unknown-race", "race", raceId);
            return;
        }
        RaceManager.ChooseResult res = plugin.races().choose(p, r, false);
        switch (res) {
            case OK -> {
                cm().send(p, "race-chosen", "race", r.getDisplayName());
                p.closeInventory();
            }
            case SAME -> cm().send(p, "race-same");
            case NO_CHANGES -> cm().send(p, "race-no-changes");
            case NO_DATA -> cm().send(p, "data-not-loaded");
        }
    }

    private void upgrade(Player p, String attr) {
        RaceManager.UpgradeResult res = plugin.races().upgrade(p, attr);
        PlayerRaceData d = plugin.races().data(p);
        String name = attr.toUpperCase(Locale.ROOT);
        switch (res) {
            case OK -> cm().send(p, "upgrade-ok", "attr", name, "level", String.valueOf(d == null ? 0 : d.getUpgrade(attr)));
            case MAXED -> cm().send(p, "upgrade-max");
            case NO_POINTS -> cm().send(p, "upgrade-no-points", "cost", String.valueOf(plugin.races().upgradeCost(attr)));
            case NO_RACE -> cm().send(p, "no-race");
            case INVALID -> {
                // không làm gì
            }
        }
        if (res == RaceManager.UpgradeResult.OK) openUpgrade(p);
    }
}
