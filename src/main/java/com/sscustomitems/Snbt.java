package com.sscustomitems;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.math.BigInteger;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 把 Gson 解析出的 JSON 结构转换为原版 SNBT 文本，
 * 再交给服务端自带的 TagParser 解析，从而支持任意原版数据组件。
 *
 * 类型规则：
 * - JSON 布尔值     -> SNBT true/false
 * - JSON 整数       -> int；超出 int 范围自动加 L 后缀（long）
 * - JSON 小数       -> 默认 float（自动加 f，物品组件中的小数绝大多数为 float）
 * - JSON 字符串     -> SNBT 字符串
 * - 字符串内容若为带类型后缀的数字（如 "1.5d"、"64b"、"100s"、"123L"），原样输出，
 *                      用于需要 double / byte / short / long 的场景
 * - JSON 对象/数组  -> SNBT 复合标签/列表（列表元素类型必须一致）
 * - null            -> 在对象中跳过，在数组中过滤
 */
public final class Snbt {

    private static final Pattern TYPED_BYTE_SHORT_LONG =
            Pattern.compile("^[+-]?[0-9]+[bBsSlL]$");
    private static final Pattern TYPED_FLOAT_DOUBLE =
            Pattern.compile("^[+-]?[0-9]*\\.?[0-9]+(?:[eE][+-]?[0-9]+)?[fFdD]$");

    private Snbt() {
    }

    public static String fromJson(JsonElement element) {
        StringBuilder sb = new StringBuilder();
        write(sb, element);
        return sb.toString();
    }

    private static void write(StringBuilder sb, JsonElement element) {
        if (element == null || element.isJsonNull()) {
            throw new IllegalArgumentException("SNBT 不支持 null 值");
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                JsonElement value = entry.getValue();
                if (value == null || value.isJsonNull()) {
                    continue;
                }
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeQuoted(sb, entry.getKey());
                sb.append(':');
                write(sb, value);
            }
            sb.append('}');
        } else if (element.isJsonArray()) {
            sb.append('[');
            boolean first = true;
            for (JsonElement value : element.getAsJsonArray()) {
                if (value == null || value.isJsonNull()) {
                    continue;
                }
                if (!first) {
                    sb.append(',');
                }
                first = false;
                write(sb, value);
            }
            sb.append(']');
        } else {
            writePrimitive(sb, element.getAsJsonPrimitive());
        }
    }

    private static void writePrimitive(StringBuilder sb, JsonPrimitive primitive) {
        if (primitive.isBoolean()) {
            sb.append(primitive.getAsBoolean() ? "true" : "false");
            return;
        }
        if (primitive.isNumber()) {
            // Gson 的 LazilyParsedNumber 会保留 JSON 里的原始写法
            String raw = primitive.getAsNumber().toString();
            if (raw.indexOf('.') >= 0 || raw.indexOf('e') >= 0 || raw.indexOf('E') >= 0) {
                sb.append(raw);
                char last = raw.charAt(raw.length() - 1);
                if (last != 'f' && last != 'F' && last != 'd' && last != 'D') {
                    sb.append('f');
                }
            } else {
                BigInteger value = new BigInteger(raw);
                if (value.bitLength() <= 31) {
                    sb.append(value);
                } else if (value.bitLength() <= 63) {
                    sb.append(value).append('L');
                } else {
                    throw new IllegalArgumentException("整数超出 long 范围: " + raw);
                }
            }
            return;
        }
        String text = primitive.getAsString();
        if (TYPED_BYTE_SHORT_LONG.matcher(text).matches()
                || TYPED_FLOAT_DOUBLE.matcher(text).matches()) {
            sb.append(text);
        } else {
            writeQuoted(sb, text);
        }
    }

    private static void writeQuoted(StringBuilder sb, String text) {
        sb.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
