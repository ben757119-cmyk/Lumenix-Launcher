package com.lumenix.launcher.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Json {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private Json() {}

    public static JsonObject parseObject(String s) {
        JsonElement el = JsonParser.parseString(s);
        return el != null && el.isJsonObject() ? el.getAsJsonObject() : new JsonObject();
    }

    public static JsonObject readObject(Path path) throws Exception {
        if (!Files.isRegularFile(path)) return new JsonObject();
        return parseObject(Files.readString(path, StandardCharsets.UTF_8));
    }

    public static void write(Path path, Object obj) throws Exception {
        if (path.getParent() != null) Files.createDirectories(path.getParent());
        Files.writeString(path, GSON.toJson(obj), StandardCharsets.UTF_8);
    }
}
