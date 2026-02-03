package com.linghy.mods;

import com.google.gson.Gson;
import com.linghy.download.DownloadManager;
import com.linghy.env.Environment;
import com.linghy.mods.curseforge.CurseForgeAPI;
import com.linghy.mods.manifest.ModManifest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ModManager
{
    private final Gson gson;
    private final Path modsDir;
    private final Map<String, ModManifest> manifestCache;

    public ModManager()
    {
        this.gson = new Gson();
        this.manifestCache = new ConcurrentHashMap<>();

        this.modsDir = Environment.getDefaultAppDir()
                .resolve("UserData")
                .resolve("Mods");

        try {
            Files.createDirectories(modsDir);
        } catch (IOException e) {
            System.err.println("Failed to create mod directories: " + e.getMessage());
        }
    }

    public Path getModsDir() {
        return this.modsDir;
    }

    public CompletableFuture<List<InstalledMod>> getInstalledModsAsync()
    {
        return CompletableFuture.supplyAsync(() -> getInstalledMods());
    }

    public List<InstalledMod> getInstalledMods()
    {
        List<InstalledMod> mods = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir, "*.jar"))
        {
            for (Path modFile : stream)
            {
                InstalledMod mod = loadModInfo(modFile);

                if (mod != null) {
                    mods.add(mod);
                }
            }
        } catch (IOException e)
        {
            System.err.println("Failed to list installed mods: " + e.getMessage());
        }

        return mods;
    }

    private ModManifest readManifest(Path jar)
    {
        String jarPath = jar.toString();

        if (manifestCache.containsKey(jarPath)) {
            return manifestCache.get(jarPath);
        }

        try (ZipFile zip = new ZipFile(jar.toFile()))
        {
            ZipEntry entry = zip.getEntry("manifest.json");
            if (entry == null) return null;

            try (InputStream is = zip.getInputStream(entry))
            {
                String json = new String(is.readAllBytes());
                ModManifest manifest = gson.fromJson(json, ModManifest.class);
                manifestCache.put(jarPath, manifest);
                return manifest;
            }
        } catch (Exception e)
        {
            return null;
        }
    }

    private InstalledMod loadModInfo(Path modFile)
    {
        try
        {
            Path metaFile = modFile.resolveSibling(modFile.getFileName() + ".cfmeta");
            int curseForgeId = 0;
            int fileId = 0;
            String iconUrl = null;

            if (Files.exists(metaFile))
            {
                try {
                    String metaJson = Files.readString(metaFile);
                    ModMetadata meta = gson.fromJson(metaJson, ModMetadata.class);
                    if (meta != null)
                    {
                        curseForgeId = meta.curseForgeId;
                        fileId = meta.fileId;
                        iconUrl = meta.iconUrl;
                    }
                } catch (Exception e) {
                    // ...
                }
            }

            ModManifest manifest = readManifest(modFile);

            if (manifest != null)
            {
                return new InstalledMod(
                        modFile.getFileName().toString(),
                        manifest.Name,
                        manifest.Version != null ? manifest.Version : "Unknown",
                        manifest.Authors != null && !manifest.Authors.isEmpty()
                                ? manifest.Authors.get(0).Name
                                : "Unknown",
                        !manifest.DisabledByDefault,
                        iconUrl,
                        curseForgeId,
                        fileId
                );
            }

            String fileName = modFile.getFileName().toString();

            return new InstalledMod(
                    fileName,
                    fileName.replace(".jar", ""),
                    "Unknown",
                    "Unknown",
                    true,
                    iconUrl,
                    curseForgeId,
                    fileId
            );

        } catch (Exception e) {
            return null;
        }
    }

    public CompletableFuture<Void> downloadAndInstallAsync(CurseForgeAPI.ModFile file, ModProgressListener listener)
    {
        return CompletableFuture.runAsync(() ->
        {
            try {
                downloadAndInstall(file, listener);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void downloadAndInstall(CurseForgeAPI.ModFile file, ModProgressListener listener) throws IOException
    {
        downloadAndInstall(file, 0, null, listener);
    }

    public void downloadAndInstall(CurseForgeAPI.ModFile file, int curseForgeId,
                                   String iconUrl, ModProgressListener listener) throws IOException
    {
        String fileName = file.fileName;
        Path outPath = modsDir.resolve(fileName);

        System.out.println("=== Mod Download Start ===");
        System.out.println("File: " + fileName);
        System.out.println("URL: " + file.downloadUrl);
        System.out.println("Target: " + outPath);

        if (listener != null) {
            listener.onProgress(0, "Initializing download...");
        }

        try
        {
            DownloadManager downloader = new DownloadManager(
                    65536,
                    5,
                    3000
            );

            com.linghy.model.ProgressCallback callback = (update) ->
            {
                if (listener != null) {
                    double percent = update.getProgress();
                    String speed = update.getSpeed();
                    String message = speed.isEmpty() ? "Downloading..." : speed;

                    if (percent % 10 < 1) {
                        System.out.println(String.format("Progress: %.1f%% - %s", percent, message));
                    }

                    listener.onProgress(percent, message);
                }
            };

            System.out.println("Starting download with HttpClient...");
            String targetUrl = file.downloadUrl;
//                    .replace("edge.forgecdn.net", "mediafilez.forgecdn.net");

            downloader.downloadWithNIO(targetUrl, outPath, callback);

            System.out.println("Download completed successfully");

            if (listener != null) {
                listener.onProgress(100, "Installing...");
            }

            if (curseForgeId > 0)
            {
                Path metaFile = outPath.resolveSibling(fileName + ".cfmeta");
                String metaJson = gson.toJson(new ModMetadata(curseForgeId, file.id, iconUrl));
                Files.writeString(metaFile, metaJson);
                System.out.println("Metadata saved");
            }

            manifestCache.remove(outPath.toString());

            if (listener != null) {
                listener.onProgress(100, "Done");
            }

            System.out.println("=== Mod Download Complete ===");
        }
        catch (Exception e)
        {
            System.err.println("=== Mod Download Failed ===");
            System.err.println("Error: " + e.getClass().getSimpleName());
            System.err.println("Message: " + e.getMessage());
            e.printStackTrace();

            try {
                Files.deleteIfExists(outPath);
                System.out.println("Cleaned up incomplete file");
            } catch (IOException ex) {
                System.err.println("Failed to delete incomplete file: " + ex.getMessage());
            }

            throw new IOException("Failed to download mod '" + fileName + "': " + e.getMessage(), e);
        }
    }

    private static class ModMetadata
    {
        int curseForgeId;
        int fileId;
        String iconUrl;

        ModMetadata(int curseForgeId, int fileId, String iconUrl)
        {
            this.curseForgeId = curseForgeId;
            this.fileId = fileId;
            this.iconUrl = iconUrl;
        }
    }

    public void uninstallMod(String modFileName) throws IOException
    {
        Path modFile = modsDir.resolve(modFileName);
        Path metaFile = modFile.resolveSibling(modFileName + ".cfmeta");

        manifestCache.remove(modFile.toString());

        Files.deleteIfExists(modFile);
        Files.deleteIfExists(metaFile);
    }

    public void openModsFolder() throws IOException
    {
        if (java.awt.Desktop.isDesktopSupported()) {
            java.awt.Desktop.getDesktop().open(modsDir.toFile());
        }
    }

    public static class InstalledMod
    {
        public String id;
        public String name;
        public String version;
        public String author;
        public boolean enabled;
        public String iconUrl;
        public int curseForgeId;
        public int fileId;

        public InstalledMod(String id, String name, String version, String author, boolean enabled, String iconUrl, int curseForgeId, int fileId)
        {
            this.id = id;
            this.name = name;
            this.version = version;
            this.author = author;
            this.enabled = enabled;
            this.iconUrl = iconUrl;
            this.curseForgeId = curseForgeId;
            this.fileId = fileId;
        }
    }

    public interface ModProgressListener {
        void onProgress(double percent, String message);
    }
}