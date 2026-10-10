package com.sscustomitems.combat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sscustomitems.SSCustomItemsPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public final class DamageTypeManager {

    private static final List<String> VANILLA_TYPES = List.of(
            "minecraft:player_attack", "minecraft:mob_attack", "minecraft:arrow",
            "minecraft:trident", "minecraft:fireball", "minecraft:fireworks",
            "minecraft:magic", "minecraft:dragon_breath", "minecraft:thorns",
            "minecraft:fall", "minecraft:fly_into_wall", "minecraft:drowning",
            "minecraft:starve", "minecraft:suffocate", "minecraft:cramming",
            "minecraft:lava", "minecraft:on_fire", "minecraft:in_fire",
            "minecraft:hot_floor", "minecraft:lightning_bolt", "minecraft:stalactite",
            "minecraft:stalagmite", "minecraft:falling_block", "minecraft:anvil",
            "minecraft:falling_stalactite", "minecraft:falling_anvil",
            "minecraft:generic", "minecraft:generic_kill", "minecraft:outside_border",
            "minecraft:dry_out", "minecraft:freeze", "minecraft:stalactite_fall",
            "minecraft:sonic_boom", "minecraft:wind_charge", "minecraft:bad_respawn_point",
            "minecraft:crafting", "minecraft:explosion", "minecraft:explosion.player",
            "minecraft:fireworks", "minecraft:fireball", "minecraft:unattributed_fireball",
            "minecraft:thrown", "minecraft:indirect_magic", "minecraft:mob_attack_no_aggro",
            "minecraft:mob_projectile", "minecraft:mob_skip_armor", "minecraft:player_explosion",
            "minecraft:sting", "minecraft:temperature", "minecraft:wind_charge",
            "minecraft:withering", "minecraft:withering_skull", "minecraft:yggdrasil"
    );

    private final SSCustomItemsPlugin plugin;
    private final Path directory;
    private final Map<String, CustomDamageType> types = new LinkedHashMap<>();
    private final Map<UUID, String> activeTypes = new ConcurrentHashMap<>();

    public DamageTypeManager(SSCustomItemsPlugin plugin) {
        this.plugin = plugin;
        this.directory = plugin.getDataFolder().toPath().resolve("damage_types");
    }

    public void init() throws IOException {
        Files.createDirectories(directory);
        createExampleIfEmpty();
        load();
    }

    public void load() {
        types.clear();
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(directory)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .forEach(files::add);
        } catch (IOException e) { plugin.getLogger().severe("无法读取 damage_types 目录: " + e.getMessage()); return; }
        for (Path file : files) loadFile(file);
        plugin.getLogger().info("已加载 " + types.size() + " 个自定义伤害类型");
    }

    private void loadFile(Path file) {
        String fileName = file.getFileName().toString();
        try {
            JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            String typeId = json.get("type_id").getAsString();
            String baseType = json.has("base_type") ? json.get("base_type").getAsString() : "minecraft:player_attack";
            String byPlayer = null, byEntity = null, byNature = null;
            if (json.has("translate") && json.get("translate").isJsonObject()) {
                JsonObject translate = json.getAsJsonObject("translate");
                JsonObject zh = translate.has("zh_CN") && translate.get("zh_CN").isJsonObject()
                        ? translate.getAsJsonObject("zh_CN") : translate;
                if (zh.has("byplayer")) byPlayer = zh.get("byplayer").getAsString();
                if (zh.has("byentity")) byEntity = zh.get("byentity").getAsString();
                if (zh.has("bynature")) byNature = zh.get("bynature").getAsString();
            }
            types.put(CustomDamageType.normalize(typeId),
                    new CustomDamageType(typeId, baseType, byPlayer, byEntity, byNature));
        } catch (Exception e) { plugin.getLogger().warning("加载伤害类型 " + fileName + " 失败: " + e.getMessage()); }
    }

    public CustomDamageType get(String id) { return types.get(CustomDamageType.normalize(id)); }

    public List<String> allTypeNames() {
        List<String> all = new ArrayList<>(VANILLA_TYPES);
        all.addAll(types.keySet());
        return all;
    }

    public void setActiveType(UUID entityId, String typeId) { activeTypes.put(entityId, typeId); }

    public CustomDamageType findActiveType(UUID entityId) {
        String typeId = activeTypes.get(entityId);
        return typeId == null ? null : get(typeId);
    }

    public void clearActiveType(UUID entityId) { activeTypes.remove(entityId); }

    private void createExampleIfEmpty() {
        Path example = directory.resolve("example.json");
        if (Files.exists(example)) return;
        String exampleJson = """
                {
                  "type_id": "sscustomitems:example",
                  "base_type": "minecraft:player_attack",
                  "translate": {
                    "zh_CN": {
                      "byplayer": "<PLAYER1>被<PLAYER2>杀死了",
                      "byentity": "<PLAYER>因为<ENTITY>注定要死亡",
                      "bynature": "<PLAYER>死了"
                    }
                  }
                }
                """;
        try { Files.writeString(example, exampleJson, StandardCharsets.UTF_8); }
        catch (IOException e) { plugin.getLogger().warning("无法生成示例伤害类型: " + e.getMessage()); }
    }
}
