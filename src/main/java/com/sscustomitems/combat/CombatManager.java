package com.sscustomitems.combat;

import com.sscustomitems.SSCustomItemsPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public final class CombatManager implements Listener {

    private final SSCustomItemsPlugin plugin;
    private final DamageTypeManager damageTypeManager;
    private final NmsCombat nmsCombat;

    public CombatManager(SSCustomItemsPlugin plugin, DamageTypeManager damageTypeManager) {
        this.plugin = plugin;
        this.damageTypeManager = damageTypeManager;
        this.nmsCombat = new NmsCombat();
    }

    public void damageLook(Player player, String damageType, double maxDistance,
                           double spread, double gravity, double damage) {
        List<LivingEntity> targets = new ArrayList<>();
        Location impact = Trajectory.raycast(player, maxDistance, spread, gravity, targets);
        if (impact == null) return;
        for (LivingEntity target : targets) applyDamage(player, target, damageType, damage);
    }

    public void fireLook(Player player, int fireSeconds, double maxDistance,
                         double spread, double gravity) {
        List<LivingEntity> targets = new ArrayList<>();
        Location impact = Trajectory.raycast(player, maxDistance, spread, gravity, targets);
        if (impact == null) return;
        for (LivingEntity target : targets) target.setFireTicks(fireSeconds * 20);
    }

    public void damageLine(Player player, String damageType, double radius,
                           double spread, double gravity, double damage) {
        List<LivingEntity> direct = new ArrayList<>();
        Location impact = Trajectory.raycast(player, 64.0, spread, gravity, direct);
        if (impact == null) return;
        for (LivingEntity target : entitiesInRadius(impact, radius, player))
            applyDamage(player, target, damageType, damage);
    }

    public void fireLine(Player player, int fireSeconds, double radius,
                         double spread, double gravity) {
        List<LivingEntity> direct = new ArrayList<>();
        Location impact = Trajectory.raycast(player, 64.0, spread, gravity, direct);
        if (impact == null) return;
        for (LivingEntity target : entitiesInRadius(impact, radius, player))
            target.setFireTicks(fireSeconds * 20);
    }

    public void killLook(Player player, double maxDistance, double spread, double gravity) {
        List<LivingEntity> targets = new ArrayList<>();
        Location impact = Trajectory.raycast(player, maxDistance, spread, gravity, targets);
        if (impact == null) return;
        for (LivingEntity target : targets) target.setHealth(0.0);
    }

    public void killLine(Player player, double radius, double spread, double gravity) {
        List<LivingEntity> direct = new ArrayList<>();
        Location impact = Trajectory.raycast(player, 64.0, spread, gravity, direct);
        if (impact == null) return;
        for (LivingEntity target : entitiesInRadius(impact, radius, player)) target.setHealth(0.0);
    }

    private void applyDamage(Player player, LivingEntity target, String damageType, double damage) {
        CustomDamageType custom = damageTypeManager.get(damageType);
        String baseType = custom != null ? custom.baseType() : damageType;
        nmsCombat.damage(player, target, baseType, damage);
    }

    private static List<LivingEntity> entitiesInRadius(Location center, double radius, Player exclude) {
        List<LivingEntity> result = new ArrayList<>();
        double r2 = radius * radius;
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(exclude)) continue;
            if (living.getLocation().distanceSquared(center) <= r2) result.add(living);
        }
        return result;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.CUSTOM) return;
        if (victim.getHealth() - event.getFinalDamage() > 0) return;
        CustomDamageType type = damageTypeManager.findActiveType(victim.getUniqueId());
        if (type == null) return;
        damageTypeManager.clearActiveType(victim.getUniqueId());
        nmsCombat.setDeathMessage(victim, type);
    }
}
