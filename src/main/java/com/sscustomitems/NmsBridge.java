package com.sscustomitems;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;

/**
 * 服务端 NMS 反射桥接：SNBT 字符串 -> 原版 ItemStack -> Bukkit ItemStack。
 * 全部反射调用，避免编译期依赖 NMS。
 *
 * 兼容：
 *  - 26.2：TagParser.parseCompoundFully + ItemStack.CODEC + RegistryOps
 *  - 旧版：TagParser.parseTag + ItemStack.parse(HolderLookup.Provider, Tag)
 */
public final class NmsBridge {

    private final Object registryProvider;

    private final Method parseCompoundMethod;    // TagParser.parseCompoundFully / parseTag(String)
    private final Object nbtOps;                 // NbtOps.INSTANCE
    private final Method createContextMethod;    // Provider#createSerializationContext/createSerializationOps（可空）
    private final Method registryOpsCreate;      // RegistryOps.create(DynamicOps, Provider)（回退，可空）
    private final Object itemStackCodec;         // ItemStack.CODEC
    private final Method codecParse;             // Decoder#parse(DynamicOps, Object)
    private final Method dataResultResult;       // DataResult#result()
    private final Method dataResultError;        // DataResult#error()
    private final Method errorMessage;           // DataResult.Error#message()
    private final Method legacyItemStackParse;   // 旧版 ItemStack.parse(Provider, Tag)（可空）
    private final Method asBukkitCopy;           // CraftItemStack.asBukkitCopy
    private final Method asNmsCopy;              // CraftItemStack.asNMSCopy
    private final Method codecEncodeStart;       // Encoder#encodeStart(DynamicOps, Object)
    private final Method itemStackSet;           // ItemStack.set(DataComponentType, Object)
    private final Object containerComponentType; // DataComponents.CONTAINER
    private final Method containerContentsFromItems; // ItemContainerContents.fromItems(List)

    private final Object customDataType;        // DataComponents.CUSTOM_DATA
    private final Method componentHolderGet;    // DataComponentHolder#get(DataComponentType)
    private final Method customDataGetUnsafe;   // CustomData#getUnsafe
    private final Method compoundGetString;     // CompoundTag#getString（26.x 起返回 Optional<String>）
    private final Method compoundGetShort;      // CompoundTag#getShort（26.x 起返回 Optional<Short>）
    private final Method compoundContains;      // CompoundTag#contains
    private final Method compoundGet;           // CompoundTag#get(String) -> Tag（数值强转回退用）
    private final Class<?> numericTagClass;     // net.minecraft.nbt.NumericTag（可空）
    private final Method numericShortValue;     // NumericTag#shortValue（26.x）/ getAsShort（旧版）

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

            // 1. SNBT 字符串 -> CompoundTag
            Method parseCompound = findOptionalStaticMethod(tagParserClass, "parseCompoundFully", compoundTagClass, String.class);
            if (parseCompound == null) {
                parseCompound = findStaticMethod(tagParserClass, "parseTag", compoundTagClass, String.class);
            }
            this.parseCompoundMethod = parseCompound;

            // 2. NbtOps.INSTANCE
            this.nbtOps = findStaticField(nbtOpsClass, "INSTANCE");

            // 3. HolderLookup.Provider + NbtOps -> RegistryOps
            Method createContext = findOptionalMethod(providerClass, "createSerializationContext", registryOpsClass, dynamicOpsClass);
            if (createContext == null) {
                createContext = findOptionalMethod(providerClass, "createSerializationOps", registryOpsClass, dynamicOpsClass);
            }
            this.createContextMethod = createContext;
            this.registryOpsCreate = findOptionalStaticMethod(registryOpsClass, "create", registryOpsClass, dynamicOpsClass, providerClass);

