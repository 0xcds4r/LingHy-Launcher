package com.linghy.patches;

import com.linghy.env.Environment;
import com.linghy.model.ProgressCallback;
import com.linghy.model.ProgressUpdate;
import com.linghy.version.GameVersion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.stream.Stream;

public class PatchManager
{
    private static final String DEFAULT_TARGET_DOMAIN = "sanasol.ws";
    private String targetDomain;

    public PatchManager() {
        this(DEFAULT_TARGET_DOMAIN);
    }

    public PatchManager(String targetDomain)
    {
        this.targetDomain = (targetDomain != null && !targetDomain.trim().isEmpty())
                ? targetDomain
                : DEFAULT_TARGET_DOMAIN;
    }

    public void setTargetDomain(String domain)
    {
        this.targetDomain = (domain != null && !domain.trim().isEmpty())
                ? domain
                : DEFAULT_TARGET_DOMAIN;
    }

    public String getTargetDomain() {
        return targetDomain;
    }

    public void ensureGamePatched(GameVersion version, ProgressCallback callback) throws Exception
    {
        if (callback != null) {
            callback.onProgress(new ProgressUpdate("patch", 0,
                    "Checking patch status...", "", "", 0, 0));
        }

        Path clientPath = getClientPath(version);
        if (clientPath == null || !Files.exists(clientPath)) {
            if (callback != null) {
                callback.onProgress(new ProgressUpdate("patch", 100, "Client not found", "", "", 0, 0));
            }
            return;
        }

        Path originalBackup = clientPath.resolveSibling(clientPath.getFileName() + ".original");
        if (Files.exists(originalBackup)) {
            System.out.println("Client already patched (backup .original exists)");
            if (callback != null) {
                callback.onProgress(new ProgressUpdate("patch", 100,
                        "Game already patched (backup found)", "", "", 0, 0));
            }
            return;
        }

        Path gameDir    = getGameDirectory(version);
        Path serverPath = getServerPath(version);

        boolean clientExists = clientPath != null && Files.exists(clientPath);
        boolean serverExists = serverPath != null && Files.exists(serverPath);

        if (!clientExists && !serverExists)
        {
            System.out.println("No game files found for version: " + version.getName());

            if (callback != null) {
                callback.onProgress(new ProgressUpdate("patch", 100,
                        "No files to patch", "", "", 0, 0));
            }

            return;
        }

        DomainPatcher domainPatcher = new DomainPatcher(targetDomain);

        double progress = 0.0;
        final double stepClientDomain = 20.0;
        final double stepOnlineFix    = 25.0;
        final double stepServerDomain = 15.0;

        if (clientExists)
        {
            final double startProgress = progress;

            if (callback != null)
            {
                callback.onProgress(new ProgressUpdate("patch", startProgress,
                        "Patching domain in client...", "", "", 0, 0));
            }

            DomainPatcher.PatchProgressListener domainListener = (pct, msg) ->
            {
                if (callback != null)
                {
                    double sub = startProgress + (pct / 100.0) * stepClientDomain;
                    callback.onProgress(new ProgressUpdate("patch", sub, msg, "", "", 0, 0));
                }
            };

            try
            {
                domainPatcher.patchClient(clientPath, domainListener);
                progress = startProgress + stepClientDomain;
            }
            catch (Exception e)
            {
                System.err.println("Failed to patch domain in client: " + e.getMessage());
                throw e;
            }
        }

//        if (clientExists)
//        {
           /* final double startProgress = progress;

            if (callback != null)
            {
                callback.onProgress(new ProgressUpdate("patch", startProgress,
                        "Applying online fix...", "", "", 0, 0));
            }

            if (isOnlineFixApplied(version))
            {
                System.out.println("Online fix already applied (backup detected)");
                progress = startProgress + stepOnlineFix;
                if (callback != null)
                {
                    callback.onProgress(new ProgressUpdate("patch", progress,
                            "Online fix already applied", "", "", 0, 0));
                }
            }
            else
            {
                OnlineFixResult result = applyOnlineFix(version, (pct, msg) ->
                {
                    if (callback != null)
                    {
                        double sub = startProgress + (pct / 100.0) * stepOnlineFix;
                        callback.onProgress(new ProgressUpdate("patch", sub, msg, "", "", 0, 0));
                    }
                });

                if (result.success)
                {
                    System.out.println("Online fix applied successfully -> " + result);
                    progress = startProgress + stepOnlineFix;
                }
                else
                {
                    System.err.println("Online fix failed: " + result.message);

                    if (callback != null) {
                        callback.onProgress(new ProgressUpdate("patch", startProgress + stepOnlineFix,
                                "Online fix could not be applied: " + result.message, "", "", 0, 0));
                    }
                }
            }*/
//        }

        if (serverExists)
        {
            final double startProgress = progress;

            if (callback != null)
            {
                callback.onProgress(new ProgressUpdate("patch", startProgress,
                        "Patching domain in server...", "", "", 0, 0));
            }

            DomainPatcher.PatchProgressListener serverListener = (pct, msg) ->
            {
                if (callback != null)
                {
                    double sub = startProgress + (pct / 100.0) * stepServerDomain;
                    callback.onProgress(new ProgressUpdate("patch", sub, msg, "", "", 0, 0));
                }
            };

            try
            {
                domainPatcher.patchServer(serverPath, serverListener);
                progress = startProgress + stepServerDomain;
            }
            catch (Exception e)
            {
                System.err.println("Failed to patch domain in server: " + e.getMessage());
            }
        }

        if (callback != null)
        {
            callback.onProgress(new ProgressUpdate("patch", 100,
                    "All patches completed", "", "", 0, 0));
        }

        System.out.println("ensureGamePatched completed for version: " + version.getName());
    }

