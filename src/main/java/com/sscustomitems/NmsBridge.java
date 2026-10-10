package com.sscustomitems;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;

public final class NmsBridge {

    private final Object registryProvider;
    private final Method parseCompoundMethod;
    private final Object nbtOps;
    private final Method createContextMethod;
    private final Method registryOpsCreate;
    private final Object itemStackCodec;
    private final Method codecParse;
    private final Method dataResultResult;
    private final Method dataResultError;
    private final Method errorMessage;
    private final Method legacyItemStackParse;
    private final Method asBukkitCopy;
    private final Method asNmsCopy;
    private final Method codecEncodeStart;
    private final Method itemStackSet;
    private final Object containerComponentType;
    private final Method containerContentsFromItems;
    private final Object customDataType;
    private final Method componentHolderGet;
    private final Method customDataGetUnsafe;
    private final Method compoundGetString;
    private final Method compoundGetShort;
    private final Method compoundContains;
    private final Method compoundGet;
    private final Class<?> numericTagClass;
    private final Method numericShortValue;

    public NmsBridge() {
        try {
            Class<?> dynamicOpsClass = Class.forName("com.mojang.serialization.DynamicOps");
            Class<?> providerClass = Class.forName("net.minecraft.core.HolderLookup$Provider");
            Class<?> tagClass = Class.forName("net.minecraft.nbt.Tag");
            Class<?> compoundTagClass = Class.forName("net.minecraft.nbt.CompoundTag");
            Class<?> tagParserClass = Class.forName("net.minecraft.nbt.TagParser");
            Class<?> nbtOpsClass = Class.forName("net.minecraft.nbt.NbtOps");
            Class<?> nmsItemStackClass = Class.forName("net.minecraft.world.item.ItemStack");
            Class<?> registryOpsClass = Class.forName("net.minecraft.resources.RegistryOps");
            Class<?> decoderClass = Class.forName("com.mojang.serialization.Decoder");
            Class<?> dataResultClass = Class.forName("com.mojang.serialization.DataResult");
            Class<?> dataResultErrorClass = Class.forName("com.mojang.serialization.DataResult$Error");

            Class<?> minecraftServerClass = Class.forName("net.minecraft.server.MinecraftServer");
            Object minecraftServer = findStaticMethod(minecraftServerClass, "getServer", minecraftServerClass).invoke(null);
            Method registryAccess = findMethodByName(minecraftServerClass, "registryAccess");
            this.registryProvider = registryAccess.invoke(minecraftServer);

            Method parseCompound = findOptionalStaticMethod(tagParserClass, "parseCompoundFully", compoundTagClass, String.class);
            if (parseCompound == null) parseCompound = findStaticMethod(tagParserClass, "parseTag", compoundTagClass, String.class);
            this.parseCompoundMethod = parseCompound;
            this.nbtOps = findStaticField(nbtOpsClass, "INSTANCE");

            Method createContext = findOptionalMethod(providerClass, "createSerializationContext", registryOpsClass, dynamicOpsClass);
            if (createContext == null) createContext = findOptionalMethod(providerClass, "createSerializationOps", registryOpsClass, dynamicOpsClass);
            this.createContextMethod = createContext;
            this.registryOpsCreate = findOptionalStaticMethod(registryOpsClass, "create", registryOpsClass, dynamicOpsClass, providerClass);

            this.itemStackCodec = findStaticField(nmsItemStackClass, "CODEC");
            this.codecParse = findMethod(decoderClass, "parse", dataResultClass, dynamicOpsClass, Object.class);
            this.dataResultResult = findMethod(dataResultClass, "result", Optional.class);
            this.dataResultError = findMethod(dataResultClass, "error", Optional.class);
            this.errorMessage = findMethod(dataResultErrorClass, "message", String.class);

            Method legacy = null;
            for (Method candidate : nmsItemStackClass.getDeclaredMethods()) {
                if (candidate.getName().equals("parse") && candidate.getParameterCount() == 2
                        && providerClass.isAssignableFrom(candidate.getParameterTypes()[0])
                        && tagClass.isAssignableFrom(candidate.getParameterTypes()[1])) {
                    candidate.setAccessible(true); legacy = candidate; break;
                }
            }
            this.legacyItemStackParse = legacy;

            String craftPackage = Bukkit.getServer().getClass().getPackageName();
            Class<?> craftItemStackClass = Class.forName(craftPackage + ".inventory.CraftItemStack");
            this.asBukkitCopy = findStaticMethod(craftItemStackClass, "asBukkitCopy", ItemStack.class, nmsItemStackClass);
            this.asNmsCopy = findStaticMethod(craftItemStackClass, "asNMSCopy", Class.forName("net.minecraft.world.item.ItemStack"), ItemStack.class);

            Class<?> encoderClass = Class.forName("com.mojang.serialization.Encoder");
            this.codecEncodeStart = findMethod(encoderClass, "encodeStart", dataResultClass, dynamicOpsClass, Object.class);
            this.itemStackSet = findMethod(nmsItemStackClass, "set", Object.class, Class.forName("net.minecraft.core.component.DataComponentType"), Object.class);

            Class<?> dataComponentsClass = Class.forName("net.minecraft.core.component.DataComponents");
            this.containerComponentType = dataComponentsClass.getField("CONTAINER").get(null);
            Class<?> contentsClass = Class.forName("net.minecraft.world.item.component.ItemContainerContents");
            this.containerContentsFromItems = contentsClass.getMethod("fromItems", java.util.List.class);

            this.customDataType = dataComponentsClass.getField("CUSTOM_DATA").get(null);
            Class<?> componentHolderClass = Class.forName("net.minecraft.core.component.DataComponentHolder");
            this.componentHolderGet = componentHolderClass.getMethod("get", Class.forName("net.minecraft.core.component.DataComponentType"));
            Class<?> customDataClass = Class.forName("net.minecraft.world.item.component.CustomData");
            this.customDataGetUnsafe = customDataClass.getMethod("getUnsafe");
            this.compoundGetString = compoundTagClass.getMethod("getString", String.class);
            this.compoundGetShort = compoundTagClass.getMethod("getShort", String.class);
            this.compoundContains = compoundTagClass.getMethod("contains", String.class);
            this.compoundGet = compoundTagClass.getMethod("get", String.class);

            Class<?> numericClass; Method numericShort;
            try {
                numericClass = Class.forName("net.minecraft.nbt.NumericTag");
                Method candidate;
                try { candidate = numericClass.getMethod("shortValue"); }
                catch (NoSuchMethodException e) { candidate = numericClass.getMethod("getAsShort"); }
                candidate.setAccessible(true); numericShort = candidate;
            } catch (ReflectiveOperationException e) { numericClass = null; numericShort = null; }
            this.numericTagClass = numericClass;
            this.numericShortValue = numericShort;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法初始化 NMS 桥接（服务端版本不受支持？）: " + e, e);
        }
    }

