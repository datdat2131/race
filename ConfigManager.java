package me.astrax.race;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Nạp config.yml / races.yml / messages.yml và tiện ích tin nhắn, item. */
public final class ConfigManager {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder().character('&').hexColors().build();

    private final AstraXRace plugin;
    private FileConfiguration config;
    private FileConfiguration races;
    private FileConfiguration messages;

    public ConfigManager(AstraXRace plugin) {
        this.plugin = plugin;
    }

    /** Nạp (hoặc nạp lại) toàn bộ cấu hình từ đĩa. Không đụng tới dữ liệu người chơi. */
    public void load() {
        plugin.getDataFolder().mkdirs();
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
        races = loadYaml("races.yml", false);
        messages = loadYaml("messages.yml", true);
    }

    private FileConfiguration loadYaml(String name, boolean withDefaults) {
        File f = new File(plugin.getDataFolder(), name);
        if (!f.exists()) plugin.saveResource(name, false);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        if (withDefaults) {
            try (InputStream in = plugin.getResource(name)) {
                if (in != null) {
                    y.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
                }
            } catch (IOException ignored) {
                // dùng file trên đĩa
            }
        }
        return y;
    }

    public FileConfiguration config() { return config; }
    public FileConfiguration races() { return races; }
    public FileConfiguration messages() { return messages; }

    // ---------------- Tin nhắn ----------------
    public static String replace(String s, String... kv) {
        for (int i = 0; i + 1 < kv.length; i += 2) s = s.replace("{" + kv[i] + "}", kv[i + 1]);
        return s;
    }

    public static Component color(String s) {
        return LEGACY.deserialize(s);
    }

    public String raw(String key, String... kv) {
        String s = messages.getString(key);
        if (s == null) s = "&cThiếu thông báo: " + key;
        return replace(s, kv);
    }

    public Component comp(String key, String... kv) {
        return color(raw("prefix") + raw(key, kv));
    }

    public void send(CommandSender to, String key, String... kv) {
        to.sendMessage(comp(key, kv));
    }

    public void sendRaw(CommandSender to, String key, String... kv) {
        to.sendMessage(color(raw(key, kv)));
    }

    public void sendList(CommandSender to, String key, String... kv) {
        for (String line : messages.getStringList(key)) to.sendMessage(color(replace(line, kv)));
    }

    public static String num(double d) {
        if (d == Math.rint(d)) return String.valueOf((long) d);
        return String.format(Locale.US, "%.1f", d);
    }

    // ---------------- Item ----------------
    private static Component noItalic(Component c) {
        return c.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** Tạo item từ một section (material, name, lore, amount, glow) + dòng lore thêm. */
    public ItemStack item(ConfigurationSection sec, List<String> extraLore, String... kv) {
        Material m = Material.matchMaterial(sec.getString("material", "STONE"));
        if (m == null) m = Material.STONE;
        ItemStack it = new ItemStack(m, Math.max(1, sec.getInt("amount", 1)));
        ItemMeta meta = it.getItemMeta();
        meta.displayName(noItalic(color(replace(sec.getString("name", " "), kv))));
        List<Component> lore = new ArrayList<>();
        for (String l : sec.getStringList("lore")) lore.add(noItalic(color(replace(l, kv))));
        if (extraLore != null) for (String l : extraLore) lore.add(noItalic(color(l)));
        if (!lore.isEmpty()) meta.lore(lore);
        if (sec.getBoolean("glow", false)) meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        it.setItemMeta(meta);
        return it;
    }

    public ItemStack simple(Material material, String name, List<String> lore, boolean glow) {
        ItemStack it = new ItemStack(material);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(noItalic(color(name)));
        List<Component> lines = new ArrayList<>();
        for (String l : lore) lines.add(noItalic(color(l)));
        if (!lines.isEmpty()) meta.lore(lines);
        if (glow) meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        it.setItemMeta(meta);
        return it;
    }
}
