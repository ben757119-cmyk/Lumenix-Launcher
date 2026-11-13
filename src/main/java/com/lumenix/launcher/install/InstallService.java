package com.lumenix.launcher.install;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lumenix.launcher.Paths;
import com.lumenix.launcher.download.DownloadManager;
import com.lumenix.launcher.util.Json;
import com.lumenix.launcher.util.Log;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Persistent Minecraft 26.2 + Fabric installer. Never wipes a valid install. */
public final class InstallService {
    private final DownloadManager dl;

    public InstallService(DownloadManager dl) { this.dl = dl; }

    public InstallMeta loadMeta() {
        try {
            if (Files.isRegularFile(Paths.installMeta())) {
                return Json.GSON.fromJson(Files.readString(Paths.installMeta(), StandardCharsets.UTF_8), InstallMeta.class);
            }
        } catch (Throwable t) { Log.warn("Could not read install meta: " + t.getMessage()); }
        return new InstallMeta();
    }

    public void saveMeta(InstallMeta meta) throws Exception {
        meta.lastVerifiedAt = System.currentTimeMillis();
        Json.write(Paths.installMeta(), meta);
    }

    public LauncherConfig loadConfig() {
        try {
            if (Files.isRegularFile(Paths.launcherConfig())) {
                return Json.GSON.fromJson(Files.readString(Paths.launcherConfig(), StandardCharsets.UTF_8), LauncherConfig.class);
            }
        } catch (Throwable ignored) {}
        return new LauncherConfig();
    }

    public void saveConfig(LauncherConfig cfg) throws Exception { Json.write(Paths.launcherConfig(), cfg); }

    public void ensureMinecraft(InstallMeta meta) throws Exception {
        Paths.ensureDirs();
        Log.info("Checking Minecraft " + Paths.MC_VERSION + " …");
        String manifestJson = dl.getString(Paths.MOJANG_MANIFEST);
        JsonObject manifest = Json.parseObject(manifestJson);
        JsonArray versions = manifest.getAsJsonArray("versions");
        String versionUrl = null;
        for (JsonElement el : versions) {
            JsonObject v = el.getAsJsonObject();
            if (Paths.MC_VERSION.equals(v.get("id").getAsString())) {
                versionUrl = v.get("url").getAsString();
                break;
            }
        }
        if (versionUrl == null) {
            Log.warn("Version " + Paths.MC_VERSION + " not listed in Mojang manifest; using cached/local if present");
            Path jar = clientJarPath();
            if (Files.isRegularFile(jar)) { Log.info("Using existing client jar: " + jar); return; }
            throw new IllegalStateException("Minecraft " + Paths.MC_VERSION + " not on Mojang manifest and no local jar at " + jar);
        }
        Path versionJsonPath = Paths.minecraft().resolve("versions").resolve(Paths.MC_VERSION).resolve(Paths.MC_VERSION + ".json");
        dl.download(versionUrl, versionJsonPath, null);
        JsonObject version = Json.readObject(versionJsonPath);
        JsonObject client = version.getAsJsonObject("downloads").getAsJsonObject("client");
        Path clientJar = clientJarPath();
        String sha1 = client.has("sha1") ? client.get("sha1").getAsString() : null;
        downloadMojangSha1(client.get("url").getAsString(), clientJar, sha1);
        JsonArray libs = version.getAsJsonArray("libraries");
        if (libs != null) {
            for (JsonElement el : libs) {
                JsonObject lib = el.getAsJsonObject();
                if (!libraryAllowedOnOs(lib)) continue;
                if (!lib.has("downloads") || !lib.getAsJsonObject("downloads").has("artifact")) continue;
                JsonObject artifact = lib.getAsJsonObject("downloads").getAsJsonObject("artifact");
                Path dest = Paths.minecraft().resolve("libraries").resolve(artifact.get("path").getAsString());
                downloadMojangSha1(artifact.get("url").getAsString(), dest, artifact.has("sha1") ? artifact.get("sha1").getAsString() : null);
            }
        }
        if (version.has("assetIndex")) {
            JsonObject assetIndex = version.getAsJsonObject("assetIndex");
            String id = assetIndex.get("id").getAsString();
            Path indexPath = Paths.minecraft().resolve("assets").resolve("indexes").resolve(id + ".json");
            dl.download(assetIndex.get("url").getAsString(), indexPath, null);
            ensureAssets(indexPath);
        }
        meta.minecraftVersion = Paths.MC_VERSION;
        saveMeta(meta);
        Log.info("Minecraft " + Paths.MC_VERSION + " ready");
    }

