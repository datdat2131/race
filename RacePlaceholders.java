package me.astrax.race;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Placeholder: %astraxrace_<tên>%
 * race, race_id, rarity, level, exp, required, points, tickets, changes_left, pity_epic, pity_legend.
 * (Chỉ hoạt động với người chơi đang online vì dữ liệu được cache khi online.)
 */
public final class RacePlaceholders extends PlaceholderExpansion {

    private final AstraXRace plugin;

    public RacePlaceholders(AstraXRace plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "astraxrace";
    }

    @Override
    public String getAuthor() {
        return "Astrax";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    private static String plain(String s) {
        return s.replaceAll("(?i)&(#[0-9a-f]{6}|[0-9a-fk-or])", "");
    }

    @Override
    public String onRequest(OfflinePlayer offline, String params) {
        if (!(offline instanceof Player p)) return "";
        PlayerRaceData d = plugin.races().data(p);
        if (d == null) return "";
        Race r = plugin.races().raceOf(d);
        String none = plugin.configs().config().getString("placeholders.no-race", "Chưa có");

        switch (params.toLowerCase(Locale.ROOT)) {
            case "race":
                return r == null ? none : plain(r.getDisplayName());
            case "race_id":
                return r == null ? "" : r.getId();
            case "rarity":
                return r == null ? "" : plugin.rolls().rarityName(r);
            case "level":
                return String.valueOf(d.getLevel());
            case "exp":
                return String.valueOf(d.getExp());
            case "required":
                return d.getLevel() >= plugin.races().maxLevel() ? "MAX"
                        : String.valueOf(plugin.races().requiredExp(d.getLevel()));
            case "points":
                return String.valueOf(d.getPoints());
            case "tickets":
                return String.valueOf(plugin.rolls().ticketCount(p));
            case "changes_left":
                return plugin.races().changesLeft(d);
            case "pity_epic":
                return String.valueOf(d.getPityEpic());
            case "pity_legend":
                return String.valueOf(d.getPityLegend());
            default:
                return null;
        }
    }
}
