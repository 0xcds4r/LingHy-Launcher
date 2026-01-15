package com.hylauncher.model;

import java.util.Map;

public class JREManifest {
    private String version;
    private Map<String, Map<String, JREPlatform>> download_url;

    public static class JREPlatform {
        private String url;
        private String sha256;

        public String getUrl() { return url; }
        public String getSha256() { return sha256; }
    }

    public String getVersion() { return version; }
    public Map<String, Map<String, JREPlatform>> getDownloadUrl() { return download_url; }
}