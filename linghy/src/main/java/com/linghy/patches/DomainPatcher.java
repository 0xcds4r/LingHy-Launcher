package com.linghy.patches;

import com.linghy.patches.BinaryPatcher;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public class DomainPatcher
{
    private static final String ORIGINAL_DOMAIN = "hytale.com";
    private static final int MIN_DOMAIN_LENGTH = 4;
    private static final int MAX_DOMAIN_LENGTH = 10;
    private static final String DEFAULT_TARGET_DOMAIN = "sanasol.ws";

    private final String targetDomain;

    public DomainPatcher(String targetDomain)
    {
        if (targetDomain == null || targetDomain.length() < MIN_DOMAIN_LENGTH ||
                targetDomain.length() > MAX_DOMAIN_LENGTH)
        {
            System.err.printf("Warning: Domain '%s' is invalid (must be %d-%d chars), using default: %s%n",
                    targetDomain, MIN_DOMAIN_LENGTH, MAX_DOMAIN_LENGTH, DEFAULT_TARGET_DOMAIN);
            this.targetDomain = DEFAULT_TARGET_DOMAIN;
        }
        else
        {
            this.targetDomain = targetDomain;
        }
    }

    private byte[] stringToLengthPrefixed(String str)
    {
        int length = str.length();
        byte[] result = new byte[4 + length + (length - 1)];

        result[0] = (byte) length;

        int pos = 4;
        for (int i = 0; i < length; i++)
        {
            result[pos++] = (byte) str.charAt(i);
            if (i < length - 1)
            {
                result[pos++] = 0x00;
            }
        }

        return result;
    }

    private byte[] stringToUTF16LE(String str)
    {
        ByteBuffer buffer = ByteBuffer.allocate(str.length() * 2);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        for (char c : str.toCharArray())
        {
            buffer.putChar(c);
        }

        return buffer.array();
    }

    private byte[] stringToUTF8(String str)
    {
        return str.getBytes(StandardCharsets.UTF_8);
    }

    public PatchResult findAndReplaceDomainUTF8(byte[] data, String oldDomain, String newDomain)
    {
        byte[] oldUtf8 = stringToUTF8(oldDomain);
        byte[] newUtf8 = stringToUTF8(newDomain);

        if (newUtf8.length > oldUtf8.length)
        {
            System.err.printf("  Skipping UTF-8 patch: new domain too long (%d > %d)%n",
                    newUtf8.length, oldUtf8.length);
            return new PatchResult(data, 0);
        }

        byte[] result = Arrays.copyOf(data, data.length);
        List<Integer> positions = findAllOccurrences(result, oldUtf8);
        int count = 0;

        for (int pos : positions)
        {
            if (pos + oldUtf8.length <= result.length)
            {
                System.arraycopy(newUtf8, 0, result, pos, newUtf8.length);

                for (int i = newUtf8.length; i < oldUtf8.length; i++)
                {
                    result[pos + i] = 0x00;
                }

                count++;
                System.out.printf("  Patched UTF-8 occurrence at offset 0x%x%n", pos);
            }
        }

        return new PatchResult(result, count);
    }

    public PatchResult findAndReplaceDomainSmart(byte[] data, String oldDomain, String newDomain)
    {
        if (newDomain.length() > oldDomain.length())
        {
            System.err.printf("  Skipping UTF-16LE patch: new domain too long (%d > %d)%n",
                    newDomain.length(), oldDomain.length());
            return new PatchResult(data, 0);
        }

        byte[] result = Arrays.copyOf(data, data.length);

        String oldPrefix = oldDomain.substring(0, oldDomain.length() - 1);
        String newPrefix = newDomain.substring(0, newDomain.length() - 1);

        byte[] oldUtf16NoLast = stringToUTF16LE(oldPrefix);
        byte[] newUtf16NoLast = stringToUTF16LE(newPrefix);

        byte oldLastCharByte = (byte) oldDomain.charAt(oldDomain.length() - 1);
        byte newLastCharByte = (byte) newDomain.charAt(newDomain.length() - 1);

        List<Integer> positions = findAllOccurrences(result, oldUtf16NoLast);
        int count = 0;

        for (int pos : positions)
        {
            int lastCharPos = pos + oldUtf16NoLast.length;
            if (lastCharPos + 1 > result.length)
            {
                continue;
            }

            if (result[lastCharPos] != oldLastCharByte)
            {
                continue;
            }

            System.arraycopy(newUtf16NoLast, 0, result, pos, newUtf16NoLast.length);
            result[lastCharPos] = newLastCharByte;

            if (newDomain.length() < oldDomain.length())
            {
                byte[] newFull = stringToUTF16LE(newDomain);
                byte[] oldFull = stringToUTF16LE(oldDomain);

                int paddingStart = pos + newFull.length;
                int paddingEnd = pos + oldFull.length;

                for (int i = paddingStart; i < paddingEnd && i < result.length; i++)
                {
                    result[i] = 0x00;
                }
            }

            boolean isNullTerminated = (lastCharPos + 1 < result.length) &&
                    (result[lastCharPos + 1] == 0x00);

            if (isNullTerminated)
            {
                System.out.printf("  Patched UTF-16LE occurrence at offset 0x%x%n", pos);
            }
            else
            {
                System.out.printf("  Patched length-prefixed occurrence at offset 0x%x%n", pos);
            }

            count++;
        }

        return new PatchResult(result, count);
    }

    public PatchResult applyDomainPatches(byte[] data, String protocol)
    {
        byte[] result = Arrays.copyOf(data, data.length);
        int totalCount = 0;

        System.out.printf("  Replacing %s -> %s%n", ORIGINAL_DOMAIN, targetDomain);

        String oldSentry = "https://ca900df42fcf57d4dd8401a86ddd7da2@sentry.hytale.com/2";
        String newSentry = String.format("%st@%s/2", protocol, targetDomain);

        PatchResult sentryResult = replaceBytes(result,
                stringToLengthPrefixed(oldSentry),
                stringToLengthPrefixed(newSentry));
        result = sentryResult.data;
        if (sentryResult.count > 0)
        {
            System.out.printf("  Patched sentry URL: %d occurrences%n", sentryResult.count);
            totalCount += sentryResult.count;
        }

        String[] subdomains = {"", "sessions.", "account-data.", "gameservers."};
        for (String sub : subdomains)
        {
            String oldUrl = protocol + sub + ORIGINAL_DOMAIN;
            String newUrl = protocol + targetDomain;

            PatchResult urlResult = replaceBytes(result,
                    stringToLengthPrefixed(oldUrl),
                    stringToLengthPrefixed(newUrl));
            result = urlResult.data;
            if (urlResult.count > 0)
            {
                System.out.printf("  Patched URL %s: %d occurrences%n", oldUrl, urlResult.count);
                totalCount += urlResult.count;
            }
        }

        PatchResult domainResult = replaceBytes(result,
                stringToLengthPrefixed(ORIGINAL_DOMAIN),
                stringToLengthPrefixed(targetDomain));
        result = domainResult.data;
        if (domainResult.count > 0)
        {
            System.out.printf("  Patched bare domain: %d occurrences%n", domainResult.count);
            totalCount += domainResult.count;
        }

        PatchResult utf16Result = findAndReplaceDomainSmart(result, ORIGINAL_DOMAIN, targetDomain);
        result = utf16Result.data;
        if (utf16Result.count > 0)
        {
            System.out.printf("  Patched UTF-16LE: %d occurrences%n", utf16Result.count);
            totalCount += utf16Result.count;
        }

        return new PatchResult(result, totalCount);
    }

    public void patchClient(Path clientPath, PatchProgressListener listener) throws IOException
    {
        System.out.println("=== Client Patcher ===");
        System.out.printf("Target: %s%n", clientPath);
        System.out.printf("Domain: %s (%d chars)%n", targetDomain, targetDomain.length());

        if (!Files.exists(clientPath))
        {
            throw new IOException("Client binary not found: " + clientPath);
        }

        if (listener != null)
        {
            listener.onProgress(10, "Reading client binary...");
        }

        byte[] data = Files.readAllBytes(clientPath);

        if (listener != null)
        {
            listener.onProgress(30, "Applying patches...");
        }

        PatchResult result = applyDomainPatches(data, "https://");

        if (result.count == 0)
        {
            System.out.println("No patches applied (already patched or no matches found)");
            return;
        }

        if (listener != null)
        {
            listener.onProgress(70, "Writing patched binary...");
        }

        Path backupPath = clientPath.resolveSibling(clientPath.getFileName() + ".original");
        if (!Files.exists(backupPath))
        {
            Files.copy(clientPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
        }

        Files.write(clientPath, result.data, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);

        if (!System.getProperty("os.name").toLowerCase().contains("win")) {
            clientPath.toFile().setExecutable(true, false);
        }

        if (listener != null)
        {
            listener.onProgress(100, String.format("Client patched (%d occurrences)", result.count));
        }

        System.out.printf("Client patched successfully (%d total occurrences)%n", result.count);
    }

    public void patchServer(Path serverPath, PatchProgressListener listener) throws IOException
    {
        System.out.println("=== Server Patcher ===");
        System.out.printf("Target: %s%n", serverPath);
        System.out.printf("Domain: %s (%d chars)%n", targetDomain, targetDomain.length());

        if (!Files.exists(serverPath))
        {
            throw new IOException("Server JAR not found: " + serverPath);
        }

        if (listener != null)
        {
            listener.onProgress(10, "Opening server JAR...");
        }

        Path tempPath = serverPath.resolveSibling(serverPath.getFileName() + ".tmp");

        try (ZipFile zipReader = new ZipFile(serverPath.toFile());
             ZipOutputStream zipWriter = new ZipOutputStream(
                     Files.newOutputStream(tempPath, StandardOpenOption.CREATE,
                             StandardOpenOption.TRUNCATE_EXISTING)))
        {
            int totalCount = 0;
            byte[] oldUtf8 = stringToUTF8(ORIGINAL_DOMAIN);

            Enumeration<? extends ZipEntry> entries = zipReader.entries();
            List<? extends ZipEntry> entryList = Collections.list(entries);

            if (listener != null)
            {
                listener.onProgress(30, "Patching JAR entries...");
            }

            int entryIndex = 0;
            for (ZipEntry entry : entryList)
            {
                if (listener != null && entryIndex % 100 == 0)
                {
                    int pct = 30 + (40 * entryIndex / entryList.size());
                    listener.onProgress(pct, "Patching JAR entries...");
                }
                entryIndex++;

                byte[] data;
                try (InputStream is = zipReader.getInputStream(entry))
                {
                    data = is.readAllBytes();
                }

                boolean shouldPatch = entry.getName().endsWith(".class") ||
                        entry.getName().endsWith(".properties") ||
                        entry.getName().endsWith(".json") ||
                        entry.getName().endsWith(".xml") ||
                        entry.getName().endsWith(".yml");

                if (shouldPatch && contains(data, oldUtf8))
                {
                    PatchResult patchResult = findAndReplaceDomainUTF8(data, ORIGINAL_DOMAIN, targetDomain);
                    if (patchResult.count > 0)
                    {
                        data = patchResult.data;
                        totalCount += patchResult.count;
                    }
                }

                ZipEntry newEntry = new ZipEntry(entry.getName());
                newEntry.setTime(entry.getTime());
                zipWriter.putNextEntry(newEntry);
                zipWriter.write(data);
                zipWriter.closeEntry();
            }

            if (listener != null)
            {
                listener.onProgress(80, "Finalizing patched JAR...");
            }

            zipWriter.close();

            if (totalCount > 0)
            {
                Path backupPath = serverPath.resolveSibling(serverPath.getFileName() + ".original");
                if (!Files.exists(backupPath))
                {
                    Files.move(serverPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
                }
                else
                {
                    Files.delete(serverPath);
                }

                Files.move(tempPath, serverPath, StandardCopyOption.REPLACE_EXISTING);

                if (listener != null)
                {
                    listener.onProgress(100, String.format("Server patched (%d occurrences)", totalCount));
                }

                System.out.printf("Server patched successfully (%d total occurrences)%n", totalCount);
            }
            else
            {
                Files.deleteIfExists(tempPath);
                System.out.println("No patches applied (already patched or no matches found)");
            }
        }
    }

    private List<Integer> findAllOccurrences(byte[] data, byte[] pattern)
    {
        List<Integer> positions = new ArrayList<>();

        for (int i = 0; i <= data.length - pattern.length; i++)
        {
            boolean found = true;
            for (int j = 0; j < pattern.length; j++)
            {
                if (data[i + j] != pattern[j])
                {
                    found = false;
                    break;
                }
            }

            if (found)
            {
                positions.add(i);
            }
        }

        return positions;
    }

    private PatchResult replaceBytes(byte[] data, byte[] oldBytes, byte[] newBytes)
    {
        if (newBytes.length > oldBytes.length)
        {
            System.err.printf("  Warning: New pattern (%d) longer than old (%d), skipping%n",
                    newBytes.length, oldBytes.length);
            return new PatchResult(data, 0);
        }

        byte[] result = Arrays.copyOf(data, data.length);
        List<Integer> positions = findAllOccurrences(result, oldBytes);

        for (int pos : positions)
        {
            System.arraycopy(newBytes, 0, result, pos, newBytes.length);

            for (int i = newBytes.length; i < oldBytes.length; i++)
            {
                result[pos + i] = 0x00;
            }
        }

        return new PatchResult(result, positions.size());
    }

    private boolean contains(byte[] data, byte[] pattern)
    {
        for (int i = 0; i <= data.length - pattern.length; i++)
        {
            boolean found = true;
            for (int j = 0; j < pattern.length; j++)
            {
                if (data[i + j] != pattern[j])
                {
                    found = false;
                    break;
                }
            }

            if (found)
            {
                return true;
            }
        }

        return false;
    }

    public static class PatchResult
    {
        public final byte[] data;
        public final int count;

        public PatchResult(byte[] data, int count)
        {
            this.data = data;
            this.count = count;
        }
    }

    public interface PatchProgressListener
    {
        void onProgress(double percent, String message);
    }

    public static void restoreOriginalGame(Path clientPath, Path serverPath) throws IOException
    {
        int restored = 0;

        if (clientPath != null)
        {
            Path backupPath = clientPath.resolveSibling(clientPath.getFileName() + ".original");
            if (Files.exists(backupPath))
            {
                Files.delete(clientPath);
                Files.move(backupPath, clientPath, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Restored original client binary");
                restored++;
            }
        }

        if (serverPath != null)
        {
            Path backupPath = serverPath.resolveSibling(serverPath.getFileName() + ".original");
            if (Files.exists(backupPath))
            {
                Files.delete(serverPath);
                Files.move(backupPath, serverPath, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Restored original server JAR");
                restored++;
            }
        }

        if (restored == 0)
        {
            throw new IOException("No backups found to restore");
        }

        System.out.printf("Restored %d original file(s)%n", restored);
    }
}