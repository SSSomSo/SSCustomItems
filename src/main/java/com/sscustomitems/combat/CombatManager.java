package com.sscustomitems.combat;

import com.sscustomitems.SSCustomItemsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 战斗指令执行器 + 自定义死亡消息覆盖。
 */
public final class CombatManager implements Listener {

    private static final long MARK_VALID_MILLIS = 15000L;

    private final SSCustomItemsPlugin plugin;
    private final DamageTypeManager damageTypeManager;
    private final NmsCombat nmsCombat;

    // 被自定义伤害类型击中的实体标记
    private final Map<UUID, String> customMark = new HashMap<>();
    private final Map<UUID, Long> markTime = new HashMap<>();

    public CombatManager(SSCustomItemsPlugin plugin, DamageTypeManager damageTypeManager) {
        this.plugin = plugin;
        this.damageTypeManager = damageTypeManager;
        this.nmsCombat = new NmsCombat();
    }

    /** /damagelook：命中返回 1，未命中返回 0。 */
    public int lookDamage(Player player, String typeId, ShotSettings settings) {
        Trajectory trajectory = Trajectory.simulate(player, settings.maxDistance,
                settings.spread, settings.drop, settings.raySize, true);
        LivingEntity target = trajectory.hitEntity();
        if (target == null) {
            return 0;
        }
        applyDamage(target, player, typeId, settings.damageAmount);
        return 1;
    }

    /** /firelook：命中返回 1，未命中返回 0。 */
    public int lookFire(Player player, int burnSeconds, ShotSettings settings) {
        Trajectory trajectory = Trajectory.simulate(player, settings.maxDistance,
                settings.spread, settings.drop, settings.raySize, true);
        LivingEntity target = trajectory.hitEntity();
        if (target == null) {
            return 0;
        }
        target.setFireTicks(burnSeconds * 20);
        return 1;
    }

    /** /damageln：返回受影响的实体数量。 */
    public int locationDamage(Player player, String typeId, double radius, ShotSettings settings) {
        Vector center = impactPoint(player, settings);
        int count = 0;
        for (LivingEntity target : targetsAround(player, center, radius)) {
            applyDamage(target, player, typeId, settings.damageAmount);
            count++;
        }
        return count;
    }

    /** /fireln：返回受影响的实体数量。 */
    public int locationFire(Player player, int burnSeconds, double radius, ShotSettings settings) {
        Vector center = impactPoint(player, settings);
        int count = 0;
        for (LivingEntity target : targetsAround(player, center, radius)) {
            target.setFireTicks(burnSeconds * 20);
            count++;
        }
        return count;
    }

    /** /killlook：直接清除视角所指实体（与原版 /kill 相同的结算方式）。命中返回 1。 */
    public int lookKill(Player player, ShotSettings settings) {
        Trajectory trajectory = Trajectory.simulate(player, settings.maxDistance,
                settings.spread, settings.drop, settings.raySize, true);
        LivingEntity target = trajectory.hitEntity();
        if (target == null) {
            return 0;
        }
        nmsCombat.damage(target, player, "minecraft:generic_kill", Float.MAX_VALUE);
        return 1;
    }

    /** /killln：直接清除落点球内全部实体，返回数量。 */
    public int locationKill(Player player, double radius, ShotSettings settings) {
        Vector center = impactPoint(player, settings);
        int count = 0;
        for (LivingEntity target : targetsAround(player, center, radius)) {
            nmsCombat.damage(target, player, "minecraft:generic_kill", Float.MAX_VALUE);
            count++;
        }
        return count;
    }

    private Vector impactPoint(Player player, ShotSettings settings) {
        Trajectory trajectory = Trajectory.simulate(player, settings.maxDistance,
                settings.spread, settings.drop, settings.raySize, false);
        return trajectory.endPoint();
    }

    private List<LivingEntity> targetsAround(Player player, Vector center, double radius) {
        List<LivingEntity> targets = new ArrayList<>();
        for (LivingEntity entity : player.getWorld().getLivingEntities()) {
            if (entity.equals(player) || !entity.isValid()) {
                continue;
            }
            if (entity.getLocation().toVector().isInSphere(center, radius)) {
                targets.add(entity);
            }
        }
        return targets;
    }

    private void applyDamage(LivingEntity target, Player player, String typeId, float amount) {
        CustomDamageType custom = damageTypeManager.get(typeId);
        String vanillaType = custom != null ? custom.baseType() : typeId;
        nmsCombat.damage(target, player, vanillaType, amount);
        if (custom != null) {
            customMark.put(target.getUniqueId(), custom.typeId());
            markTime.put(target.getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player dead = event.getEntity();
        String customTypeId = customMark.get(dead.getUniqueId());
        if (customTypeId == null) {
            return;
        }
        Long markedAt = markTime.get(dead.getUniqueId());
        if (markedAt == null || System.currentTimeMillis() - markedAt > MARK_VALID_MILLIS) {
            return;
        }

        CustomDamageType type = damageTypeManager.get(customTypeId);
        if (type == null) {
            return;
        }

        String language = plugin.getConfig().getString("language", "zh_CN");
        String victimName = dead.getName();
        String raw;

        Player killerPlayer = dead.getKiller();
        if (killerPlayer != null) {
            raw = type.message(language, "byplayer");
            if (raw != null) {
                raw = raw.replace("<PLAYER1>", victimName)
                        .replace("<PLAYER2>", killerPlayer.getName());
            }
        } else {
            LivingEntity mobCausing = findMobCausing(event.getDamageSource());
            if (mobCausing != null) {
                raw = type.message(language, "byentity");
                if (raw != null) {
                    String entityName = PlainTextComponentSerializer.plainText()
                            .serialize(mobCausing.name());
                    raw = raw.replace("<ENTITY>", entityName);
                }
            } else {
                raw = type.message(language, "bynature");
            }
        }

        if (raw != null) {
            raw = raw.replace("<PLAYER>", victimName);
            event.deathMessage(Component.text(raw));
        }
    }

    /** 从 Bukkit DamageSource 中找非玩家的生物来源（用于 byentity）。 */
    private LivingEntity findMobCausing(org.bukkit.damage.DamageSource damageSource) {
        if (damageSource == null) {
            return null;
        }
        org.bukkit.entity.Entity causing = damageSource.getCausingEntity();
        if (causing instanceof LivingEntity living && !(living instanceof Player)) {
            return living;
        }
        return null;
    }

    /** 一次射击的全部参数。 */
    public record ShotSettings(double maxDistance,
                               double spread,
                               double drop,
                               double raySize,
                               float damageAmount) {
    }
}
