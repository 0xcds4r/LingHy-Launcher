package com.linghy.version;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.linghy.env.Environment;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

public class VersionManager
{
    private static final String PATCHES_BASE_URL = "https://game-patches.hytale.com/patches";
    private static final int MAX_PATCH_SCAN = 100;
    private static final int SCAN_THREADS = 10;

    private final Path versionsFile;
    private final Path installedVersionsFile;
    private final HttpClient httpClient;
    private final Gson gson;

    public VersionManager()
    {
        Path appDir = Environment.getDefaultAppDir();
        this.versionsFile = appDir.resolve("available_versions.json");
        this.installedVersionsFile = appDir.resolve("installed_versions.json");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.gson = new Gson();
    }

    public List<GameVersion> scanAvailableVersions(String branch, ProgressListener listener) throws Exception
    {
        String os = Environment.getOS();
        String arch = Environment.getArch();

        List<GameVersion> versions = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(SCAN_THREADS);
        List<Future<GameVersion>> futures = new ArrayList<>();

        listener.onProgress(0, "Scanning " + branch + " versions...");

        for (int i = 0; i <= MAX_PATCH_SCAN; i++)
        {
            final int patchNumber = i;
            futures.add(executor.submit(() -> checkPatchExists(os, arch, patchNumber, branch)));
        }

        int completed = 0;
        for (Future<GameVersion> future : futures)
        {
            try {
                GameVersion version = future.get();
                if (version != null) {
                    versions.add(version);
                }
            } catch (Exception e) {
                // ...
            }

            completed++;
            int progress = (completed * 100) / futures.size();
            listener.onProgress(progress, "count: " + versions.size());
        }

        executor.shutdown();

        versions.sort(Comparator.comparingInt(GameVersion::getPatchNumber).reversed());

        saveAvailableVersions(versions, branch);

        listener.onProgress(100, "count: " + versions.size());
        return versions;
    }

    private GameVersion checkPatchExists(String os, String arch, int patchNumber, String branch)
    {
        String fileName = patchNumber + ".pwr";
        String url = String.format("%s/%s/%s/%s/0/%s",
                PATCHES_BASE_URL, os, arch, branch, fileName);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<Void> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.discarding());

            if (response.statusCode() == 200)
            {
                long size = response.headers()
                        .firstValueAsLong("Content-Length")
                        .orElse(-1);

                String versionName = branch.equals("pre-release")
                        ? "Pre-Release " + patchNumber
                        : "Release " + patchNumber;

                return new GameVersion(
                        versionName,
                        fileName,
                        url,
                        patchNumber,
                        size,
                        false,
                        branch
                );
            }
        } catch (Exception e) {
            // ...
        }

        return null;
    }

    public List<GameVersion> loadCachedVersions(String branch)
    {
        try {
            Path cacheFile = getCacheFile(branch);
            if (Files.exists(cacheFile))
            {
                String json = Files.readString(cacheFile);
                return gson.fromJson(json, new TypeToken<List<GameVersion>>(){}.getType());
            }
        } catch (IOException e) {
            System.err.println("Failed to load cached versions for " + branch + ": " + e.getMessage());
        }
        return new ArrayList<>();
    }

    public void saveSelectedVersion(GameVersion version)
    {
        try {
            Path selectedFile = Environment.getDefaultAppDir().resolve("selected_version.json");
            String json = gson.toJson(version);
            Files.writeString(selectedFile, json);
        } catch (IOException e) {
            System.err.println("Failed to save selected version: " + e.getMessage());
        }
    }

    public GameVersion loadSelectedVersion()
    {
        try {
            Path selectedFile = Environment.getDefaultAppDir().resolve("selected_version.json");
            if (Files.exists(selectedFile))
            {
                String json = Files.readString(selectedFile);
                return gson.fromJson(json, GameVersion.class);
            }
        } catch (IOException e) {
            System.err.println("Failed to load selected version: " + e.getMessage());
        }
        return null;
    }

    private void saveAvailableVersions(List<GameVersion> versions, String branch)
    {
        try {
            Path cacheFile = getCacheFile(branch);
            String json = gson.toJson(versions);
            Files.writeString(cacheFile, json);
        } catch (IOException e) {
            System.err.println("Failed to save cached versions for " + branch + ": " + e.getMessage());
        }
    }

    private Path getCacheFile(String branch)
    {
        Path appDir = Environment.getDefaultAppDir();
        if (branch.equals("pre-release")) {
            return appDir.resolve("available_versions_prerelease.json");
        } else {
            return appDir.resolve("available_versions_release.json");
        }
    }

    public List<GameVersion> getInstalledVersions()
    {
        try {
            if (Files.exists(installedVersionsFile))
            {
                String json = Files.readString(installedVersionsFile);
                return gson.fromJson(json, new TypeToken<List<GameVersion>>(){}.getType());
            }
        } catch (IOException e) {
            System.err.println("Failed to load installed versions: " + e.getMessage());
        }
        return new ArrayList<>();
    }

    public void markVersionInstalled(GameVersion version)
    {
        List<GameVersion> installed = getInstalledVersions();

        installed.removeIf(v -> v.getPatchNumber() == version.getPatchNumber()
                && v.getBranch().equals(version.getBranch()));

        GameVersion installedVersion = new GameVersion(
                version.getName(),
                version.getFileName(),
                version.getDownloadUrl(),
                version.getPatchNumber(),
                version.getSize(),
                true,
                version.getBranch()
        );

        installed.add(installedVersion);

        try {
            String json = gson.toJson(installed);
            Files.writeString(installedVersionsFile, json);
        } catch (IOException e) {
            System.err.println("Failed to save installed versions: " + e.getMessage());
        }
    }

    public boolean isVersionInstalled(int patchNumber, String branch)
    {
        Path gameDir = getVersionDirectory(patchNumber, branch);

        String clientName = Environment.getOS().equals("windows")
                ? "HytaleClient.exe" : "HytaleClient";
        Path clientPath = gameDir.resolve("Client").resolve(clientName);

        return Files.exists(clientPath);
    }

    public Path getVersionDirectory(int patchNumber, String branch)
    {
        String dirName = branch.equals("pre-release")
                ? "patch-pre-" + patchNumber
                : "patch-" + patchNumber;

        return Environment.getDefaultAppDir()
                .resolve("release").resolve("package")
                .resolve("game").resolve(dirName);
    }

    public void deleteVersion(int patchNumber, String branch) throws IOException
    {
        Path versionDir = getVersionDirectory(patchNumber, branch);

        if (Files.exists(versionDir)) {
            deleteRecursively(versionDir);
        }

        List<GameVersion> installed = getInstalledVersions();
        installed.removeIf(v -> v.getPatchNumber() == patchNumber
                && v.getBranch().equals(branch));

        String json = gson.toJson(installed);
        Files.writeString(installedVersionsFile, json);
    }

    private void deleteRecursively(Path path) throws IOException
    {
        if (!Files.exists(path)) return;

        Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        System.err.println("Failed to delete: " + p);
                    }
                });
    }

    public interface ProgressListener {
        void onProgress(int percent, String message);
    }
}