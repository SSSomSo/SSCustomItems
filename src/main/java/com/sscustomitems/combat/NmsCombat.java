package com.sscustomitems.combat;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 战斗相关 NMS 反射：从服务端动态注册表取原版伤害类型，构造 DamageSource 并对实体造成伤害。
 * 伤害类型属于动态注册表（随数据包加载），必须通过 MinecraftServer 的 RegistryAccess 获取。
 */
public final class NmsCombat {

    private final Object damageTypeRegistryKey;
    private final Method getServer;
    private final Method registryAccess;
    private final Method lookupOrThrow;
    private final Method registryGet;
    private final Method registryKeySet;
    private final Method identifierFromNamespaceAndPath;
    private final Method identifierWithDefaultNamespace;
    private final Constructor<?> damageSourceConstructor;
    private final Method hurt;
    private final Method getHandle;

    private Object cachedRegistry;

    public NmsCombat() {
        try {
            Class<?> identifierClass = Class.forName("net.minecraft.resources.Identifier");
            Class<?> holderClass = Class.forName("net.minecraft.core.Holder");
            Class<?> nmsEntityClass = Class.forName("net.minecraft.world.entity.Entity");
            Class<?> registryClass = Class.forName("net.minecraft.core.Registry");
            Class<?> registryAccessClass = Class.forName("net.minecraft.core.RegistryAccess");
            Class<?> registriesClass = Class.forName("net.minecraft.core.registries.Registries");
            Class<?> minecraftServerClass = Class.forName("net.minecraft.server.MinecraftServer");
            Class<?> damageSourceClass = Class.forName("net.minecraft.world.damagesource.DamageSource");
            Class<?> craftEntityClass = Class.forName("org.bukkit.craftbukkit.entity.CraftEntity");

            this.damageTypeRegistryKey = registriesClass.getField("DAMAGE_TYPE").get(null);
            this.getServer = minecraftServerClass.getMethod("getServer");
            this.registryAccess = minecraftServerClass.getMethod("registryAccess");
            this.lookupOrThrow = registryAccessClass.getMethod("lookupOrThrow",
                    Class.forName("net.minecraft.resources.ResourceKey"));
            this.registryGet = registryClass.getMethod("get", identifierClass);
            this.registryKeySet = registryClass.getMethod("keySet");
            this.identifierFromNamespaceAndPath =
                    identifierClass.getMethod("fromNamespaceAndPath", String.class, String.class);
            this.identifierWithDefaultNamespace =
                    identifierClass.getMethod("withDefaultNamespace", String.class);
            this.damageSourceConstructor =
                    damageSourceClass.getConstructor(holderClass, nmsEntityClass, nmsEntityClass);
            this.hurt = nmsEntityClass.getMethod("hurtOrSimulate", damageSourceClass, float.class);
            this.getHandle = craftEntityClass.getMethod("getHandle");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法初始化战斗 NMS 桥接（服务端版本不受支持？）: " + e, e);
        }
    }

    /** 以玩家为伤害来源，用指定原版伤害类型对目标造成伤害。 */
    public void damage(LivingEntity target, Player attacker, String fullTypeId, float amount) {
        try {
            Object registry = damageRegistry();
            Object identifier = parseIdentifier(fullTypeId);
            Optional<?> optional = (Optional<?>) registryGet.invoke(registry, identifier);
            if (optional.isEmpty()) {
                throw new IllegalArgumentException("无效的原版伤害类型: " + fullTypeId);
            }

            Object nmsTarget = getHandle.invoke(target);
            Object nmsAttacker = attacker == null ? null : getHandle.invoke(attacker);
            Object damageSource = damageSourceConstructor.newInstance(
                    optional.get(), nmsAttacker, nmsAttacker);
            hurt.invoke(nmsTarget, damageSource, amount);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("造成伤害失败: " + e.getMessage(), e);
        }
    }

    /** 读取当前服务端已加载的全部原版伤害类型 id（含数据包新增）。 */
    public List<String> vanillaTypeNames() {
        try {
            Set<?> keys = (Set<?>) registryKeySet.invoke(damageRegistry());
            List<String> names = new ArrayList<>(keys.size());
            for (Object key : keys) {
                names.add(key.toString());
            }
            return names;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("读取伤害类型列表失败: " + e.getMessage(), e);
        }
    }

    private Object damageRegistry() throws ReflectiveOperationException {
        if (cachedRegistry != null) {
            return cachedRegistry;
        }
        Object server = getServer.invoke(null);
        if (server == null) {
            throw new IllegalStateException("服务端尚未启动，无法获取伤害类型注册表");
        }
        Object access = registryAccess.invoke(server);
        cachedRegistry = lookupOrThrow.invoke(access, damageTypeRegistryKey);
        return cachedRegistry;
    }

    private Object parseIdentifier(String fullTypeId) throws ReflectiveOperationException {
        int separator = fullTypeId.indexOf(':');
        if (separator < 0) {
            return identifierWithDefaultNamespace.invoke(null, fullTypeId);
        }
        return identifierFromNamespaceAndPath.invoke(null,
                fullTypeId.substring(0, separator), fullTypeId.substring(separator + 1));
    }
}
