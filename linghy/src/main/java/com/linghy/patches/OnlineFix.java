package com.linghy.patches;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.Arrays;

public class OnlineFix
{
    /*
     *   Ugh, one fuckin address offset and it'll stop working.
     *   But so far there seem to be no problems, and the pattern doesn't seem to be changing.
     * */

    private static final long SEARCH_START = 0x80DB40L;
    private static final long SEARCH_END = 0x80DB90L;

    private static final byte[] SEARCH_PATTERN = hexToBytes(
            "55 53 48 83 ec 38 48 8d 6c 24 40 33 c0 48 89 45"
    );

    private static final byte[] REPLACE_PATTERN = hexToBytes(
            "b8 01 00 00 00 c3"
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
        if (!Files.exists(targetFile)) {
            return new PatchResult(false, "File not found: " + targetFile, null);
        }

        if (listener != null) {
            listener.onProgress("Creating backup...");
        }

        Path backupFile = createBackup(targetFile);
        if (backupFile == null) {
            return new PatchResult(false, "Failed to create backup", null);
        }

        try
        {
            if (listener != null) {
                listener.onProgress("Searching for pattern...");
            }

            long offset = findPattern(targetFile);

            if (offset == -1)
            {
                restoreBackup(backupFile, targetFile);
                return new PatchResult(false, "Pattern not found in specified range", backupFile);
            }

            if (listener != null) {
                listener.onProgress(String.format("Pattern found at 0x%X", offset));
            }

            if (listener != null) {
                listener.onProgress("Applying patch...");
            }

            boolean success = applyPatchAtOffset(targetFile, offset);

            if (!success)
            {
                restoreBackup(backupFile, targetFile);
                return new PatchResult(false, "Failed to apply patch", backupFile);
            }

            if (listener != null) {
                listener.onProgress("Verifying patch...");
            }

            if (!verifyPatch(targetFile, offset))
            {
                restoreBackup(backupFile, targetFile);
                return new PatchResult(false, "Patch verification failed", backupFile);
            }

            if (listener != null) {
                listener.onProgress("Patch applied successfully!");
            }

            return new PatchResult(true, "Patch applied successfully", backupFile);
        }
        catch (IOException e)
        {
            restoreBackup(backupFile, targetFile);
            throw e;
        }
    }

    private static long findPattern(Path file) throws IOException
    {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r"))
        {
            long rangeLen = SEARCH_END - SEARCH_START;
            byte[] buffer = new byte[(int) rangeLen];

            raf.seek(SEARCH_START);
            int bytesRead = raf.read(buffer);

            if (bytesRead != rangeLen) {
                return -1;
            }

            for (int i = 0; i <= buffer.length - SEARCH_PATTERN.length; i++)
            {
                boolean found = true;

                for (int j = 0; j < SEARCH_PATTERN.length; j++)
                {
                    if (buffer[i + j] != SEARCH_PATTERN[j])
                    {
                        found = false;
                        break;
                    }
                }

                if (found) {
                    return SEARCH_START + i;
                }
            }

            return -1;
        }
    }

    private static boolean applyPatchAtOffset(Path file, long offset) throws IOException
    {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw"))
        {
            raf.seek(offset);

            byte[] originalData = new byte[SEARCH_PATTERN.length];
            int bytesRead = raf.read(originalData);

            if (bytesRead != SEARCH_PATTERN.length) {
                return false;
            }

            if (!Arrays.equals(originalData, SEARCH_PATTERN)) {
                return false;
            }

            raf.seek(offset);
            raf.write(REPLACE_PATTERN);

            if (REPLACE_PATTERN.length < SEARCH_PATTERN.length)
            {
                int paddingLen = SEARCH_PATTERN.length - REPLACE_PATTERN.length;
                byte[] padding = new byte[paddingLen];
                raf.write(padding);
            }

            return true;
        }
    }

    private static boolean verifyPatch(Path file, long offset) throws IOException
    {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r"))
        {
            raf.seek(offset);

            byte[] readData = new byte[REPLACE_PATTERN.length];
            int bytesRead = raf.read(readData);

            if (bytesRead != REPLACE_PATTERN.length) {
                return false;
            }

            return Arrays.equals(readData, REPLACE_PATTERN);
        }
    }

    private static Path createBackup(Path file) throws IOException
    {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        Path backupPath = file.resolveSibling(file.getFileName() + ".backup." + timestamp);

        try {
            Files.copy(file, backupPath, StandardCopyOption.REPLACE_EXISTING);
            return backupPath;
        } catch (IOException e) {
            System.err.println("Failed to create backup: " + e.getMessage());
            return null;
        }
    }

    private static void restoreBackup(Path backup, Path target) throws IOException
    {
        if (backup != null && Files.exists(backup)) {
            Files.copy(backup, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void deleteBackup(Path backup) throws IOException
    {
        if (backup != null && Files.exists(backup)) {
            Files.delete(backup);
        }
    }

    private static byte[] hexToBytes(String hex)
    {
        hex = hex.replaceAll("\\s+", "");
        int len = hex.length();
        byte[] data = new byte[len / 2];

        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }

        return data;
    }

    private static String bytesToHex(byte[] bytes)
    {
        StringBuilder sb = new StringBuilder();

        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }

        return sb.toString().trim();
    }

    public interface PatchProgressListener {
        void onProgress(String message);
    }
}