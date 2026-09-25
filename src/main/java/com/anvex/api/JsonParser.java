package com.anvex.api;

import java.util.HashMap;
import java.util.Map;

public final class JsonParser {

    private JsonParser() {}

    public static Map<String, String> parseFlat(String json) {
        Map<String, String> values = new HashMap<>();
        if (json == null) {
            return values;
        }
        String trimmed = json.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return values;
        }

        String body = trimmed.substring(1, trimmed.length() - 1);
        int index = 0;
        int length = body.length();

        while (index < length) {
            while (index < length && Character.isWhitespace(body.charAt(index))) index++;
            if (index >= length) break;
            if (body.charAt(index) == ',') { index++; continue; }

            if (body.charAt(index) != '"') return new HashMap<>();
            int[] keyEnd = readString(body, index);
            if (keyEnd == null) return new HashMap<>();
            String key = unescape(body.substring(index + 1, keyEnd[0]));
            index = keyEnd[1];

            while (index < length && Character.isWhitespace(body.charAt(index))) index++;
            if (index >= length || body.charAt(index) != ':') return new HashMap<>();
            index++;

            while (index < length && Character.isWhitespace(body.charAt(index))) index++;
            if (index >= length) return new HashMap<>();

            char c = body.charAt(index);
            if (c == '"') {
                int[] valueEnd = readString(body, index);
                if (valueEnd == null) return new HashMap<>();
                values.put(key, unescape(body.substring(index + 1, valueEnd[0])));
                index = valueEnd[1];
            } else {
                int start = index;
                while (index < length && body.charAt(index) != ',' ) index++;
                values.put(key, body.substring(start, index).trim());
            }
        }
        return values;
    }

    private static int[] readString(String body, int start) {
        int index = start + 1;
        while (index < body.length()) {
            char c = body.charAt(index);
            if (c == '\\') {
                index += 2;
                continue;
            }
            if (c == '"') {
                return new int[]{index, index + 1};
            }
            index++;
        }
        return null;
    }

    private static String unescape(String s) {
        return s.replace("\\\"", "\"").replace("\\\\", "\\")
                .replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t");
    }
}
