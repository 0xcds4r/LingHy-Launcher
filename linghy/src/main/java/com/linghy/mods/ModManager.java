package com.linghy.mods;

import com.google.gson.Gson;
import com.linghy.download.DownloadManager;
import com.linghy.env.Environment;
import com.linghy.mods.curseforge.CurseForgeAPI;
import com.linghy.mods.manifest.ModManifest;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
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

        String downloadUrl = file.downloadUrl;
        if (downloadUrl.startsWith("http://")) {
            downloadUrl = downloadUrl.replace("http://", "https://");
            System.out.println("Forced HTTPS: " + downloadUrl);
        }

        if (!downloadUrl.startsWith("https://")) {
            throw new IOException("Insecure connection: HTTPS required for mod downloads");
        }

        System.out.println("=== Mod Download Start ===");
        System.out.println("File: " + fileName);
        System.out.println("URL: " + downloadUrl);
        System.out.println("Target: " + outPath);

        if (listener != null) {
            listener.onProgress(0, "Initializing secure download...");
        }

        try
        {
            String userAgent = String.format("Linghy-Launcher/%s (Mod Manager)",
                    Environment.getVersion());

            SSLContext sslContext;
            try {
                sslContext = SSLContext.getInstance("TLSv1.3");
                sslContext.init(null, null, null);
            } catch (NoSuchAlgorithmException e)
            {
                try {
                    sslContext = SSLContext.getInstance("TLSv1.2");
                    sslContext.init(null, null, null);
                    System.out.println("Using TLS 1.2 (TLS 1.3 not available)");
                } catch (Exception ex) {
                    throw new IOException("Failed to initialize secure SSL context", ex);
                }
            } catch (Exception e) {
                throw new IOException("Failed to initialize secure SSL context", e);
            }

            SSLParameters sslParameters = new SSLParameters();
            sslParameters.setProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
            sslParameters.setEndpointIdentificationAlgorithm("HTTPS");

            HttpClient client = HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .sslParameters(sslParameters)
                    .connectTimeout(Duration.ofSeconds(30))
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .build();

            System.out.println("Secure SSL/TLS connection configured");
            System.out.println("Certificate verification enabled");

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(downloadUrl))
                    .timeout(Duration.ofMinutes(5))
                    .header("User-Agent", userAgent)
                    .header("Accept", "*/*")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Accept-Encoding", "identity")
                    .GET();

            String url = downloadUrl.toLowerCase();
            boolean needsApiKey = url.contains("api.curseforge.com");

            if (needsApiKey)
            {
                try {
                    String apiKey = CurseForgeAPI.getApiKey();
                    requestBuilder.header("x-api-key", apiKey);
                    System.out.println("Using API key for CurseForge API request");
                } catch (Exception e) {
                    System.err.println("Failed to get API key: " + e.getMessage());
                }
            }
            else
            {
                System.out.println("CDN download via secure HTTPS - API key not required");
            }

            HttpRequest request = requestBuilder.build();

            System.out.println("Starting secure HTTPS download...");
            System.out.println("Connecting to server...");

            Path tempFile = outPath.resolveSibling(fileName + ".tmp");
            Files.deleteIfExists(tempFile);

            HttpResponse<InputStream> response;

            long connectStart = System.currentTimeMillis();
            try {
                response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                long connectTime = System.currentTimeMillis() - connectStart;
                System.out.println("Connected in " + connectTime + "ms");
            } catch (java.net.ConnectException e) {
                System.err.println("Connection failed: Cannot reach server");
                throw new IOException("Cannot connect to download server. Check your internet connection or try again later.", e);
            } catch (java.net.SocketTimeoutException e) {
                System.err.println("Connection timeout: Server not responding");
                throw new IOException("Connection timeout. The server is not responding. Check your internet connection.", e);
            } catch (javax.net.ssl.SSLHandshakeException e) {
                System.err.println("SSL Certificate verification failed!");
                System.err.println("Certificate error: " + e.getMessage());
                throw new IOException("SSL Certificate verification failed. The server certificate is invalid or untrusted.", e);
            } catch (javax.net.ssl.SSLException e) {
                System.err.println("SSL/TLS error occurred!");
                System.err.println("SSL error: " + e.getMessage());
                throw new IOException("Secure connection failed: " + e.getMessage(), e);
            } catch (java.io.IOException e)
            {
                System.err.println("Network error: " + e.getMessage());
                if (e.getMessage() != null)
                {
                    if (e.getMessage().contains("Connection reset")) {
                        throw new IOException("Connection was reset by the server. The CDN might be blocking your connection.", e);
                    } else if (e.getMessage().contains("Connection refused")) {
                        throw new IOException("Connection refused. The server is not accepting connections.", e);
                    }
                }
                throw new IOException("Network error during download: " + e.getMessage(), e);
            }

            System.out.println("Response status: " + response.statusCode());
            System.out.println("SSL Certificate verified successfully");

            System.out.println("Response headers:");
            response.headers().map().forEach((key, values) -> {
                if (key != null && !key.equalsIgnoreCase("set-cookie")) {
                    System.out.println("  " + key + ": " + String.join(", ", values));
                }
            });

            if (response.statusCode() == 403)
            {
                System.err.println("HTTP 403 Forbidden - Access denied");
                System.err.println("This CDN may be blocked in your region or requires additional authentication.");
                throw new IOException("Access denied (HTTP 403). The CDN may be blocked in your region. Try using a VPN.");
            }

            if (response.statusCode() != 200)
            {
                String errorMsg = "Server returned HTTP " + response.statusCode();

                try (InputStream errorStream = response.body())
                {
                    byte[] errorBytes = errorStream.readAllBytes();
                    if (errorBytes.length > 0 && errorBytes.length < 1024) {
                        String errorBody = new String(errorBytes, StandardCharsets.UTF_8);
                        System.err.println("Error body: " + errorBody);
                        errorMsg += " - " + errorBody;
                    }
                } catch (Exception ignored) {}

                throw new IOException(errorMsg);
            }

            long contentLength = response.headers()
                    .firstValueAsLong("Content-Length")
                    .orElse(-1);

            System.out.println("Content length: " + (contentLength > 0 ? contentLength + " bytes (" + (contentLength / 1024 / 1024) + " MB)" : "unknown"));

            if (contentLength <= 0) {
                System.out.println("Warning: Content-Length not provided by server");
            }

            long downloaded = 0;
            long startTime = System.currentTimeMillis();
            long lastUpdate = startTime;
            long lastLogTime = startTime;
            final long UPDATE_INTERVAL_MS = 200;
            final long LOG_INTERVAL_MS = 5000;

            System.out.println("Starting download stream...");

            try (InputStream in = response.body();
                 OutputStream out = Files.newOutputStream(tempFile,
                         StandardOpenOption.CREATE,
                         StandardOpenOption.TRUNCATE_EXISTING))
            {
                byte[] buffer = new byte[65536];
                int bytesRead;
                int readCount = 0;

                while ((bytesRead = in.read(buffer)) != -1)
                {
                    out.write(buffer, 0, bytesRead);
                    downloaded += bytesRead;
                    readCount++;

                    long now = System.currentTimeMillis();

                    if (now - lastLogTime > LOG_INTERVAL_MS)
                    {
                        double elapsed = (now - startTime) / 1000.0;
                        double mbDownloaded = downloaded / 1024.0 / 1024.0;
                        double speed = elapsed > 0 ? mbDownloaded / elapsed : 0;
                        System.out.println(String.format("Download in progress: %.2f MB downloaded, %.2f MB/s, %d reads",
                                mbDownloaded, speed, readCount));
                        lastLogTime = now;
                    }

                    if (listener != null && (now - lastUpdate > UPDATE_INTERVAL_MS))
                    {
                        double percent = contentLength > 0 ? (downloaded * 100.0 / contentLength) : 0;
                        double elapsed = (now - startTime) / 1000.0;
                        String speed = elapsed > 0
                                ? String.format("%.2f MB/s", downloaded / 1024.0 / 1024.0 / elapsed)
                                : "";

                        listener.onProgress(percent, speed);
                        lastUpdate = now;
                    }
                }

                System.out.println("Download stream completed");
            } catch (java.net.SocketTimeoutException e) {
                System.err.println("Read timeout during download");
                throw new IOException("Download interrupted: Read timeout. The connection was too slow or stalled.", e);
            } catch (java.io.IOException e) {
                System.err.println("IO error during download: " + e.getMessage());
                throw new IOException("Download interrupted: " + e.getMessage(), e);
            }

            long actualSize = Files.size(tempFile);
            System.out.println("Downloaded: " + actualSize + " bytes (" + (actualSize / 1024 / 1024) + " MB)");

            if (actualSize == 0) {
                Files.deleteIfExists(tempFile);
                throw new IOException("Downloaded file is empty (0 bytes)");
            }

            if (contentLength > 0 && actualSize != contentLength) {
                Files.deleteIfExists(tempFile);
                throw new IOException("Incomplete download: expected " + contentLength +
                        " bytes, got " + actualSize + " (missing " + (contentLength - actualSize) + " bytes)");
            }

            Files.move(tempFile, outPath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);

            double totalTime = (System.currentTimeMillis() - startTime) / 1000.0;
            double avgSpeed = (actualSize / 1024.0 / 1024.0) / totalTime;
            System.out.println(String.format("✓ Secure HTTPS download completed successfully in %.1fs (avg %.2f MB/s)",
                    totalTime, avgSpeed));

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
                Files.deleteIfExists(outPath.resolveSibling(fileName + ".tmp"));
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