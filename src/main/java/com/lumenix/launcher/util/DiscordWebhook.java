package com.lumenix.launcher.util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Fire-and-forget Discord webhook posts. Empty URL = no-op. */
public final class DiscordWebhook {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private DiscordWebhook() {}

    public static void send(String webhookUrl, String content) {
        if (webhookUrl == null || webhookUrl.isBlank() || content == null || content.isBlank()) return;
        if (!webhookUrl.startsWith("https://discord.com/api/webhooks/")
                && !webhookUrl.startsWith("https://discordapp.com/api/webhooks/")) {
            Log.warn("Ignoring non-Discord webhook URL");
            return;
        }
        new Thread(() -> {
            try {
                String body = "{\"content\":" + jsonString(content) + "}";
                HttpRequest req = HttpRequest.newBuilder(URI.create(webhookUrl.trim()))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/json")
                        .header("User-Agent", "LumenixLauncher/1.0")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() >= 300) {
                    Log.warn("Webhook HTTP " + res.statusCode());
                }
            } catch (Exception e) {
                Log.warn("Webhook failed: " + e.getMessage());
            }
        }, "lumenix-webhook").start();
    }

    private static String jsonString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }
}
