package com.lumenix.launcher.download;

import com.lumenix.launcher.Paths;
import com.lumenix.launcher.util.Log;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Intelligent download manager: cache, SHA-256 verify, retries, skip if valid. */
public final class DownloadManager {
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ExecutorService pool = Executors.newFixedThreadPool(4);

    public void shutdown() { pool.shutdownNow(); }

    public Path cacheKeyPath(String url) {
        String name = Integer.toHexString(url.hashCode()) + "-" + url.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (name.length() > 120) name = name.substring(0, 120);
        return Paths.cache().resolve(name);
    }

    public void download(String url, Path dest, String sha256Hex) throws Exception {
        Paths.ensureDirs();
        if (dest.getParent() != null) Files.createDirectories(dest.getParent());

        if (Files.isRegularFile(dest) && sha256Hex != null && !sha256Hex.isBlank()) {
            String got = sha256(dest);
            if (got.equalsIgnoreCase(sha256Hex)) {
                Log.info("Reuse valid file: " + dest.getFileName());
                return;
            }
            Log.warn("Checksum mismatch, redownloading: " + dest.getFileName());
        } else if (Files.isRegularFile(dest) && (sha256Hex == null || sha256Hex.isBlank())) {
            Log.info("Reuse existing file: " + dest.getFileName());
            return;
        }

        Path cache = cacheKeyPath(url);
        if (Files.isRegularFile(cache)) {
            if (sha256Hex == null || sha256Hex.isBlank() || sha256(cache).equalsIgnoreCase(sha256Hex)) {
                Files.createDirectories(dest.getParent());
                Files.copy(cache, dest, StandardCopyOption.REPLACE_EXISTING);
                Log.info("Installed from cache: " + dest.getFileName());
                return;
            }
        }

        Exception last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                Log.info("Download (" + attempt + "/3): " + url);
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofMinutes(10))
                        .header("User-Agent", "LumenixLauncher/1.0")
                        .GET()
                        .build();
                HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
                if (res.statusCode() < 200 || res.statusCode() >= 300) {
                    throw new IllegalStateException("HTTP " + res.statusCode() + " for " + url);
                }
                Path tmp = dest.resolveSibling(dest.getFileName() + ".part");
                try (InputStream in = res.body(); OutputStream out = Files.newOutputStream(tmp)) {
                    in.transferTo(out);
                }
                if (sha256Hex != null && !sha256Hex.isBlank()) {
                    String got = sha256(tmp);
                    if (!got.equalsIgnoreCase(sha256Hex)) {
                        Files.deleteIfExists(tmp);
                        throw new IllegalStateException("SHA-256 mismatch for " + dest.getFileName());
                    }
                }
                Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
                Files.createDirectories(cache.getParent());
                Files.copy(dest, cache, StandardCopyOption.REPLACE_EXISTING);
                Log.info("Saved: " + dest.getFileName());
                return;
            } catch (Exception e) {
                last = e;
                Log.warn("Download failed attempt " + attempt + ": " + e.getMessage());
                Thread.sleep(500L * attempt);
            }
        }
        throw last != null ? last : new IllegalStateException("Download failed: " + url);
    }

    public Future<?> downloadAsync(String url, Path dest, String sha256Hex) {
        return pool.submit(() -> {
            try { download(url, dest, sha256Hex); }
            catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    public String getString(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "LumenixLauncher/1.0")
                .GET()
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + res.statusCode() + " for " + url);
        }
        return res.body();
    }

    public static String sha256(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        return HexFormat.of().formatHex(md.digest()).toLowerCase(Locale.ROOT);
    }
}
