package com.sscustomitems.combat;

import com.sscustomitems.SSCustomItemsPlugin;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 加载 plugins/SSCustomItems/damage_types/ 下的自定义伤害类型，
 * 并提供“原版伤害类型 + 自定义伤害类型”的名称列表（用于指令补全）。
 */
public final class DamageTypeManager {

    private final SSCustomItemsPlugin plugin;
    private final Path directory;
    private final Map<String, CustomDamageType> customTypes = new LinkedHashMap<>();

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
        customTypes.clear();
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(directory)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .forEach(files::add);
        } catch (IOException e) {
            plugin.getLogger().severe("无法读取 damage_types 目录: " + e.getMessage());
            return;
        }
        for (Path file : files) {
            loadFile(file);
        }
        plugin.getLogger().info("已加载 " + customTypes.size() + " 个自定义伤害类型");
    }

    private void loadFile(Path file) {
        String fileName = file.getFileName().toString();
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();

            String typeId = json.get("type_id").getAsString();
            String baseType = json.has("base_type")
                    ? json.get("base_type").getAsString()
                    : "minecraft:generic_kill";

            Map<String, Map<String, String>> messages = new LinkedHashMap<>();
            if (json.has("translate") && json.get("translate").isJsonObject()) {
                JsonObject translate = json.getAsJsonObject("translate");
                for (Map.Entry<String, JsonElement> languageEntry : translate.entrySet()) {
                    if (!languageEntry.getValue().isJsonObject()) {
                        continue;
                    }
                    Map<String, String> scenarios = new LinkedHashMap<>();
                    JsonObject languageObject = languageEntry.getValue().getAsJsonObject();
                    for (Map.Entry<String, JsonElement> scenario : languageObject.entrySet()) {
                        if (scenario.getValue().isJsonPrimitive()) {
                            scenarios.put(scenario.getKey(), scenario.getValue().getAsString());
                        }
                    }
                    messages.put(languageEntry.getKey(), scenarios);
                }
            }

            customTypes.put(typeId, new CustomDamageType(typeId, baseType, messages));
        } catch (Exception e) {
            plugin.getLogger().warning("加载伤害类型文件 " + fileName + " 失败: " + e.getMessage());
        }
    }

    public CustomDamageType get(String typeId) {
        return customTypes.get(typeId);
    }

    /** 原版伤害类型 id 列表（通过服务端动态注册表读取，含数据包新增）。 */
    public List<String> vanillaTypeNames() {
        List<String> result = new ArrayList<>();
        try {
            result.addAll(new NmsCombat().vanillaTypeNames());
        } catch (Exception e) {
            plugin.getLogger().warning("无法读取原版伤害类型列表: " + e.getMessage());
        }
        result.sort(String::compareTo);
        return result;
    }

    /** 全部可用于指令的伤害类型名称（原版 + 自定义）。 */
    public List<String> allTypeNames() {
        List<String> result = new ArrayList<>(vanillaTypeNames());
        result.addAll(customTypes.keySet());
        return result;
    }

    private void createExampleIfEmpty() {
        Path example = directory.resolve("example.json");
        if (Files.exists(example)) {
            return;
        }
        String exampleJson = """
                {
                  "type_id": "example",
                  "base_type": "minecraft:generic_kill",
                  "translate": {
                    "zh_CN": {
                      "byplayer": "<PLAYER1>被<PLAYER2>杀死了",
                      "byentity": "<PLAYER>因为<ENTITY>注定要死亡",
                      "bynature": "<PLAYER>死了"
                    },
                    "en_us": {
                      "byplayer": "<PLAYER1> was slain by <PLAYER2>",
                      "byentity": "<PLAYER> was doomed to die by <ENTITY>",
                      "bynature": "<PLAYER> died"
                    }
                  }
                }
                """;
        try {
            Files.writeString(example, exampleJson, StandardCharsets.UTF_8);
            plugin.getLogger().info("已生成示例伤害类型 damage_types/example.json");
        } catch (IOException e) {
            plugin.getLogger().warning("无法生成示例伤害类型: " + e.getMessage());
        }
    }
}
