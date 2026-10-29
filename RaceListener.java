package me.astrax.race;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.Set;

/** Toàn bộ sự kiện của AstraXRace: dữ liệu, chiến đấu, EXP, skill item, GUI. */
public final class RaceListener implements Listener {

    private final AstraXRace plugin;

    public RaceListener(AstraXRace plugin) {
        this.plugin = plugin;
    }

    private RaceManager races() {
        return plugin.races();
    }

    private SkillManager skills() {
        return plugin.skills();
    }

    // ---------------- Dữ liệu người chơi ----------------
    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        if (e.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        races().preload(e.getUniqueId(), e.getName());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        races().attach(p);
        PlayerRaceData d = races().data(p);
        if (d != null && d.getRace() == null) {
            plugin.configs().send(p, "prompt-choose");
            ConfigurationSection c = plugin.configs().config();
            if (c.getBoolean("race.open-gui-on-join", true)) {
                long delay = Math.max(1, c.getLong("race.open-gui-delay-ticks", 60));
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    PlayerRaceData cur = races().data(p);
                    if (p.isOnline() && cur != null && cur.getRace() == null) plugin.guis().openMain(p);
                }, delay);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        races().detach(e.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            races().applyAttributes(p);
            skills().initEnergy(p);
            plugin.cosmetics().refresh(p);
            if (plugin.configs().config().getBoolean("skill-item.give-on-respawn", true) && races().raceOf(p) != null) {
                skills().giveSkillItem(p, false);
            }
        });
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        plugin.cosmetics().remove(e.getEntity());
        e.getDrops().removeIf(skills()::isSkillItem);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        plugin.cosmetics().refresh(e.getPlayer());
    }

    // ---------------- Chiến đấu ----------------
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        Entity victim = e.getEntity();

        if (e.getCause() == EntityDamageEvent.DamageCause.FALL
                && victim instanceof Player vp && skills().hasNoFall(vp.getUniqueId())) {
            e.setCancelled(true);
            return;
        }
        if (skills().isSkillDamage()) return;   // sát thương skill đã tự tính hệ số

        double mult = 1.0;
        Set<String> tags = Set.of();
        Player attacker = null;
        boolean melee = false;

        if (e instanceof EntityDamageByEntityEvent be) {
            Entity damager = be.getDamager();
            boolean arrow = false;
            if (damager instanceof Player p) {
                attacker = p;
                melee = true;
            } else if (damager instanceof Projectile pr && pr.getShooter() instanceof Player sp) {
                attacker = sp;
                arrow = damager instanceof AbstractArrow;
            }
            if (attacker != null) {
                mult *= races().outgoingMultiplier(attacker, melee, arrow);
                Race ar = races().raceOf(attacker);
                if (ar != null) tags = ar.getDamageTags();
            }
        }

        if (victim instanceof Player vp) {
            mult *= races().incomingMultiplier(vp, tags, e.getCause());
        }
        if (mult != 1.0) e.setDamage(e.getDamage() * mult);

        // Hiệu ứng khi đánh trúng (Wither/Fire của Demon...)
        if (melee && attacker != null && victim instanceof LivingEntity target && !victim.equals(attacker)) {
            Race ar = races().raceOf(attacker);
            if (ar != null) {
                for (EffectSpec spec : ar.getOnHit()) spec.apply(target);
            }
        }
    }

    // ---------------- Miễn nhiễm hiệu ứng xấu ----------------
    @EventHandler(ignoreCancelled = true)
    public void onPotion(EntityPotionEffectEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        PotionEffect added = e.getNewEffect();
        if (added == null) return;
        EntityPotionEffectEvent.Action a = e.getAction();
        if (a != EntityPotionEffectEvent.Action.ADDED && a != EntityPotionEffectEvent.Action.CHANGED) return;
        Race r = races().raceOf(p);
        if (r == null || r.getImmuneEffects().isEmpty()) return;
        String name = added.getType().getKey().getKey().toUpperCase(java.util.Locale.ROOT);
        if (r.getImmuneEffects().contains(name)) e.setCancelled(true);
    }

    // ---------------- EXP ----------------
    @EventHandler(ignoreCancelled = true)
    public void onKill(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        Player killer = dead.getKiller();
        if (killer == null || races().raceOf(killer) == null) return;

        ConfigurationSection c = plugin.configs().config();
        long amount;
        if (dead instanceof Player victim) {
            if (victim.equals(killer)) return;
            amount = c.getLong("exp.player-kill", 40);
        } else if (dead instanceof Enemy || dead instanceof Boss) {
            amount = c.getLong("exp.mobs." + dead.getType().name(), c.getLong("exp.mob-default", 5));
        } else {
            return;
        }
        if (amount <= 0) return;
        races().addExp(killer, amount, true);
        if (c.getBoolean("exp.show-actionbar", true)) {
            Race r = races().raceOf(killer);
            long shown = Math.round(amount * (1.0 + (r == null ? 0 : r.getExpBonus())));
            killer.sendActionBar(ConfigManager.color(plugin.configs().raw("exp-gained", "amount", String.valueOf(shown))));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onVanillaExp(PlayerExpChangeEvent e) {
        Race r = races().raceOf(e.getPlayer());
        if (r == null || r.getVanillaExpBonus() <= 0) return;
        e.setAmount((int) Math.ceil(e.getAmount() * (1.0 + r.getVanillaExpBonus())));
    }

    // ---------------- Kích hoạt skill bằng vật phẩm ----------------
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        ItemStack item = e.getItem();
        boolean tagged = skills().isSkillItem(item);
        boolean requireItem = plugin.configs().config().getBoolean("skill-item.require-item", true);
        if (requireItem && !tagged) return;
        if (!p.hasPermission("astraxrace.use")) return;
        if (tagged) e.setCancelled(true);

        PlayerRaceData d = races().data(p);
        Race r = races().raceOf(d);
        if (d == null || r == null) {
            if (tagged) plugin.configs().send(p, "no-race");
            return;
        }
        String trigger = p.isSneaking() ? SkillManager.TRIGGER_SHIFT : SkillManager.TRIGGER_RIGHT;
        Skill s = skills().findByTrigger(r, d.getLevel(), trigger);
        if (s != null) skills().castAndNotify(p, s);
    }

    // ---------------- GUI ----------------
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof RaceGuiHolder holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getInventory()) return;
        String action = holder.getAction(e.getRawSlot());
        if (action == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> plugin.guis().handleClick(p, action));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof RaceGuiHolder) e.setCancelled(true);
    }
}
