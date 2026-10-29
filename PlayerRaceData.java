package me.astrax.race;

import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;

/** Dữ liệu race của một người chơi (được cache khi online, lưu qua StorageManager). */
public final class PlayerRaceData {

    private final UUID uuid;
    private String name;
    private String race;          // null = chưa chọn
    private int level = 1;
    private long exp;
    private int points;
    private int changesUsed;
    private final Map<String, Integer> upgrades = new HashMap<>();

    private boolean dirty;
    private boolean persistable = true;   // false nếu tải dữ liệu lỗi -> không ghi đè DB

    public PlayerRaceData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRace() { return race; }
    public void setRace(String race) { this.race = race; dirty = true; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; dirty = true; }

    public long getExp() { return exp; }
    public void setExp(long exp) { this.exp = exp; dirty = true; }

    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; dirty = true; }

    public int getChangesUsed() { return changesUsed; }
    public void setChangesUsed(int changesUsed) { this.changesUsed = changesUsed; dirty = true; }

    public int getUpgrade(String attr) { return upgrades.getOrDefault(attr, 0); }
    public void setUpgrade(String attr, int value) { upgrades.put(attr, value); dirty = true; }
    public void clearUpgrades() { upgrades.clear(); dirty = true; }

    public boolean isDirty() { return dirty; }
    public void setDirty(boolean dirty) { this.dirty = dirty; }
    public boolean isPersistable() { return persistable; }
    public void setPersistable(boolean persistable) { this.persistable = persistable; }

    public String serializeUpgrades() {
        StringJoiner j = new StringJoiner(",");
        for (Map.Entry<String, Integer> e : upgrades.entrySet()) {
            if (e.getValue() > 0) j.add(e.getKey() + "=" + e.getValue());
        }
        return j.toString();
    }

    public void parseUpgrades(String s) {
        upgrades.clear();
        if (s == null || s.isBlank()) return;
        for (String part : s.split(",")) {
            String[] kv = part.split("=");
            if (kv.length != 2) continue;
            try {
                upgrades.put(kv[0].trim(), Integer.parseInt(kv[1].trim()));
            } catch (NumberFormatException ignored) {
                // bỏ qua mục lỗi
            }
        }
    }

    /** Bản sao độc lập để ghi bất đồng bộ. */
    public PlayerRaceData copy() {
        PlayerRaceData c = new PlayerRaceData(uuid, name);
        c.race = race;
        c.level = level;
        c.exp = exp;
        c.points = points;
        c.changesUsed = changesUsed;
        c.upgrades.putAll(upgrades);
        return c;
    }
}
