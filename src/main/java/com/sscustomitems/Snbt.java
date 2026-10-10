package com.sscustomitems;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.math.BigInteger;
import java.util.Map;
import java.util.regex.Pattern;

public final class Snbt {

    private static final Pattern TYPED_BYTE_SHORT_LONG =
            Pattern.compile("^[+-]?[0-9]+[bBsSlL]$");
    private static final Pattern TYPED_FLOAT_DOUBLE =
            Pattern.compile("^[+-]?[0-9]*\\.?[0-9]+(?:[eE][+-]?[0-9]+)?[fFdD]$");

    private Snbt() {}

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
                if (value == null || value.isJsonNull()) continue;
                if (!first) sb.append(',');
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
                if (value == null || value.isJsonNull()) continue;
                if (!first) sb.append(',');
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
            String raw = primitive.getAsNumber().toString();
            if (raw.indexOf('.') >= 0 || raw.indexOf('e') >= 0 || raw.indexOf('E') >= 0) {
                sb.append(raw);
                char last = raw.charAt(raw.length() - 1);
                if (last != 'f' && last != 'F' && last != 'd' && last != 'D') sb.append('f');
            } else {
                BigInteger value = new BigInteger(raw);
                if (value.bitLength() <= 31) sb.append(value);
                else if (value.bitLength() <= 63) sb.append(value).append('L');
                else throw new IllegalArgumentException("整数超出 long 范围: " + raw);
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
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }
}
