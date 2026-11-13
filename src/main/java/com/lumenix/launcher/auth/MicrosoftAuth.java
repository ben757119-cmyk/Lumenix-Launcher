package com.lumenix.launcher.auth;

import com.google.gson.JsonObject;
import com.lumenix.launcher.Paths;
import com.lumenix.launcher.util.Json;
import com.lumenix.launcher.util.Log;

import java.awt.Desktop;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.function.Consumer;

public final class MicrosoftAuth {
    private static final String CLIENT_ID = "00000000402b5328";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20)).build();

    private MicrosoftAuth() {}

    public static AuthSession load() {
        try {
            if (Files.isRegularFile(Paths.authStore())) {
                AuthSession s = Json.GSON.fromJson(
                        Files.readString(Paths.authStore(), StandardCharsets.UTF_8), AuthSession.class);
                if (s != null) return s;
            }
        } catch (Throwable ignored) {}
        return new AuthSession();
    }

    public static void save(AuthSession s) throws Exception { Json.write(Paths.authStore(), s); }

    public static void clear() {
        try { Files.deleteIfExists(Paths.authStore()); } catch (Throwable ignored) {}
    }

    public static AuthSession loginDeviceCode(Consumer<String> status) throws Exception {
        status.accept("Requesting Microsoft device code…");
        String body = "client_id=" + url(CLIENT_ID) + "&scope=" + url("XboxLive.signin offline_access");
        JsonObject dc = postForm("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode", body);
        String userCode = dc.get("user_code").getAsString();
        String deviceCode = dc.get("device_code").getAsString();
        String verifyUrl = dc.has("verification_uri") ? dc.get("verification_uri").getAsString() : "https://www.microsoft.com/link";
        int interval = dc.has("interval") ? dc.get("interval").getAsInt() : 5;
        int expires = dc.has("expires_in") ? dc.get("expires_in").getAsInt() : 900;
        status.accept("Open " + verifyUrl + " and enter code: " + userCode);
        try {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create(verifyUrl));
        } catch (Throwable ignored) {}
        long deadline = System.currentTimeMillis() + expires * 1000L;
        String accessToken = null, refresh = null;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(Math.max(3, interval) * 1000L);
            JsonObject token;
            try {
                token = postForm("https://login.microsoftonline.com/consumers/oauth2/v2.0/token",
                        "grant_type=urn:ietf:params:oauth:grant-type:device_code&client_id=" + url(CLIENT_ID)
                                + "&device_code=" + url(deviceCode));
            } catch (Exception e) { continue; }
            if (token.has("access_token")) {
                accessToken = token.get("access_token").getAsString();
                if (token.has("refresh_token")) refresh = token.get("refresh_token").getAsString();
                break;
            }
            if (token.has("error")) {
                String err = token.get("error").getAsString();
                if ("authorization_pending".equals(err) || "slow_down".equals(err)) continue;
                throw new IllegalStateException("Microsoft auth error: " + err);
            }
        }
        if (accessToken == null) throw new IllegalStateException("Microsoft login timed out");
        status.accept("Xbox Live…");
        JsonObject xbl = postJson("https://user.auth.xboxlive.com/user/authenticate",
                "{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\",\"RpsTicket\":\"d="
                        + accessToken + "\"},\"RelyingParty\":\"http://auth.xboxlive.com\",\"TokenType\":\"JWT\"}");
        String xblToken = xbl.get("Token").getAsString();
        String uhs = xbl.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();
        status.accept("XSTS…");
        JsonObject xsts = postJson("https://xsts.auth.xboxlive.com/xsts/authorize",
                "{\"Properties\":{\"SandboxId\":\"RETAIL\",\"UserTokens\":[\"" + xblToken
                        + "\"]},\"RelyingParty\":\"rp://api.minecraftservices.com/\",\"TokenType\":\"JWT\"}");
        String xstsToken = xsts.get("Token").getAsString();
        status.accept("Minecraft services…");
        JsonObject mcLogin = postJson("https://api.minecraftservices.com/authentication/login_with_xbox",
                "{\"identityToken\":\"XBL3.0 x=" + uhs + ";" + xstsToken + "\"}");
        String mcToken = mcLogin.get("access_token").getAsString();
        JsonObject profile = getJson("https://api.minecraftservices.com/minecraft/profile", mcToken);
        AuthSession session = new AuthSession();
        session.minecraftToken = mcToken;
        session.username = profile.get("name").getAsString();
        session.uuid = profile.get("id").getAsString();
        session.expiresAt = System.currentTimeMillis() + 24L * 60 * 60 * 1000;
        session.refreshToken = refresh != null ? refresh : "";
        session.accountType = "msa";
        save(session);
        status.accept("Signed in as " + session.username);
        Log.info("Authenticated as " + session.username);
        return session;
    }

    private static JsonObject postForm(String url, String form) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded").header("User-Agent", "LumenixLauncher/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        return Json.parseObject(HTTP.send(req, HttpResponse.BodyHandlers.ofString()).body());
    }

    private static JsonObject postJson(String url, String json) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json").header("User-Agent", "LumenixLauncher/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 400) throw new IllegalStateException("HTTP " + res.statusCode() + ": " + res.body());
        return Json.parseObject(res.body());
    }

    private static JsonObject getJson(String url, String bearer) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + bearer).header("User-Agent", "LumenixLauncher/1.0").GET().build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 400) throw new IllegalStateException("HTTP " + res.statusCode() + ": " + res.body());
        return Json.parseObject(res.body());
    }

    private static String url(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
}
