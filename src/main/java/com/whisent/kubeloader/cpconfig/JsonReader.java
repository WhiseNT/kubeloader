package com.whisent.kubeloader.cpconfig;

import com.google.gson.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JsonReader {
    public static Map<String, Object> loadConfig(Path configPath) {
        try {
            JsonObject json = getJsonObject(configPath);
            return parseJsonObject(json); // 返回 Map 结构
        } catch (Exception e) {
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    public static JsonObject getJsonObject(Path jsonPath) {
        try {
            String jsonString = Files.readString(jsonPath);
            JsonObject json = JsonParser.parseString(jsonString).getAsJsonObject();
            return json;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // 解析 JSON 对象为 Map
    public static Map<String, Object> parseJsonObject(JsonObject json) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            result.put(key, parseJsonElement(value));
        }
        return result;
    }

    private static Object parseJsonElement(JsonElement element) {
        if (element.isJsonObject()) {
            return parseJsonObject(element.getAsJsonObject());
        } else if (element.isJsonArray()) {
            return parseJsonArray(element.getAsJsonArray());
        } else if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isString()) {
                return primitive.getAsString();
            } else if (primitive.isBoolean()) {
                return primitive.getAsBoolean();
            } else if (primitive.isNumber()) {
                return primitive.getAsNumber();
            }
        } else if (element.isJsonNull()) {
            return null;
        }
        return "Unsupported JSON Type";
    }

    // 解析 JSON 数组为 List
    private static List<Object> parseJsonArray(JsonArray jsonArray) {
        List<Object> list = new ArrayList<>();
        for (JsonElement element : jsonArray) {
            list.add(parseJsonElement(element));
        }
        return list;
    }
}
