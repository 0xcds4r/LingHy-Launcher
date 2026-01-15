package com.hylauncher.pwr;

import com.hylauncher.env.Environment;
import com.hylauncher.model.ProgressCallback;
import com.hylauncher.model.ProgressUpdate;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;

public class PWRDownloader {
    public static Path downloadPWR(String version, String fileName,
                                   ProgressCallback callback) throws Exception {
        Path cacheDir = Environment.getDefaultAppDir().resolve("cache");
        Files.createDirectories(cacheDir);

        String os = Environment.getOS();
        String arch = Environment.getArch();

        String url = String.format(
                "https://game-patches.hytale.com/patches/%s/%s/%s/0/%s",
                os, arch, version, fileName);

        Path dest = cacheDir.resolve(fileName);
        Path tempDest = Paths.get(dest.toString() + ".tmp");

        Files.deleteIfExists(tempDest);

        if (Files.exists(dest)) {
            System.out.println("PWR file already exists: " + dest);
            callback.onProgress(new ProgressUpdate("game", 40,
                    "PWR file cached", fileName, "", 0, 0));
            return dest;
        }

        System.out.println("Downloading PWR file: " + url);
        downloadFile(url, tempDest, callback);

        Files.move(tempDest, dest, StandardCopyOption.ATOMIC_MOVE);

        System.out.println("PWR downloaded to: " + dest);
        return dest;
    }

    private static void downloadFile(String url, Path dest,
                                     ProgressCallback callback) throws Exception {
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

            byte[] buffer = new byte[32768];
            int bytesRead;

            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                downloaded += bytesRead;

                long now = System.currentTimeMillis();
                if (now - lastUpdate > 200) {
                    if (total > 0) {
                        double percent = (downloaded * 100.0 / total);
                        double elapsed = (now - startTime) / 1000.0;
                        String speed = elapsed > 0
                                ? String.format("%.2f MB/s", downloaded / 1024.0 / 1024.0 / elapsed)
                                : "";

                        double overallProgress = percent * 0.4;

                        callback.onProgress(new ProgressUpdate("game", overallProgress,
                                "Downloading game files...", dest.getFileName().toString(),
                                speed, downloaded, total));

                        System.out.printf("\r%.1f%% downloaded (%.2f MB/s)",
                                percent, downloaded / 1024.0 / 1024.0 / elapsed);
                    }
                    lastUpdate = now;
                }
            }
        }
        System.out.println();
    }
}