    public static class OnlineFixResult
    {
        public final boolean success;
        public final String message;
        public final Path backupPath;

        public OnlineFixResult(boolean success, String message, Path backup) {
            this.success = success;
            this.message = message;
            this.backupPath = backup;
        }

        @Override
        public String toString() {
            return "OnlineFixResult{" +
                    "success=" + success +
                    ", message='" + message + '\'' +
                    ", backup=" + (backupPath != null ? backupPath.getFileName() : "null") +
                    '}';
        }
    }

    public OnlineFixResult applyOnlineFix(GameVersion version, DomainPatcher.PatchProgressListener listener) throws IOException
    {
        if (version == null) {
            return new OnlineFixResult(false, "No game version selected", null);
        }

        Path clientPath = getClientPath(version);
        if (clientPath == null || !Files.exists(clientPath)) {
            return new OnlineFixResult(false, "HytaleClient binary not found", null);
        }

        String os = Environment.getOS().toLowerCase();
        boolean isWindows = os.contains("win") || os.contains("nt");

        byte[] outer, search, replace;
        long startOffset;
        long maxSearch = 50 * 1024 * 1024L;

        if (isWindows) {
            outer  = BinaryPatcher.hexToBytes("53 48 83 ec 50 0f 57 e4 0f 29 64 24 20 0f 29 64");
            search = BinaryPatcher.hexToBytes("53 48 83 ec 50 0f 57 e4 0f 29 64 24 20 0f 29 64");
            replace = BinaryPatcher.hexToBytes("b8 01 00 00 00 c3 57 e4 0f 29 64 24 20 0f 29 64");
            startOffset = 0x639600L;
        } else {
            outer  = BinaryPatcher.hexToBytes("55 53 48 83 EC 38 48 8D 6C 24 40 33 C0 48 89 45");
            search = BinaryPatcher.hexToBytes("55 53 48 83 EC 38 48 8D 6C 24 40 33 C0 48 89 45");
            replace = BinaryPatcher.hexToBytes("b8 01 00 00 00 c3");
            startOffset = 0x800000L;
        }

        var config = new BinaryPatcher.SearchConfig()
                .startAt(startOffset)
                .maxSize(maxSearch)
                .withContext(64, 64);

        BinaryPatcher.ProgressListener progressListener = (long cur, long tot, String msg) ->
        {
            if (listener != null) {
                double pct = tot > 0 ? (double) cur / tot * 100 : 0;
                listener.onProgress(pct, msg);
            }
        };

        Path backup = null;

        try {
            if (listener != null) listener.onProgress(5, "Creating backup...");
            String suffix = ".onlinefix-" + Instant.now().getEpochSecond();
            backup = BinaryPatcher.createBackup(clientPath, suffix);
            if (backup == null) {
                return new OnlineFixResult(false, "Failed to create backup", null);
            }

            if (listener != null) listener.onProgress(20, "Locating online check function...");
            var match = BinaryPatcher.findNestedPattern(clientPath, outer, search, config, progressListener);

            if (match == null)
            {
                BinaryPatcher.restoreBackup(backup, clientPath);
                return new OnlineFixResult(false, "Online check signature not found", backup);
            }

            if (listener != null) {
                listener.onProgress(60, String.format("Found at 0x%X — applying patch...", match.offset));
            }

            boolean ok = BinaryPatcher.replaceBytes(clientPath, match.offset, search, replace);
            if (!ok)
            {
                BinaryPatcher.restoreBackup(backup, clientPath);
                return new OnlineFixResult(false, "Failed to write patch bytes", backup);
            }

            if (!BinaryPatcher.verify(clientPath, match.offset, replace)) {
                BinaryPatcher.restoreBackup(backup, clientPath);
                return new OnlineFixResult(false, "Patch verification failed", backup);
            }

            if (listener != null) listener.onProgress(100, "Online fix applied successfully");

            return new OnlineFixResult(true, "Online fix applied successfully", backup);

        } catch (Exception e)
        {
            if (backup != null && Files.exists(backup)) {
                try { BinaryPatcher.restoreBackup(backup, clientPath); } catch (Exception ignored) {}
            }

            return new OnlineFixResult(false, "Error: " + e.getMessage(), backup);
        }
    }

