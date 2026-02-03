package com.linghy.java;

import com.google.gson.Gson;
import com.linghy.download.DownloadManager;
import com.linghy.env.Environment;
import com.linghy.model.JREManifest;
import com.linghy.model.ProgressCallback;
import com.linghy.model.ProgressUpdate;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;

public class JREDownloader
{
    private static final String JRE_MANIFEST_URL =
            "https://launcher.hytale.com/version/release/jre.json";

    public static void downloadJRE(ProgressCallback callback) throws Exception
    {
        String osName = Environment.getOS();
        String arch = Environment.getArch();

        Path basePath = Environment.getDefaultAppDir();
        Path cacheDir = basePath.resolve("cache");
        Path jreLatest = basePath.resolve("release").resolve("package")
                .resolve("jre").resolve("latest");

        if (isJREInstalled(jreLatest))
        {
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

        if (!Files.exists(cacheFile))
        {
            System.out.println("Downloading JRE...");
            callback.onProgress(new ProgressUpdate("jre", 0,
                    "Downloading JRE...", fileName, "", 0, 0));

            DownloadManager downloader = new DownloadManager();

            ProgressCallback wrappedCallback = (update) ->
            {
                callback.onProgress(new ProgressUpdate(
                        "jre",
                        update.getProgress(),
                        "Downloading JRE...",
                        fileName,
                        update.getSpeed(),
                        update.getDownloaded(),
                        update.getTotal()
                ));
            };

            downloader.downloadWithHttpClient(
                    platform.getUrl(),
                    cacheFile,
                    platform.getSha256(),
                    wrappedCallback
            );
        }
        else
        {
            System.out.println("JRE already cached, verifying...");
            callback.onProgress(new ProgressUpdate("jre", 90,
                    "Verifying JRE...", fileName, "", 0, 0));

            DownloadManager downloader = new DownloadManager();
            if (!downloader.verifySHA256(cacheFile, platform.getSha256()))
            {
                Files.deleteIfExists(cacheFile);
                throw new Exception("Cached JRE failed SHA256 verification, re-download required");
            }
        }

        System.out.println("Extracting JRE...");
        callback.onProgress(new ProgressUpdate("jre", 95,
                "Extracting JRE...", fileName, "", 0, 0));

        JREExtractor.extractJRE(cacheFile, jreLatest);

        if (!osName.equals("windows"))
        {
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

    private static boolean isJREInstalled(Path jreDir)
    {
        String javaBin = Environment.getOS().equals("windows")
                ? "java.exe" : "java";
        return Files.exists(jreDir.resolve("bin").resolve(javaBin));
    }

    public static String getJavaExec()
    {
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