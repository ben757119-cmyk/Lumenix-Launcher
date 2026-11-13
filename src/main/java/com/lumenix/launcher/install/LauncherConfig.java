package com.lumenix.launcher.install;

/** Persisted under %APPDATA%/Lumenix/Config/launcher.json */
public class LauncherConfig {
    public String edition = "FREE"; // FREE | PAID
    public String minecraftVersion = "26.2";
    public int ramMb = 4096;
    public String javaPath = ""; // empty = auto
    public String clientRepo = "ben757119-cmyk/Lumenix";
    public boolean offlineDemo = false;

    // Discord webhooks (paste full URL; leave blank to disable that channel)
    public String webhookLaunches = "";
    public String webhookLogins = "";
    public String webhookInstalls = "";
    public String webhookErrors = "";
    public String webhookGeneral = "";
}
