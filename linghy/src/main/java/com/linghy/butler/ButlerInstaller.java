package com.linghy.butler;

import com.linghy.download.DownloadManager;
import com.linghy.env.Environment;
import com.linghy.model.ProgressCallback;
import com.linghy.model.ProgressUpdate;

import java.io.*;
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
            try
            {
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
        Path cacheZip = toolsDir.resolve("butler.zip");

        DownloadManager downloader = new DownloadManager();

        ProgressCallback wrappedCallback = (update) -> {
            callback.onProgress(new ProgressUpdate(
                    "butler",
                    update.getProgress() * 0.5,
                    "Downloading Butler...",
                    "butler.zip",
                    update.getSpeed(),
                    update.getDownloaded(),
                    update.getTotal()
            ));
        };

        downloader.downloadWithNIO(url, cacheZip, wrappedCallback);

        boolean validZip = false;
        try (ZipFile zip = new ZipFile(cacheZip.toFile()))
        {
            System.out.println("ZIP validated OK");
            validZip = true;
        }
        catch (Exception e)
        {
            System.err.println("Invalid ZIP file: " + e.getMessage());
            Files.deleteIfExists(cacheZip);
            throw new IOException("Downloaded file is not a valid ZIP archive");
        }

        System.out.println("Extracting butler...");
        callback.onProgress(new ProgressUpdate("butler", 50, "Extracting Butler...", "", "", 0, 0));

        Files.deleteIfExists(finalButlerPath);
        ButlerExtractor.extractButler(cacheZip, toolsDir);

        Path extractedButler = toolsDir.resolve(butlerName);
        if (!Files.exists(extractedButler)) {
            throw new IOException("Butler binary not found after extraction");
        }

        if (!Environment.getOS().equals("windows")) {
            extractedButler.toFile().setExecutable(true, false);
        }

        Files.deleteIfExists(cacheZip);

        try
        {
            Process p = new ProcessBuilder(finalButlerPath.toString(), "--version").start();
            int exit = p.waitFor();

            if (exit != 0) {
                throw new IOException("Extracted butler fails to run (exit " + exit + ")");
            }

        } catch (Exception e)
        {
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
}