            // 4. ItemStack.CODEC#parse(RegistryOps, CompoundTag) -> DataResult
            this.itemStackCodec = findStaticField(nmsItemStackClass, "CODEC");
            this.codecParse = findMethod(decoderClass, "parse", dataResultClass, dynamicOpsClass, Object.class);
            this.dataResultResult = findMethod(dataResultClass, "result", Optional.class);
            this.dataResultError = findMethod(dataResultClass, "error", Optional.class);
            this.errorMessage = findMethod(dataResultErrorClass, "message", String.class);

            // 旧版 ItemStack.parse(HolderLookup.Provider, Tag) -> Optional<ItemStack>
            Method legacy = null;
            for (Method candidate : nmsItemStackClass.getDeclaredMethods()) {
                if (candidate.getName().equals("parse")
                        && candidate.getParameterCount() == 2
                        && providerClass.isAssignableFrom(candidate.getParameterTypes()[0])
                        && tagClass.isAssignableFrom(candidate.getParameterTypes()[1])) {
                    candidate.setAccessible(true);
                    legacy = candidate;
                    break;
                }
            }
            this.legacyItemStackParse = legacy;

            String craftPackage = Bukkit.getServer().getClass().getPackageName();
            Class<?> craftItemStackClass = Class.forName(craftPackage + ".inventory.CraftItemStack");
            this.asBukkitCopy = findStaticMethod(craftItemStackClass, "asBukkitCopy", ItemStack.class, nmsItemStackClass);
            this.asNmsCopy = findStaticMethod(craftItemStackClass, "asNMSCopy",
                    Class.forName("net.minecraft.world.item.ItemStack"), ItemStack.class);

            // 编码：ItemStack.CODEC#encodeStart(DynamicOps, ItemStack) -> DataResult<Tag>
            Class<?> encoderClass = Class.forName("com.mojang.serialization.Encoder");
            this.codecEncodeStart = findMethod(encoderClass, "encodeStart", dataResultClass, dynamicOpsClass, Object.class);
            this.itemStackSet = findMethod(nmsItemStackClass, "set", Object.class,
                    Class.forName("net.minecraft.core.component.DataComponentType"), Object.class);

            // 潜影盒/容器物品的内容物组件
            Class<?> dataComponentsClass = Class.forName("net.minecraft.core.component.DataComponents");
            this.containerComponentType = dataComponentsClass.getField("CONTAINER").get(null);
            Class<?> contentsClass = Class.forName("net.minecraft.world.item.component.ItemContainerContents");
            this.containerContentsFromItems = contentsClass.getMethod("fromItems", java.util.List.class);

            // custom_data 读取
            this.customDataType = dataComponentsClass.getField("CUSTOM_DATA").get(null);
            Class<?> componentHolderClass = Class.forName("net.minecraft.core.component.DataComponentHolder");
            this.componentHolderGet = componentHolderClass.getMethod("get",
                    Class.forName("net.minecraft.core.component.DataComponentType"));
            Class<?> customDataClass = Class.forName("net.minecraft.world.item.component.CustomData");
            this.customDataGetUnsafe = customDataClass.getMethod("getUnsafe");
            this.compoundGetString = compoundTagClass.getMethod("getString", String.class);
            this.compoundGetShort = compoundTagClass.getMethod("getShort", String.class);
            this.compoundContains = compoundTagClass.getMethod("contains", String.class);
            this.compoundGet = compoundTagClass.getMethod("get", String.class);

