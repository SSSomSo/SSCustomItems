package com.sscustomitems;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自定义物品的 random 配置。
 *
 * JSON 写法（写在物品 json 顶层，与 components 平级、可替代 components）：
 * <pre>
 * "random": {
 *   "random_rules": [
 *     { "id": 1, "type": "double",  "min": 1, "max": 10 },
 *     { "id": 2, "type": "integer", "min": 1, "max": 100 }
 *   ],
 *   "variants": [
 *     { "minecraft:item_model": "ns:model_a" },
 *     { "minecraft:item_model": "ns:model_b", "minecraft:custom_name": { "text": "编号<random_002>" } }
 *   ]
 * }
 * </pre>
 *
 * 规则：
 * - /ci give 时，若有 variants 则随机选一套 components；否则使用顶层 components。
 * - components 任意字符串中的占位符 &lt;random_xxx&gt;（xxx 为三位规则 id）会被替换为
 *   按 random_rules 生成的随机数；
 *   字符串本身就是一个占位符时，直接写成数字（SNBT 数值），否则替换为数字文本。
 * - random 也可直接写成数组（等价于只写 variants，无随机数规则）。
 */
public final class RandomItemSpec {

    /** 占位符：&lt;random_&gt; 后恰好三位数字（规则 id）。 */
    private static final Pattern PLACEHOLDER = Pattern.compile("<random_(\\d{3})>");

    private static final class Rule {
        final boolean integer;
        final double min;
        final double max;

        Rule(boolean integer, double min, double max) {
            this.integer = integer;
            this.min = min;
            this.max = max;
        }
    }

    private final Map<Integer, Rule> rules = new HashMap<>();
    private final List<JsonObject> variants = new ArrayList<>();

    public static RandomItemSpec parse(JsonElement element) {
        RandomItemSpec spec = new RandomItemSpec();
        if (element == null) {
            return spec;
        }
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("random_rules") && obj.get("random_rules").isJsonArray()) {
                for (JsonElement re : obj.getAsJsonArray("random_rules")) {
                    if (!re.isJsonObject()) {
                        continue;
                    }
                    JsonObject ro = re.getAsJsonObject();
                    int id = ro.get("id").getAsInt();
                    String type = ro.has("type") ? ro.get("type").getAsString() : "double";
                    double min = ro.has("min") ? ro.get("min").getAsDouble() : 0.0;
                    double max = ro.has("max") ? ro.get("max").getAsDouble() : 1.0;
                    boolean integer = type.toLowerCase(Locale.ROOT).startsWith("int");
                    spec.rules.put(id, new Rule(integer, min, max));
                }
            }
            JsonArray arr = null;
            if (obj.has("variants") && obj.get("variants").isJsonArray()) {
                arr = obj.getAsJsonArray("variants");
            } else if (obj.has("components") && obj.get("components").isJsonArray()) {
                arr = obj.getAsJsonArray("components");
            }
            if (arr != null) {
                for (JsonElement ve : arr) {
                    if (ve.isJsonObject()) {
                        spec.variants.add(ve.getAsJsonObject());
                    }
                }
            }
        } else if (element.isJsonArray()) {
            // 直接写数组：等价于 variants
            for (JsonElement ve : element.getAsJsonArray()) {
                if (ve.isJsonObject()) {
                    spec.variants.add(ve.getAsJsonObject());
                }
            }
        }
        return spec;
    }

    public boolean hasVariants() {
        return !variants.isEmpty();
    }

    public boolean hasRules() {
        return !rules.isEmpty();
    }

    /**
     * 生成本次给予的 components：随机选变体（或用顶层 components），再替换其中的随机数占位符。
     */
    public JsonObject roll(JsonObject baseComponents, Random rnd) {
        JsonObject chosen = hasVariants()
                ? variants.get(rnd.nextInt(variants.size())).deepCopy()
                : baseComponents.deepCopy();
        return (JsonObject) transform(chosen, rnd);
    }

    /** 递归替换 JSON 树中的占位符。 */
    private JsonElement transform(JsonElement element, Random rnd) {
        if (element.isJsonObject()) {
            JsonObject out = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                out.add(entry.getKey(), transform(entry.getValue(), rnd));
            }
            return out;
        }
        if (element.isJsonArray()) {
            JsonArray out = new JsonArray();
            for (JsonElement item : element.getAsJsonArray()) {
                JsonElement transformed = transform(item, rnd);
                if (!(transformed.isJsonNull())) {
                    out.add(transformed);
                }
            }
            return out;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return transformString(element.getAsString(), rnd);
        }
        return element;
    }

    /** 字符串整体是单个占位符→数字；否则把占位符替换为数字文本。 */
    private JsonElement transformString(String text, Random rnd) {
        Matcher whole = PLACEHOLDER.matcher(text);
        if (whole.matches()) {
            Number value = generate(whole.group(1), rnd);
            if (value != null) {
                return new JsonPrimitive(value);
            }
            return new JsonPrimitive(text);
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            Number value = generate(matcher.group(1), rnd);
            String replacement = value != null ? String.valueOf(value) : matcher.group();
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return new JsonPrimitive(sb.toString());
    }

    private Number generate(String idText, Random rnd) {
        int id = Integer.parseInt(idText);
        Rule rule = rules.get(id);
        if (rule == null) {
            return null;
        }
        if (rule.integer) {
            int lo = (int) Math.ceil(rule.min);
            int hi = (int) Math.floor(rule.max);
            if (hi < lo) {
                return lo;
            }
            return lo + rnd.nextInt(hi - lo + 1);
        }
        return rule.min + rnd.nextDouble() * (rule.max - rule.min);
    }
}
