package com.linghy.butler;

import com.linghy.env.Environment;
import com.linghy.model.ProgressCallback;
import com.linghy.model.ProgressUpdate;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;

import org.apache.commons.compress.archivers.zip.ZipFile;

public class ButlerInstaller
{
    public static Path installButler(ProgressCallback callback) throws Exception
    {
        Path toolsDir = Environment.getDefaultAppDir().resolve("tools").resolve("butler");
        Files.createDirectories(toolsDir);

        String butlerName = Environment.getOS().equals("windows") ? "butler.exe" : "butler";
        Path finalButlerPath = toolsDir.resolve(butlerName);

        if (Files.exists(finalButlerPath) && Files.isExecutable(finalButlerPath))
        {
            try {
                Process p = new ProcessBuilder(finalButlerPath.toString(), "--version").start();
                int exit = p.waitFor();
                if (exit == 0)
                {
                    callback.onProgress(new ProgressUpdate("butler", 100, "Butler already installed", "", "", 0, 0));
                    return finalButlerPath;
                }
            } catch (Exception ignored) {}
        }

        String url = getButlerURL();
        if (url == null) {
            throw new Exception("Unsupported OS/architecture");
        }

        System.out.println("Downloading Butler zip from: " + url);
        callback.onProgress(new ProgressUpdate("butler", 0, "Downloading Butler...", "butler.zip", "", 0, 0));

        Path tempZip = toolsDir.resolve("butler.zip.tmp");
        Files.deleteIfExists(tempZip);

        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++)
        {
            try {
                downloadFile(url, tempZip, callback);

                try (var zip = new ZipFile(tempZip.toFile()))
                {
                    System.out.println("ZIP validated OK on attempt " + attempt);
                    break;
                } catch (Exception e)
                {
                    System.err.println("Invalid ZIP on attempt " + attempt + ": " + e);
                    Files.deleteIfExists(tempZip);

                    if (attempt == maxRetries) {
                        throw new IOException("Failed to download valid butler ZIP after " + maxRetries + " attempts");
                    }

                    Thread.sleep(2000 * attempt);
                    continue;
                }
            } catch (Exception e)
            {
                Files.deleteIfExists(tempZip);
                if (attempt == maxRetries) throw e;
                Thread.sleep(2000 * attempt);
            }
        }

        System.out.println("Extracting butler...");
        callback.onProgress(new ProgressUpdate("butler", 50, "Extracting Butler...", "", "", 0, 0));

        Files.deleteIfExists(finalButlerPath);
        ButlerExtractor.extractButler(tempZip, toolsDir);

        Path extractedButler = toolsDir.resolve(butlerName);
        if (!Files.exists(extractedButler)) {
            throw new IOException("Butler binary not found after extraction");
        }

        if (!Environment.getOS().equals("windows")) {
            extractedButler.toFile().setExecutable(true, false);
        }

        Files.deleteIfExists(tempZip);

        try {
            Process p = new ProcessBuilder(finalButlerPath.toString(), "--version").start();
            int exit = p.waitFor();
            if (exit != 0) {
                throw new IOException("Extracted butler fails to run (exit " + exit + ")");
            }
        } catch (Exception e) {
            throw new IOException("Butler installation failed validation: " + e.getMessage());
        }

        System.out.println("Butler installed successfully at: " + finalButlerPath);
        callback.onProgress(new ProgressUpdate("butler", 100, "Butler installed", "", "", 0, 0));

        return finalButlerPath;
    }

    private static String getButlerURL()
    {
        String os = Environment.getOS();
        String arch = Environment.getArch();

        String baseUrl = "https://broth.itch.zone/butler/";

        return switch (os)
        {
            case "windows" -> baseUrl + "windows-" + arch + "/LATEST/archive/default";
            case "darwin" -> baseUrl + "darwin-" + arch + "/LATEST/archive/default";
            case "linux" -> baseUrl + "linux-" + arch + "/LATEST/archive/default";
            default -> null;
        };
    }

    private static void downloadFile(String urlStr, Path dest, ProgressCallback callback) throws Exception
    {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "Linghy/1.0");
        conn.setInstanceFollowRedirects(true);

        int responseCode = conn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("Server returned HTTP " + responseCode);
        }

        long total = conn.getContentLengthLong();
        long downloaded = 0;
        long startTime = System.currentTimeMillis();
        long lastUpdate = startTime;

        try (InputStream in = conn.getInputStream();
             OutputStream out = Files.newOutputStream(dest, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))
        {
            byte[] buffer = new byte[32768];
            int bytesRead;

            while ((bytesRead = in.read(buffer)) != -1)
            {
                out.write(buffer, 0, bytesRead);
                downloaded += bytesRead;

                long now = System.currentTimeMillis();
                if (now - lastUpdate > 200)
                {
                    double percent = total > 0 ? (downloaded * 100.0 / total) : 0;
                    double elapsed = (now - startTime) / 1000.0;
                    String speed = elapsed > 0 ? String.format("%.2f MB/s", downloaded / 1024.0 / 1024.0 / elapsed) : "";

                    callback.onProgress(new ProgressUpdate("butler", percent,
                            "Downloading Butler...", dest.getFileName().toString(),
                            speed, downloaded, total));
                    lastUpdate = now;
                }
            }
        } finally {
            conn.disconnect();
        }

        if (total > 0 && downloaded != total) {
            throw new IOException("Incomplete download: got " + downloaded + " of " + total + " bytes");
        }
    }
}