    public ItemStack fromSnbt(String snbt) {
        try {
            Object compoundTag = parseCompoundMethod.invoke(null, snbt);
            Object nmsItemStack;
            if (legacyItemStackParse != null) {
                Object optional = legacyItemStackParse.invoke(null, registryProvider, compoundTag);
                nmsItemStack = ((Optional<?>) optional).orElseThrow(() -> new IllegalStateException("SNBT 解析失败（物品无效）: " + snbt));
            } else {
                Object dataResult = codecParse.invoke(itemStackCodec, registryOps(), compoundTag);
                Optional<?> result = (Optional<?>) dataResultResult.invoke(dataResult);
                if (result.isPresent()) { nmsItemStack = result.get(); }
                else {
                    String message = "未知错误";
                    Optional<?> error = (Optional<?>) dataResultError.invoke(dataResult);
                    if (error.isPresent()) message = String.valueOf(errorMessage.invoke(error.get()));
                    throw new IllegalStateException("SNBT 解析失败: " + message);
                }
            }
            return (ItemStack) asBukkitCopy.invoke(null, nmsItemStack);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("无法从 SNBT 创建物品: " + e.getMessage(), e); }
    }

    public String toSnbt(ItemStack bukkitItem) {
        try {
            Object nmsItem = asNmsCopy.invoke(null, bukkitItem);
            Object dataResult = codecEncodeStart.invoke(itemStackCodec, registryOps(), nmsItem);
            Optional<?> result = (Optional<?>) dataResultResult.invoke(dataResult);
            if (result.isPresent()) return result.get().toString();
            String message = "未知错误";
            Optional<?> error = (Optional<?>) dataResultError.invoke(dataResult);
            if (error.isPresent()) message = String.valueOf(errorMessage.invoke(error.get()));
            throw new IllegalStateException("SNBT 序列化失败: " + message);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("无法序列化物品: " + e.getMessage(), e); }
    }

