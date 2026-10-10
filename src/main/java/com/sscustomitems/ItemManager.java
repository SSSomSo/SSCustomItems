package com.sscustomitems;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public final class ItemManager {

    private final SSCustomItemsPlugin plugin;
    private final Path itemsDir;
    private final Map<String, CustomItem> items = new LinkedHashMap<>();
    private final NamespacedKey itemIdKey;
    private NmsBridge nmsBridge;
    private boolean ready;

    public ItemManager(SSCustomItemsPlugin plugin) {
        this.plugin = plugin;
        this.itemsDir = plugin.getDataFolder().toPath().resolve("items");
        this.itemIdKey = new NamespacedKey(plugin, "item_id");
    }

    public NamespacedKey itemIdKey() { return itemIdKey; }
    public boolean isReady() { return ready; }

    public void init() throws IOException {
        Files.createDirectories(itemsDir);
        createExampleIfEmpty();
        try { this.nmsBridge = new NmsBridge(); }
        catch (RuntimeException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "NMS 桥接初始化失败，自定义物品功能不可用", e);
            return;
        }
        load();
        ready = true;
    }

    public void load() {
        items.clear();
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(itemsDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .forEach(files::add);
        } catch (IOException e) { plugin.getLogger().severe("无法读取 items 目录: " + e.getMessage()); return; }
        for (Path file : files) loadFile(file);
        plugin.getLogger().info("已加载 " + items.size() + " 个自定义物品");
    }

    private void loadFile(Path file) {
        String fileName = file.getFileName().toString();
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            String vanillaId = requireString(json, "vanilla_id", fileName);
            String id = requireString(json, "id", fileName);
            boolean enableModel = json.has("enable_model") && json.get("enable_model").getAsBoolean();
            JsonObject components = (json.has("components") && json.get("components").isJsonObject())
                    ? json.getAsJsonObject("components") : new JsonObject();
            ActionBindings actions = ActionBindings.parse(json);
            String fullVanillaId = vanillaId.contains(":") ? vanillaId : "minecraft:" + vanillaId;
            String fullId = id.toLowerCase(Locale.ROOT);
            if (!isValidVanillaItem(fullVanillaId))
                plugin.getLogger().warning("文件 " + fileName + " 的 vanilla_id 无效: " + fullVanillaId);
            CustomItem item = new CustomItem(fileName, fullVanillaId, fullId, enableModel, components, actions);
            CustomItem old = items.put(fullId, item);
            if (old != null) plugin.getLogger().warning("物品 id " + fullId + " 重复，" + fileName + " 覆盖了 " + old.sourceFile());
        } catch (Exception e) { plugin.getLogger().warning("加载物品文件 " + fileName + " 失败: " + e.getMessage()); }
    }

    public ItemStack createItem(String id) throws Exception {
        CustomItem item = items.get(id.toLowerCase(Locale.ROOT));
        if (item == null) throw new IllegalArgumentException("未找到自定义物品: " + id + "（可用 /ci give 后按 Tab 查看已加载列表）");
        JsonObject components = item.components().deepCopy();
        if (item.enableModel() && !hasComponent(components, "item_model"))
            components.addProperty("minecraft:item_model", item.id());
        String snbt = "{count:1b,id:" + quote(item.vanillaId()) + ",components:" + Snbt.fromJson(components) + "}";
        plugin.getLogger().info("给予自定义物品 " + item.id() + "，SNBT: " + snbt);
        ItemStack stack = nmsBridge.fromSnbt(snbt);
        stampId(stack, item.id());
        applyArmorRules(stack);
        return stack;
    }

    private void applyArmorRules(ItemStack stack) {
        if (!ArmorSystem.isArmorMaterial(stack.getType())) return;
        String armorType = nmsBridge.customDataString(stack, "armor_type");
        if (!ArmorSystem.ARMOR_TYPE_VALUE.equals(armorType)
                || !(stack.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable damageable)
                || damageable.hasMaxDamage()) return;
        int original = stack.getType().getMaxDurability();
        int cap = Math.max(1, Math.round(original * 0.15f));
        damageable.setMaxDamage(cap);
        if (damageable.getDamage() > cap) damageable.setDamage(cap);
        stack.setItemMeta(damageable);
    }

    public CustomItem getItem(String id) { return items.get(id.toLowerCase(Locale.ROOT)); }

    private void stampId(ItemStack stack, String id) {
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, id);
        stack.setItemMeta(meta);
    }

    public Collection<String> ids() { return items.keySet(); }

    private static boolean hasComponent(JsonObject components, String componentName) {
        return components.has(componentName) || components.has("minecraft:" + componentName);
    }

    private static boolean isValidVanillaItem(String fullVanillaId) {
        String shortName = fullVanillaId.contains(":") ? fullVanillaId.substring(fullVanillaId.indexOf(':') + 1) : fullVanillaId;
        Material material = Material.matchMaterial(shortName.toUpperCase(Locale.ROOT));
        return material != null && material.isItem();
    }

    private static String requireString(JsonObject json, String key, String fileName) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive() || !json.get(key).getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("缺少字符串字段 \"" + key + "\"");
        String value = json.get(key).getAsString();
        if (value.isBlank()) throw new IllegalArgumentException("字段 \"" + key + "\" 不能为空");
        return value;
    }

    private static String quote(String text) { return '"' + text.replace("\\", "\\\\").replace("\"", "\\\"") + '"'; }

    private void createExampleIfEmpty() {
        Path example = itemsDir.resolve("ruby.json");
        if (Files.exists(example)) return;
        String exampleJson = """
                {
                  "vanilla_id": "minecraft:paper",
                  "id": "sscustomitems:ruby",
                  "enable_model": true,
                  "components": {
                    "minecraft:custom_name": { "text": "红宝石", "color": "red", "bold": true },
                    "minecraft:lore": [ { "text": "一块闪闪发光的红宝石", "color": "gray", "italic": false } ],
                    "minecraft:enchantment_glint_override": true,
                    "minecraft:max_stack_size": 16,
                    "minecraft:custom_data": { "sscustomitems": { "ruby": true } }
                  },
                  "handed": {
                    "offhand": [ "/tellraw @a {\"text\":\"%player% 把红宝石切到了副手\",\"color\":\"yellow\"}" ],
                    "inoffhand": [ "/tellraw %player% {\"text\":\"红宝石正在你的副手中\",\"color\":\"gray\"}" ],
                    "leftclick": [ "/tellraw @a {\"text\":\"%player% 左键使用了红宝石\",\"color\":\"aqua\"}" ],
                    "rightclick": [ "/say %player% 右键使用了红宝石" ],
                    "wear": { "helmet": [ "/tellraw @a {\"text\":\"%player% 戴上了红宝石\",\"color\":\"red\"}" ] },
                    "inhelmet": [ "/say %player% 正戴着红宝石" ]
                  }
                }
                """;
        try { Files.writeString(example, exampleJson, StandardCharsets.UTF_8); plugin.getLogger().info("已生成示例物品配置 items/ruby.json"); }
        catch (IOException e) { plugin.getLogger().warning("无法生成示例配置: " + e.getMessage()); }
    }
}
