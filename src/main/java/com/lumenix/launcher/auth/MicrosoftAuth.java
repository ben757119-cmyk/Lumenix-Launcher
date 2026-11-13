package com.lumenix.launcher.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
    private static final String CLIENT_ID = "c36a9fb6-4f2a-41ff-90bd-ae7cc92031eb";
    private static final String SCOPE = "XboxLive.signin offline_access";
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

    public static void save(AuthSession s) throws Exception {
        Json.write(Paths.authStore(), s);
    }

    public static void clear() {
        try { Files.deleteIfExists(Paths.authStore()); } catch (Throwable ignored) {}
    }

    public static AuthSession loginDeviceCode(Consumer<String> status) throws Exception {
        status.accept("Requesting Microsoft device code…");
        JsonObject dc = postForm(
                "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode",
                "client_id=" + url(CLIENT_ID) + "&scope=" + url(SCOPE));
        if (dc.has("error")) {
            throw new IllegalStateException("Device code failed: "
                    + str(dc, "error") + " — " + str(dc, "error_description"));
        }

        String userCode = require(dc, "user_code", "device-code");
        String deviceCode = require(dc, "device_code", "device-code");
        String verifyUrl = hasStr(dc, "verification_uri")
                ? require(dc, "verification_uri", "device-code")
                : "https://www.microsoft.com/link";
        int interval = intOr(dc, "interval", 5);
        int expires = intOr(dc, "expires_in", 900);

        status.accept("Open " + verifyUrl + " and enter code: " + userCode);
        Log.info("Microsoft login code=" + userCode + " url=" + verifyUrl);
        try {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create(verifyUrl));
        } catch (Throwable ignored) {}

        long deadline = System.currentTimeMillis() + expires * 1000L;
        String accessToken = null;
        String refresh = null;

        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(Math.max(3, interval) * 1000L);
            JsonObject token = postForm(
                    "https://login.microsoftonline.com/consumers/oauth2/v2.0/token",
                    "grant_type=" + url("urn:ietf:params:oauth:grant-type:device_code")
                            + "&client_id=" + url(CLIENT_ID)
                            + "&device_code=" + url(deviceCode));

            if (hasStr(token, "access_token")) {
                accessToken = token.get("access_token").getAsString();
                if (hasStr(token, "refresh_token")) refresh = token.get("refresh_token").getAsString();
                break;
            }
            if (hasStr(token, "error")) {
                String err = token.get("error").getAsString();
                if ("authorization_pending".equals(err) || "slow_down".equals(err)) {
                    status.accept("Waiting for Microsoft approval… (code " + userCode + ")");
                    if ("slow_down".equals(err)) interval = Math.min(interval + 2, 15);
                    continue;
                }
                throw new IllegalStateException("Microsoft auth error: " + err
                        + " — " + str(token, "error_description"));
            }
        }
        if (accessToken == null) {
            throw new IllegalStateException("Microsoft login timed out. Approve the code and try again.");
        }

        status.accept("Xbox Live…");
        String xblBody = "{"
                + "\"Properties\":{"
                + "\"AuthMethod\":\"RPS\","
                + "\"SiteName\":\"user.auth.xboxlive.com\","
                + "\"RpsTicket\":\"d=" + accessToken + "\""
                + "},"
                + "\"RelyingParty\":\"http://auth.xboxlive.com\","
                + "\"TokenType\":\"JWT\""
                + "}";
        JsonObject xbl = postJson("https://user.auth.xboxlive.com/user/authenticate", xblBody);
        String xblToken = require(xbl, "Token", "Xbox Live");
        String uhs = extractUhs(xbl);

        status.accept("XSTS…");
        String xstsBody = "{"
                + "\"Properties\":{"
                + "\"SandboxId\":\"RETAIL\","
                + "\"UserTokens\":[\"" + xblToken + "\"]"
                + "},"
                + "\"RelyingParty\":\"rp://api.minecraftservices.com/\","
                + "\"TokenType\":\"JWT\""
                + "}";
        JsonObject xsts = postJson("https://xsts.auth.xboxlive.com/xsts/authorize", xstsBody);
        if (xsts.has("XErr")) throw new IllegalStateException(xstsError(xsts));
        String xstsToken = require(xsts, "Token", "XSTS");
        try {
            String alt = extractUhs(xsts);
            if (alt != null && !alt.isBlank()) uhs = alt;
        } catch (Exception ignored) {}

        status.accept("Minecraft services…");
        String mcBody = "{\"identityToken\":\"XBL3.0 x=" + uhs + ";" + xstsToken + "\"}";
        JsonObject mcLogin = postJson(
                "https://api.minecraftservices.com/authentication/login_with_xbox", mcBody);
        String mcToken = require(mcLogin, "access_token", "Minecraft login");

        status.accept("Loading Minecraft profile…");
        JsonObject profile = getJson("https://api.minecraftservices.com/minecraft/profile", mcToken);
        if (!hasStr(profile, "name") || !hasStr(profile, "id")) {
            throw new IllegalStateException(
                    "No Minecraft Java profile on this Microsoft account. "
                            + "Claim/buy Java Edition, then retry. Response: " + profile);
        }

        AuthSession session = new AuthSession();
        session.minecraftToken = mcToken;
        session.username = profile.get("name").getAsString();
        session.uuid = profile.get("id").getAsString();
        session.expiresAt = System.currentTimeMillis() + 12L * 60 * 60 * 1000;
        session.refreshToken = refresh != null ? refresh : "";
        session.accountType = "msa";
        save(session);
        status.accept("Signed in as " + session.username);
        Log.info("Authenticated as " + session.username);
        return session;
    }

    private static String extractUhs(JsonObject auth) {
        JsonObject claims = auth.getAsJsonObject("DisplayClaims");
        if (claims == null) throw new IllegalStateException("Xbox response missing DisplayClaims");
        JsonArray xui = claims.getAsJsonArray("xui");
        if (xui == null || xui.isEmpty()) throw new IllegalStateException("Xbox response missing xui");
        JsonObject first = xui.get(0).getAsJsonObject();
        if (!hasStr(first, "uhs")) throw new IllegalStateException("Xbox response missing uhs");
        return first.get("uhs").getAsString();
    }

    private static String xstsError(JsonObject xsts) {
        String code = str(xsts, "XErr");
        return switch (code) {
            case "2148916233" -> "No Xbox profile. Open xbox.com and create one.";
            case "2148916235" -> "Xbox Live unavailable in this region.";
            case "2148916236", "2148916237" -> "Adult verification required on this Microsoft account.";
            case "2148916238" -> "Child account needs adult approval.";
            default -> "XSTS failed (" + code + "): " + str(xsts, "Message");
        };
    }

    private static boolean hasStr(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) return false;
        JsonElement el = obj.get(key);
        return el != null && !el.isJsonNull();
    }

    private static String require(JsonObject obj, String key, String stage) {
        if (!hasStr(obj, key)) {
            throw new IllegalStateException(stage + " missing '" + key + "'. Keys: "
                    + (obj == null ? "null" : obj.keySet()));
        }
        return obj.get(key).getAsString();
    }

    private static String str(JsonObject obj, String key) {
        if (!hasStr(obj, key)) return "";
        try { return obj.get(key).getAsString(); }
        catch (Exception e) { return String.valueOf(obj.get(key)); }
    }

    private static int intOr(JsonObject obj, String key, int def) {
        try {
            if (obj != null && obj.has(key) && obj.get(key) != null && !obj.get(key).isJsonNull()) {
                return obj.get(key).getAsInt();
            }
        } catch (Exception ignored) {}
        return def;
    }

    private static JsonObject postForm(String url, String form) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", "LumenixLauncher/1.0.2")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        JsonObject body = Json.parseObject(res.body());
        if (res.statusCode() >= 400 && body.has("error")) return body;
        if (res.statusCode() >= 400) {
            throw new IllegalStateException("HTTP " + res.statusCode() + ": " + trim(res.body()));
        }
        return body;
    }

    private static JsonObject postJson(String url, String json) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "LumenixLauncher/1.0.2")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        JsonObject body = Json.parseObject(res.body());
        if (res.statusCode() >= 400 && !body.has("XErr")) {
            throw new IllegalStateException("HTTP " + res.statusCode() + ": " + trim(res.body()));
        }
        return body;
    }

    private static JsonObject getJson(String url, String bearer) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + bearer)
                .header("User-Agent", "LumenixLauncher/1.0.2")
                .GET().build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 400) {
            throw new IllegalStateException("HTTP " + res.statusCode() + " profile: " + trim(res.body()));
        }
        return Json.parseObject(res.body());
    }

    private static String url(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String trim(String s) {
        if (s == null) return "";
        return s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }
}
