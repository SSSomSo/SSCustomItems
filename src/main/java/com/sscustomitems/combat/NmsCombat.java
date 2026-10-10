package com.sscustomitems.combat;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

public final class NmsCombat {

    private final Method asNmsCopy;
    private final Method getBukkitEntity;
    private final Method hurt;
    private final Method damageSourceRegistry;
    private final Method create;

    public NmsCombat() {
        try {
            String craftPackage = Bukkit.getServer().getClass().getPackageName();
            Class<?> craftEntityClass = Class.forName(craftPackage + ".entity.CraftEntity");
            this.asNmsCopy = craftEntityClass.getMethod("getHandle");
            Class<?> nmsEntityClass = Class.forName("net.minecraft.world.entity.Entity");
            this.getBukkitEntity = nmsEntityClass.getMethod("getBukkitEntity");

            Class<?> damageSourceClass = Class.forName("net.minecraft.world.damagesource.DamageSource");
            Method hurtMethod = null;
            for (Method candidate : nmsEntityClass.getMethods()) {
                if (candidate.getName().equals("hurt") && candidate.getParameterCount() == 2
                        && damageSourceClass.isAssignableFrom(candidate.getParameterTypes()[0])
                        && candidate.getParameterTypes()[1] == float.class) {
                    candidate.setAccessible(true); hurtMethod = candidate; break;
                }
            }
            if (hurtMethod == null) throw new IllegalStateException("找不到 Entity.hurt(DamageSource, float)");
            this.hurt = hurtMethod;

            Class<?> damageSourcesClass = Class.forName("net.minecraft.world.damagesource.DamageSources");
            Class<?> damageTypeClass = Class.forName("net.minecraft.world.damagesource.DamageType");
            Class<?> holderClass = Class.forName("net.minecraft.core.Holder");
            Method registryMethod = null;
            for (Method candidate : damageSourcesClass.getMethods()) {
                if (candidate.getName().equals("source") && candidate.getParameterCount() == 1
                        && holderClass.isAssignableFrom(candidate.getParameterTypes()[0])) {
                    candidate.setAccessible(true); registryMethod = candidate; break;
                }
            }
            if (registryMethod == null) throw new IllegalStateException("找不到 DamageSources.source(Holder)");
            this.damageSourceRegistry = registryMethod;

            Class<?> minecraftServerClass = Class.forName("net.minecraft.server.MinecraftServer");
            Object server = minecraftServerClass.getMethod("getServer").invoke(null);
            Class<?> registryAccessClass = Class.forName("net.minecraft.core.RegistryAccess");
            Object registryAccess = minecraftServerClass.getMethod("registryAccess").invoke(server);
            Class<?> registryClass = Class.forName("net.minecraft.core.Registry");
            Class<?> resourceKeyClass = Class.forName("net.minecraft.resources.ResourceKey");
            Class<?> registriesClass = Class.forName("net.minecraft.core.registries.Registries");
            Object damageTypeKey = registriesClass.getField("DAMAGE_TYPE").get(null);
            Method lookupMethod = registryAccessClass.getMethod("lookupOrThrow", resourceKeyClass);
            Object registry = lookupMethod.invoke(registryAccess, damageTypeKey);
            Class<?> resourceLocationClass = Class.forName("net.minecraft.resources.ResourceLocation");
            Method parseMethod = resourceLocationClass.getMethod("parse", String.class);
            Method getHolderMethod = registryClass.getMethod("get", resourceLocationClass);
            Method createMethod = damageSourcesClass.getDeclaredMethod("create", holderClass);
            createMethod.setAccessible(true);

            Object damageSources = server.getClass().getMethod("damageSources").invoke(server);
            this.create = (attacker, typeId) -> {
                Object resourceLocation = parseMethod.invoke(null, typeId);
                Object holderOptional = getHolderMethod.invoke(registry, resourceLocation);
                Object holder = ((java.util.Optional<?>) holderOptional).orElseThrow();
                return createMethod.invoke(damageSources, holder);
            };
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("NMS 战斗桥接初始化失败: " + e.getMessage(), e);
        }
    }

    public void damage(Player attacker, LivingEntity target, String damageType, double amount) {
        try {
            Object nmsTarget = asNmsCopy.invoke(target);
            Object source = create.apply(attacker, damageType);
            hurt.invoke(nmsTarget, source, (float) amount);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法造成伤害: " + e.getMessage(), e);
        }
    }

    public void setDeathMessage(LivingEntity victim, CustomDamageType type) {
        try {
            Object nmsEntity = asNmsCopy.invoke(victim);
            Component message = type.deathMessage(victim.getName(),
                    victim.getKiller() != null ? victim.getKiller().getName() : null, null);
            Method setMessage = nmsEntity.getClass().getMethod("setCustomName", Component.class);
            setMessage.invoke(nmsEntity, message);
        } catch (ReflectiveOperationException e) {
            Bukkit.getLogger().warning("设置死亡消息失败: " + e.getMessage());
        }
    }

    @FunctionalInterface
    private interface DamageSourceFactory {
        Object apply(Player attacker, String typeId) throws ReflectiveOperationException;
    }
}
