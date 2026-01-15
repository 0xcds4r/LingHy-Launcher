package com.hylauncher.java;

import com.google.gson.Gson;
import com.hylauncher.env.Environment;
import com.hylauncher.model.JREManifest;
import com.hylauncher.model.ProgressCallback;
import com.hylauncher.model.ProgressUpdate;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;

public class JREDownloader {
    private static final String JRE_MANIFEST_URL =
            "https://launcher.hytale.com/version/release/jre.json";

    public static void downloadJRE(ProgressCallback callback) throws Exception {
        String osName = Environment.getOS();
        String arch = Environment.getArch();

        Path basePath = Environment.getDefaultAppDir();
        Path cacheDir = basePath.resolve("cache");
        Path jreLatest = basePath.resolve("release").resolve("package")
                .resolve("jre").resolve("latest");

        if (isJREInstalled(jreLatest)) {
            System.out.println("JRE already installed, skipping");
            callback.onProgress(new ProgressUpdate("jre", 100,
                    "JRE already installed", "", "", 0, 0));
            return;
        }

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(JRE_MANIFEST_URL))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());

        Gson gson = new Gson();
        JREManifest manifest = gson.fromJson(response.body(), JREManifest.class);

        JREManifest.JREPlatform platform = manifest.getDownloadUrl()
                .get(osName).get(arch);

        if (platform == null) {
            throw new Exception("No JRE available for " + osName + "/" + arch);
        }

        String fileName = Paths.get(URI.create(platform.getUrl()).getPath())
                .getFileName().toString();
        Path cacheFile = cacheDir.resolve(fileName);
        Path tempFile = Paths.get(cacheFile.toString() + ".tmp");

        Files.deleteIfExists(tempFile);

        if (!Files.exists(cacheFile)) {
            System.out.println("Downloading JRE...");
            callback.onProgress(new ProgressUpdate("jre", 0,
                    "Downloading JRE...", fileName, "", 0, 0));

            downloadFile(platform.getUrl(), tempFile, callback, fileName);
            Files.move(tempFile, cacheFile, StandardCopyOption.ATOMIC_MOVE);
        }

        System.out.println("Verifying JRE...");
        callback.onProgress(new ProgressUpdate("jre", 90,
                "Verifying JRE...", fileName, "", 0, 0));

        if (!verifySHA256(cacheFile, platform.getSha256())) {
            Files.deleteIfExists(cacheFile);
            throw new Exception("SHA256 verification failed");
        }

        System.out.println("Extracting JRE...");
        callback.onProgress(new ProgressUpdate("jre", 95,
                "Extracting JRE...", fileName, "", 0, 0));

        JREExtractor.extractJRE(cacheFile, jreLatest);

        if (!osName.equals("windows")) {
            Path javaBin = jreLatest.resolve("bin").resolve("java");
            if (Files.exists(javaBin)) {
                javaBin.toFile().setExecutable(true);
            }
        }

        Files.deleteIfExists(cacheFile);

        System.out.println("JRE installed successfully");
        callback.onProgress(new ProgressUpdate("jre", 100,
                "JRE installed", "", "", 0, 0));
    }

    private static void downloadFile(String url, Path dest,
                                     ProgressCallback callback, String fileName) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<InputStream> response = client.send(request,
                HttpResponse.BodyHandlers.ofInputStream());

        long total = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        long downloaded = 0;
        long startTime = System.currentTimeMillis();
        long lastUpdate = startTime;

        try (InputStream in = response.body();
             OutputStream out = Files.newOutputStream(dest)) {

            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                downloaded += bytesRead;

                long now = System.currentTimeMillis();
                if (now - lastUpdate > 200) {
                    double percent = total > 0 ? (downloaded * 100.0 / total) : 0;
                    double elapsed = (now - startTime) / 1000.0;
                    String speed = elapsed > 0
                            ? String.format("%.2f MB/s", downloaded / 1024.0 / 1024.0 / elapsed)
                            : "";

                    callback.onProgress(new ProgressUpdate("jre", percent,
                            "Downloading JRE...", fileName, speed, downloaded, total));

                    lastUpdate = now;
                }
            }
        }
    }

    private static boolean verifySHA256(Path file, String expected) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }

        String calculated = HexFormat.of().formatHex(digest.digest());
        return calculated.equalsIgnoreCase(expected);
    }

    private static boolean isJREInstalled(Path jreDir) {
        String javaBin = Environment.getOS().equals("windows")
                ? "java.exe" : "java";
        return Files.exists(jreDir.resolve("bin").resolve(javaBin));
    }

    public static String getJavaExec() {
        Path jreDir = Environment.getDefaultAppDir()
                .resolve("release").resolve("package").resolve("jre").resolve("latest");

        String javaBin = Environment.getOS().equals("windows")
                ? "java.exe" : "java";
        Path javaPath = jreDir.resolve("bin").resolve(javaBin);

        if (Files.exists(javaPath)) {
            return javaPath.toString();
        }

        System.err.println("Warning: JRE not found, falling back to system java");
        return "java";
    }
}