package com.lumenix.launcher;

import java.nio.file.Path;

/** Persistent Lumenix data root and layout under %APPDATA%/Lumenix (or ~/.lumenix). */
public final class Paths {
    public static final String MC_VERSION = "26.2";
    public static final String FABRIC_LOADER_META = "https://meta.fabricmc.net/v2";
    public static final String MOJANG_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    public static final String CLIENT_REPO = "ben757119-cmyk/Lumenix";

    private Paths() {}

    public static Path root() {
        String appdata = System.getenv("APPDATA");
        if (appdata != null && !appdata.isBlank()) {
            return java.nio.file.Path.of(appdata, "Lumenix");
        }
        return java.nio.file.Path.of(System.getProperty("user.home"), ".lumenix");
    }

    public static Path minecraft() { return root().resolve("Minecraft"); }
    public static Path versions() { return root().resolve("Versions"); }
    public static Path clients() { return root().resolve("Clients"); }
    public static Path mods() { return root().resolve("Mods"); }
    public static Path cache() { return root().resolve("Cache"); }
    public static Path logs() { return root().resolve("Logs"); }
    public static Path config() { return root().resolve("Config"); }

    public static Path launcherConfig() { return config().resolve("launcher.json"); }
    public static Path installMeta() { return config().resolve("install.json"); }
    public static Path authStore() { return config().resolve("auth.json"); }

    public static void ensureDirs() throws Exception {
        for (Path p : new Path[]{minecraft(), versions(), clients(), mods(), cache(), logs(), config()}) {
            java.nio.file.Files.createDirectories(p);
        }
        java.nio.file.Files.createDirectories(minecraft().resolve("libraries"));
        java.nio.file.Files.createDirectories(minecraft().resolve("assets"));
        java.nio.file.Files.createDirectories(minecraft().resolve("versions"));
    }
}