    private void ensureAssets(Path indexPath) throws Exception {
        JsonObject index = Json.readObject(indexPath);
        if (!index.has("objects")) return;
        JsonObject objects = index.getAsJsonObject("objects");
        int missing = 0;
        for (String key : objects.keySet()) {
            JsonObject obj = objects.getAsJsonObject(key);
            String hash = obj.get("hash").getAsString();
            String prefix = hash.substring(0, 2);
            Path dest = Paths.minecraft().resolve("assets").resolve("objects").resolve(prefix).resolve(hash);
            if (Files.isRegularFile(dest) && Files.size(dest) > 0) continue;
            try {
                downloadMojangSha1("https://resources.download.minecraft.net/" + prefix + "/" + hash, dest, hash);
                missing++;
            } catch (Exception e) { Log.warn("Asset skip " + hash + ": " + e.getMessage()); }
        }
        Log.info("Assets checked; fetched " + missing + " missing objects");
    }

    public void ensureFabric(InstallMeta meta) throws Exception {
        Log.info("Checking Fabric for " + Paths.MC_VERSION + " …");
        String loadersJson = dl.getString(Paths.FABRIC_LOADER_META + "/versions/loader/" + Paths.MC_VERSION);
        JsonArray loaders = parseArray(loadersJson);
        if (loaders == null || loaders.isEmpty()) {
            throw new IllegalStateException("No Fabric loader builds for Minecraft " + Paths.MC_VERSION);
        }
        JsonObject best = loaders.get(0).getAsJsonObject();
        String loaderVer = best.getAsJsonObject("loader").get("version").getAsString();
        String intermediaryVer = best.getAsJsonObject("intermediary").get("version").getAsString();
        String profileUrl = Paths.FABRIC_LOADER_META + "/versions/loader/" + Paths.MC_VERSION + "/" + loaderVer + "/profile/json";
        Path profilePath = Paths.versions().resolve("fabric-loader-" + loaderVer + "-" + Paths.MC_VERSION + ".json");
        dl.download(profileUrl, profilePath, null);
        String fabricId = "fabric-loader-" + loaderVer + "-" + Paths.MC_VERSION;
        Path mcVersionDir = Paths.minecraft().resolve("versions").resolve(fabricId);
        Files.createDirectories(mcVersionDir);
        Files.copy(profilePath, mcVersionDir.resolve(fabricId + ".json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        JsonObject profile = Json.readObject(profilePath);
        if (profile.has("libraries")) {
            for (JsonElement el : profile.getAsJsonArray("libraries")) {
                JsonObject lib = el.getAsJsonObject();
                String name = lib.get("name").getAsString();
                String urlBase = lib.has("url") ? lib.get("url").getAsString() : "https://maven.fabricmc.net/";
                MavenCoord c = MavenCoord.parse(name);
                Path dest = Paths.minecraft().resolve("libraries").resolve(c.toPath());
                if (Files.isRegularFile(dest)) continue;
                String url = urlBase.endsWith("/") ? urlBase + c.toPath() : urlBase + "/" + c.toPath();
                try { dl.download(url, dest, null); }
                catch (Exception e) { Log.warn("Fabric lib failed " + name + ": " + e.getMessage()); }
            }
        }
        meta.fabricLoaderVersion = loaderVer;
        meta.fabricYarnOrIntermediary = intermediaryVer;
        saveMeta(meta);
        Log.info("Fabric loader " + loaderVer + " ready for " + Paths.MC_VERSION);
    }

    public Path ensureLumenixClient(LauncherConfig cfg, InstallMeta meta) throws Exception {
        Paths.ensureDirs();
        String edition = cfg.edition == null ? "FREE" : cfg.edition.toUpperCase(Locale.ROOT);
        boolean paid = "PAID".equals(edition);
        String prefix = paid ? "lumenix-paid-" : "lumenix-free-";
        Log.info("Resolving Lumenix " + edition + " from GitHub " + cfg.clientRepo + " …");
        String body = dl.getString("https://api.github.com/repos/" + cfg.clientRepo + "/releases/latest");
        JsonObject rel = Json.parseObject(body);
        String tag = rel.has("tag_name") ? rel.get("tag_name").getAsString() : "unknown";
        JsonArray assets = rel.getAsJsonArray("assets");
        String jarUrl = null, jarName = null;
        if (assets != null) {
            for (JsonElement el : assets) {
                JsonObject a = el.getAsJsonObject();
                String name = a.get("name").getAsString();
                if (name.startsWith(prefix) && name.endsWith(".jar")) {
                    jarUrl = a.get("browser_download_url").getAsString();
                    jarName = name;
                    break;
                }
            }
        }
        if (jarUrl == null) throw new IllegalStateException("No " + prefix + "*.jar on latest release of " + cfg.clientRepo);
        Path dest = Paths.clients().resolve(jarName);
        String knownSha = paid ? meta.paidClientSha256 : meta.freeClientSha256;
        String knownTag = paid ? meta.paidClientTag : meta.freeClientTag;
        if (Files.isRegularFile(dest) && tag.equals(knownTag) && knownSha != null && !knownSha.isBlank()) {
            if (DownloadManager.sha256(dest).equalsIgnoreCase(knownSha)) {
                Log.info("Lumenix JAR up to date: " + jarName);
                syncModsFolder(dest);
                return dest;
            }
        }
        dl.download(jarUrl, dest, null);
        String sha = DownloadManager.sha256(dest);
        if (paid) { meta.paidClientTag = tag; meta.paidClientSha256 = sha; }
        else { meta.freeClientTag = tag; meta.freeClientSha256 = sha; }
        saveMeta(meta);
        syncModsFolder(dest);
        Log.info("Lumenix client installed: " + jarName + " (" + tag + ")");
        return dest;
    }

    public void syncModsFolder(Path clientJar) throws Exception {
        Files.createDirectories(Paths.mods());
        try (var stream = Files.list(Paths.mods())) {
            stream.filter(p -> p.getFileName().toString().startsWith("lumenix-") && p.toString().endsWith(".jar"))
                    .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
        }
        Files.copy(clientJar, Paths.mods().resolve(clientJar.getFileName().toString()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    public void repair(LauncherConfig cfg) throws Exception {
        Log.info("Repair: scanning installation …");
        InstallMeta meta = loadMeta();
        meta.fileHashes.clear();
        ensureMinecraft(meta);
        ensureFabric(meta);
        ensureLumenixClient(cfg, meta);
        Log.info("Repair complete");
    }

    public Path clientJarPath() {
        return Paths.minecraft().resolve("versions").resolve(Paths.MC_VERSION).resolve(Paths.MC_VERSION + ".jar");
    }

    private void downloadMojangSha1(String url, Path dest, String sha1) throws Exception {
        if (Files.isRegularFile(dest) && sha1 != null && !sha1.isBlank()) {
            if (sha1(dest).equalsIgnoreCase(sha1)) return;
        } else if (Files.isRegularFile(dest)) return;
        dl.download(url, dest, null);
        if (sha1 != null && !sha1.isBlank() && !sha1(dest).equalsIgnoreCase(sha1)) {
            Files.deleteIfExists(dest);
            throw new IllegalStateException("SHA1 mismatch: " + dest.getFileName());
        }
    }

    private static String sha1(Path file) throws Exception {
        var md = java.security.MessageDigest.getInstance("SHA-1");
        try (var in = Files.newInputStream(file)) {
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        return java.util.HexFormat.of().formatHex(md.digest());
    }

    private static boolean libraryAllowedOnOs(JsonObject lib) {
        if (!lib.has("rules")) return true;
        boolean allowed = false;
        for (JsonElement el : lib.getAsJsonArray("rules")) {
            JsonObject rule = el.getAsJsonObject();
            String action = rule.get("action").getAsString();
            boolean match = true;
            if (rule.has("os")) {
                String name = rule.getAsJsonObject("os").get("name").getAsString();
                String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
                match = switch (name) {
                    case "windows" -> os.contains("win");
                    case "osx" -> os.contains("mac");
                    case "linux" -> os.contains("nux") || os.contains("nix");
                    default -> true;
                };
            }
            if (match) allowed = "allow".equals(action);
        }
        return allowed;
    }

    private static JsonArray parseArray(String s) {
        try { return com.google.gson.JsonParser.parseString(s).getAsJsonArray(); }
        catch (Throwable t) { return null; }
    }

    private record MavenCoord(String group, String artifact, String version) {
        static MavenCoord parse(String name) {
            String[] p = name.split(":");
            return new MavenCoord(p[0], p[1], p[2]);
        }
        String toPath() {
            return group.replace('.', '/') + "/" + artifact + "/" + version + "/" + artifact + "-" + version + ".jar";
        }
    }
}
