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

/**
 * Microsoft device-code login for Minecraft.
 * Uses a public device-code-capable client id (same pattern as Prism Launcher).
 */
public final class MicrosoftAuth {
    // Public client id used by Prism Launcher / many FOSS launchers (device code flow)
    private static final String CLIENT_ID = "c36a9fb6-4f2a-41ff-90bd-ae7cc92031eb";
    private static final String SCOPE = "XboxLive.signin offline_access";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

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
        try {
            Files.deleteIfExists(Paths.authStore());
        } catch (Throwable ignored) {}
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

        String userCode = require(dc, "user_code", "device-code response");
        String deviceCode = require(dc, "device_code", "device-code response");
        String verifyUrl = dc.has("verification_uri")
                ? require(dc, "verification_uri", "device-code response")
                : "https://www.microsoft.com/link";
        int interval = dc.has("interval") && !dc.get("interval").isJsonNull()
                ? dc.get("interval").getAsInt() : 5;
        int expires = dc.has("expires_in") && !dc.get("expires_in").isJsonNull()
                ? dc.get("expires_in").getAsInt() : 900;

        status.accept("Open " + verifyUrl + " and enter code: " + userCode);
        Log.info("Microsoft login: open " + verifyUrl + " code=" + userCode);
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(verifyUrl));
            }
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

            if (token.has("access_token") && token.get("access_token") != null
                    && !token.get("access_token").isJsonNull()) {
                accessToken = token.get("access_token").getAsString();
                if (token.has("refresh_token") && token.get("refresh_token") != null
                        && !token.get("refresh_token").isJsonNull()) {
                    refresh = token.get("refresh_token").getAsString();
                }
                break;
            }

            if (token.has("error") && token.get("error") != null && !token.get("error").isJsonNull()) {
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
            throw new IllegalStateException("Microsoft login timed out. Try again and approve the code quickly.");
        }

        status.accept("Xbox Live…");
        JsonObject xbl = postJson(
                "https://user.auth.xboxlive.com/user/authenticate",
                "{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\",