package com.lumenix.launcher.install;

import java.util.LinkedHashMap;
import java.util.Map;

/** Fingerprint of the persistent install — used to avoid full reinstalls. */
public class InstallMeta {
    public String minecraftVersion = "26.2";
    public String fabricLoaderVersion = "";
    public String fabricYarnOrIntermediary = "";
    public String fabricApiVersion = "";
    public String freeClientTag = "";
    public String paidClientTag = "";
    public String freeClientSha256 = "";
    public String paidClientSha256 = "";
    public long lastVerifiedAt;
    public Map<String, String> fileHashes = new LinkedHashMap<>();
}