    public ItemStack withContainerContents(ItemStack bukkitBase, java.util.List<ItemStack> bukkitContents) {
        try {
            Object nmsBase = asNmsCopy.invoke(null, bukkitBase);
            java.util.List<Object> nmsContents = new java.util.ArrayList<>(bukkitContents.size());
            for (ItemStack content : bukkitContents) nmsContents.add(asNmsCopy.invoke(null, content));
            Object contents = containerContentsFromItems.invoke(null, nmsContents);
            itemStackSet.invoke(nmsBase, containerComponentType, contents);
            return (ItemStack) asBukkitCopy.invoke(null, nmsBase);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("无法写入容器内容物: " + e.getMessage(), e); }
    }

    public String customDataString(ItemStack bukkitItem, String key) {
        try {
            Object tag = customTag(bukkitItem);
            if (tag == null || !(Boolean) compoundContains.invoke(tag, key)) return null;
            Object result = compoundGetString.invoke(tag, key);
            if (result instanceof Optional<?> optional) { Object value = optional.orElse(null); return value == null ? null : value.toString(); }
            return (String) result;
        } catch (Exception e) { return null; }
    }

    public short customDataShort(ItemStack bukkitItem, String key) {
        try {
            Object tag = customTag(bukkitItem);
            if (tag == null || !(Boolean) compoundContains.invoke(tag, key)) return -1;
            Object result = compoundGetShort.invoke(tag, key);
            if (result instanceof Optional<?> optional) {
                if (optional.isPresent()) return ((Number) optional.get()).shortValue();
                Object raw = compoundGet.invoke(tag, key);
                if (raw != null && numericTagClass != null && numericTagClass.isInstance(raw) && numericShortValue != null)
                    return ((Number) numericShortValue.invoke(raw)).shortValue();
                return -1;
            }
            return ((Number) result).shortValue();
        } catch (Exception e) { return -1; }
    }

    private Object customTag(ItemStack bukkitItem) throws ReflectiveOperationException {
        Object nmsItem = asNmsCopy.invoke(null, bukkitItem);
        Object customData = componentHolderGet.invoke(nmsItem, customDataType);
        if (customData == null) return null;
        return customDataGetUnsafe.invoke(customData);
    }

    private Object registryOps() throws ReflectiveOperationException {
        if (createContextMethod != null) return createContextMethod.invoke(registryProvider, nbtOps);
        else if (registryOpsCreate != null) return registryOpsCreate.invoke(null, nbtOps, registryProvider);
        else throw new IllegalStateException("找不到 RegistryOps 的创建方法");
    }

    private static Method findMethod(Class<?> type, String name, Class<?> returnType, Class<?>... parameterTypes) {
        Method method = findOptionalMethod(type, name, returnType, parameterTypes);
        if (method == null) throw new IllegalStateException("找不到方法 " + type.getName() + "#" + name);
        return method;
    }

    private static Method findOptionalMethod(Class<?> type, String name, Class<?> returnType, Class<?>... parameterTypes) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && returnType.isAssignableFrom(method.getReturnType()) && parametersMatch(method.getParameterTypes(), parameterTypes)) {
                method.setAccessible(true); return method;
            }
        }
        return null;
    }

    private static Method findMethodByName(Class<?> type, String name) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 0) { method.setAccessible(true); return method; }
        }
        throw new IllegalStateException("找不到方法 " + type.getName() + "#" + name + "()");
    }

    private static Method findStaticMethod(Class<?> type, String name, Class<?> returnType, Class<?>... parameterTypes) {
        Method method = findOptionalStaticMethod(type, name, returnType, parameterTypes);
        if (method == null) throw new IllegalStateException("找不到静态方法 " + type.getName() + "#" + name);
        return method;
    }

    private static Method findOptionalStaticMethod(Class<?> type, String name, Class<?> returnType, Class<?>... parameterTypes) {
        for (Method method : type.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers()) && method.getName().equals(name)
                    && returnType.isAssignableFrom(method.getReturnType()) && parametersMatch(method.getParameterTypes(), parameterTypes)) {
                method.setAccessible(true); return method;
            }
        }
        return null;
    }

    private static boolean parametersMatch(Class<?>[] actual, Class<?>[] expected) {
        if (actual.length != expected.length) return false;
        for (int i = 0; i < actual.length; i++) if (!expected[i].isAssignableFrom(actual[i])) return false;
        return true;
    }

    private static Object findStaticField(Class<?> type, String name) throws ReflectiveOperationException {
        java.lang.reflect.Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }
}
