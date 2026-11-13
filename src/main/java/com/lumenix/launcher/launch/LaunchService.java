package com.lumenix.launcher.launch;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lumenix.launcher.Paths;
import com.lumenix.launcher.auth.AuthSession;
import com.lumenix.launcher.install.InstallMeta;
import com.lumenix.launcher.install.LauncherConfig;
import com.lumenix.launcher.util.Json;
import com.lumenix.launcher.util.Log;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class LaunchService {
    private LaunchService() {}

    public static Process launch(LauncherConfig cfg, InstallMeta meta, AuthSession auth) throws Exception {
        Path fabricJson = Paths.minecraft().resolve("versions")
                .resolve("fabric-loader-" + meta.fabricLoaderVersion + "-" + Paths.MC_VERSION)
                .resolve("fabric-loader-" + meta.fabricLoaderVersion + "-" + Paths.MC_VERSION + ".json");
        if (!Files.isRegularFile(fabricJson)) {
            throw new IllegalStateException("Fabric profile missing — run install first");
        }
        JsonObject profile = Json.readObject(fabricJson);
        Path clientJar = Paths.minecraft().resolve("versions").resolve(Paths.MC_VERSION)
                .resolve(Paths.MC_VERSION + ".jar");
        if (!Files.isRegularFile(clientJar)) {
            throw new IllegalStateException("Minecraft client jar missing: " + clientJar);
        }
        List<Path> classpath = new ArrayList<>();
        classpath.add(clientJar);
        collectLibraries(profile, classpath);
        Path vanillaJson = Paths.minecraft().resolve("versions").resolve(Paths.MC_VERSION)
                .resolve(Paths.MC_VERSION + ".json");
        if (Files.isRegularFile(vanillaJson)) collectLibraries(Json.readObject(vanillaJson), classpath);

        String cp = classpath.stream().filter(Files::isRegularFile)
                .map(p -> p.toAbsolutePath().toString()).distinct()
                .collect(Collectors.joining(File.pathSeparator));

        String mainClass = profile.has("mainClass")
                ? profile.get("mainClass").getAsString()
                : "net.fabricmc.loader.impl.launch.knot.KnotClient";

        String java = resolveJava(cfg);
        List<String> cmd = new ArrayList<>();
        cmd.add(java);
        cmd.add("-Xmx" + Math.max(1024, cfg.ramMb) + "M");
        cmd.add("-Xms512M");
        cmd.add("-Djava.library.path=" + Paths.minecraft().resolve("natives").toAbsolutePath());
        cmd.add("-Dfabric.addMods=" + Paths.mods().toAbsolutePath());
        cmd.add("-cp");
        cmd.add(cp);
        cmd.add(mainClass);
        cmd.add("--username"); cmd.add(auth.username != null ? auth.username : "Player");
        cmd.add("--uuid"); cmd.add(normalizeUuid(auth.uuid));
        cmd.add("--accessToken"); cmd.add(auth.minecraftToken != null ? auth.minecraftToken : "0");
        cmd.add("--version"); cmd.add("fabric-loader-" + meta.fabricLoaderVersion + "-" + Paths.MC_VERSION);
        cmd.add("--gameDir"); cmd.add(Paths.minecraft().toAbsolutePath().toString());
        cmd.add("--assetsDir"); cmd.add(Paths.minecraft().resolve("assets").toAbsolutePath().toString());
        cmd.add("--assetIndex"); cmd.add(assetIndexId(vanillaJson));
        cmd.add("--userType"); cmd.add("msa");
        cmd.add("--versionType"); cmd.add("Lumenix");

        Log.info("Launching Minecraft " + Paths.MC_VERSION + " + Fabric with Lumenix (" + cfg.edition + ")");
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(Paths.minecraft().toFile());
        pb.redirectErrorStream(true);
        Path log = Paths.logs().resolve("game.log");
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
        return pb.start();
    }

    private static void collectLibraries(JsonObject versionJson, List<Path> out) {
        if (versionJson == null || !versionJson.has("libraries")) return;
        for (JsonElement el : versionJson.getAsJsonArray("libraries")) {
            JsonObject lib = el.getAsJsonObject();
            if (lib.has("downloads") && lib.getAsJsonObject("downloads").has("artifact")) {
                String path = lib.getAsJsonObject("downloads").getAsJsonObject("artifact").get("path").getAsString();
                out.add(Paths.minecraft().resolve("libraries").resolve(path));
            } else if (lib.has("name")) {
                String[] p = lib.get("name").getAsString().split(":");
                if (p.length >= 3) {
                    String rel = p[0].replace('.', '/') + "/" + p[1] + "/" + p[2] + "/" + p[1] + "-" + p[2] + ".jar";
                    out.add(Paths.minecraft().resolve("libraries").resolve(rel));
                }
            }
        }
    }

    private static String assetIndexId(Path vanillaJson) {
        try {
            if (Files.isRegularFile(vanillaJson)) {
                JsonObject v = Json.readObject(vanillaJson);
                if (v.has("assetIndex")) return v.getAsJsonObject("assetIndex").get("id").getAsString();
            }
        } catch (Throwable ignored) {}
        return Paths.MC_VERSION;
    }

    private static String resolveJava(LauncherConfig cfg) {
        if (cfg.javaPath != null && !cfg.javaPath.isBlank() && Files.isRegularFile(Path.of(cfg.javaPath))) {
            return cfg.javaPath;
        }
        String home = System.getProperty("java.home");
        String bin = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "bin\\java.exe" : "bin/java";
        Path p = Path.of(home, bin);
        if (Files.isRegularFile(p)) return p.toAbsolutePath().toString();
        return "java";
    }

    private static String normalizeUuid(String id) {
        if (id == null || id.isBlank()) return "00000000000000000000000000000000";
        String hex = id.replace("-", "");
        if (hex.length() == 32) {
            return hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16)
                    + "-" + hex.substring(16, 20) + "-" + hex.substring(20);
        }
        return id;
    }
}
