package me.astrax.race;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Lưu dữ liệu người chơi bằng SQLite (mặc định) hoặc YAML. Ghi bất đồng bộ trên một luồng riêng. */
public final class StorageManager {

    private final AstraXRace plugin;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AstraXRace-Storage");
        t.setDaemon(true);
        return t;
    });
    private final Object yamlLock = new Object();

    private boolean sqlite;
    private String url;
    private File yamlFile;
    private YamlConfiguration yaml;

    public StorageManager(AstraXRace plugin) {
        this.plugin = plugin;
    }

    public void init() throws Exception {
        String type = plugin.configs().config().getString("storage.type", "sqlite");
        sqlite = !type.equalsIgnoreCase("yaml");
        plugin.getDataFolder().mkdirs();
        if (sqlite) {
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException ignored) {
                // DriverManager sẽ tự tìm
            }
            url = "jdbc:sqlite:" + new File(plugin.getDataFolder(), "players.db").getAbsolutePath();
            try (Connection c = DriverManager.getConnection(url); Statement st = c.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS race_players ("
                        + "uuid TEXT PRIMARY KEY, name TEXT, race TEXT, level INTEGER NOT NULL, exp INTEGER NOT NULL, "
                        + "points INTEGER NOT NULL, changes_used INTEGER NOT NULL, upgrades TEXT)");
            }
        } else {
            yamlFile = new File(plugin.getDataFolder(), "players.yml");
            yaml = YamlConfiguration.loadConfiguration(yamlFile);
        }
    }

    /** Đọc dữ liệu; trả về null nếu người chơi chưa có bản ghi. Có thể gọi từ luồng bất kỳ. */
    public PlayerRaceData load(UUID uuid, String name) throws Exception {
        if (sqlite) {
            try (Connection c = DriverManager.getConnection(url);
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT race, level, exp, points, changes_used, upgrades FROM race_players WHERE uuid=?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    PlayerRaceData d = new PlayerRaceData(uuid, name);
                    d.setRace(rs.getString("race"));
                    d.setLevel(Math.max(1, rs.getInt("level")));
                    d.setExp(rs.getLong("exp"));
                    d.setPoints(rs.getInt("points"));
                    d.setChangesUsed(rs.getInt("changes_used"));
                    d.parseUpgrades(rs.getString("upgrades"));
                    d.setDirty(false);
                    return d;
                }
            }
        }
        synchronized (yamlLock) {
            ConfigurationSection s = yaml.getConfigurationSection("players." + uuid);
            if (s == null) return null;
            PlayerRaceData d = new PlayerRaceData(uuid, name);
            d.setRace(s.getString("race"));
            d.setLevel(Math.max(1, s.getInt("level", 1)));
            d.setExp(s.getLong("exp", 0));
            d.setPoints(s.getInt("points", 0));
            d.setChangesUsed(s.getInt("changes-used", 0));
            d.parseUpgrades(s.getString("upgrades", ""));
            d.setDirty(false);
            return d;
        }
    }

    /** Ghi bất đồng bộ một bản sao (snapshot) đã chụp ở luồng chính. */
    public void saveAsync(PlayerRaceData snapshot) {
        if (executor.isShutdown()) {
            write(snapshot);
            return;
        }
        executor.execute(() -> write(snapshot));
    }

    private void write(PlayerRaceData d) {
        try {
            if (sqlite) {
                try (Connection c = DriverManager.getConnection(url);
                     PreparedStatement ps = c.prepareStatement(
                             "INSERT OR REPLACE INTO race_players (uuid, name, race, level, exp, points, changes_used, upgrades) "
                                     + "VALUES (?,?,?,?,?,?,?,?)")) {
                    ps.setString(1, d.getUuid().toString());
                    ps.setString(2, d.getName());
                    ps.setString(3, d.getRace());
                    ps.setInt(4, d.getLevel());
                    ps.setLong(5, d.getExp());
                    ps.setInt(6, d.getPoints());
                    ps.setInt(7, d.getChangesUsed());
                    ps.setString(8, d.serializeUpgrades());
                    ps.executeUpdate();
                }
            } else {
                synchronized (yamlLock) {
                    String p = "players." + d.getUuid() + ".";
                    yaml.set(p + "name", d.getName());
                    yaml.set(p + "race", d.getRace());
                    yaml.set(p + "level", d.getLevel());
                    yaml.set(p + "exp", d.getExp());
                    yaml.set(p + "points", d.getPoints());
                    yaml.set(p + "changes-used", d.getChangesUsed());
                    yaml.set(p + "upgrades", d.serializeUpgrades());
                    yaml.save(yamlFile);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Lỗi lưu dữ liệu " + d.getName() + ": " + e.getMessage());
        }
    }

    /** Chờ các tác vụ ghi đang chờ, ghi snapshot cuối cùng rồi đóng. */
    public void shutdown(Collection<PlayerRaceData> finalSnapshots) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Một số thao tác lưu chưa kịp hoàn tất.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        for (PlayerRaceData d : finalSnapshots) write(d);
    }
}
