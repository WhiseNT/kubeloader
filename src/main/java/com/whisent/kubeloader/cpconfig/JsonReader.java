package com.whisent.kubeloader.cpconfig;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.whisent.kubeloader.Kubeloader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.jar.JarEntry;

public class JsonReader {
    public static ArrayList<?> loadContentPackConfig(Path configPath) {
        try {
            JsonObject json = getJsonObject(configPath);
            loadPackConfigByJson(json);
            return loadPackConfigByJson(json);
        } catch (Exception e) {
            // 处理异常
            e.printStackTrace();
        }
        return null;
    }
    public static JsonObject getJsonObject(Path jsonPath) {
        try {
            String jsonString = Files.readString(jsonPath);
            JsonObject json = JsonParser.parseString(jsonString).getAsJsonObject();
            return json;
        } catch (Exception e) {
            // 处理异常
            e.printStackTrace();
        }
        return null;
    }
    public static ArrayList<?> loadPackConfigByJson(JsonObject json) {
        String packName = json.get("name").getAsString();
        String packVersion = json.get("version").getAsString();
        JsonArray authors = json.getAsJsonArray("authors").getAsJsonArray();
        String description = json.get("description").getAsString();
        String requireGameVersion = json.get("requireGameVersion").getAsString();
        JsonArray requireMods = json.getAsJsonArray("requireMods").getAsJsonArray();
        ArrayList<?> configs = new ArrayList<>(){{
            add(packName);
            add(packVersion);
            add(authors);
            add(description);
            add(requireGameVersion);
            add(requireMods);
        }};
        return configs;
    }

}