    public boolean isOnlineFixApplied(GameVersion version)
    {
        Path clientPath = getClientPath(version);
        if (clientPath == null || !Files.exists(clientPath)) {
            return false;
        }

        try (Stream<Path> stream = Files.list(clientPath.getParent()))
        {
            return stream.anyMatch(p -> {
                String name = p.getFileName().toString();
                return name.startsWith(clientPath.getFileName().toString() + ".onlinefix-");
            });
        }
        catch (IOException e) {
            return false;
        }
    }

    public void restoreOriginalGame(GameVersion version) throws Exception
    {
        Path clientPath = getClientPath(version);
        Path serverPath = getServerPath(version);
        DomainPatcher.restoreOriginalGame(clientPath, serverPath);
    }

    public boolean isGamePatched(GameVersion version)
    {
        Path clientPath = getClientPath(version);

        if (clientPath != null && Files.exists(clientPath))
        {
            Path backup = clientPath.resolveSibling(clientPath.getFileName() + ".original");
            return Files.exists(backup);
        }

        return false;
    }

    private Path getGameDirectory(GameVersion version)
    {
        String branch = version.getBranch();
        int patch = version.getPatchNumber();

        String dirName = branch.equals("pre-release")
                ? "patch-pre-" + patch
                : "patch-" + patch;

        return Environment.getDefaultAppDir()
                .resolve("release").resolve("package")
                .resolve("game").resolve(dirName);
    }

    private Path getClientPath(GameVersion version)
    {
        Path dir = getGameDirectory(version).resolve("Client");
        String name = Environment.getOS().toLowerCase().contains("win")
                ? "HytaleClient.exe"
                : "HytaleClient";

        Path path = dir.resolve(name);
        return Files.exists(path) ? path : null;
    }

    private Path getServerPath(GameVersion version)
    {
        Path path = getGameDirectory(version).resolve("Server").resolve("HytaleServer.jar");
        return Files.exists(path) ? path : null;
    }
}