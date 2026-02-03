package com.linghy.pwr;

import com.linghy.download.DownloadManager;
import com.linghy.env.Environment;
import com.linghy.model.ProgressCallback;
import com.linghy.model.ProgressUpdate;

import java.io.*;
import java.nio.file.*;

public class PWRDownloader
{
    public static Path downloadPWRFromUrl(String url, String fileName,
                                          ProgressCallback callback) throws Exception
    {
        Path cacheDir = Environment.getDefaultAppDir().resolve("cache");
        Files.createDirectories(cacheDir);

        Path dest = cacheDir.resolve(fileName);

        if (Files.exists(dest))
        {
            System.out.println("PWR file already cached: " + dest);
            callback.onProgress(new ProgressUpdate("game", 40,
                    "PWR file cached", fileName, "", 0, 0));
            return dest;
        }

        System.out.println("Downloading PWR file: " + url);

        DownloadManager downloader = new DownloadManager();

        ProgressCallback wrappedCallback = (update) ->
        {
            double scaledProgress = update.getProgress() * 0.4;

            callback.onProgress(new ProgressUpdate(
                    "game",
                    scaledProgress,
                    "Downloading game files...",
                    fileName,
                    update.getSpeed(),
                    update.getDownloaded(),
                    update.getTotal()
            ));
        };

        downloader.downloadWithNIO(url, dest, wrappedCallback);

        System.out.println("PWR downloaded to: " + dest);
        return dest;
    }

    public static Path downloadPWR(String version, String fileName,
                                   ProgressCallback callback) throws Exception
    {
        Path cacheDir = Environment.getDefaultAppDir().resolve("cache");
        Files.createDirectories(cacheDir);

        String os = Environment.getOS();
        String arch = Environment.getArch();

        String url = String.format(
                "https://game-patches.hytale.com/patches/%s/%s/%s/0/%s",
                os, arch, version, fileName);

        return downloadPWRFromUrl(url, fileName, callback);
    }
}