package com.lumenix.launcher.auth;

public class AuthSession {
    public String minecraftToken = "";
    public String username = "";
    public String uuid = "";
    public long expiresAt;
    public String refreshToken = "";
    public String accountType = "msa";

    public boolean isValid() {
        return minecraftToken != null && !minecraftToken.isBlank()
                && username != null && !username.isBlank()
                && (expiresAt <= 0 || System.currentTimeMillis() < expiresAt);
    }
}
