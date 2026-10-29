package me.astrax.race;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** Xử lý lệnh /race và tab completion. */
public final class CommandManager implements CommandExecutor, TabCompleter {

    private static final List<String> PLAYER_SUBS = List.of("choose", "info", "skill", "upgrade", "item", "level", "help");
    private static final List<String> ADMIN_SUBS = List.of("reload", "set", "reset", "giveexp");

    private final AstraXRace plugin;

    public CommandManager(AstraXRace plugin) {
        this.plugin = plugin;
    }

    private ConfigManager cm() {
        return plugin.configs();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                cm().sendList(sender, "help");
                return true;
            }
            if (!p.hasPermission("astraxrace.use")) {
                cm().send(p, "no-permission");
                return true;
            }
            plugin.guis().openMain(p);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "choose" -> choose(sender, args);
            case "info" -> info(sender);
            case "skill" -> skill(sender, args);
            case "upgrade" -> upgrade(sender);
            case "item" -> item(sender);
            case "level" -> level(sender, args);
            case "reload" -> reload(sender);
            case "set" -> set(sender, args);
            case "reset" -> reset(sender, args);
            case "giveexp" -> giveExp(sender, args);
            default -> cm().sendList(sender, "help");
        }
        return true;
    }

    // ---------------- Tiện ích ----------------
    private Player player(CommandSender s) {
        if (s instanceof Player p) {
            if (!p.hasPermission("astraxrace.use")) {
                cm().send(p, "no-permission");
                return null;
            }
            return p;
        }
        cm().send(s, "only-player");
        return null;
    }

    private boolean admin(CommandSender s) {
        if (s.hasPermission("astraxrace.admin")) return true;
        cm().send(s, "no-permission");
        return false;
    }

    private Player online(CommandSender s, String name) {
        Player t = Bukkit.getPlayerExact(name);
        if (t == null) cm().send(s, "player-offline");
        return t;
    }

    // ---------------- Lệnh người chơi ----------------
    private void choose(CommandSender sender, String[] args) {
        Player p = player(sender);
        if (p == null) return;
        if (args.length < 2) {
            cm().send(p, "usage", "usage", "/race choose <race>");
            return;
        }
        Race r = plugin.races().getRace(args[1]);
        if (r == null) {
            cm().send(p, "unknown-race", "race", args[1]);
            return;
        }
        switch (plugin.races().choose(p, r, false)) {
            case OK -> cm().send(p, "race-chosen", "race", r.getDisplayName());
            case SAME -> cm().send(p, "race-same");
            case NO_CHANGES -> cm().send(p, "race-no-changes");
            case NO_DATA -> cm().send(p, "data-not-loaded");
        }
    }

    private void info(CommandSender sender) {
        Player p = player(sender);
        if (p == null) return;
        PlayerRaceData d = plugin.races().data(p);
        Race r = plugin.races().raceOf(d);
        if (d == null || r == null) {
            cm().send(p, "no-race");
            return;
        }
        RaceManager rm = plugin.races();
        cm().sendList(p, "info",
                "race", r.getDisplayName(),
                "level", String.valueOf(d.getLevel()),
                "exp", String.valueOf(d.getExp()),
                "required", d.getLevel() >= rm.maxLevel() ? "MAX" : String.valueOf(rm.requiredExp(d.getLevel())),
                "points", String.valueOf(d.getPoints()),
                "health", ConfigManager.num(rm.healthOf(d, r)),
                "speed", ConfigManager.num(rm.speedOf(d, r) * 100),
                "strength", ConfigManager.num(rm.strengthOf(d, r) * 100),
                "critical", ConfigManager.num(rm.critOf(d, r) * 100),
                "changes_left", rm.changesLeft(d));
    }

    private void skill(CommandSender sender, String[] args) {
        Player p = player(sender);
        if (p == null) return;
        PlayerRaceData d = plugin.races().data(p);
        Race r = plugin.races().raceOf(d);
        if (d == null || r == null) {
            cm().send(p, "no-race");
            return;
        }
        if (args.length >= 2) {
            Skill s = r.getSkill(args[1]);
            if (s == null) {
                cm().send(p, "skill-unknown", "skill", args[1]);
                return;
            }
            plugin.skills().castAndNotify(p, s);
            return;
        }
        cm().sendRaw(p, "skill-list-header");
        for (Skill s : r.getSkills()) {
            String status;
            long left = plugin.skills().cooldownRemaining(p.getUniqueId(), r, s);
            if (d.getLevel() < s.getUnlockLevel()) status = cm().raw("skill-status-locked");
            else if (left > 0) status = cm().raw("skill-status-cooldown", "seconds", String.valueOf(left));
            else status = cm().raw("skill-status-ready");
            cm().sendRaw(p, "skill-list-line",
                    "id", s.getId(), "name", s.getName(),
                    "level", String.valueOf(s.getUnlockLevel()),
                    "cooldown", ConfigManager.num(s.getCooldown()),
                    "cost", ConfigManager.num(s.getEnergyCost()),
                    "trigger", s.getTrigger(), "status", status);
        }
    }

    private void upgrade(CommandSender sender) {
        Player p = player(sender);
        if (p != null) plugin.guis().openUpgrade(p);
    }

    private void item(CommandSender sender) {
        Player p = player(sender);
        if (p == null) return;
        if (plugin.races().raceOf(p) == null) {
            cm().send(p, "no-race");
            return;
        }
        plugin.skills().giveSkillItem(p, false);
        cm().send(p, "item-given");
    }

    private void level(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2) {
            if (!admin(sender)) return;
            target = online(sender, args[1]);
        } else {
            target = player(sender);
        }
        if (target == null) return;
        PlayerRaceData d = plugin.races().data(target);
        Race r = plugin.races().raceOf(d);
        if (d == null || r == null) {
            cm().send(sender, "no-race");
            return;
        }
        RaceManager rm = plugin.races();
        cm().sendList(sender, "level-info",
                "player", target.getName(),
                "race", r.getDisplayName(),
                "level", String.valueOf(d.getLevel()),
                "exp", String.valueOf(d.getExp()),
                "required", d.getLevel() >= rm.maxLevel() ? "MAX" : String.valueOf(rm.requiredExp(d.getLevel())),
                "points", String.valueOf(d.getPoints()));
    }

    // ---------------- Lệnh admin ----------------
    private void reload(CommandSender sender) {
        if (!admin(sender)) return;
        plugin.reloadAll();
        cm().send(sender, "reloaded");
    }

    private void set(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (args.length < 3) {
            cm().send(sender, "usage", "usage", "/race set <player> <race>");
            return;
        }
        Player t = online(sender, args[1]);
        if (t == null) return;
        Race r = plugin.races().getRace(args[2]);
        if (r == null) {
            cm().send(sender, "unknown-race", "race", args[2]);
            return;
        }
        if (plugin.races().choose(t, r, true) == RaceManager.ChooseResult.OK) {
            cm().send(sender, "race-set", "player", t.getName(), "race", r.getDisplayName());
            cm().send(t, "race-set-target", "race", r.getDisplayName());
        } else {
            cm().send(sender, "data-not-loaded");
        }
    }

    private void reset(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (args.length < 2) {
            cm().send(sender, "usage", "usage", "/race reset <player>");
            return;
        }
        Player t = online(sender, args[1]);
        if (t == null) return;
        plugin.races().resetPlayer(t);
        cm().send(sender, "race-reset", "player", t.getName());
        cm().send(t, "race-reset-target");
    }

    private void giveExp(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (args.length < 3) {
            cm().send(sender, "usage", "usage", "/race giveexp <player> <amount>");
            return;
        }
        Player t = online(sender, args[1]);
        if (t == null) return;
        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            cm().send(sender, "invalid-number");
            return;
        }
        if (amount <= 0) {
            cm().send(sender, "invalid-number");
            return;
        }
        if (plugin.races().raceOf(t) == null) {
            cm().send(sender, "no-race");
            return;
        }
        plugin.races().addExp(t, amount, false);
        cm().send(sender, "exp-given", "player", t.getName(), "amount", String.valueOf(amount));
    }

    // ---------------- Tab completion ----------------
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        boolean isAdmin = sender.hasPermission("astraxrace.admin");
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(PLAYER_SUBS);
            if (isAdmin) subs.addAll(ADMIN_SUBS);
            return filter(subs, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            switch (sub) {
                case "choose" -> {
                    return filter(raceIds(), args[1]);
                }
                case "skill" -> {
                    if (sender instanceof Player p) {
                        Race r = plugin.races().raceOf(p);
                        if (r != null) return filter(r.getSkillIds(), args[1]);
                    }
                }
                case "set", "reset", "giveexp", "level" -> {
                    if (isAdmin) return filter(playerNames(), args[1]);
                }
                default -> {
                }
            }
        } else if (args.length == 3 && isAdmin) {
            if (sub.equals("set")) return filter(raceIds(), args[2]);
            if (sub.equals("giveexp")) return filter(List.of("100", "500", "1000", "5000"), args[2]);
        }
        return List.of();
    }

    private List<String> raceIds() {
        List<String> ids = new ArrayList<>();
        for (Race r : plugin.races().getRaces()) ids.add(r.getId());
        return ids;
    }

    private List<String> playerNames() {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
        return names;
    }

    private static List<String> filter(Collection<String> options, String prefix) {
        String low = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(low)) out.add(o);
        }
        return out;
    }
}
