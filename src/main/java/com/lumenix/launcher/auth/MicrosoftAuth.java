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
        String xblBody = "{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\",