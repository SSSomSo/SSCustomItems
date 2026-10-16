package com.sscustomitems;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.regex.Pattern;

/**
 * 把原版 SNBT 文本（ItemStack.CODEC 序列化结果）解析为 Gson JsonElement，
 * 用于 /ssci save 把手中物品还原成自定义物品 JSON。
 *
 * 数字处理（与 {@link Snbt} 的写入规则对应，保证往返一致）：
 * - 带类型后缀的数字（1b / 64s / 100L / 1.5f / 2.0d）→ JSON 字符串，
 *   再次写出时 Snbt 会原样识别为带类型的 NBT 数值；
 * - 裸整数 → JSON 数字（long）；
 * - 裸小数 → JSON 数字（double）；
 * - true/false → 布尔；
 * - 裸 token（如 minecraft:paper）→ 字符串。
 */
public final class SnbtToJson {

    private static final Pattern INTEGER_TYPED = Pattern.compile("^[+-]?[0-9]+[bBsSlL]$");
    private static final Pattern DECIMAL_TYPED =
            Pattern.compile("^[+-]?[0-9]*\\.?[0-9]+(?:[eE][+-]?[0-9]+)?[fFdD]$");
    private static final Pattern INTEGER_RAW = Pattern.compile("^[+-]?[0-9]+$");
    private static final Pattern DECIMAL_RAW =
            Pattern.compile("^[+-]?[0-9]*\\.[0-9]+(?:[eE][+-]?[0-9]+)?$");

    private final String text;
    private int pos;

    private SnbtToJson(String text) {
        this.text = text;
    }

    public static JsonElement parse(String snbt) {
        SnbtToJson parser = new SnbtToJson(snbt);
        parser.skipWhitespace();
        JsonElement value = parser.readValue();
        return value;
    }

    private JsonElement readValue() {
        skipWhitespace();
        char c = peek();
        if (c == '{') {
            return readCompound();
        }
        if (c == '[') {
            return readList();
        }
        return readPrimitive();
    }

    private JsonObject readCompound() {
        JsonObject object = new JsonObject();
        expect('{');
        skipWhitespace();
        if (peek() == '}') {
            next();
            return object;
        }
        while (true) {
            skipWhitespace();
            String key = readKey();
            skipWhitespace();
            expect(':');
            JsonElement value = readValue();
            object.add(key, value);
            skipWhitespace();
            if (peek() == ',') {
                next();
                continue;
            }
            expect('}');
            break;
        }
        return object;
    }

    private JsonArray readList() {
        JsonArray array = new JsonArray();
        expect('[');
        skipWhitespace();
        // 类型化数组前缀：[I; [L; [D; [B;（跳过 "字母;"）
        if (pos + 1 < text.length() && text.charAt(pos + 1) == ';') {
            next(); // 类型字母
            next(); // ';'
            skipWhitespace();
        }
        if (peek() == ']') {
            next();
            return array;
        }
        while (true) {
            skipWhitespace();
            if (peek() == ']') {
                next();
                break;
            }
            JsonElement value = readValue();
            array.add(value);
            skipWhitespace();
            if (peek() == ',') {
                next();
                continue;
            }
            expect(']');
            break;
        }
        return array;
    }

    private String readKey() {
        char c = peek();
        if (c == '"' || c == '\'') {
            return readQuotedString();
        }
        return readBareToken();
    }

    private JsonElement readPrimitive() {
        char c = peek();
        if (c == '"' || c == '\'') {
            return new JsonPrimitive(readQuotedString());
        }
        String token = readBareToken();
        if (token.equals("true")) {
            return new JsonPrimitive(true);
        }
        if (token.equals("false")) {
            return new JsonPrimitive(false);
        }
        if (INTEGER_TYPED.matcher(token).matches() || DECIMAL_TYPED.matcher(token).matches()) {
            // 保留后缀，交给 Snbt 原样输出为带类型数值
            return new JsonPrimitive(token);
        }
        if (INTEGER_RAW.matcher(token).matches()) {
            return new JsonPrimitive(Long.parseLong(token));
        }
        if (DECIMAL_RAW.matcher(token).matches()) {
            return new JsonPrimitive(Double.parseDouble(token));
        }
        return new JsonPrimitive(token);
    }

    private String readQuotedString() {
        char quote = next();
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = next();
            if (c == quote) {
                break;
            }
            if (c == '\\') {
                char escaped = next();
                switch (escaped) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case '"' -> sb.append('"');
                    case '\'' -> sb.append('\'');
                    case '\\' -> sb.append('\\');
                    default -> sb.append(escaped);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String readBareToken() {
        int start = pos;
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c == ',' || c == '}' || c == ']' || c == ':' || c == '{' || c == '['
                    || Character.isWhitespace(c)) {
                break;
            }
            pos++;
        }
        return text.substring(start, pos);
    }

    private char peek() {
        if (pos >= text.length()) {
            return '\0';
        }
        return text.charAt(pos);
    }

    private char next() {
        return text.charAt(pos++);
    }

    private void expect(char c) {
        skipWhitespace();
        if (peek() != c) {
            throw new IllegalStateException("SNBT 解析错误：期望 '" + c + "'，实际为 '"
                    + (peek() == '\0' ? "结束" : peek()) + "'（位置 " + pos + "）");
        }
        pos++;
    }

    private void skipWhitespace() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }
}