            // 数值标签的 short 转换：26.x 为 NumericTag#shortValue()，旧版为 NumericTag#getAsShort()。
            // 解析失败不阻断桥接初始化（customDataShort 会退化为仅使用 getShort）。
            Class<?> numericClass;
            Method numericShort;
            try {
                numericClass = Class.forName("net.minecraft.nbt.NumericTag");
                Method candidate;
                try {
                    candidate = numericClass.getMethod("shortValue");
                } catch (NoSuchMethodException e) {
                    candidate = numericClass.getMethod("getAsShort");
                }
                candidate.setAccessible(true);
                numericShort = candidate;
            } catch (ReflectiveOperationException e) {
                numericClass = null;
                numericShort = null;
            }
            this.numericTagClass = numericClass;
            this.numericShortValue = numericShort;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法初始化 NMS 桥接（服务端版本不受支持？）: " + e, e);
        }
    }

    /**
     * 把完整 SNBT（{id,count,components}）解析为 Bukkit ItemStack。
     */
    public ItemStack fromSnbt(String snbt) {
        try {
            Object compoundTag = parseCompoundMethod.invoke(null, snbt);
            Object nmsItemStack;

            if (legacyItemStackParse != null) {
                Object optional = legacyItemStackParse.invoke(null, registryProvider, compoundTag);
                nmsItemStack = ((Optional<?>) optional)
                        .orElseThrow(() -> new IllegalStateException("SNBT 解析失败（物品无效）: " + snbt));
            } else {
                Object dataResult = codecParse.invoke(itemStackCodec, registryOps(), compoundTag);
                Optional<?> result = (Optional<?>) dataResultResult.invoke(dataResult);
                if (result.isPresent()) {
                    nmsItemStack = result.get();
                } else {
                    String message = "未知错误";
                    Optional<?> error = (Optional<?>) dataResultError.invoke(dataResult);
                    if (error.isPresent()) {
                        message = String.valueOf(errorMessage.invoke(error.get()));
                    }
                    throw new IllegalStateException("SNBT 解析失败: " + message);
                }
            }

            return (ItemStack) asBukkitCopy.invoke(null, nmsItemStack);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法从 SNBT 创建物品: " + e.getMessage(), e);
        }
    }

    /** 把 Bukkit ItemStack 序列化为完整 SNBT 字符串（{id,count,components}）。 */
    public String toSnbt(ItemStack bukkitItem) {
        try {
            Object nmsItem = asNmsCopy.invoke(null, bukkitItem);
            Object dataResult = codecEncodeStart.invoke(itemStackCodec, registryOps(), nmsItem);
            Optional<?> result = (Optional<?>) dataResultResult.invoke(dataResult);
            if (result.isPresent()) {
                return result.get().toString();
            }
            String message = "未知错误";
            Optional<?> error = (Optional<?>) dataResultError.invoke(dataResult);
            if (error.isPresent()) {
                message = String.valueOf(errorMessage.invoke(error.get()));
            }
            throw new IllegalStateException("SNBT 序列化失败: " + message);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法序列化物品: " + e.getMessage(), e);
        }
    }

    /**
     * 给容器类物品（如潜影盒）写入内容物组件 minecraft:container。
     * 内容物以 Bukkit ItemStack 形式给出（非空槽位）。
     */
    public ItemStack withContainerContents(ItemStack bukkitBase, java.util.List<ItemStack> bukkitContents) {
        try {
            Object nmsBase = asNmsCopy.invoke(null, bukkitBase);
            java.util.List<Object> nmsContents = new java.util.ArrayList<>(bukkitContents.size());
            for (ItemStack content : bukkitContents) {
                nmsContents.add(asNmsCopy.invoke(null, content));
            }
            Object contents = containerContentsFromItems.invoke(null, nmsContents);
            itemStackSet.invoke(nmsBase, containerComponentType, contents);
            return (ItemStack) asBukkitCopy.invoke(null, nmsBase);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法写入容器内容物: " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------- custom_data

    /** 读取物品 minecraft:custom_data 组件中的字符串；不存在返回 null。 */
    public String customDataString(ItemStack bukkitItem, String key) {
        try {
            Object tag = customTag(bukkitItem);
            if (tag == null || !(Boolean) compoundContains.invoke(tag, key)) {
                return null;
            }
            // 26.x 起 CompoundTag#getString 返回 Optional<String>，旧版直接返回 String
            Object result = compoundGetString.invoke(tag, key);
            if (result instanceof Optional<?> optional) {
                Object value = optional.orElse(null);
                return value == null ? null : value.toString();
            }
            return (String) result;
        } catch (Exception e) {
            // 不能让运行时异常逃逸到事件回调，否则每次受伤都会刷一屏报错
            return null;
        }
    }

    /** 读取 custom_data 中的 short；不存在返回 -1。 */
    public short customDataShort(ItemStack bukkitItem, String key) {
        try {
            Object tag = customTag(bukkitItem);
            if (tag == null || !(Boolean) compoundContains.invoke(tag, key)) {
                return -1;
            }
            // 26.x 起 CompoundTag#getShort 返回 Optional<Short>，且只严格匹配 ShortTag；
            // 旧版直接返回 short 并会对任意数值标签做强转。
            Object result = compoundGetShort.invoke(tag, key);
            if (result instanceof Optional<?> optional) {
                if (optional.isPresent()) {
                    return ((Number) optional.get()).shortValue();
                }
                // SNBT 中的裸数字（如 armor_lvl:6）会被解析成 IntTag，此时按数值标签通用转换，
                // 保持与旧版 getShort 相同的强转语义。
                Object raw = compoundGet.invoke(tag, key);
                if (raw != null && numericTagClass != null
                        && numericTagClass.isInstance(raw) && numericShortValue != null) {
                    return ((Number) numericShortValue.invoke(raw)).shortValue();
                }
                return -1;
            }
            return ((Number) result).shortValue();
        } catch (Exception e) {
            // 不能让运行时异常逃逸到事件回调，否则每次受伤都会刷一屏报错
            return -1;
        }
    }

    private Object customTag(ItemStack bukkitItem) throws ReflectiveOperationException {
        Object nmsItem = asNmsCopy.invoke(null, bukkitItem);
        Object customData = componentHolderGet.invoke(nmsItem, customDataType);
        if (customData == null) {
            return null;
        }
        return customDataGetUnsafe.invoke(customData);
    }

    private Object registryOps() throws ReflectiveOperationException {
        if (createContextMethod != null) {
            return createContextMethod.invoke(registryProvider, nbtOps);
        } else if (registryOpsCreate != null) {
            return registryOpsCreate.invoke(null, nbtOps, registryProvider);
        } else {
            throw new IllegalStateException("找不到 RegistryOps 的创建方法");
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?> returnType, Class<?>... parameterTypes) {
        Method method = findOptionalMethod(type, name, returnType, parameterTypes);
        if (method == null) {
            throw new IllegalStateException("找不到方法 " + type.getName() + "#" + name);
        }
        return method;
    }

    private static Method findOptionalMethod(Class<?> type, String name, Class<?> returnType, Class<?>... parameterTypes) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name)
                    && returnType.isAssignableFrom(method.getReturnType())
                    && parametersMatch(method.getParameterTypes(), parameterTypes)) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    private static Method findMethodByName(Class<?> type, String name) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 0) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new IllegalStateException("找不到方法 " + type.getName() + "#" + name + "()");
    }

    private static Method findStaticMethod(Class<?> type, String name, Class<?> returnType, Class<?>... parameterTypes) {
        Method method = findOptionalStaticMethod(type, name, returnType, parameterTypes);
        if (method == null) {
            throw new IllegalStateException("找不到静态方法 " + type.getName() + "#" + name);
        }
        return method;
    }

    private static Method findOptionalStaticMethod(Class<?> type, String name, Class<?> returnType, Class<?>... parameterTypes) {
        for (Method method : type.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers())
                    && method.getName().equals(name)
                    && returnType.isAssignableFrom(method.getReturnType())
                    && parametersMatch(method.getParameterTypes(), parameterTypes)) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    private static boolean parametersMatch(Class<?>[] actual, Class<?>[] expected) {
        if (actual.length != expected.length) {
            return false;
        }
        for (int i = 0; i < actual.length; i++) {
            if (!expected[i].isAssignableFrom(actual[i])) {
                return false;
            }
        }
        return true;
    }

    private static Object findStaticField(Class<?> type, String name) throws ReflectiveOperationException {
        java.lang.reflect.Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }
}
