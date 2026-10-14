package com.sscustomitems;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 一个物品绑定的全部触发命令。
 *
 * JSON 同时支持嵌套写法和点号扁平写法：
 * {
 *   "handed": {
 *     "offhand": ["/tellraw @a 1"],
 *     "inoffhand": ["..."],
 *     "wear": { "helmet": ["..."] }
 *   },
 *   "handed.inhelmet": ["/say on head"]
 * }
 */
public final class ActionBindings {

    private final EnumMap<Trigger, List<String>> commands = new EnumMap<>(Trigger.class);

    public void add(Trigger trigger, String command) {
        commands.computeIfAbsent(trigger, t -> new ArrayList<>()).add(command);
    }

    /** 返回该触发的命令列表（可能为空，永不为 null）。 */
    public List<String> get(Trigger trigger) {
        return commands.getOrDefault(trigger, List.of());
    }

    public boolean isEmpty() {
        return commands.isEmpty();
    }

    public static ActionBindings parse(JsonObject root) {
        ActionBindings bindings = new ActionBindings();
        walk(root, List.of(), bindings);
        return bindings;
    }

    private static void walk(JsonElement element, List<String> path, ActionBindings bindings) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                // 同时兼容 "handed.offhand" 这种点号扁平 key
                List<String> next = new ArrayList<>(path);
                for (String segment : entry.getKey().split("\\.")) {
                    if (!segment.isEmpty()) {
                        next.add(segment.toLowerCase());
                    }
                }
                walk(entry.getValue(), next, bindings);
            }
            return;
        }
        if (element.isJsonArray()) {
            List<String> normalized = normalizePath(path);
            Trigger trigger = Trigger.fromPath(normalized);
            if (trigger == null) {
                return; // 非触发路径（如 components 里的数组），忽略
            }
            for (JsonElement value : element.getAsJsonArray()) {
                if (value != null && value.isJsonPrimitive()
                        && value.getAsJsonPrimitive().isString()) {
                    bindings.add(trigger, value.getAsString());
                }
            }
        }
    }

    private static List<String> normalizePath(List<String> path) {
        List<String> result = new ArrayList<>(path);
        if (!result.isEmpty() && result.get(0).equals("handed")) {
            result.remove(0);
        }
        return result;
    }
}
