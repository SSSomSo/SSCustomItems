package com.sscustomitems.combat;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 弹道模拟：从玩家眼睛位置出发，按“tick”步进，
 * 每步前进 3 格（箭矢基准速度），并叠加弹道下坠；
 * 散布通过“最大距离处目标平面内的随机圆盘偏移”实现。
 */
public final class Trajectory {

    private static final double SPEED = 3.0;

    private final List<Vector> path = new ArrayList<>();
    private LivingEntity hitEntity;
    private Vector endPoint;

    public static Trajectory simulate(Player player,
                                      double maxDistance,
                                      double spread,
                                      double drop,
                                      double raySize,
                                      boolean stopOnEntity) {
        World world = player.getWorld();
        Vector eye = player.getEyeLocation().toVector();
        Vector aim = player.getEyeLocation().getDirection().normalize();

        Vector initialDirection;
        if (spread > 0.0) {
            double referenceDistance = Math.max(1.0, maxDistance);
            Vector idealTarget = eye.clone().add(aim.clone().multiply(referenceDistance));
            idealTarget.add(randomDiskOffset(aim, spread));
            initialDirection = idealTarget.subtract(eye).normalize();
        } else {
            initialDirection = aim.clone();
        }

        Trajectory trajectory = new Trajectory();
        trajectory.path.add(eye.clone());

        Vector position = eye.clone();
        Vector velocity = initialDirection.clone().multiply(SPEED);
        double remaining = maxDistance;

        while (remaining > 0.0) {
            Vector segmentDirection = velocity.clone().normalize();
            double segmentLength = Math.min(velocity.length(), remaining);
            Vector segmentStart = position.clone();
            Vector segmentEnd = segmentStart.clone().add(segmentDirection.clone().multiply(segmentLength));
            Location startLocation = new Location(world, segmentStart.getX(), segmentStart.getY(), segmentStart.getZ());

            RayTraceResult blockHit = world.rayTraceBlocks(startLocation, segmentDirection,
                    segmentLength, FluidCollisionMode.NEVER, true);

            Entity entityHit = null;
            double entityDistance = Double.MAX_VALUE;
            if (stopOnEntity) {
                RayTraceResult entityResult = world.rayTraceEntities(startLocation, segmentDirection,
                        segmentLength, raySize,
                        entity -> entity instanceof LivingEntity
                                && !entity.equals(player)
                                && entity.isValid());
                if (entityResult != null && entityResult.getHitEntity() != null) {
                    entityHit = entityResult.getHitEntity();
                    entityDistance = boxEntryDistance(segmentStart, entityHit.getBoundingBox(), raySize);
                }
            }

            double blockDistance = blockHit != null && blockHit.getHitPosition() != null
                    ? segmentStart.distance(blockHit.getHitPosition())
                    : Double.MAX_VALUE;

            if (entityHit != null && entityDistance <= blockDistance) {
                trajectory.hitEntity = (LivingEntity) entityHit;
                trajectory.endPoint = segmentStart.clone()
                        .add(segmentDirection.clone().multiply(Math.max(0.0, entityDistance)));
                trajectory.path.add(trajectory.endPoint.clone());
                return trajectory;
            }

            if (blockHit != null && blockHit.getHitPosition() != null) {
                trajectory.endPoint = blockHit.getHitPosition();
                trajectory.path.add(trajectory.endPoint.clone());
                return trajectory;
            }

            position = segmentEnd;
            trajectory.path.add(position.clone());
            remaining -= segmentLength;

            // 重力：每 tick 速度向下增加 drop（格/tick²）
            velocity.add(new Vector(0.0, -drop, 0.0));
        }

        trajectory.endPoint = position;
        return trajectory;
    }

    public LivingEntity hitEntity() {
        return hitEntity;
    }

    public Vector endPoint() {
        return endPoint;
    }

    /** 射线起点到实体包围盒（外扩 raySize）最近点的距离。 */
    private static double boxEntryDistance(Vector origin, BoundingBox box, double raySize) {
        double x = clamp(origin.getX(), box.getMinX() - raySize, box.getMaxX() + raySize);
        double y = clamp(origin.getY(), box.getMinY() - raySize, box.getMaxY() + raySize);
        double z = clamp(origin.getZ(), box.getMinZ() - raySize, box.getMaxZ() + raySize);
        return origin.distance(new Vector(x, y, z));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /** 在垂直于 normal、过原点的平面上取一个均匀随机圆盘偏移（半径内）。 */
    private static Vector randomDiskOffset(Vector normal, double radius) {
        Vector u = perpendicular(normal);
        Vector v = normal.clone().crossProduct(u).normalize();

        double angle = ThreadLocalRandom.current().nextDouble(2.0 * Math.PI);
        double distance = radius * Math.sqrt(ThreadLocalRandom.current().nextDouble());
        return u.multiply(distance * Math.cos(angle))
                .add(v.multiply(distance * Math.sin(angle)));
    }

    private static Vector perpendicular(Vector normal) {
        Vector reference = Math.abs(normal.getY()) < 0.99
                ? new Vector(0.0, 1.0, 0.0)
                : new Vector(1.0, 0.0, 0.0);
        return normal.clone().crossProduct(reference).normalize();
    }
}
