package com.sscustomitems.combat;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class Trajectory {

    private static final double STEP = 0.25;
    private static final double ENTITY_RADIUS = 1.0;

    private Trajectory() {}

    public static Location raycast(Player player, double maxDistance, double spread,
                                   double gravity, List<LivingEntity> hitEntities) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        if (spread > 0) direction = applySpread(direction, spread, maxDistance);

        Vector position = eye.toVector();
        Vector velocity = direction.clone();
        double traveled = 0.0;
        World world = player.getWorld();

        while (traveled < maxDistance) {
            Vector next = position.clone().add(velocity.clone().multiply(STEP));
            traveled += STEP;

            RayTraceResult blockHit = world.rayTraceBlocks(
                    position.toLocation(world), next.clone().subtract(position), STEP + 0.01);
            if (blockHit != null && blockHit.getHitBlock() != null) {
                collectEntitiesAlong(world, position, next, ENTITY_RADIUS, player, hitEntities);
                return blockHit.getHitPosition().toLocation(world);
            }

            LivingEntity direct = hitEntityAt(world, next, ENTITY_RADIUS, player);
            if (direct != null) {
                hitEntities.add(direct);
                return next.toLocation(world);
            }

            position = next;
            if (gravity > 0) velocity.setY(velocity.getY() - gravity * STEP);
        }
        return null;
    }

    private static Vector applySpread(Vector direction, double spread, double maxDistance) {
        double angle = Math.atan2(spread, maxDistance);
        if (angle <= 0) return direction;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double yaw = Math.toRadians(random.nextDouble(360.0));
        double pitch = Math.toRadians(random.nextDouble(Math.toDegrees(angle)));
        Vector offset = new Vector(
                Math.cos(pitch) * Math.cos(yaw),
                Math.sin(pitch),
                Math.cos(pitch) * Math.sin(yaw));
        return direction.clone().add(offset).normalize();
    }

    private static LivingEntity hitEntityAt(World world, Vector point, double radius, Player exclude) {
        double r2 = radius * radius;
        for (Entity entity : world.getNearbyEntities(point.toLocation(world), radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(exclude)) continue;
            if (living.getBoundingBox().contains(point) || living.getLocation().toVector().distanceSquared(point) <= r2)
                return living;
        }
        return null;
    }

    private static void collectEntitiesAlong(World world, Vector from, Vector to,
                                             double radius, Player exclude,
                                             List<LivingEntity> hits) {
        Vector delta = to.clone().subtract(from);
        double length = delta.length();
        if (length <= 0) return;
        Vector step = delta.normalize().multiply(STEP);
        Vector current = from.clone();
        double traveled = 0;
        while (traveled < length) {
            LivingEntity hit = hitEntityAt(world, current, radius, exclude);
            if (hit != null && !hits.contains(hit)) hits.add(hit);
            current.add(step);
            traveled += STEP;
        }
    }
}
