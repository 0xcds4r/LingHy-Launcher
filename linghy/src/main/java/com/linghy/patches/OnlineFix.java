package com.linghy.patches;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.Arrays;

public class OnlineFix
{
    private static final long SEARCH_START = 0x800000L;
    private static final long MAX_SEARCH_SIZE = 50 * 1024 * 1024L;

    ////////////////////////////////////////////////////////////////////
    // outer big badass chunk-pattern
    ////////////////////////////////////////////////////////////////////
    private static final byte[] OUTER_PATTERN = hexToBytes(
            "55 53 48 83 EC 38 48 8D 6C 24 40 33 C0 48 89 45 "
    );

    ////////////////////////////////////////////////////////////////////
    // actual target
    ////////////////////////////////////////////////////////////////////
    private static final byte[] SEARCH_PATTERN = hexToBytes(
            "55 53 48 83 EC 38 48 8D 6C 24 40 33 C0 48 89 45"
    );

    ////////////////////////////////////////////////////////////////////
    // just make a ret, if found
    ////////////////////////////////////////////////////////////////////
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
                listener.onProgress("Searching for pattern context...");
            }

            NestedPatternMatch match = findNestedPattern(targetFile, listener);

            if (match == null)
            {
                restoreBackup(backupFile, targetFile);
                return new PatchResult(false, "Pattern not found in file", backupFile);
            }

            if (listener != null) {
                listener.onProgress(String.format("Pattern found at 0x%X (context at 0x%X, offset +0x%X)",
                        match.innerOffset, match.outerOffset, match.innerRelativePos));
            }

            if (listener != null) {
                listener.onProgress("Applying patch...");
            }

            boolean success = applyPatchAtOffset(targetFile, match.innerOffset);

            if (!success)
            {
                restoreBackup(backupFile, targetFile);
                return new PatchResult(false, "Failed to apply patch", backupFile);
            }

            if (listener != null) {
                listener.onProgress("Verifying patch...");
            }

            if (!verifyPatch(targetFile, match.innerOffset))
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

    private static class NestedPatternMatch
    {
        final long outerOffset;
        final long innerOffset;
        final int innerRelativePos;

        NestedPatternMatch(long outerOffset, long innerOffset, int innerRelativePos)
        {
            this.outerOffset = outerOffset;
            this.innerOffset = innerOffset;
            this.innerRelativePos = innerRelativePos;
        }
    }

    private static NestedPatternMatch findNestedPattern(Path file, PatchProgressListener listener) throws IOException
    {
        long fileSize = Files.size(file);
        long searchEnd = Math.min(fileSize, SEARCH_START + MAX_SEARCH_SIZE);
        long searchSize = searchEnd - SEARCH_START;

        if (searchSize <= 0) {
            return null;
        }

        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r"))
        {
            int bufferSize = (int) Math.min(searchSize, 10 * 1024 * 1024); // 10 MB блоки
            byte[] buffer = new byte[bufferSize];

            long currentPos = SEARCH_START;
            raf.seek(SEARCH_START);

            while (currentPos < searchEnd)
            {
                int toRead = (int) Math.min(bufferSize, searchEnd - currentPos);
                int bytesRead = raf.read(buffer, 0, toRead);

                if (bytesRead <= 0) {
                    break;
                }

                for (int i = 0; i <= bytesRead - OUTER_PATTERN.length; i++)
                {
                    if (matchesPattern(buffer, i, OUTER_PATTERN))
                    {
                        long outerOffset = currentPos + i;

                        if (listener != null) {
                            listener.onProgress(String.format("Found context at 0x%X, searching for target...", outerOffset));
                        }

                        int innerPos = findPatternInArray(buffer, i, OUTER_PATTERN.length, SEARCH_PATTERN);

                        if (innerPos != -1)
                        {
                            long innerOffset = currentPos + innerPos;
                            int relativePos = innerPos - i;

                            if (listener != null) {
                                listener.onProgress(String.format("Target found at +0x%X from context start", relativePos));
                            }

                            return new NestedPatternMatch(outerOffset, innerOffset, relativePos);
                        }
                        else
                        {
                            if (listener != null) {
                                listener.onProgress("Context found but target pattern missing inside it");
                            }
                        }
                    }
                }

                if (listener != null && searchSize > 0)
                {
                    int progress = (int) ((currentPos - SEARCH_START) * 100 / searchSize);
                    if (progress > 0 && progress < 100) {
                        listener.onProgress(String.format("Scanning... %d%%", progress));
                    }
                }

                currentPos += bytesRead - OUTER_PATTERN.length + 1;
                raf.seek(currentPos);
            }
        }

        return null;
    }

    private static boolean matchesPattern(byte[] data, int offset, byte[] pattern)
    {
        if (offset + pattern.length > data.length) {
            return false;
        }

        for (int i = 0; i < pattern.length; i++)
        {
            if (data[offset + i] != pattern[i]) {
                return false;
            }
        }

        return true;
    }

    private static int findPatternInArray(byte[] data, int start, int length, byte[] pattern)
    {
        int end = Math.min(start + length, data.length);

        for (int i = start; i <= end - pattern.length; i++)
        {
            if (matchesPattern(data, i, pattern)) {
                return i;
            }
        }

        return -1;
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