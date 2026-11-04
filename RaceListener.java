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
import org.bukkit.potion.PotionEffect;

import java.util.Locale;
import java.util.Set;

/** Sự kiện của AstraXRace: dữ liệu người chơi, chiến đấu (nội tại + buff), EXP, vé roll, GUI. */
public final class RaceListener implements Listener {

    private final AstraXRace plugin;

    public RaceListener(AstraXRace plugin) {
        this.plugin = plugin;
    }

    private RaceManager races() {
        return plugin.races();
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
            plugin.cosmetics().refresh(p);
        });
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        plugin.cosmetics().remove(e.getEntity());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        plugin.cosmetics().refresh(e.getPlayer());
    }

    // ---------------- Chiến đấu: nội tại + buff ----------------
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        Entity victim = e.getEntity();
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

        // Hiệu ứng khi đánh trúng (nội tại on-hit)
        if (melee && attacker != null && victim instanceof LivingEntity target && !victim.equals(attacker)) {
            Race ar = races().raceOf(attacker);
            if (ar != null) {
                for (EffectSpec spec : ar.getOnHit()) spec.apply(target);
            }
        }
    }

    /** Buff "khi bị đánh" – xét sau cùng, chỉ khi sát thương thực sự được gây ra. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && e.getFinalDamage() > 0) {
            plugin.buffs().onHurt(p);
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
        String name = added.getType().getKey().getKey().toUpperCase(Locale.ROOT);
        if (r.getImmuneEffects().contains(name)) e.setCancelled(true);
    }

    // ---------------- EXP + buff khi hạ gục ----------------
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
        plugin.buffs().onKill(killer);
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

    // ---------------- Vé roll tộc ----------------
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        if (plugin.rolls().isTicket(e.getItem())) {
            e.setCancelled(true);
            plugin.rolls().use(e.getPlayer());
        }
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
