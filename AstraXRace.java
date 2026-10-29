package me.astrax.race;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/** AstraXRace - hệ thống chủng tộc cho BoxMine RPG (Paper 1.21.x, Java 21). */
public final class AstraXRace extends JavaPlugin {

    private ConfigManager configs;
    private StorageManager storage;
    private SkillManager skills;
    private RaceManager races;
    private GUIManager guis;
    private CosmeticManager cosmetics;
    private boolean ready;

    @Override
    public void onEnable() {
        configs = new ConfigManager(this);
        configs.load();

        storage = new StorageManager(this);
        try {
            storage.init();
        } catch (Exception ex) {
            getLogger().severe("Không thể khởi tạo lưu trữ: " + ex.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        skills = new SkillManager(this);
        races = new RaceManager(this);
        races.loadRaces();
        guis = new GUIManager(this);
        cosmetics = new CosmeticManager(this);

        CommandManager commands = new CommandManager(this);
        PluginCommand cmd = getCommand("race");
        if (cmd != null) {
            cmd.setExecutor(commands);
            cmd.setTabCompleter(commands);
        }
        getServer().getPluginManager().registerEvents(new RaceListener(this), this);

        // Người chơi đang online (khi /reload hoặc plugin được bật muộn)
        for (Player p : Bukkit.getOnlinePlayers()) races.attach(p);
        cosmetics.start();

        Bukkit.getScheduler().runTaskTimer(this, skills::tick, 20L, 20L);
        long autosave = Math.max(1, configs.config().getLong("storage.autosave-minutes", 5)) * 1200L;
        Bukkit.getScheduler().runTaskTimer(this, races::saveDirty, autosave, autosave);

        ready = true;
        getLogger().info("AstraXRace đã sẵn sàng.");
    }

    @Override
    public void onDisable() {
        if (!ready) {
            if (storage != null) storage.shutdown(List.of());
            return;
        }
        cosmetics.shutdown();
        for (Player p : Bukkit.getOnlinePlayers()) races.clearAttributes(p);
        storage.shutdown(races.snapshots());
    }

    /** /race reload: nạp lại cấu hình, giữ nguyên dữ liệu người chơi. */
    public void reloadAll() {
        configs.load();
        races.loadRaces();
        for (Player p : Bukkit.getOnlinePlayers()) {
            races.applyAttributes(p);
            skills.initEnergy(p);
        }
        cosmetics.start();
        cosmetics.refreshAll();
    }

    public ConfigManager configs() { return configs; }
    public StorageManager storage() { return storage; }
    public SkillManager skills() { return skills; }
    public RaceManager races() { return races; }
    public GUIManager guis() { return guis; }
    public CosmeticManager cosmetics() { return cosmetics; }
}
