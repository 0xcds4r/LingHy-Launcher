package com.linghy.patches;

import java.io.IOException;
import java.nio.file.Path;

public class OnlineFixWin
{
    private static final byte[] OUTER_PATTERN = BinaryPatcher.hexToBytes(
            "53 48 83 ec 50 0f 57 e4 0f 29 64 24 20 0f 29 64"
    );

    private static final byte[] SEARCH_PATTERN = BinaryPatcher.hexToBytes(
            "53 48 83 ec 50 0f 57 e4 0f 29 64 24 20 0f 29 64"
    );

    private static final byte[] REPLACE_PATTERN = BinaryPatcher.hexToBytes(
            "b8 01 00 00 00 c3 57 e4 0f 29 64 24 20 0f 29 64"
    );

    public static class PatchResult
    {
        public final boolean success;
        public final String message;
        public final Path backupFile;

        public PatchResult(boolean success, String message, Path backupFile)
        {
            this.success = success;
            this.message = message;
            this.backupFile = backupFile;
        }
    }

    public static PatchResult applyPatch(Path targetFile, PatchProgressListener listener) throws IOException
    {
        BinaryPatcher.SearchConfig config = new BinaryPatcher.SearchConfig()
                .startAt(0x639600L)
                .maxSize(50 * 1024 * 1024L)
                .withContext(64, 64);

        BinaryPatcher.ProgressListener progressWrapper = (current, total, msg) -> {
            if (listener != null) {
                listener.onProgress(msg);
            }
        };

        try
        {
            if (listener != null) {
                listener.onProgress("Creating backup...");
            }

            Path backupFile = BinaryPatcher.createBackup(targetFile);
            if (backupFile == null) {
                return new PatchResult(false, "Failed to create backup", null);
            }

            if (listener != null) {
                listener.onProgress("Searching for pattern...");
            }

            BinaryPatcher.PatternMatch match = BinaryPatcher.findNestedPattern(
                    targetFile, OUTER_PATTERN, SEARCH_PATTERN, config, progressWrapper
            );

            if (match == null)
            {
                BinaryPatcher.restoreBackup(backupFile, targetFile);
                return new PatchResult(false, "Pattern not found", backupFile);
            }

            if (listener != null) {
                listener.onProgress(String.format("Pattern found at 0x%X", match.offset));
            }

            if (listener != null) {
                listener.onProgress("Applying patch...");
            }

            boolean success = BinaryPatcher.replaceBytes(
                    targetFile, match.offset, SEARCH_PATTERN, REPLACE_PATTERN
            );

            if (!success)
            {
                BinaryPatcher.restoreBackup(backupFile, targetFile);
                return new PatchResult(false, "Failed to apply patch", backupFile);
            }

            if (listener != null) {
                listener.onProgress("Verifying patch...");
            }

            if (!BinaryPatcher.verify(targetFile, match.offset, REPLACE_PATTERN))
            {
                BinaryPatcher.restoreBackup(backupFile, targetFile);
                return new PatchResult(false, "Patch verification failed", backupFile);
            }

            if (listener != null) {
                listener.onProgress("Patch applied successfully!");
            }

            return new PatchResult(true, "Patch applied successfully", backupFile);
        }
        catch (IOException e)
        {
            return new PatchResult(false, "Error: " + e.getMessage(), null);
        }
    }

    public interface PatchProgressListener {
        void onProgress(String message);
    }
}